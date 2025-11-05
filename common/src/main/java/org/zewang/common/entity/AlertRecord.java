package org.zewang.common.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警记录实体类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:28
 */

@Entity
@Table(name = "alert_record")
@Getter
@Setter
public class AlertRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private Long windowEndTs;
    private BigDecimal averageScore;
    private String alertMessage;
    private LocalDateTime createdAt;
}
