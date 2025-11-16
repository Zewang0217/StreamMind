package org.zewang.alertservice.processor;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.zewang.alertservice.service.AlertMessageService;
import org.zewang.common.dto.alert.AlertMessage;
import org.zewang.common.dto.analyzer.AnalyzedMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警处理器（FixedKeyProcessor，支持上下文访问和值转换）
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/15 21:31
 */

@Slf4j
@RequiredArgsConstructor
public class AlertProcessor implements FixedKeyProcessor<String, AnalyzedMessage, AlertMessage> {
    private final AlertMessageService alertMessageService;
    private FixedKeyProcessorContext context;

    @Override
    public void init(FixedKeyProcessorContext context) {
        this.context = context;
        log.info("初始化 AlertProcessor");
    }

    @Override
    public void process(FixedKeyRecord<String, AnalyzedMessage> record) {
        AnalyzedMessage analyzedMessage = record.value();

        // 判断是否触发预警
        boolean isNegativeAlert = analyzedMessage.sentimentScore() < -0.5;
        boolean isToxicAlert = analyzedMessage.toxicityScore() > 0.5;

        // 如果没有触发预警，不转发（等价于过滤）
        if (!isNegativeAlert && !isToxicAlert) {
            return; // 不调用 forward，消息被丢弃
        }

        // 构建预警消息
        AlertMessage alert = new AlertMessage();
        alert.setTopic(analyzedMessage.topic());
        alert.setUserId(analyzedMessage.userId());
        alert.setNegativeScore(analyzedMessage.sentimentScore() < 0 ?
            Math.abs(analyzedMessage.sentimentScore()) : 0.0);
        alert.setToxicScore(analyzedMessage.toxicityScore());

        String alertType = isNegativeAlert ? "负面情感" : "高毒性";
        alert.setMessage(String.format("话题 '%s' 触发%s预警: 情感分数=%.2f, 毒性分数=%.2f",
            analyzedMessage.topic(), alertType,
            analyzedMessage.sentimentScore(), analyzedMessage.toxicityScore()));

        // ✅ 从 FixedKeyRecord 获取事件时间戳
        long eventTimestamp = record.timestamp();
        alert.setWindowEnd(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(eventTimestamp),
                ZoneOffset.UTC
            )
        );

        if (alert.getCreatedAt() == null) {
            alert.setCreatedAt(LocalDateTime.now());
        }

        try {
            // 保存到数据库
            AlertMessage savedAlert = alertMessageService.saveAlert(alert);
            log.debug("成功保存预警消息到数据库: id={}", savedAlert.getId());

            // 继续转发到下游（Key 不变）
            context.forward(record.withValue(savedAlert));
            /**
             * // 框架内部会创建新的 Record 对象
             * new FixedKeyRecord<>(
             *     record.key(),      // ✅ Key 保持 "user123" 不变
             *     savedAlert,        // ✅ Value 变成 AlertMessage
             *     record.timestamp() // ✅ 时间戳从上游传递下来
             * )
             */

        } catch (Exception e) {
            log.error("保存预警消息失败: {}", e.getMessage(), e);
            // TODO: 发送到死信队列
        }
    }

    @Override
    public void close() {
        log.info("关闭 AlertProcessor");
    }

}
