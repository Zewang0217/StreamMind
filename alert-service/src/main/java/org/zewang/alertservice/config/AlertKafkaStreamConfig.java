package org.zewang.alertservice.config;


import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.zewang.alertservice.processor.AlertPersistenceProcessor;
import org.zewang.alertservice.processor.AlertProcessor;
import org.zewang.alertservice.service.AlertMessageService;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.alert.AlertMessage;
import org.zewang.common.dto.analyzer.AnalyzedMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警模块的stream配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/13 18:18
 */

@Slf4j
@Configuration
public class AlertKafkaStreamConfig {

    @Bean
    public KStream<String, AnalyzedMessage> alertStream(StreamsBuilder streamsBuilder,
        AlertMessageService alertMessageService) {

        // 配置 JsonSerde 信任的包
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put("spring.json.trusted.packages", "*");

        // 使用 Kafka 提供的标准 JsonSerde
        JsonSerde<AnalyzedMessage> analyzedMessageJsonSerde = new JsonSerde<>(AnalyzedMessage.class);
        JsonSerde<AlertMessage> alertMessageJsonSerde = new JsonSerde<>(AlertMessage.class);

        // 应用配置
        analyzedMessageJsonSerde.configure(serdeConfig, false);
        alertMessageJsonSerde.configure(serdeConfig, false);

        // 创建输入流并指定 serde
        KStream<String, AnalyzedMessage> sourceStream = streamsBuilder.stream(
            KafkaConstants.ANALYZED_STREAM_TOPIC,
            Consumed.with(Serdes.String(), analyzedMessageJsonSerde)
        );

        // 使用自定义处理器处理消息，并获取输出流
        sourceStream
            .processValues(
                () -> new AlertProcessor(alertMessageService), // ✅ FixedKeyProcessor
                Named.as("alert-processor")
            )
            // ✅ 过滤 null 值（未触发预警的）
            .filter((key, alert) -> alert != null)
            // ✅ 修改 Key 为 topic
            .map((key, alert) -> KeyValue.pair(alert.getTopic(), alert))
            .to(
                KafkaConstants.ALERT_EVENTS_TOPIC,
                Produced.with(Serdes.String(), alertMessageJsonSerde)
            );

        // 打印拓扑信息
        Topology topology = streamsBuilder.build();
        log.info("构建的预警服务拓扑结构: {}", topology.describe());

        return sourceStream; // 返回源流以确保Bean被正确创建
    }

}
