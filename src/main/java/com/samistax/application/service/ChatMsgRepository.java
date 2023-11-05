package com.samistax.application.service;

import com.samistax.astra.entity.ChatMsg;
import com.samistax.astra.entity.ChatMsgKey;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.time.Instant;
import java.util.List;

public interface ChatMsgRepository  extends CassandraRepository<ChatMsg, ChatMsgKey> {
    List<ChatMsg> findByKeyTopic(final String topic);
    List<ChatMsg> findByKeyTopicAndKeyTimeAfter(final String key, final Instant timestamp);
    List<ChatMsg> findByKeyTopicAndKeyTime(final String key, final Instant timestamp);
    List<ChatMsg> findByKeyTopicAndUserId(final String key, final String userid);
}