package org.zewang.streamanalyzer.config;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.analyzer.AnalyzedMessage;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.streamanalyzer.processor.BatchAIAnalysisProcessor;
import org.zewang.streamanalyzer.service.MockAIService;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 创建拓扑
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/11 18:26
 */

@Slf4j
@Configuration
public class KafkaStreamConfig {

    @Bean
    public KStream<String, SocialMessage> kStream(StreamsBuilder streamsBuilder, MockAIService mockAIService) {
        // 配置 JsonSerde 信任的包
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put("spring.json.trusted.packages", "*");

        // 使用Kafka提供的标准JsonSerde
        JsonSerde<SocialMessage> socialMessageSerde = new JsonSerde<>(SocialMessage.class);
        JsonSerde<AnalyzedMessage> analyzedMessageSerde = new JsonSerde<>(AnalyzedMessage.class);

        // 应用配置
        socialMessageSerde.configure(serdeConfig, false);
        analyzedMessageSerde.configure(serdeConfig, false);

        // 创建输入流并指定 serde
        KStream<String, SocialMessage> sourceStream = streamsBuilder.stream(
            KafkaConstants.SOCIAL_MESSAGES_TOPIC,
            org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), socialMessageSerde)
        );

        // 使用自定义处理器处理消息
        KStream<String, AnalyzedMessage> analyzedStream = sourceStream.process(
            () -> new BatchAIAnalysisProcessor(mockAIService),
            Named.as("ai-analysis-processor") // 添加 processor 名称
        );

        // 将分析结果发送到 analyzed-stream topic，指定 serde
        analyzedStream.to(
            KafkaConstants.ANALYZED_STREAM_TOPIC,
            org.apache.kafka.streams.kstream.Produced.with(Serdes.String(), analyzedMessageSerde)
        );

        // 打印拓扑信息
        Topology topology = streamsBuilder.build();
        log.info("构建的拓扑结构: {}", topology.describe());

        return sourceStream; // 返回源流以确保Bean被正确创建
    }

}
