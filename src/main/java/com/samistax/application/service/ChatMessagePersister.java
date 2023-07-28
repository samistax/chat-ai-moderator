package com.samistax.application.service;


import com.samistax.astra.entity.ChatMsg;
import com.samistax.astra.entity.PulsarChatMessage;
import com.vaadin.collaborationengine.CollaborationMessage;
import com.vaadin.collaborationengine.CollaborationMessagePersister;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.pulsar.core.PulsarTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SpringComponent
public class ChatMessagePersister implements CollaborationMessagePersister {

    private final ChatMessageService chatService;
    private final PulsarTemplate<PulsarChatMessage> pulsarTemplate;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ChatMessagePersister(PulsarTemplate<PulsarChatMessage> pulsarTemplate, ChatMessageService chatService) {
        this.pulsarTemplate = pulsarTemplate;
        this.chatService = chatService;
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
        if (this.pulsarTemplate != null) {

            // Send a message to the topic
            UserInfo user = message.getUser();

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
    }
}


