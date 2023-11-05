package com.samistax.application.views.files;

import java.util.List;
import java.util.stream.Collectors;

import com.datastax.oss.driver.api.core.data.CqlVector;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

public class ChatFile {
    private int id;
    private String chatId;
    private String fileName;
    private String content;
    private CqlVector<Float> embedding;

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

    public CqlVector<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(CqlVector<Float> embedding) {
        this.embedding = embedding;
    }
    // Convenience method
    public void setVector(List<Double> embedding) {
        setEmbedding(CqlVector.newInstance(embedding.stream().map(d -> d.floatValue()).collect(Collectors.toList())));
    }
}
