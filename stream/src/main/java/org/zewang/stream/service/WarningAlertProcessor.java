package org.zewang.stream.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.springframework.stereotype.Component;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.SentimentScore;
import org.zewang.common.dto.WarningAlert;

// 【还原】移除了所有 in-memory store 的 imports
// (例如: Bytes, Stores, WindowBytesStoreSupplier, WindowStore)

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 实现实时预警计算功能
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/09 20:56
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WarningAlertProcessor {

    private final Serde<SentimentScore> sentimentScoreSerde;
    private final Serde<WarningAlert> warningAlertSerde;


    public void buildTopology(StreamsBuilder streamsBuilder) {
        log.info("开始构建预警处理器拓扑...");

        log.info("尝试从 topic '{}' 读取数据", KafkaConstants.SENTIMENT_SCORES_TOPIC);

        // 从 sentiment-scores 读取数据
        KStream<String, SentimentScore> sentimentScores = streamsBuilder
            .stream(KafkaConstants.SENTIMENT_SCORES_TOPIC, Consumed.with(Serdes.String(), sentimentScoreSerde))
            .peek((key, value) -> log.debug("接收到情感分数数据: userId={}, score={}", key, value.getSentimentScore()));

        log.info("已建立从 sentiment-scores 读取数据的流，topic: {}", KafkaConstants.SENTIMENT_SCORES_TOPIC);

        Duration windowSize = Duration.ofSeconds(10);
        Duration gracePeriod = Duration.ofSeconds(5);
        Duration advanceBy = Duration.ofSeconds(5);

        // 使用 hopping window: 10s 窗口，5s 宽
        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(windowSize, gracePeriod)
            .advanceBy(advanceBy);

        KGroupedStream<String, SentimentScore> groupedStream = sentimentScores.groupByKey();

        // 【还原】使用标准的、基于磁盘 (RocksDB) 的 Materialized
        // 这将使用 KafkaStreamConfig 中定义的 STATE_DIR_CONFIG
        KTable<Windowed<String>, Double> sumScores = groupedStream
            .windowedBy(timeWindows)
            .aggregate(
                () -> 0.0,
                (key, value, aggregate) -> {
                    double newAggregate = aggregate + value.getSentimentScore();
                    log.debug("累计分数计算: key={}, value={}, aggregate={}", key, value.getSentimentScore(), newAggregate);
                    return newAggregate;
                },
                Materialized.with(Serdes.String(), Serdes.Double()) // <---【还原】使用标准方法
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口总分计算完成: window={}, sum={}", readOnlyKey, value);
                return value;
            });

        // 【还原】对 count store 重复此操作
        KTable<Windowed<String>, Long> countScores = groupedStream
            .windowedBy(timeWindows)
            .count(
                Materialized.with(Serdes.String(), Serdes.Long()) // <---【还原】使用标准方法
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口计数: window={}, count={}", readOnlyKey, value);
                return value;
            });

        // 计算平均分 (不变)
        KTable<Windowed<String>, Double> avgScores = sumScores.join(
                countScores,
                (sum, count) -> {
                    if (count == 0) return 0.0;
                    double avg = sum / count;
                    log.debug("计算平均分: sum={}, count={}, avg={}", sum, count, avg);
                    return avg;
                }
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口平均分计算完成: window={}, avg={}", readOnlyKey, value);
                return value;
            });

        // 过滤低分预警 (不变)
        KStream<Windowed<String>, WarningAlert> alerts = avgScores
            .toStream()
            .peek((key, avgScore) -> log.debug("窗口结果输出到流: userId={}, windowEnd={}, averageScore={} timestamp={}",
                key.key(), key.window().end(), avgScore, key.window().start()))
            .filter((key, avgScore) -> {
                log.debug("过滤检查: userId={}, score={}, threshold={}", key.key(), avgScore, 0.5);
                boolean shouldAlert = avgScore <= 1.0;
                if (shouldAlert) {
                    log.info("检测到用户情绪预警: userId={}, windowEnd={}, averageScore={}",
                        key.key(), key.window().end(), avgScore);
                } else {
                    log.debug("未触发预警: userId={}, averageScore={}", key.key(), avgScore);
                }
                return shouldAlert;
            })
            .map((key, avgScore) -> {
                WarningAlert alert = new WarningAlert();
                alert.setUserId(key.key());
                alert.setWindowEnd(key.window().end());
                alert.setAverageScore(avgScore);
                alert.setAlertMessage("用户情绪持续低落");

                log.info("生成预警消息: userId={}, windowEnd={}, averageScore={}, message={} topic={}",
                    alert.getUserId(), alert.getWindowEnd(), alert.getAverageScore(), alert.getAlertMessage(), KafkaConstants.WARNING_ALERTS_TOPIC);

                return new KeyValue<>(key, alert);
            });

        log.info("准备发送预警...");
        // 发送到 warning-alerts 主题 (不变)
        Serde<Windowed<String>> windowedSerde = WindowedSerdes.timeWindowedSerdeFrom(String.class, windowSize.toMillis());
        alerts.to(KafkaConstants.WARNING_ALERTS_TOPIC, Produced.with(windowedSerde, warningAlertSerde));
        log.info("预警拓扑已经构建完成");
    }
}