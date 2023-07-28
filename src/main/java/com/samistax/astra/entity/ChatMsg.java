package com.samistax.astra.entity;

import com.vaadin.collaborationengine.CollaborationMessage;
import com.vaadin.collaborationengine.CollaborationMessagePersister;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("chat_message")
public class ChatMsg {

    @PrimaryKey
    @CassandraType(type = CassandraType.Name.UDT)
    private ChatMsgKey key; // Topic and time
    // CollaborationEngine Massage params
    private String text;
    // De normalizing entities
    // Collaboration Engine User Info  parameter
    private String userId;
    private String userName;
    private String userAbbreviation;
    private String userImage;
    private int userColorIndex;

    public ChatMsg() {}

    public ChatMsg(ChatMsgKey key) {
        this.key = key;
    }

    public ChatMsg(CollaborationMessagePersister.PersistRequest request) {
        if ( request != null && request.getMessage() != null ) {
            CollaborationMessage message = request.getMessage();
            // Initialize Key
            this.key = new ChatMsgKey();
            key.setTopic(request.getTopicId());
            key.setTime(message.getTime());
            // Initialize other properties

            // CollaborationEngine Massage params
            this.text = message.getText();
            this.userId = message.getUser().getId();
            this.userName = message.getUser().getName();
            this.userAbbreviation = message.getUser().getAbbreviation();
            this.userImage = message.getUser().getImage();
            this.userColorIndex = message.getUser().getColorIndex();
        }
    }
    public ChatMsgKey getKey() {
        return key;
    }

    public void setKey(ChatMsgKey key) {
        this.key = key;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserAbbreviation() {
        return userAbbreviation;
    }

    public void setUserAbbreviation(String userAbbreviation) {
        this.userAbbreviation = userAbbreviation;
    }

    public String getUserImage() {
        return userImage;
    }

    public void setUserImage(String userImage) {
        this.userImage = userImage;
    }

    public int getUserColorIndex() {
        return userColorIndex;
    }

    public void setUserColorIndex(int userColorIndex) {
        this.userColorIndex = userColorIndex;
    }
}
