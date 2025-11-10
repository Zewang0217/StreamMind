package org.zewang.stream.config;

import java.nio.file.Paths; // 【1】确保导入 Paths
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.zewang.common.serde.JsonSerde; // 【2】确保导入 JsonSerde
import org.zewang.stream.service.SentimentAnalysisProcessor;
import org.zewang.stream.service.WarningAlertProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: KafkaStream配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/06 18:23
 */
@Slf4j
@Configuration
@EnableKafkaStreams
@RequiredArgsConstructor
public class KafkaStreamConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final WarningAlertProcessor warningAlertProcessor;
    private final SentimentAnalysisProcessor sentimentAnalysisProcessor;

    @Bean
    public KafkaStreams kafkaStreams(StreamsBuilder streamsBuilder) {
        KafkaStreamsConfiguration config = kafkaStreamsConfig();

        log.info("KafkaStreamConfig: 构建情感分析拓扑...");
        sentimentAnalysisProcessor.buildTopology(streamsBuilder);

        log.info("KafkaStreamConfig: 构建预警处理器拓扑...");
        warningAlertProcessor.buildTopology(streamsBuilder);

        log.info("KafkaStreamConfig: 所有拓扑构建完毕，正在创建 KafkaStreams 实例...");

        KafkaStreams kafkaStreams = new KafkaStreams(
            streamsBuilder.build(),
            config.asProperties()
        );

        kafkaStreams.setStateListener((newState, oldState) -> {
            log.info("Kafka Streams 状态变化: {} -> {}", oldState, newState);
        });

        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
        log.info("Kafka Streams 已启动。");
        return kafkaStreams;
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kafkaStreamsConfig() {
        Map<String, Object> props = new HashMap<>();

        // 【3. 关键修复】还原所有必需的配置
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-mind-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());

        // 【4. 保持】使用 java.io.tmpdir（一个安全可写的目录）
        String stateDirLocation = Paths.get(
            System.getProperty("java.io.tmpdir"), // "C:\Users\zewan\AppData\Local\Temp"
            "stream-mind-app" // 在其中创建一个唯一的子目录
        ).toString();

        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDirLocation);
        log.info("Kafka Streams 状态目录设置为: {}", stateDirLocation);

        // 【5. 保持】清理过的配置
        props.put(StreamsConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0L); // 禁用缓存以便实时处理

        return new KafkaStreamsConfiguration(props);
    }
}