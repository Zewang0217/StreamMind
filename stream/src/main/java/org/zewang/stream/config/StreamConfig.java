package org.zewang.stream.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.ChatMessage;
import org.zewang.common.dto.SentimentScore;
import org.zewang.common.dto.WarningAlert;
import org.zewang.common.serde.JsonSerde;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 解决依赖问题，独立部分config出来
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 10:25
 */

@Configuration
public class StreamConfig {

    // --- Serde Beans ---

    @Bean
    public Serde<ChatMessage> chatMessageSerde() {
        return new JsonSerde<>(ChatMessage.class);
    }

    @Bean
    public Serde<SentimentScore> sentimentScoreSerde() {
        return new JsonSerde<>(SentimentScore.class);
    }

    @Bean
    public Serde<WarningAlert> warningAlertSerde() {
        return new JsonSerde<>(WarningAlert.class);
    }

    // --- Topic Beans ---

    @Bean
    public NewTopic sentimentScoresTopic() {
        return new NewTopic(KafkaConstants.SENTIMENT_SCORES_TOPIC, 3, (short) 1);
    }

    @Bean
    public NewTopic warningAlertsTopic() {
        return new NewTopic(KafkaConstants.WARNING_ALERTS_TOPIC, 3, (short) 1);
    }
}