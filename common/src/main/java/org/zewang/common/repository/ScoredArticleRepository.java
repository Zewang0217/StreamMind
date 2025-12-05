package org.zewang.common.repository;


import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zewang.common.entity.ScoredArticle;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 评分文章Repository接口
 * @email "Zewang0217@outlook.com"
 * @date 2025/12/04 23:23
 */

@Repository
public interface ScoredArticleRepository extends JpaRepository<ScoredArticle, Long> {
    /**
     * 根据messageId查询文章
     */
    Optional<ScoredArticle> findByMessageId(String messageId);

}
