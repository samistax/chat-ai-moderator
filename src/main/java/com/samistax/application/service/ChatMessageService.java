package com.samistax.application.service;

import com.samistax.astra.entity.ChatMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ChatMessageService {

    private ChatMsgRepository repository;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ChatMessageService(ChatMsgRepository repository) {
        this.repository = repository;
    }

    private void printRequestDuration(String methodName, long startTime) {
        logger.info(methodName+" request (ms): " + (System.currentTimeMillis() - startTime));
    }

    public int countAllMessages() {
        return (int) repository.count();
    }

    public List<ChatMsg> findAllMessages() {
        long startTime = System.currentTimeMillis();
        List<ChatMsg> response = repository.findAll();
        printRequestDuration("findAll: ",startTime);
        return response;
    }

    public List<ChatMsg> findAllMessages(String topic) {
        long startTime = System.currentTimeMillis();
        List<ChatMsg> response = repository.findByKeyTopic(topic);
        printRequestDuration("findAllMessages("+topic+"): ",startTime);
        return response;
    }
    public List<ChatMsg> findAllMessagesSince(String topic, Instant timestamp) {

        System.out.println("findAllMessagesSince. Timestamp: " + timestamp );
        long startTime = System.currentTimeMillis();
        List<ChatMsg> test = repository.findByKeyTopicAndKeyTime(topic, timestamp);
        List<ChatMsg> response = repository.findByKeyTopicAndKeyTimeAfter(topic, timestamp);
        printRequestDuration("findAllMessagesSince("+topic+"): ", startTime);
        if ( test.size() > 0) {
            System.out.println("Fetched exact match" + test.get(0).getKey().getTime());
        }
        // workaround for JPA not having equal or after convention
        response.addAll(test);
        System.out.println("findAllMessagesSince returned  " +response.size() + " messages!!!" );
        return response;
    }

    // Start of Producer methods
    public ChatMsg saveMessage(ChatMsg entity) {
        long startTime = System.currentTimeMillis();
        ChatMsg response =  repository.save(entity);
        printRequestDuration("saveTopicMessage: ",startTime);
        return response;
    }
}
