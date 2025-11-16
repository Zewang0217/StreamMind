package org.zewang.alertservice.service;


import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zewang.alertservice.repository.AlertMessageRepository;
import org.zewang.common.dto.alert.AlertMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: TODO
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/12 18:37
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMessageService {

    private final AlertMessageRepository repository;

    // 保存单条预警消息 （Kafka Streams 调用）
    @Transactional
    public AlertMessage saveAlert(AlertMessage alert) {
        log.debug("保存预警消息: topic={}, userId={}, score={}",
            alert.getTopic(), alert.getUserId(),  alert.getNegativeScore());
        return repository.save(alert);
    }

    // 批量保存
    @Transactional
    public List<AlertMessage> saveAlertsBatch(List<AlertMessage> alerts) {
        log.info("批量保存 {} 条预警消息", alerts.size());
        return repository.saveAll(alerts);
    }

    // 查询某话题最近N条预警
    public List<AlertMessage> getRecentAlertsByTopic(String topic, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByTopicOrderByCreatedAtDesc(topic, pageable).getContent();
    }

    // 查询时间窗口内的所有预警 （Kafka Streams 回填数据用）
    public List<AlertMessage> getAlertsInWindow(LocalDateTime start, LocalDateTime end) {
        log.info("查询时间窗口: {} to {}", start, end);
        return repository.findByTimeWindow(start, end);
    }

    // 查询某话题在特定时间窗口的预警
    public List<AlertMessage> getTopicAlertsInWindow(String topic, LocalDateTime start, LocalDateTime end) {
        log.info("查询话题 {} 在 {} to {} 的预警", topic, start, end);
        return repository.findByTopicAndTimeWindow(topic, start, end);
    }

    // 查询高位预警
    public List<AlertMessage> getHighRiskAlerts(Double threshold) {
        return repository.findHighRiskAlerts(threshold);
    }

}
