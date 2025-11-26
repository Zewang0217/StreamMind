package org.zewang.common.dto.analyzer;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import org.zewang.common.dto.social_message.SocialMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: AI分析后的文章消息模型
 *     用于存储经过AI处理后的文章内容，分析结果等信息
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/26 21:38
 */

@Builder
public record AiArticleMessage (
    String messageId, // 原始消息的唯一标识

    // 原始消息来源
    String source,

    // 话题
    String topic,

    // 消息URL
    String url,

    // 原始标题
    String originalTitle,

    // 原始内容
    String originalContent,

    // Jina Reader 提取的文章正文
    String extractedContent,

    // 文章评分
    double articleScore,

    // 是否通过筛选
    boolean passedFilter,

    // 关键段落
    List<String> keyParagraphs,

    // 关键短语
    List<String> keyPhrases,

    // 关键词
    List<String> keywords,

    // 摘要
    String summary,

    // 分类结果
    String category,

    // 提取时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    LocalDateTime extractionTime,

    // 是否已经完成
    boolean finished

) {

    // 从SocialMessage创建AiAriticleMessage的构造器
    public static AiArticleMessageBuilder fromSocialMessage(SocialMessage socialMessage) {
        return AiArticleMessage.builder()
            .messageId(socialMessage.messageId())
            .source(socialMessage.source())
            .topic(socialMessage.topic())
            .url(socialMessage.url())
            .originalTitle(socialMessage.content() != null && socialMessage.content().contains(" - ")
                ? socialMessage.content().split(" - ")[0] : socialMessage.content())
            .originalContent(socialMessage.content())
            .extractionTime(LocalDateTime.now())
            .finished(false);
    }
}
