package org.zewang.collectorservice.service.interfaces;

import org.zewang.collectorservice.model.ScoredArticleMessage;
import org.zewang.common.dto.social_message.SocialMessage;

import java.util.List;

/**
 * 负责解析AI服务响应
 */
public interface ResponseProcessor {
    /**
     * 处理AI服务响应，转换为评分文章消息列表
     * @param response AI服务响应
     * @param originalArticles 原始文章列表
     * @return 评分文章消息列表
     */
    List<ScoredArticleMessage> processResponse(String response, List<SocialMessage> originalArticles);
}
