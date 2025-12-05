package org.zewang.collectorservice.service.interfaces;

import org.zewang.common.dto.social_message.SocialMessage;

import java.util.List;

/**
 * 负责调用AI服务进行评分和分类
 */
public interface AIScoringService {
    /**
     * 对一批文章进行评分和分类
     * @param articles 待评分的文章列表
     * @return AI服务的响应结果
     */
    String scoreArticles(List<SocialMessage> articles);
}
