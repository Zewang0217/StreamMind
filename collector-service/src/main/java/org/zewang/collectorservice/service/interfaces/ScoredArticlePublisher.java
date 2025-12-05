package org.zewang.collectorservice.service.interfaces;

import org.zewang.collectorservice.model.ScoredArticleMessage;
import org.zewang.common.dto.social_message.SocialMessage;

import java.util.List;

/**
 * 负责将评分结果发布到Kafka和数据库
 */
public interface ScoredArticlePublisher {
    /**
     * 发布评分结果
     * @param scoredArticles 评分文章消息列表
     * @param originalArticles 原始文章列表
     */
    void publish(List<ScoredArticleMessage> scoredArticles, List<SocialMessage> originalArticles);
}
