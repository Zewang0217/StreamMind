package org.zewang.alertservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.zewang.alertservice.service.AlertMessageService;
import org.zewang.common.dto.alert.AlertMessage;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预警仪表盘控制器
 * 提供前端仪表盘所需的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许跨域访问
public class AlertDashboardController {

    private final AlertMessageService alertMessageService;

    /**
     * 获取最近的预警列表
     */
    @GetMapping("/alerts/recent")
    public ResponseEntity<Map<String, Object>> getRecentAlerts(
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("获取最近 {} 条预警", limit);
        
        // 获取不同话题的最近预警
        List<AlertMessage> weiboAlerts = alertMessageService.getRecentAlertsByTopic("weibo", limit / 2);
        List<AlertMessage> zhihuAlerts = alertMessageService.getRecentAlertsByTopic("zhihu", limit / 2);
        
        Map<String, Object> result = new HashMap<>();
        result.put("weiboAlerts", weiboAlerts);
        result.put("zhihuAlerts", zhihuAlerts);
        result.put("totalCount", weiboAlerts.size() + zhihuAlerts.size());
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取高风险预警
     */
    @GetMapping("/alerts/high-risk")
    public ResponseEntity<Map<String, Object>> getHighRiskAlerts(
            @RequestParam(defaultValue = "0.7") double threshold) {
        
        log.info("获取高风险预警，阈值: {}", threshold);
        List<AlertMessage> highRiskAlerts = alertMessageService.getHighRiskAlerts(threshold);
        
        Map<String, Object> result = new HashMap<>();
        result.put("alerts", highRiskAlerts);
        result.put("count", highRiskAlerts.size());
        result.put("threshold", threshold);
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取预警统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAlertStatistics() {
        
        log.info("获取预警统计信息");
        
        // 获取最近24小时的数据
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);
        
        List<AlertMessage> recentAlerts = alertMessageService.getAlertsInWindow(startTime, endTime);
        
        // 统计不同来源的预警数量
        long weiboCount = recentAlerts.stream()
                .filter(alert -> "weibo".equals(alert.getTopic()))
                .count();
        long zhihuCount = recentAlerts.stream()
                .filter(alert -> "zhihu".equals(alert.getTopic()))
                .count();
        
        // 统计不同风险等级的数量
        long highRiskCount = recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && alert.getNegativeScore() > 0.7)
                .count();
        long mediumRiskCount = recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && 
                        alert.getNegativeScore() > 0.5 && alert.getNegativeScore() <= 0.7)
                .count();
        long lowRiskCount = recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && alert.getNegativeScore() <= 0.5)
                .count();
        
        // 计算平均分数
        double avgNegativeScore = recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null)
                .mapToDouble(AlertMessage::getNegativeScore)
                .average()
                .orElse(0.0);
        
        double avgToxicScore = recentAlerts.stream()
                .filter(alert -> alert.getToxicScore() != null)
                .mapToDouble(AlertMessage::getToxicScore)
                .average()
                .orElse(0.0);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlerts", recentAlerts.size());
        stats.put("weiboAlerts", weiboCount);
        stats.put("zhihuAlerts", zhihuCount);
        stats.put("highRiskAlerts", highRiskCount);
        stats.put("mediumRiskAlerts", mediumRiskCount);
        stats.put("lowRiskAlerts", lowRiskCount);
        stats.put("averageNegativeScore", String.format("%.3f", avgNegativeScore));
        stats.put("averageToxicScore", String.format("%.3f", avgToxicScore));
        stats.put("timeRange", Map.of("start", startTime, "end", endTime));
        stats.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取时间窗口内的预警趋势（按小时统计）
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getAlertTrends(
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("获取过去 {} 小时的预警趋势", hours);
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        List<AlertMessage> alerts = alertMessageService.getAlertsInWindow(startTime, endTime);
        
        // 按小时分组统计
        Map<String, Long> hourlyStats = new HashMap<>();
        Map<String, Long> weiboHourly = new HashMap<>();
        Map<String, Long> zhihuHourly = new HashMap<>();
        
        for (int i = 0; i < hours; i++) {
            LocalDateTime hourStart = startTime.plusHours(i);
            String hourKey = hourStart.toString();
            
            long totalCount = alerts.stream()
                    .filter(alert -> !alert.getCreatedAt().isBefore(hourStart) && 
                                   alert.getCreatedAt().isBefore(hourStart.plusHours(1)))
                    .count();
            
            long weiboCount = alerts.stream()
                    .filter(alert -> "weibo".equals(alert.getTopic()) && 
                                   !alert.getCreatedAt().isBefore(hourStart) && 
                                   alert.getCreatedAt().isBefore(hourStart.plusHours(1)))
                    .count();
            
            long zhihuCount = alerts.stream()
                    .filter(alert -> "zhihu".equals(alert.getTopic()) && 
                                   !alert.getCreatedAt().isBefore(hourStart) && 
                                   alert.getCreatedAt().isBefore(hourStart.plusHours(1)))
                    .count();
            
            hourlyStats.put(hourKey, totalCount);
            weiboHourly.put(hourKey, weiboCount);
            zhihuHourly.put(hourKey, zhihuCount);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("hourlyStats", hourlyStats);
        result.put("weiboHourly", weiboHourly);
        result.put("zhihuHourly", zhihuHourly);
        result.put("totalAlerts", alerts.size());
        result.put("timeRange", Map.of("start", startTime, "end", endTime));
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取话题分布统计
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, Object>> getTopicDistribution() {
        
        log.info("获取话题分布统计");
        
        // 获取最近24小时的数据
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(24);
        
        List<AlertMessage> recentAlerts = alertMessageService.getAlertsInWindow(startTime, endTime);
        
        // 按话题分组
        Map<String, Long> topicStats = recentAlerts.stream()
                .collect(Collectors.groupingBy(AlertMessage::getTopic, Collectors.counting()));
        
        // 按风险等级分组
        Map<String, Long> riskStats = new HashMap<>();
        riskStats.put("HIGH", recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && alert.getNegativeScore() > 0.7)
                .count());
        riskStats.put("MEDIUM", recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && 
                        alert.getNegativeScore() > 0.5 && alert.getNegativeScore() <= 0.7)
                .count());
        riskStats.put("LOW", recentAlerts.stream()
                .filter(alert -> alert.getNegativeScore() != null && alert.getNegativeScore() <= 0.5)
                .count());
        
        Map<String, Object> result = new HashMap<>();
        result.put("topicDistribution", topicStats);
        result.put("riskDistribution", riskStats);
        result.put("totalAlerts", recentAlerts.size());
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "alert-dashboard");
        health.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(health);
    }
}