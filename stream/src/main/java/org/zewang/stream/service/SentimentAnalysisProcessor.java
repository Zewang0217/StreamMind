// src/main/java/org/zewang/stream/service/SentimentAnalysisProcessor.java
package org.zewang.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.stereotype.Component;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.ChatMessage;
import org.zewang.common.dto.SentimentScore;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 情感分析处理器
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/06 18:33
 */
@Slf4j
@Component
@RequiredArgsConstructor // Lombok 注解，自动生成构造函数
public class SentimentAnalysisProcessor {


    // 注入自定义的 Serdes
    private final Serde<ChatMessage> chatMessageSerde;
    private final Serde<SentimentScore> sentimentScoreSerde;

    public void buildTopology(StreamsBuilder streamsBuilder) {
        // 1. 从 chat-messages 主题读取消息
        KStream<String, ChatMessage> chatMessages = streamsBuilder
            .stream(KafkaConstants.CHAT_MESSAGES_TOPIC, Consumed.with(Serdes.String(), chatMessageSerde));

        // 2. 对每条消息进行情感分析
        KStream<String, SentimentScore> sentimentScores = chatMessages
            .mapValues(this::analyzeSentiment); // 使用 mapValues 转换值


        // 3. 将结果写入 sentiment-scores 主题
        sentimentScores.to(KafkaConstants.SENTIMENT_SCORES_TOPIC, Produced.with(Serdes.String(), sentimentScoreSerde));

        log.info("情感分析处理器拓扑构建完毕");
    }

    /**
     * 模拟情感分析，实际项目中需要调用 AI API
     *
     * @param chatMessage 聊天消息
     * @return 情感分析结果
     */
    private SentimentScore analyzeSentiment(ChatMessage chatMessage) {
        // TODO: 调用 Gemini API 进行情感分析
        // 这里先模拟一个简单的实现
        SentimentScore score = new SentimentScore();
        score.setUserId(chatMessage.getUserId());
        score.setTimestamp(chatMessage.getTimestamp());

        String message = chatMessage.getMessage().toLowerCase();
        if (message.contains("开心") || message.contains("高兴") || message.contains("不错") || message.contains("好")) {
            score.setSentimentScore(0.8);
            score.setSentimentLabel("Positive");
        } else if (message.contains("难过") || message.contains("沮丧") || message.contains("不好") || message.contains("糟")) {
            score.setSentimentScore(-0.6);
            score.setSentimentLabel("Negative");
        } else {
            score.setSentimentScore(0.0);
            score.setSentimentLabel("Neutral");
        }

//        log.debug("分析情感结果如下：user: {}: {} ({})", chatMessage.getUserId(), score.getSentimentScore(), score.getSentimentLabel());

        // 添加特殊日志，当分数为负时
        if (score.getSentimentScore() < 0) {
            log.info("检测到负面情绪消息: userId={}, score={}, message={}",
                chatMessage.getUserId(), score.getSentimentScore(), chatMessage.getMessage());
        }

        return score;
    }
}
