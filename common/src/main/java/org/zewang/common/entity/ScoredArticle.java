package org.zewang.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.Getter;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 评分后的文章实体类
 * @email "Zewang0217@outlook.com"
 * @date 2025/12/05
 */

@Entity
@Table(name = "scored_articles")
@Data
@Getter
public class ScoredArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(name = "link", length = 500)
    private String link;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords; // 使用JSON字符串存储关键词列表

    @Column(name = "pub_date")
    private LocalDateTime pubDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 辅助方法：设置关键词列表，自动转换为JSON字符串
    public void setKeywordsList(List<String> keywordsList) {
        if (keywordsList == null) {
            this.keywords = null;
            return;
        }
        // 简单的JSON数组转换，实际项目中可使用Jackson或Gson
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < keywordsList.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(keywordsList.get(i)).append("\"");
        }
        sb.append("]");
        this.keywords = sb.toString();
    }

}