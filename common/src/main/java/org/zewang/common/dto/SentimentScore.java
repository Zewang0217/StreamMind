package org.zewang.common.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 情感分析结果结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:20
 */

@Getter
@Setter
@NoArgsConstructor
public class SentimentScore {
    private String userId;
    private long timestamp;
    private double sentimentScore;  // [-1.0, 1.0]
    private String sentimentLabel;      // "Positive"/"Neutral"/"Negative"
}
