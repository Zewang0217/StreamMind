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
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
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
//@RequiredArgsConstructor
public class WarningAlertProcessor {

    private final StreamsBuilder streamsBuilder;
    private final Serde<SentimentScore> sentimentScoreSerde;
    private final Serde<WarningAlert> warningAlertSerde;

    public WarningAlertProcessor(StreamsBuilder streamsBuilder,
        Serde<SentimentScore> sentimentScoreSerde,
        Serde<WarningAlert> warningAlertSerde) {
        this.streamsBuilder = streamsBuilder;
        this.sentimentScoreSerde = sentimentScoreSerde;
        this.warningAlertSerde = warningAlertSerde;
    }


    public void buildTopology() {
        log.info("开始构建预警处理器拓扑...");

        // 检查是否有消费者订阅该 topic
        log.info("尝试从 topic '{}' 读取数据", KafkaConstants.SENTIMENT_SCORES_TOPIC);



        // 1. 从 sentiment-scores 读取数据
        KStream<String, SentimentScore> sentimentScores = streamsBuilder
            .stream(KafkaConstants.SENTIMENT_SCORES_TOPIC, Consumed.with(Serdes.String(), sentimentScoreSerde))
            .peek((key, value) -> log.debug("接收到情感分数数据: userId={}, score={}", key, value.getSentimentScore()));

        log.info("已建立从 sentiment-scores 读取数据的流，topic: {}", KafkaConstants.SENTIMENT_SCORES_TOPIC);

        // 添加计数器来跟踪是否有数据流入
        sentimentScores = sentimentScores.peek((key, value) -> {
            log.info(">>> 接收到情感分数数据: userId={}, score={}, timestamp={}",
                key, value.getSentimentScore(), value.getTimestamp());
        });

        // 使用 hopping window: 10s 窗口，5s 宽
        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(5))
            .advanceBy(Duration.ofSeconds(5));

        KGroupedStream<String, SentimentScore> groupedStream = sentimentScores.groupByKey();

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
                Materialized.with(Serdes.String(), Serdes.Double())
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口总分计算完成: window={}, sum={}", readOnlyKey, value);
                return value;
            });

        // 计算消息数量
        KTable<Windowed<String>, Long> countScores = groupedStream
            .windowedBy(timeWindows)
            .count(Materialized.with(Serdes.String(), Serdes.Long()))
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
