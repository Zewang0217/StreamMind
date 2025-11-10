// src/main/java/org/zewang/stream/config/KafkaStreamConfig.java
package org.zewang.stream.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties.Netty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.EnableKafkaStreams; // 更准确的注解
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.zewang.common.constant.KafkaConstants;
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
@Slf4j
@Configuration
@EnableKafkaStreams // 启用 Kafka Streams
@RequiredArgsConstructor
public class KafkaStreamConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final WarningAlertProcessor warningAlertProcessor;
    private final SentimentAnalysisProcessor sentimentAnalysisProcessor;

    @Bean
    public KafkaStreams kafkaStreams(StreamsBuilder streamsBuilder) { // 7. 注入 Spring 默认的 builder
        KafkaStreamsConfiguration config = kafkaStreamsConfig();

        // 8.【关键】在这里按顺序构建拓扑
        log.info("KafkaStreamConfig: 构建情感分析拓扑...");
        sentimentAnalysisProcessor.buildTopology(streamsBuilder);

        log.info("KafkaStreamConfig: 构建预警处理器拓扑...");
        warningAlertProcessor.buildTopology(streamsBuilder);

        log.info("KafkaStreamConfig: 所有拓扑构建完毕，正在创建 KafkaStreams 实例...");

        // 9. 使用配置和已构建的 builder 来创建实例
        KafkaStreams kafkaStreams = new KafkaStreams(
            streamsBuilder.build(),
            config.asProperties()
        );

        // 10. 添加状态监听（可选，但推荐）
        kafkaStreams.setStateListener((newState, oldState) -> {
            log.info("Kafka Streams 状态变化: {} -> {}", oldState, newState);
        });

        // 11. 手动启动 Kafka Streams
        kafkaStreams.start();

        // 12. 注册一个关闭钩子，以便在 Spring 应用关闭时优雅地关闭 Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

        log.info("Kafka Streams 已启动。");
        return kafkaStreams;
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-mind-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());

        // 2.【重要】使用 java.io.tmpdir（一个安全可写的目录）
        String stateDirLocation = Paths.get(
            System.getProperty("java.io.tmpdir"), // 获取 "C:\Users\YourName\AppData\Local\Temp"
            "stream-mind-app" // 在其中创建一个唯一的子目录
        ).toString();

        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDirLocation);
        log.info("Kafka Streams 状态目录设置为: {}", stateDirLocation); // 添加日志

        props.put(StreamsConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0L); // 替换为这个

        return new KafkaStreamsConfiguration(props);
    }


}
