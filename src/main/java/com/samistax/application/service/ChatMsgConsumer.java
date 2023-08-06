package com.samistax.application.service;

import com.samistax.application.ai.ContentPolicyEnforcement;
import com.samistax.astra.entity.ChatMsg;
import com.samistax.astra.entity.ChatMsgKey;
import com.datastax.astra.sdk.AstraClient;
import com.samistax.astra.entity.PulsarChatMessage;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationResult;
import com.theokanning.openai.service.FunctionExecutor;
import com.vaadin.collaborationengine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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

        System.out.println(" - chatMessageReceived: " + chatMsg.getTime());
        UserInfo info = new UserInfo(chatMsg.getUserId(), chatMsg.getUserName(), chatMsg.getUserImage());
        String topicId = chatMsg.getTopicId();
        Instant msgTimestamp = Instant.ofEpochMilli(chatMsg.getTime());
        ChatMsgKey key = new ChatMsgKey(topicId, msgTimestamp);
        ChatMsg msg = new ChatMsg(key);

        // Use Open AI to Moderate the user message
        AtomicBoolean flagged = new AtomicBoolean(false);
       // ModerationResult modResult = aiClient.moderateContent(chatMsg.getText());
        ModerationResult modResult = chatMsg.getModerationResult();

        List<Moderation> mods = modResult.getResults();
        List<String> flaggedCategories = getFlaggedCategories(mods);
        UserInfo chatUser = new UserInfo(chatMsg.getUserId(), chatMsg.getUserName());

        long startTime = System.currentTimeMillis();

        // If moderator API resulted in flagged content take action
        if ( flaggedCategories.size() > 0 ) {

            chatUser = new UserInfo("0", "AI Moderator");
            publishChatMessage(chatUser, topicId, "Your message is violating content policy and thus was not published in chat room. Msg: " + chatMsg.getText(), msgTimestamp);

            String category = flaggedCategories.get(0);
            // Use ChatGPT to explain customer what proprietary content policy rules the end user is violating
            String generatedMsg = callChatAPI("Using proprietary content policy rules that applies to " + category + " content, please generate a response to the user explaining how the end user can comply with the proprietary content policy.");

            printRequestDuration("callChatAPI Prompt total -> ",startTime);
            // Write OpenAI ChatGPT response to chat channel
            publishChatMessage(chatUser, topicId,generatedMsg, msgTimestamp);
        } else {
            // Submit the none toxic message to chat view
            if ( ce != null ) {

                ConnectionContext context = ce.getSystemContext();
                MessageManager messageManager = new MessageManager(context, chatUser, chatMsg.getTopicId(), ce);
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
            System.out.println("Calling GPT: " + messages.toString());

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    .model("gpt-3.5-turbo-0613")
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
        } while (functionsCalled);

        return response;
    }

}
