package org.zewang.producer.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.zewang.common.dto.ChatMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 消息生产服务
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:47
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducerService {

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    private static final String TOPIC_NAME = "chat-messages";

    public void sendMessage(ChatMessage message) {
        try {
            kafkaTemplate.send(TOPIC_NAME, message.getUserId(), message);
            log.info("发送消息给user: {} at {}", message.getUserId(), message.getTimestamp());
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
        }
    }
}
