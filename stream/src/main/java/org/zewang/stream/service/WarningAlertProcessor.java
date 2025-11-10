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

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 实现实时预警计算功能
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/09 20:56
 */

@Slf4j
@Component
@RequiredArgsConstructor // Lombok: 自动为此类中所有 final 字段生成构造函数（用于依赖注入）
public class WarningAlertProcessor {

    // 依赖注入：Kafka Streams 需要知道如何序列化/反序列化（Serde）自定义Java对象
    private final Serde<SentimentScore> sentimentScoreSerde;
    private final Serde<WarningAlert> warningAlertSerde;


    /**
     * 构建 Kafka Streams 拓扑（Topology）
     * "拓扑"是流处理的蓝图，定义了数据如何从源（Source）流向汇（Sink）
     * @param streamsBuilder Spring Boot 自动配置并传入的构建器
     */
    public void buildTopology(StreamsBuilder streamsBuilder) {

        // 1. 定义数据源 (Source Processor)
        // 从 "sentiment-scores" 主题读取数据
        KStream<String, SentimentScore> sentimentScores = streamsBuilder
            .stream(
                KafkaConstants.SENTIMENT_SCORES_TOPIC, // 指定要消费的主题
                Consumed.with(Serdes.String(), sentimentScoreSerde) // 指定 Key 和 Value 的 Serde
            );

        log.info("已建立从 sentiment-scores 读取数据的流，topic: {}", KafkaConstants.SENTIMENT_SCORES_TOPIC);

        // 2. 定义窗口参数
        // 这是一个 "Hopping Window"（跳动窗口）
        Duration windowSize = Duration.ofSeconds(60);   // 窗口大小：每个窗口包含 60 秒的数据
        Duration gracePeriod = Duration.ofSeconds(30);  // 宽限期：允许 30 秒内的迟到数据被正确处理
        Duration advanceBy = Duration.ofSeconds(30);    // 步长：每 30 秒（而不是60秒）就创建一个新窗口

        // 举例：
        // 窗口 1: [00:00 - 01:00]
        // 窗口 2: [00:30 - 01:30]  (步长为 30s)
        // 窗口 3: [01:00 - 02:00]
        // 这意味着在 00:30 到 01:00 之间的数据会同时属于窗口 1 和 2

        // 3. 定义窗口规格
        TimeWindows timeWindows = TimeWindows.ofSizeAndGrace(windowSize, gracePeriod)
            .advanceBy(advanceBy);

        // 4. 按 Key 分组 (Group By Key)
        // 聚合（如 sum, count, aggregate）之前必须先分组
        // 这里的 Key 假定是 userId（来自 SentimentScore 消息）
        // 这会触发一次 "repartition"（重新分区），确保具有相同 Key (userId) 的所有消息
        // 都被发送到同一个 Kafka Streams 实例（Task）进行处理
        KGroupedStream<String, SentimentScore> groupedStream = sentimentScores.groupByKey();

        // 5. 聚合计算窗口内的总分 (sum)
        // .windowedBy() 将 KGroupedStream 转换为 KWindowedStream
        KTable<Windowed<String>, Double> sumScores = groupedStream
            .windowedBy(timeWindows) // 应用上面定义的跳动窗口
            .aggregate(
                // Initializer：定义聚合的初始值（每个新窗口开始时，总分都是 0.0）
                () -> 0.0,
                // Aggregator：定义如何将新值合并到聚合值中
                // (key, value, aggregate) -> (消息Key, SentimentScore对象, 当前窗口的聚合值)
                (key, value, aggregate) -> {
                    double newAggregate = aggregate + value.getSentimentScore();
                    return newAggregate;
                },
                // Materialized：定义如何 "物化"（即存储）这个中间状态
                // Kafka Streams 使用一个 "状态存储"（State Store，默认是 RocksDB）来保存窗口的
                // 实时聚合值。这使得流处理具有容错性。
                Materialized.with(Serdes.String(), Serdes.Double())
            )
            .mapValues((readOnlyKey, value) -> {
                // readOnlyKey 是一个 Windowed<String> 对象，包含了 Key 和窗口信息
                log.debug("窗口总分计算完成: window={}, sum={}", readOnlyKey, value);
                return value;
            });

        // 6. 聚合计算窗口内的消息数 (count)
        // .count() 是 .aggregate() 的一个特例，更简洁
        KTable<Windowed<String>, Long> countScores = groupedStream
            .windowedBy(timeWindows) // 必须使用完全相同的窗口定义！
            .count(
                // 同样，需要物化这个计数值
                Materialized.with(Serdes.String(), Serdes.Long())
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口计数: window={}, count={}", readOnlyKey, value);
                return value;
            });

