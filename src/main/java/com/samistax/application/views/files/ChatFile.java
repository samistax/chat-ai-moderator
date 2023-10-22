package com.samistax.application.views.files;

public class ChatFile {
    private int id;
    private String chatId;
    private String fileName;
    private String content;

    public ChatFile(){}

    public ChatFile(int id, String chatId, String fileName, String content) {
        this.id = id;
        this.chatId = chatId;
        this.fileName = fileName;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
