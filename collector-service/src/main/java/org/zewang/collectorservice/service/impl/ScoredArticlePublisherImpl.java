package org.zewang.collectorservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.zewang.collectorservice.model.ScoredArticleMessage;
import org.zewang.collectorservice.service.interfaces.ScoredArticlePublisher;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.common.entity.ScoredArticle;
import org.zewang.common.repository.ScoredArticleRepository;

import java.util.List;

/**
 * 负责将评分结果发布到Kafka和数据库的实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoredArticlePublisherImpl implements ScoredArticlePublisher {

    private final KafkaTemplate<String, ScoredArticleMessage> kafkaTemplate1;
    private final KafkaTemplate<String, SocialMessage> kafkaTemplate;
    private final ScoredArticleRepository scoredArticleRepository;

    @Override
    public void publish(List<ScoredArticleMessage> scoredArticles, List<SocialMessage> originalArticles) {
        // 首先将原始文章发送到原topic
        originalArticles.forEach(message -> {
            kafkaTemplate.send(KafkaConstants.SOCIAL_MESSAGES_TOPIC, message.messageId(), message);
        });

        // 然后将评分文章发送到scored-articles-topic并保存到数据库
        for (ScoredArticleMessage scoredArticle : scoredArticles) {
            // 发送到kafka
            kafkaTemplate1.send(
                    KafkaConstants.SCORED_ARTICLES_TOPIC, scoredArticle.getMessageId(), scoredArticle
            );
            log.info("已发送文章 {} 评分信息到kafka", scoredArticle.getMessageId());

            // 将评分文章持久化到数据库
            ScoredArticle article = new ScoredArticle();
            article.setMessageId(scoredArticle.getMessageId());
            article.setLink(scoredArticle.getLink());
            article.setCategory(scoredArticle.getCategory());
            article.setScore(scoredArticle.getScore());
            article.setKeywordsList(scoredArticle.getKeyWords());
            article.setPubDate(scoredArticle.getPubDate());
            scoredArticleRepository.save(article);
            log.info("已将文章 {} 评分信息持久化到数据库", scoredArticle.getMessageId());
        }
    }
}
