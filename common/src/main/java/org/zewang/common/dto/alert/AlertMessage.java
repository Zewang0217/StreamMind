package org.zewang.common.dto.alert;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警消息实体类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/12 18:24
 */

@Entity
@Table(name = "alert_messages"
//    indexes = {
//    @Index(name = "idx_alert_topic", columnList = "topic"),
//    @Index(name = "idx_alert_user", columnList = "user_id"),
//    @Index(name = "idx_alert_window_end", columnList = "window_end DESC"),
//    @Index(name = "idx_alert_created_at", columnList = "created_at DESC"),
//    @Index(name = "idx_alert_topic_window", columnList = "topic, window_end DESC")}
    )
@Data
public class AlertMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    @Column(name = "negative_score", precision = 5)
    private Double negativeScore;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
