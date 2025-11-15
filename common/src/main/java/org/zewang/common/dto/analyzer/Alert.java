package org.zewang.common.dto.analyzer;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: alert实体类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 21:35
 */

@Builder
public record Alert (
    String topic,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime windowEnd,
    double negativeScore,
    String message
) {}
