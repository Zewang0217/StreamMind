package org.zewang.stream.service;


import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.types.Field.Str;
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
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
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
@RequiredArgsConstructor
public class WarningAlertProcessor {

    private final StreamsBuilder streamsBuilder;
    private final Serde<SentimentScore> sentimentScoreSerde;
    private final Serde<WarningAlert> warningAlertSerde;

    public void buildTopology() {
        // 1. 从 sentiment-scores 读取数据
        KStream<String, SentimentScore> sentimentScores = streamsBuilder
            .stream(KafkaConstants.SENTIMENT_SCORES_TOPIC, Consumed.with(Serdes.String(), sentimentScoreSerde));

        // 转换为 KTable 进行窗口聚合
        // 使用 hopping window：窗口大小60秒，跳跃30秒
        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60), Duration.ofSeconds(10))
            .advanceBy(Duration.ofSeconds(30)); // Q:为什么三个数字？60、10、30

        KGroupedStream<String, SentimentScore> groupedStream = sentimentScores.groupByKey();

        // 计算总分
        KTable<Windowed<String>, Double> sumScores = groupedStream
            .windowedBy(timeWindows)
            .aggregate(
                () -> 0.0,
                (key, value, aggregate) -> aggregate + value.getSentimentScore(),
                Materialized.with(Serdes.String(), Serdes.Double())
            );

        // 计算消息数量
        KTable<Windowed<String>, Long> countScores = groupedStream
            .windowedBy(timeWindows)
            .count(Materialized.with(Serdes.String(), Serdes.Long()));

        // 计算平均分
        KTable<Windowed<String>, Double> avgScores = sumScores.join(
            countScores,
            (sum, count) -> {
                if (count == 0) return 0.0;
                return sum / count;
            }
        );

        // 3. 过滤低分预警（平均值低于-0.5）
        KStream<Windowed<String>, WarningAlert> alerts = avgScores
            .toStream()
            .filter((key, avgScore) -> avgScore < -0.5)
            .map((key, avgScore) -> {
                WarningAlert alert = new WarningAlert();
                alert.setUserId(key.key());
                alert.setWindowEnd(key.window().end());
                alert.setAverageScore(avgScore);
                alert.setAlertMessage("用户情绪持续低落");
                return new KeyValue<>(key, alert);
            });

        // 4. 发送到 warning-alerts 主题
        alerts.to(KafkaConstants.WARNING_ALERTS_TOPIC, Produced.with(WindowedSerdes.timeWindowedSerdeFrom(String.class), warningAlertSerde));
        log.info("预警拓扑已经构建");
    }


}
