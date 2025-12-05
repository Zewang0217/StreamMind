package org.zewang.collectorservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zewang.collectorservice.model.ScoredArticleMessage;
import org.zewang.collectorservice.service.interfaces.ResponseProcessor;
import org.zewang.common.constant.ContentFetchStatus;
import org.zewang.common.dto.social_message.SocialMessage;
import org.zewang.common.exception.BusinessException;
import org.zewang.common.exception.ErrorCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责解析AI服务响应的实现类
 */
@Slf4j
@Service
public class ResponseProcessorImpl implements ResponseProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ScoredArticleMessage> processResponse(String response, List<SocialMessage> originalArticles) {
        List<ScoredArticleMessage> scoredArticles = new ArrayList<>();

        try {
            // 尝试直接解析为文章评分数组（如果返回了数组）
            JsonNode rootNode = objectMapper.readTree(response);

            if (rootNode.isArray()) {
                // 直接是文章评分数组
                scoredArticles.addAll(processArticlesArray(rootNode, originalArticles));
            } else if (rootNode.has("choices") && rootNode.get("choices").isArray()) {
                // 火山方舟标准响应格式
                JsonNode choicesNode = rootNode.get("choices").get(0);
                if (choicesNode.has("message") && choicesNode.get("message").has("content")) {
                    String content = choicesNode.get("message").get("content").asText();
                    // 解析content中的JSON数组
                    JsonNode articlesArray = objectMapper.readTree(content);
                    if (articlesArray.isArray()) {
                        scoredArticles.addAll(processArticlesArray(articlesArray, originalArticles));
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理API响应时出错: {}", e.getMessage(), e);
            scoredArticles.addAll(extractAndProcessJson(response, originalArticles));
        }

        return scoredArticles;
    }

    // 处理文章评分数组
    private List<ScoredArticleMessage> processArticlesArray(JsonNode articlesArray, List<SocialMessage> originalArticles) {
        List<ScoredArticleMessage> scoredArticles = new ArrayList<>();

        try {
            Map<String, SocialMessage> messageMap = new HashMap<>();
            for (SocialMessage msg : originalArticles) {
                messageMap.put(msg.messageId(), msg);
            }

            for (JsonNode articleNode : articlesArray) {
                if (articleNode.has("article_identifier") && articleNode.has("score") && articleNode.has("category")) {
                    String messageId = articleNode.get("article_identifier").asText();
                    SocialMessage originalMessage = messageMap.get(messageId);

                    if (originalMessage != null) {
                        int score = articleNode.get("score").asInt();
                        String category = articleNode.get("category").asText();

                        // 提取关键词
                        List<String> keywords = new ArrayList<>();
                        if (articleNode.has("keywords") && articleNode.get("keywords").isArray()) {
                            for (JsonNode keywordNode : articleNode.get("keywords")) {
                                keywords.add(keywordNode.asText());
                            }
                        }

                        log.info("文章 {} 评分: {}, 分类: {}, 关键词: {}",
                                messageId, score, category, keywords);

                        // 创建ScoredArticleMessage对象
                        ScoredArticleMessage scoredArticleMessage = ScoredArticleMessage.builder()
                                .messageId(messageId)
                                .content(originalMessage.content())
                                .link(originalMessage.url())
                                .category(category)
                                .keyWords(keywords)
                                .score(score)
                                .pubDate(originalMessage.timestamp())
                                .status(ContentFetchStatus.NOT_FETCHED)
                                .build();

                        scoredArticles.add(scoredArticleMessage);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理文章评分数据时出错: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SCORED_ARTICLES_PROCESS_ERROR, e.getMessage());
        }

        return scoredArticles;
    }

    /**
     * 从文本中提取JSON并处理
     */
    private List<ScoredArticleMessage> extractAndProcessJson(String text, List<SocialMessage> originalArticles) {
        List<ScoredArticleMessage> scoredArticles = new ArrayList<>();

        try {
            // 简单的JSON提取逻辑
            int startIndex = text.indexOf('[');
            int endIndex = text.lastIndexOf(']');

            if (startIndex >= 0 && endIndex > startIndex) {
                String jsonPart = text.substring(startIndex, endIndex + 1);
                JsonNode articlesArray = objectMapper.readTree(jsonPart);
                if (articlesArray.isArray()) {
                    scoredArticles.addAll(processArticlesArray(articlesArray, originalArticles));
                } else {
                    log.error("Extracted JSON is not an array");
                }
            }
        } catch (Exception e) {
            log.error("Error extracting JSON: {}", e.getMessage(), e);
        }

        return scoredArticles;
    }
}
