// src/main/java/org/zewang/stream/config/KafkaStreamConfig.java
package org.zewang.stream.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams; // 更准确的注解
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.zewang.common.dto.ChatMessage;
import org.zewang.common.dto.SentimentScore;
import org.zewang.common.dto.WarningAlert;
import org.zewang.common.serde.JsonSerde; // 使用 common 模块中的自定义 JsonSerde

import java.util.HashMap;
import java.util.Map;
import org.zewang.stream.service.SentimentAnalysisProcessor;
import org.zewang.stream.service.WarningAlertProcessor;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: KafkaStream配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/06 18:23
 */
@Configuration
@EnableKafkaStreams // 启用 Kafka Streams
public class KafkaStreamConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private WarningAlertProcessor warningAlertProcessor;
    private SentimentAnalysisProcessor sentimentAnalysisProcessor;

    private final ApplicationContext applicationContext; // 用于获取 Spring 上下文

    public KafkaStreamConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        // 延迟获取Bean对象
//        this.warningAlertProcessor = applicationContext.getBean(WarningAlertProcessor.class);
//        this.sentimentAnalysisProcessor = applicationContext.getBean(SentimentAnalysisProcessor.class);
//        sentimentAnalysisProcessor.buildTopology();
//        warningAlertProcessor.buildTopology();
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-mind-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // 使用 common 模块中定义的 Serdes
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams");
        props.put(StreamsConfig.RETRIES_CONFIG, 3);
        props.put(StreamsConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new KafkaStreamsConfiguration(props);
    }

    // 定义 ChatMessage 的 Serde
    @Bean
    public Serde<ChatMessage> chatMessageSerde() {
        return new JsonSerde<>(ChatMessage.class);
    }

    // 定义 SentimentScore 的 Serde
    @Bean
    public Serde<SentimentScore> sentimentScoreSerde() {
        return new JsonSerde<>(SentimentScore.class);
    }

    // 定义 WarningAlert 的 Serde
    @Bean
    public Serde<WarningAlert> warningAlertSerde() {
        return new JsonSerde<>(WarningAlert.class);
    }
}
