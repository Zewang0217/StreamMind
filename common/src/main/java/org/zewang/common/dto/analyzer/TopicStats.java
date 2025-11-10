package org.zewang.common.dto.analyzer;


/**
 * @author "Zewang"
 * @version 1.0
 * @description: 话题统计结果
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 22:07
 */

public class TopicStats {
    private long messageCount;
    private double totalSentimentScore;
    private double negativeRatio;

    public TopicStats(long messageCount, double totalSentimentScore, double negativeRatio) {
        this.messageCount = messageCount;
        this.totalSentimentScore = totalSentimentScore;
        this.negativeRatio = negativeRatio;
    }

    public TopicStats update(AnalyzedMessage message) {
        this.messageCount++;
        this.totalSentimentScore += message.sentimentScore();
        long negativeCount = (message.sentimentScore() < -0.1) ? 1 : 0; // TODO：调整具体阈值，根据实际情况调整分数
        this.negativeRatio = ((this.negativeRatio * (this.messageCount - 1)) + negativeRatio) / this.messageCount;
        return this;
    }

    public boolean isAlertWorthy() {
        return this.messageCount > 1000 && this.negativeRatio > 0.7; // TODO：调整阈值，根据实际情况调整
    }
}
