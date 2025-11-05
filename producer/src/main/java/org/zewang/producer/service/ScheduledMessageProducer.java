package org.zewang.producer.service;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zewang.common.dto.ChatMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 定时任务生产者
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:51
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageProducer {

    private final MessageProducerService messageProducerService;
    private final Random random = new Random();

    // 模拟用户ID列表
    private final String[] userIds = {"U001", "U002", "U003", "U004", "U005"};

    // 模拟消息内容列表
    private final String[] messages =  {
        "今天天气真不错！",
        "这个项目进展得很顺利。",
        "有点累了，需要休息一下。",
        "刚刚完成了一个重要功能。",
        "遇到了一些技术难题。",
        "心情有点低落。",
        "很高兴能和大家一起工作。",
        "周末有什么计划吗？",
        "这个想法很不错！",
        "需要进一步优化。"
    };

    // 每秒发送5-10条消息
    @Scheduled(fixedRate = 1000)
    public void produceMessages() {
        int messageCount = 5 + random.nextInt(6); // 5-10
        log.info("正在处理 {} 消息...", messageCount);
        List<ChatMessage> messages = new ArrayList<>(messageCount);

        for (int i = 0; i < messageCount; i++) {
            messages.add(createRandomChatMessage());
        }

        messages.forEach(messageProducerService::sendMessage);
    }

    private ChatMessage createRandomChatMessage() {
        ChatMessage message = new ChatMessage();
        message.setUserId(userIds[random.nextInt(userIds.length)]);
        message.setTimestamp(Instant.now().toEpochMilli());
        message.setMessage(messages[random.nextInt(messages.length)]);
        return message;
    }
}
