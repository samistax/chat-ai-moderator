package com.samistax.astra.entity;

import com.theokanning.openai.moderation.ModerationResult;

import java.io.Serializable;
import java.time.Instant;

public class PulsarChatMessage implements Serializable {

    private String text;
    private long time;

    // User properties
    private String topicId;
    private String userId;
    private String userName;
    private String userAbbreviation;
    private String userImage;
    private int userColorIndex;
    private ModerationResult moderationResult;


    public PulsarChatMessage() {}

    public PulsarChatMessage(String userId, String userName, String abbreviation, String image, int colorIndex, String topicId, String text, Instant time) {
        this.userId = userId;
        this.userName = userName;
        this.userAbbreviation = abbreviation;
        this.userImage = image;
        this.userColorIndex = colorIndex;
        this.topicId = topicId;
        this.text = text;
        this.time = time.toEpochMilli();
    }

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
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

    public ModerationResult getModerationResult() {
        return moderationResult;
    }

    public void setModerationResult(ModerationResult moderationResult) {
        this.moderationResult = moderationResult;
    }
}
