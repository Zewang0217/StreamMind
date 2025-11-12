package org.zewang.alertservice.processor;


import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.springframework.stereotype.Component;
import org.zewang.alertservice.service.AlertMessageService;
import org.zewang.common.dto.alert.AlertMessage;
import org.zewang.common.dto.analyzer.AnalyzedMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警消息处理器
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/12 18:49
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertPersistenceProcessor implements
    Processor<String, AnalyzedMessage, String, AnalyzedMessage> {

    private final AlertMessageService alertMessageService;
    private ProcessorContext<String, AnalyzedMessage> context;

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        log.info("初始化 AlertPersistenceProcessor");
    }

    @Override
    public void process(Record<String, AnalyzedMessage> record) {
        AnalyzedMessage analyzedMessage = record.value();

        // 转换为 JPA 实体
        AlertMessage alert = new AlertMessage();
        alert.setTopic(analyzedMessage.topic());
        alert.setUserId(analyzedMessage.userId());
        alert.setNegativeScore(analyzedMessage.sentimentScore() < 0.3 ?
            Math.abs(analyzedMessage.sentimentScore()) : 0.0);

        alert.setMessage(String.format("话题 '%s' 负面情感分数: %.2f",
            analyzedMessage.topic(), analyzedMessage.sentimentScore()));

        // ✅ 从 Kafka Streams 的窗口时间戳获取
        long windowEndMs = context.currentStreamTimeMs();

        alert.setWindowEnd(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(windowEndMs),  // long -> Instant
                ZoneOffset.UTC                      // 明确使用 UTC 时区
            )
        );

        try {
            // 保存到数据库
            alertMessageService.saveAlert(alert);
            log.debug("成功保存预警消息到数据库: id={}", alert.getId());

            // 继续转发到下游  暂时不用，作为数据最终点
//            context.forward(record);
        } catch (Exception e) {
            log.error("保存预警消息失败: {}",
                e.getMessage(), e);
            // TODO: 增加私信队列功能
        }
    }

    @Override
    public void close() {
        log.info("关闭 AlertPersistenceProcessor");
    }


}
