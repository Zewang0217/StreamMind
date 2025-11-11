package org.zewang.streamanalyzer.config;


import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.analyzer.AnalyzedMessage;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.common.serde.JsonSerde;
import org.zewang.streamanalyzer.processor.BatchAIAnalysisProcessor;
import org.zewang.streamanalyzer.service.MockAIService;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 创建拓扑
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/11 18:26
 */

@Configuration
public class KafkaStreamConfig {

    @Bean
    public Topology buildTopology(MockAIService mockAIService) {
        StreamsBuilder builder = new StreamsBuilder();

        // 创建自定义 serde
        JsonSerde<SocialMessage> socialMessageSerde = new JsonSerde<>(SocialMessage.class);
        JsonSerde<AnalyzedMessage> analyzedMessageSerde = new JsonSerde<>(AnalyzedMessage.class);

        // 创建输入流并指定 serde
        KStream<String, SocialMessage> sourceStream = builder.stream(
            KafkaConstants.SOCIAL_MESSAGES_TOPIC,
            org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), socialMessageSerde)
        );

        // 使用自定义处理器处理消息
        KStream<String, AnalyzedMessage> analyzedStream = sourceStream.process(
            () -> new BatchAIAnalysisProcessor(mockAIService),
            "ai-analysis-processor" // 添加 processor 名称
        );

        // 将分析结果发送到 analyzed-stream topic，指定 serde
        analyzedStream.to(
            KafkaConstants.ANALYZED_STREAM_TOPIC,
            org.apache.kafka.streams.kstream.Produced.with(Serdes.String(), analyzedMessageSerde)
        );

        return builder.build();
    }
}
