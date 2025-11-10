package org.zewang.stream.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zewang.stream.service.SentimentAnalysisProcessor;
import org.zewang.stream.service.WarningAlertProcessor;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 构建stream的拓扑结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 16:45
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTopologyBuilder {
    private final SentimentAnalysisProcessor sentimentAnalysisProcessor;
    private final WarningAlertProcessor warningAlertProcessor;

    /**
     * 【关键】使用此方法构建拓扑.
     * Spring Boot 会自动检测到这个 @Autowired 方法，
     * 并在启动 KafkaStreams 实例之前调用它来构建拓扑。
     *
     * @param streamsBuilder Spring Boot 自动提供的 StreamsBuilder 实例
     */
    @Autowired
    public void buildTopology(StreamsBuilder streamsBuilder) {
        // 2.【关键】在这里按顺序构建拓扑
        log.info("StreamTopologyBuilder: 构建情感分析拓扑...");
        sentimentAnalysisProcessor.buildTopology(streamsBuilder);

        log.info("StreamTopologyBuilder: 构建预警处理器拓扑...");
        warningAlertProcessor.buildTopology(streamsBuilder);

        log.info("StreamTopologyBuilder: 所有拓扑构建完毕。Spring Boot 将自动管理 KafkaStreams 实例的启动。");
    }

}
