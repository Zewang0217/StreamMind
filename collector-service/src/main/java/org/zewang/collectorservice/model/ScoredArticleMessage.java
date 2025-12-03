package org.zewang.collectorservice.model;


import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import org.zewang.common.constant.ContentFetchStatus;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: AI评分后的文章
 * @email "Zewang0217@outlook.com"
 * @date 2025/12/03 10:09
 */

@Builder
public record ScoredArticleMessage(
    String messageId,
    String content,
    String link,
    String category,
    int score,
    List<String> keyWords,
    LocalDateTime pubDate,
    ContentFetchStatus status
) {}
