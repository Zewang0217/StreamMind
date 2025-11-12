package org.zewang.alertservice.repository;


import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zewang.common.dto.alert.AlertMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警消息查询接口
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/12 18:30
 */

@Repository
public interface AlertMessageRepository extends JpaRepository<AlertMessage, Long> {
    // 按话题查询
    Page<AlertMessage> findByTopicOrderByCreatedAtDesc(String topic, Pageable pageable);

    // 按用户查询 分页
    @Query("SELECT a FROM AlertMessage a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    Page<AlertMessage> findByUserId(@Param("userId") String userId, Pageable pageable);

    // 按时间窗口查询 （核心业务）
    @Query("SELECT a FROM AlertMessage a WHERE a.windowEnd BETWEEN :start AND :end ORDER BY a.windowEnd DESC")
    List<AlertMessage> findByTimeWindow(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 按话题+时间窗口查询 （最高频）
    @Query("SELECT a FROM AlertMessage a WHERE a.topic = :topic AND a.windowEnd BETWEEN :start AND :end ORDER BY a.windowEnd DESC")
    List<AlertMessage> findByTopicAndTimeWindow(
        @Param("topic") String topic,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    // 查询负面分数通过阈值的高危预警
    @Query("SELECT a FROM AlertMessage a WHERE a.negativeScore > :threshold ORDER BY a.negativeScore DESC")
    List<AlertMessage> findHighRiskAlerts(@Param("threshold") Double threshold);
}
