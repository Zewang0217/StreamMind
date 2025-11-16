package org.zewang.common.dto.analyzer;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 分析消息
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 21:35
 */

@Builder
public record AnalyzedMessage (
    String messageId,
    String source,
    String topic,
    String userId,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime timestamp,
    String content,
    int interactionCount,
    double sentimentScore, // [-1.0, 1.0]
    String sentimentLabel, // "Positive", "Negative", "Neutral"
    double toxicityScore // [0.0, 1.0]
    ) {}
