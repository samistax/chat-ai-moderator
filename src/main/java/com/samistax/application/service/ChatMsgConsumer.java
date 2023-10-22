package com.samistax.application.service;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.samistax.application.ai.ChatHistory;
import com.samistax.application.ai.ContentPolicyEnforcement;
import com.samistax.astra.entity.ChatMsg;
import com.samistax.astra.entity.ChatMsgKey;
import com.datastax.astra.sdk.AstraClient;
import com.samistax.astra.entity.PulsarChatMessage;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.model.Permission;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationResult;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.model.Model;
import com.theokanning.openai.service.OpenAiService;
import com.vaadin.collaborationengine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.awt.desktop.SystemSleepEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ChatMsgConsumer {

    private final Sinks.Many<PulsarChatMessage> chatMsgSink = Sinks.many().multicast().directBestEffort();
    private final Flux<PulsarChatMessage> chatMessages = chatMsgSink.asFlux();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static CollaborationEngine ce;

    private final ChatMsgRepository repository;
    private final OpenAIClient aiClient;

    @Autowired
    private AstraClient astraClient;

    public ChatMsgConsumer( ChatMsgRepository repository,OpenAIClient aiClient) {
        this.repository = repository;
        this.aiClient = aiClient;

    }

    public static CollaborationEngine getCe() {
        return ce;
    }

    public static void setCe(CollaborationEngine ce) {
        ChatMsgConsumer.ce = ce;
    }

    @PulsarListener
    private void chatMessageReceived(PulsarChatMessage chatMsg) {
        chatMsgSink.tryEmitNext(chatMsg);

        OpenAiService openAI = aiClient.getService();
        List<Model> availableModels = openAI.listModels();
        for (Model m: availableModels) {
            for (Permission p: m.getPermission()) {
                if (p.allowFineTuning) {
                    System.out.println(m.getId() + " allows Finetuning");
                }
            }
        }
        System.out.println(" - chatMessageReceived: " + chatMsg.getTime());
        UserInfo info = new UserInfo(chatMsg.getUserId(), chatMsg.getUserName(), chatMsg.getUserImage());
        String topicId = chatMsg.getTopicId();
        Instant msgTimestamp = Instant.ofEpochMilli(chatMsg.getTime());
        ChatMsgKey key = new ChatMsgKey(topicId, msgTimestamp);
        ChatMsg msg = new ChatMsg(key);

        // Use Open AI to Moderate the user message
        ModerationResult modResult = chatMsg.getModerationResult();

        List<Moderation> mods = modResult.getResults();
        List<String> flaggedCategories = getFlaggedCategories(mods);
        UserInfo chatUser = new UserInfo(chatMsg.getUserId(), chatMsg.getUserName());

        long startTime = System.currentTimeMillis();

        // If moderator API resulted in flagged content take action
        if ( flaggedCategories.size() > 0 ) {
            chatUser = new UserInfo("0", "AI Moderator");
            publishChatMessage(chatUser, topicId, "Your message is violating content policy and thus was not published in chat room.", msgTimestamp);

            String category = flaggedCategories.get(0);
            System.out.println("Flagged cagtegory:" + category);
            // Use ChatGPT to explain customer what proprietary content policy rules the end user is violating
            String generatedMsg = "";
            if ( topicId.endsWith("finetuned") ) {
                generatedMsg = callFinetunedChatAPI("End user's message has been flagged by AI-based moderator for category: {"+category+"}. The message was: {"+chatMsg.getText()+"}. Write an message informing the end user which content policy rules were violated and what content policy enforcement actions will be taken by the company: DataStax.");
            } else {
                generatedMsg = callChatAPI("End user's message has been flagged by AI-based moderator for category: {"+category+"}. The message was: {"+chatMsg.getText()+"}. Write an message informing the end user which content policy rules were violated and what content policy enforcement actions will be taken by the company: DataStax.");

            }
            logger.info("generatedMsg = " + generatedMsg);
            printRequestDuration("callChatAPI Prompt total -> ",startTime);
            // Write OpenAI ChatGPT response to chat channel
            publishChatMessage(chatUser, topicId,generatedMsg, msgTimestamp);
        } else {
            // Submit the none toxic message to chat view
            if ( ce != null ) {

                ConnectionContext context = ce.getSystemContext();
                MessageManager messageManager = new MessageManager(context, info, chatMsg.getTopicId(), ce);
                CollaborationMessage newMessage = new CollaborationMessage(info, chatMsg.getText(), key.getTime());
                messageManager.submit(newMessage);

                startTime = System.currentTimeMillis();
                // Save the filtered message to database
                if (repository != null) {
                    msg.setText(chatMsg.getText());
                    // Store user info
                    msg.setUserId(info.getId());
                    msg.setUserName(info.getName());
                    msg.setUserImage(info.getImage());
                    msg.setUserAbbreviation(info.getAbbreviation());
                    msg.setUserColorIndex(info.getColorIndex());

                    repository.save(msg);
                }
                printRequestDuration("chatMessageReceived: repository.save(msg): ",startTime);
                // If user in chatbot channel relay user prompt to LLM and publish also the response to chat
                if ( topicId.endsWith("ChatBot") ) {
                    Instant timestamp = Instant.now();
                    String promptResponse = callChatCompletion(info.getId(), chatMsg.getText(), 10);
                    UserInfo botUser = new UserInfo("0", "system");
                    CollaborationMessage botMessage = new CollaborationMessage(botUser, promptResponse,timestamp);
                    messageManager.submit(botMessage);

                    // Persist message
                    key = new ChatMsgKey(topicId, timestamp);
                    msg = new ChatMsg(key);
                    msg.setText(promptResponse);
                    // Store user info
                    msg.setUserId(botUser.getId());
                    msg.setUserName(botUser.getName());
                    msg.setUserImage(botUser.getImage());
                    msg.setUserAbbreviation(botUser.getAbbreviation());
                    msg.setUserColorIndex(botUser.getColorIndex());

                    repository.save(msg);
                }
            }
        }
    }
    private List<String> getFlaggedCategories(List<Moderation> moderations) {

        List<String> flaggedCategories = new ArrayList<>();
        if ( moderations.size() > 0) {
            Moderation mod = moderations.get(0);
            if (mod.getCategories().isHate()) {
                flaggedCategories.add("hate");
            }
            if (mod.getCategories().isHateThreatening()) {
                flaggedCategories.add("hate threatening");
            }
            if (mod.getCategories().isSelfHarm()) {
                flaggedCategories.add("self harm");
            }
            if (mod.getCategories().isSexual()) {
                flaggedCategories.add("sexual");
            }
            if (mod.getCategories().isViolence()) {
                flaggedCategories.add("violence");
            }
            if (mod.getCategories().isSexualMinors()) {
                flaggedCategories.add("sexual minors");
            }
            if (mod.getCategories().isViolenceGraphic()) {
                flaggedCategories.add("violence");
            }
            if (mod.isFlagged()) {
                flaggedCategories.add("flagged");
            }
        }
        return flaggedCategories;
    }
    private void printRequestDuration(String methodName, long startTime) {
        logger.info(methodName+" request (ms): " + (System.currentTimeMillis() - startTime));
    }

    public Flux<PulsarChatMessage> getChatMessages() {
        return chatMessages;
    }

    private void publishChatMessage(UserInfo user, String topicId, String msg, Instant timestamp ) {
        ConnectionContext context = ce.getSystemContext();
        MessageManager messageManager = new MessageManager(context, user, topicId, ce);

        CollaborationMessage newMessage = new CollaborationMessage(user, msg, timestamp);
        messageManager.submit(newMessage);
    }

    // Open AI related functionality below
    private List<ChatFunction> getFunctionList(OpenAIClient aiClient) {

        ChatFunction contentPolicyRules = ChatFunction.builder()
                .name("contentPolicyRules")
                .description("The proprietary content policy rules")
                .executor(ContentPolicyEnforcement.class, r -> r.getContentPolicyEnforcementActions(astraClient,aiClient, r.category))
                .build();

        return Arrays.asList( contentPolicyRules);
    }

    private String callChatAPI(String prompt) {

        FunctionExecutor functionExecutor = new FunctionExecutor(getFunctionList(aiClient));

        String response = "";
        boolean functionsCalled;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user",prompt));

        do {
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    .model("gpt-3.5-turbo-0613")
                    .functions(functionExecutor.getFunctions())
                    .functionCall(new ChatCompletionRequest.ChatCompletionRequestFunctionCall("auto"))
                    .maxTokens(256)
                    .build();

            System.out.println("callChatAPI: model used ->  "+ completionRequest.getModel());
            System.out.println("callChatAPI: user msg ->  "+ messages.toString());

            List<ChatCompletionChoice> completionChoices = aiClient.createChatCompletion(completionRequest).getChoices();
            if (completionChoices.stream().anyMatch(it -> it.getMessage().getFunctionCall() != null)) {
                functionsCalled = true;
                // we have choices which include function calls
                List<ChatCompletionChoice> filteredCompletionChoices = completionChoices.stream().filter(it -> it.getMessage().getFunctionCall() != null).collect(Collectors.toList());
                for (ChatCompletionChoice filteredChoice : filteredCompletionChoices) {
                    System.out.println("Chat Completion : " + filteredChoice.toString());
                    ChatFunctionCall functionCall = filteredChoice.getMessage().getFunctionCall();
                    System.out.println("Making function call to : " + functionCall.getName());
                    ChatMessage functionResponseMessage = functionExecutor.executeAndConvertToMessageHandlingExceptions(functionCall);
                    messages.add(functionResponseMessage);
                }
            } else {
                functionsCalled = false;
                // final response
                response = completionChoices.get(0).getMessage().getContent();
            }
        } while (functionsCalled);

        return response;
    }
    private String callFinetunedChatAPI(String prompt) {

        String response = "";
        boolean functionsCalled;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user",prompt));
        try {


            // Build Chat Completion Request
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    // Use finetuned model id instead of default: gpt-3.5-turbo-0613
                    .model("ft:gpt-3.5-turbo-0613:personal:samitest:7z1qvSfo")
                    // Skip RAG and callback functions, rely only on finetuned model
                    //.functions(functionExecutor.getFunctions())
                    //.functionCall(new ChatCompletionRequest.ChatCompletionRequestFunctionCall("auto"))
                    .maxTokens(256)
                    .build();

            System.out.println("callFinetunedChatAPI: model used ->  "+ completionRequest.getModel());
            System.out.println("callFinetunedChatAPI: user msg ->  "+ messages.toString());

            List<ChatCompletionChoice> completionChoices = aiClient.createChatCompletion(completionRequest).getChoices();
            // Response
            response = completionChoices.get(0).getMessage().getContent();
        } catch ( Exception ex) {
            System.out.println("Exception: " + ex);
        }
        return response;
    }
    private String callChatCompletion(String userId, String prompt, int memorySize) {

        String response = "";
        List<ChatMessage> messages = new ArrayList<>();

        // Start by giving prompt instructions
        messages.add(new ChatMessage("system", "You are a nice chatbot having a conversation with a human. Use chatHistory function to access past conversation or if the user asks something personal."));

        // Retrieve last messages from database ( fetch memory items for LLM)
        ResultSet results = astraClient.cqlSession().execute("select text, time, userid, username from chat_message LIMIT " + memorySize);
        results.forEach(row -> {
            String username = row.getString("username");
            String usermsg = row.getString("text");
            messages.add(new ChatMessage("user",username+" said: "+usermsg));
            //System.out.println(username+" -> "+usermsg);
        });

        // add user input as the last message
        ChatMessage userPrompt = new ChatMessage("user", prompt);
        userPrompt.setName(userId);
        messages.add(userPrompt);

        try {
            // Build Chat Completion Request
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    .model("gpt-3.5-turbo")
                    .maxTokens(256)
                    .build();
            List<ChatCompletionChoice> completionChoices = aiClient.createChatCompletion(completionRequest).getChoices();
            response = completionChoices.get(0).getMessage().getContent();

            System.out.println("callChatCompletion: model used ->  " + completionRequest.getModel());
            System.out.println("callChatCompletion: user msg ->  " + messages.toString());

        } catch (Exception ex) {
            System.out.println("Exception: " + ex);
            response = ex.getMessage();
        }
        return response;
    }
    private String callChatCompletionWithCallbackFunction(String userId, String prompt, int memorySize) {

        String response = "";
        List<ChatMessage> messages = new ArrayList<>();

        // Start by giving prompt instructions
        messages.add(new ChatMessage("system", "You are a nice chatbot having a conversation with a human. Use chatHistory function to access past conversation or if the user asks something personal."));

        boolean functionsCalled = false;
        ChatFunction chatHistory = ChatFunction.builder()
                .name("chatHistory")
                .description("Access previous conversation with the user to answer personal questions or to questions referring to earlier conversation")
                .executor(ChatHistory.class, r -> r.getChatHistory(astraClient, userId, 10))
                .build();
        ChatFunction personalInformation = ChatFunction.builder()
                .name("personalInformation")
                .description("Access to user personal information. Use this to search for personal information like name, age, hobbies, etc ")
                .executor(ChatHistory.class, r -> r.getChatHistory(astraClient, userId, 10))
                .build();
        FunctionExecutor functionExecutor = new FunctionExecutor(Arrays.asList(chatHistory, personalInformation));

        // add user input as the last message
        ChatMessage userPrompt = new ChatMessage("user", prompt);
        userPrompt.setName(userId);
        messages.add(userPrompt);

        do {
            try {
                // Build Chat Completion Request
                ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                        .messages(messages)
                        .model("gpt-3.5-turbo")
                        .functions(functionExecutor.getFunctions())
                        .functionCall(new ChatCompletionRequest.ChatCompletionRequestFunctionCall("auto"))
                        .maxTokens(256)
                        .build();
                List<ChatCompletionChoice> completionChoices = aiClient.createChatCompletion(completionRequest).getChoices();

                if (completionChoices.stream().anyMatch(it -> it.getMessage().getFunctionCall() != null)) {
                    functionsCalled = true;

                    // we have choices which include function calls
                    List<ChatCompletionChoice> filteredCompletionChoices = completionChoices.stream().filter(it -> it.getMessage().getFunctionCall() != null).collect(Collectors.toList());
                    for (ChatCompletionChoice filteredChoice : filteredCompletionChoices) {
                        System.out.println("Chat Completion : " + filteredChoice.toString());
                        ChatFunctionCall functionCall = filteredChoice.getMessage().getFunctionCall();
                        System.out.println("Making function call to : " + functionCall.getName());
                        ChatMessage functionResponseMessage = functionExecutor.executeAndConvertToMessageHandlingExceptions(functionCall);
                        messages.add(functionResponseMessage);
                    }
                } else {
                    functionsCalled = false;
                    // final response
                    response = completionChoices.get(0).getMessage().getContent();
                }
                System.out.println("callChatCompletion: model used ->  " + completionRequest.getModel());
                System.out.println("callChatCompletion: user msg ->  " + messages.toString());

            } catch (Exception ex) {
                System.out.println("Exception: " + ex);
            }
        } while ( functionsCalled);

        return response;
    }
}
