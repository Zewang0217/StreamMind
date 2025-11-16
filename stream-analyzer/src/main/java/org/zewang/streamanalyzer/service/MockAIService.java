package org.zewang.streamanalyzer.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.zewang.common.dto.analyzer.AnalyzedMessage;
import org.zewang.common.dto.social_message.SocialMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 模拟AI调用
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 22:11
 */

@Service
public class MockAIService {

    private final Random random = new Random();

    /** Mock 批量情感分析
     *
     * @param messages 原始消息列表
     * @return 分析后的消息列表
     */
    public List<AnalyzedMessage> analyzeBatch(List<SocialMessage> messages) {
        List<AnalyzedMessage> results = new ArrayList<>();
        for (SocialMessage msg : messages) {
            double sentimentScore = (random.nextDouble() * 2 ) - 1; // -1 to 1
            String sentimentLabel = sentimentScore > 0.3 ? "Positive" : (sentimentScore < -0.3 ? "Negative" : "Neutral"); // TODO: 调整阈值
            double toxicityScore = random.nextDouble(); // 0 to 1

            AnalyzedMessage analyzed = AnalyzedMessage.builder()
                .messageId(msg.messageId())
                .source(msg.source())
                .topic(msg.topic())
                .userId(msg.userId())
                .timestamp(msg.timestamp())
                .content(msg.content())
                .interactionCount(msg.interactionCount())
                .sentimentScore(sentimentScore)
                .sentimentLabel(sentimentLabel)
                .toxicityScore(toxicityScore)
                .build();

            results.add(analyzed);
        }
        return results;
    }
}
