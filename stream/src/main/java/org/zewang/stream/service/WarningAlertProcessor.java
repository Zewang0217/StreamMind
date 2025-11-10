package org.zewang.stream.service;


import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
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
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.stereotype.Component;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.SentimentScore;
import org.zewang.common.dto.WarningAlert;

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

        // 检查是否有消费者订阅该 topic
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
        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(5))
            .advanceBy(Duration.ofSeconds(5));

        KGroupedStream<String, SentimentScore> groupedStream = sentimentScores.groupByKey();

        Duration retentionPeriod = Duration.ofSeconds(30);


        WindowBytesStoreSupplier sumStoreSupplier = Stores.inMemoryWindowStore(
            "in-memory-sum-store", // 唯一名称
            retentionPeriod,
            windowSize,
            false // retainDuplicates
        );
        Materialized<String, Double, WindowStore<Bytes, byte[]>> sumMaterialized =
            Materialized.as(sumStoreSupplier) // <--- 修复：使用 'as(supplier)'
                .withKeySerde(Serdes.String())   // <--- 链接 .with...
                .withValueSerde(Serdes.Double()); // <--- 链接 .with...

        // 计算总分
        KTable<Windowed<String>, Double> sumScores = groupedStream
            .windowedBy(timeWindows)
            .aggregate(
                () -> 0.0,
                (key, value, aggregate) -> {
                    double newAggregate = aggregate + value.getSentimentScore();
                    log.debug("累计分数计算: key={}, value={}, aggregate={}", key, value.getSentimentScore(), newAggregate);
                    return newAggregate;
                },
                sumMaterialized // 5. 【替换】使用新的
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口总分计算完成: window={}, sum={}", readOnlyKey, value);
                return value;
            });

        WindowBytesStoreSupplier countStoreSupplier = Stores.inMemoryWindowStore(
            "in-memory-count-store", // 唯一名称
            retentionPeriod,
            windowSize, // 10s
            false
        );
        Materialized<String, Long, WindowStore<Bytes, byte[]>> countMaterialized =
            Materialized.as(countStoreSupplier) // <--- 修复：使用 'as(supplier)'
                .withKeySerde(Serdes.String())    // <--- 链接 .with...
                .withValueSerde(Serdes.Long());     // <--- 链接 .with...

        // 计算消息数量
        KTable<Windowed<String>, Long> countScores = groupedStream
            .windowedBy(timeWindows)
            .count(
                countMaterialized // 8. 【替换】使用新的
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口计数: window={}, count={}", readOnlyKey, value);
                return value;
            });

        // 计算平均分
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

        // 3. 过滤低分预警（平均值低于阈值）
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
        // 4. 发送到 warning-alerts 主题
        Serde<Windowed<String>> windowedSerde = WindowedSerdes.timeWindowedSerdeFrom(String.class, timeWindows.size());
        alerts.to(KafkaConstants.WARNING_ALERTS_TOPIC, Produced.with(windowedSerde, warningAlertSerde));
        log.info("预警拓扑已经构建完成");
    }



}
