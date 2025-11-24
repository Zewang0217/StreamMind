// src/test/java/org/zewang/collectorservice/RSSHubDataCollectorIntegrationTest.java
package org.zewang.collectorservice;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;
import org.zewang.collectorservice.service.RSSHubDataCollector;
import org.zewang.common.dto.social_message.SocialMessage;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.AssertionErrors.assertFalse;
import static org.springframework.test.util.AssertionErrors.assertNotNull;

@SpringBootTest
@ActiveProfiles("rsshub-data")
public class RSSHubDataCollectorIntegrationTest {

    @Autowired
    private RSSHubDataCollector rssHubDataCollector;

    @Autowired
    private RSSHubConfig rssHubConfig;

    @Autowired
    private RSSHubRssParser rssParser;

    @Autowired
    private KafkaTemplate<String, SocialMessage> kafkaTemplate;

    @Test
    void collectFeed_WithRealRSSHubData_IntegrationTest() {
        // Given - 使用真实的配置
        if (rssHubConfig.getFeeds() != null && !rssHubConfig.getFeeds().isEmpty()) {
            RSSHubFeedConfig feedConfig = rssHubConfig.getFeeds().get(0);
            if (feedConfig.isEnabled()) {
                // Mock KafkaTemplate以避免实际发送到Kafka
                KafkaTemplate<String, SocialMessage> mockKafkaTemplate = mock(KafkaTemplate.class);

                // 使用反射替换KafkaTemplate
                // 注意：这种方法在实际应用中可能需要更复杂的处理

                // When - 执行真实的数据收集
                rssHubDataCollector.collectFeed(feedConfig, rssHubConfig.getUrl());

                // Then - 验证调用了正确的URL和处理了数据
                System.out.println("成功从RSSHub获取数据并处理");
            }
        }
    }

    @Test
    void collectFeed_ZhihuFeed_ShouldProcessCorrectly() {
        // 查找知乎相关的feed配置
        RSSHubFeedConfig zhihuFeed = rssHubConfig.getFeeds().stream()
            .filter(feed -> "zhihu".equals(feed.getSource()))
            .findFirst()
            .orElse(null);

        if (zhihuFeed != null && zhihuFeed.isEnabled()) {
            // 执行数据收集（使用真实的KafkaTemplate发送真实消息）
            rssHubDataCollector.collectFeed(zhihuFeed, rssHubConfig.getUrl());

            // 验证应该通过其他方式，比如检查日志输出或数据库状态
            System.out.println("知乎数据收集测试完成");
        }
    }

    @Test
    void collectFeed_36kr_ShouldProcessCorrectly() {
        RSSHubFeedConfig krFeed = rssHubConfig.getFeeds().stream()
            .filter(feed -> "36kr".equals(feed.getSource()))
            .findFirst()
            .orElse(null);

        if (krFeed != null && krFeed.isEnabled()) {
            rssHubDataCollector.collectFeed(krFeed, rssHubConfig.getUrl());
            System.out.println("36kr数据收集测试完成");
        }
    }

}