        // 7. 连接（Join）两个 KTable 以计算平均分
        // KTable-KTable Join：
        // 因为 sumScores 和 countScores 都是从 *同一个* groupedStream 以 *相同* 方式
        // 派生出来的，它们具有相同的分区和窗口键，连接（Join）操作会非常高效。
        // 当 sumScores 或 countScores 中任何一个的窗口值更新时，join 都会被触发。
        KTable<Windowed<String>, Double> avgScores = sumScores.join(
                countScores,
                // Joiner lambda: (左表的值, 右表的值) -> (新表的值)
                (sum, count) -> {
                    if (count == 0) return 0.0; // 避免除以零
                    double avg = sum / count;
                    return avg;
                }
            )
            .mapValues((readOnlyKey, value) -> {
                log.debug("窗口平均分计算完成: window={}, avg={}", readOnlyKey, value);
                return value;
            });

        // 8. 转换 KTable 为 KStream 并生成预警
        // KTable（表）代表一个 key 的 *当前* 值。
        // KStream（流）代表一个 key 的 *所有* 变化。
        // 为了将结果发送到另一个 Kafka 主题，我们需要将 KTable 的 "更新日志" 转换回 KStream
        KStream<Windowed<String>, WarningAlert> alerts = avgScores
            .toStream() // KTable -> KStream
            .peek((key, avgScore) -> log.debug("窗口结果输出到流: userId={}, windowEnd={}, averageScore={} timestamp={}",
                key.key(), key.window().end(), avgScore, key.window().start()))
            // 【注意】这里没有 .filter() 步骤！
            // 这意味着 *每一个* 窗口计算（无论平均分高低）都会生成一个 WarningAlert。
            // 如果你只想在 "分数低于某个阈值" 时才发送预警，
            // 你应该在这里添加一个 .filter((key, avgScore) -> avgScore < YOUR_THRESHOLD)
            .map((key, avgScore) -> {
                // 将 (Windowed<String>, Double) 格式的消息 转换为 (Windowed<String>, WarningAlert)
                WarningAlert alert = new WarningAlert();
                alert.setUserId(key.key()); // key.key() 获取原始的 String key (userId)
                alert.setWindowEnd(key.window().end()); // key.window().end() 获取窗口结束时间戳
                alert.setAverageScore(avgScore);
                alert.setAlertMessage("用户情绪持续低落"); // 消息是硬编码的

                log.info("生成预警消息: userId={}, windowEnd={}, averageScore={}, message={} topic={}",
                    alert.getUserId(), alert.getWindowEnd(), alert.getAverageScore(), alert.getAlertMessage(), KafkaConstants.WARNING_ALERTS_TOPIC);

                // 返回一个新的键值对
                return new KeyValue<>(key, alert);
            });

        // 9. 定义数据汇 (Sink Processor)
        // 在将数据发送到 Kafka 之前，必须为 *Key* 和 *Value* 指定 Serde

        // 这是一个常见的易错点：
        // 聚合操作后，Key 不再是 String，而是 Windowed<String>
        // 所以我们需要一个能处理 Windowed<String> 的 Serde
        Serde<Windowed<String>> windowedSerde = WindowedSerdes.timeWindowedSerdeFrom(String.class, windowSize.toMillis());

        // 将 'alerts' 流写入 "warning-alerts" 主题
        alerts.to(
            KafkaConstants.WARNING_ALERTS_TOPIC,
            Produced.with(windowedSerde, warningAlertSerde) // 提供 Key 和 Value 的 Serde
        );

        log.info("预警拓扑已经构建完成");
    }
}