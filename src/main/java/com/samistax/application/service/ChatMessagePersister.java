package com.samistax.application.service;


import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.samistax.astra.entity.ChatMsg;
import com.samistax.astra.entity.ChatMsgKey;
import com.samistax.astra.entity.PulsarChatMessage;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.vaadin.collaborationengine.CollaborationMessage;
import com.vaadin.collaborationengine.CollaborationMessagePersister;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.pulsar.core.PulsarTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpringComponent
public class ChatMessagePersister implements CollaborationMessagePersister {

    private final ChatMessageService chatService;
    private final PulsarTemplate<PulsarChatMessage> pulsarTemplate;
    private final ChatMsgRepository chatRepository;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ChatMessagePersister(PulsarTemplate<PulsarChatMessage> pulsarTemplate,
                                ChatMessageService chatService,
                                ChatMsgRepository repository) {
        this.pulsarTemplate = pulsarTemplate;
        this.chatService = chatService;
        this.chatRepository = repository;
    };

    @Override
    public Stream<CollaborationMessage> fetchMessages(FetchQuery query) {
        ArrayList<CollaborationMessage> messages = new ArrayList<>();
        if ( this.chatService != null ) {

            List<ChatMsg> chatMsgs = chatService.findAllMessagesSince(query.getTopicId(), query.getSince());
            if ( chatMsgs != null ) {
                chatMsgs.stream().forEach(e -> {
                    UserInfo info = new UserInfo(e.getUserId(), e.getUserName(), e.getUserImage());
                    messages.add(new CollaborationMessage(info, e.getText(), e.getKey().getTime()));
                });
            }
        }
        return messages.stream();
    }

    @Override
    public void persistMessage(PersistRequest request) {

        CollaborationMessage message = request.getMessage();
        String topicId = request.getTopicId();
        UserInfo user = message.getUser();
System.out.println("persistMessage: " + message.getText());

        if ( topicId.contains("Moderated") ) {
            if (this.pulsarTemplate != null) {
                // Send a message to the topic
                PulsarChatMessage pulsarMsg = new PulsarChatMessage(
                        user.getId(),
                        user.getName(),
                        user.getAbbreviation(),
                        user.getImage(),
                        user.getColorIndex(),
                        request.getTopicId(),
                        message.getText(),
                        message.getTime());
                try {
                    // Instead of persisting raw message, send it to pulsar for moderation.
                    pulsarTemplate.sendAsync(pulsarMsg);
                } catch (PulsarClientException pce) {
                    logger.debug("Exception while Pulsar sendMessage", pce);
                }
            }
        } else {
            // Persist user message into Astra DB
            ChatMsgKey key = new ChatMsgKey(topicId, message.getTime());
            ChatMsg msg = new ChatMsg(key);
            msg.setText(message.getText());
            msg.setUserId(message.getUser().getId());
            msg.setUserName(message.getUser().getName());
            msg.setUserImage(message.getUser().getImage());
            msg.setUserAbbreviation(message.getUser().getAbbreviation());
            msg.setUserColorIndex(message.getUser().getColorIndex());
            if( chatRepository != null ) {
                chatRepository.save(msg);
            }
        }
    }

}


