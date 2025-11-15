package org.zewang.alertservice.processor;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.zewang.alertservice.service.AlertMessageService;
import org.zewang.common.dto.alert.AlertMessage;
import org.zewang.common.dto.analyzer.AnalyzedMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 替换原来的处理器，新的flatTransformer
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/15 21:06
 */

@Slf4j
@RequiredArgsConstructor
public class AlertFlatTransformer implements
    Transformer<String, AnalyzedMessage, Iterable<KeyValue<String, AlertMessage>>> {
    private final AlertMessageService alertMessageService;
    private ProcessorContext context;

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        log.info("初始化 AlertFlatTransformer");
    }

    @Override
    public Iterable<KeyValue<String, AlertMessage>> transform(String key, AnalyzedMessage analyzedMessage) {
        try {
            // 判断是否处罚预警
            boolean isNegativeAlert = analyzedMessage.sentimentScore() < -0.5; // TODO: 阈值调整
            boolean isToxicAlert = analyzedMessage.toxicityScore() > 0.5;

            // 如果没有处罚预警，返回空列表（自动过滤）
            if (!isNegativeAlert && !isToxicAlert) {
                return Collections.emptyList();
            }

            // 构建预警消息
            AlertMessage alert = new AlertMessage();
            alert.setTopic(analyzedMessage.topic());
            alert.setUserId(analyzedMessage.userId());
            alert.setNegativeScore(analyzedMessage.sentimentScore() < 0 ?
                Math.abs(analyzedMessage.sentimentScore()) : 0.0);

            String alertType = isNegativeAlert ? "负面情感" : "高毒性";
            alert.setMessage(String.format("话题 '%s' 触发%s预警: 情感分数=%.2f, 毒性分数=%.2f",
                analyzedMessage.topic(), alertType,
                analyzedMessage.sentimentScore(), analyzedMessage.toxicityScore()));

            // ✅ 从 Kafka Streams 获取事件时间戳
            long eventTimestamp = context.timestamp();
            alert.setWindowEnd(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(eventTimestamp),
                    ZoneOffset.UTC
                )
            );

            if (alert.getCreatedAt() == null) {
                alert.setCreatedAt(LocalDateTime.now());
            }

            // 保存到数据库
            AlertMessage savedAlert = alertMessageService.saveAlert(alert);
            log.debug("成功保存预警消息到数据库: id={}", savedAlert.getId());

            // 返回包含单个元素的列表（自动转发到下游）
            return Collections.singletonList(
                KeyValue.pair(savedAlert.getTopic(), savedAlert)
            );
        } catch (Exception e) {
            log.error("处理预警消息失败 [topic={}, userId={}]: {}",
                analyzedMessage.topic(), analyzedMessage.userId(), e.getMessage(), e);

            // TODO: 发送到死信队列

            return Collections.emptyList();
        }
    }

    @Override
    public void close() {
        log.info("关闭 AlertFlatTransformer");
    }

}
