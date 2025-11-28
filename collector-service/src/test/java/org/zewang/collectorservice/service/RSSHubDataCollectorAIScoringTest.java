package org.zewang.collectorservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;
import org.zewang.common.constant.ContentFetchStatus;
import org.zewang.common.dto.social_message.SocialMessage;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RSSHubDataCollectorAIScoringTest {

    @Mock
    private WebClient webClient;

    @Mock
    private KafkaTemplate<String, SocialMessage> kafkaTemplate;

    @Mock
    private RSSHubRssParser rssParser;

    @Mock
    private RSSHubConfig rssHubConfig;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    @InjectMocks
    private RSSHubDataCollector rssHubDataCollector;

    private List<SocialMessage> testArticles;
    private DateTimeFormatter dateTimeFormatter;

    @BeforeEach
    void setUp() {
        // 使用预定义的RFC 1123格式器处理RSS日期格式
        dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME;
        // 或者使用自定义格式并指定英语区域设置
        // dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
        
        // 注入火山方舟API配置（在Mockito测试中需要手动设置）
        ReflectionTestUtils.setField(rssHubDataCollector, "volcengineApiKey", "fe8a0104-9161-434c-aaec-fba8a10b137d");
        ReflectionTestUtils.setField(rssHubDataCollector, "volcengineApiEndpoint", "https://ark.cn-beijing.volces.com/api/v3/chat/completions");
        ReflectionTestUtils.setField(rssHubDataCollector, "modelId", "doubao-seed-1-6-flash-250828");

        // 创建测试文章数据
        testArticles = createTestArticles();
        
        // 为kafkaTemplate设置必要的模拟行为（send方法返回ListenableFuture，不是void）
        when(kafkaTemplate.send(anyString(), anyString(), any(SocialMessage.class)))
            .thenReturn(null); // 或者返回一个模拟的ListenableFuture
    }

    private List<SocialMessage> createTestArticles() {
        return List.of(
            createArticle("3572741302942849",
                "继捐款1000万港元后，拼多多上线香港消防用品公益专区",
                "36氪获悉，继捐赠1000万港元用于香港受灾居民救援、过渡安置等工作后，11月28日，拼多多又上线了香港消防用品公益专区，针对灭火毯等多个品类的消防用品推出专项补贴。",
                "https://www.36kr.com/newsflashes/3572741302942849",
                "Fri, 28 Nov 2025 13:00:23 GMT"),

            createArticle("3572734973869184",
                "长虹美菱：全资子公司长虹空调拟1257.84万元实施技术改造项目",
                "36氪获悉，长虹美菱发布公告，根据经营发展需要，进一步提高生产效率、降低制造成本，提高市场竞争能力，公司下属全资子公司长虹空调拟以自筹资金1257.84万元实施技术改造项目。",
                "https://www.36kr.com/newsflashes/3572734973869184",
                "Fri, 28 Nov 2025 12:53:57 GMT"),

            createArticle("3572731021196164",
                "中指研究院：1月至11月TOP100企业拿地总额同比增长14.1%",
                "中指研究院最新发布《2025年1-11月全国房地产企业拿地TOP100排行榜》，2025年1-11月，TOP100企业拿地总额8478亿元，同比增长14.1%，虽继续延续增长态势，但增幅较1-10月大幅收窄，临近年底，企业拿地积极性有所减弱，拿地较为审慎。11月，民营房企拿地较为活跃，部分企业联合国企拿地，部分企业聚焦优势区域独立拿地深耕。民企拿地主要集中在热点一二线核心城市。（证券时报）",
                "https://www.36kr.com/newsflashes/3572731021196164",
                "Fri, 28 Nov 2025 12:49:56 GMT"),

            createArticle("3572729651116936",
                "热门中概股美股盘前多数上涨，京东涨超1%",
                "36氪获悉，热门中概股美股盘前多数上涨，截至发稿，京东、拼多多、蔚来、小鹏汽车涨超1%，理想汽车涨0.33%；阿里巴巴跌超1%。",
                "https://www.36kr.com/newsflashes/3572729651116936",
                "Fri, 28 Nov 2025 12:48:32 GMT"),

            createArticle("3572727718608000",
                "美股大型科技股盘前普涨，英特尔涨超1%",
                "36氪获悉，美股大型科技股盘前普涨，截至发稿，英特尔涨超1%，谷歌涨0.99%，微软涨0.75%，亚马逊涨0.73%，特斯拉涨0.61%，Meta涨0.48%，苹果涨0.37%，奈飞涨0.36%，英伟达涨0.33%。",
                "https://www.36kr.com/newsflashes/3572727718608000",
                "Fri, 28 Nov 2025 12:46:34 GMT")
        );
    }

    private SocialMessage createArticle(String id, String title, String description, String url, String pubDateStr) {
        // 解析发布时间 - 使用ZonedDateTime和RFC 1123格式器处理RSS日期格式
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(pubDateStr, dateTimeFormatter);
        LocalDateTime timestamp = zonedDateTime.toLocalDateTime();

        // 生成内容（标题+描述）
        String content = title + ". " + description;

        // 提取话题（简单地使用标题中的主要实体作为话题）
        String topic = extractTopic(title);

        return SocialMessage.builder()
            .messageId(id)
            .source("36kr")
            .topic(topic)
            .userId("36kr")
            .timestamp(timestamp)
            .content(content)
            .interactionCount(0) // 默认值
            .contentFetchStatus(ContentFetchStatus.NOT_FETCHED)
            .url(url)
            .build();
    }

    private String extractTopic(String title) {
        // 简单的话题提取逻辑
        if (title.contains("拼多多")) return "拼多多";
        if (title.contains("长虹美菱")) return "长虹美菱";
        if (title.contains("中指研究院")) return "房地产";
        if (title.contains("中概股")) return "中概股";
        if (title.contains("美股")) return "美股";
        return "科技新闻";
    }

    @Test
    void testArticlesToScoreQueueWithRealData() {
        // 将测试文章添加到队列
        ConcurrentLinkedQueue<SocialMessage> queue = rssHubDataCollector.getArticlesToScore();
        queue.addAll(testArticles);

        // 验证队列中的文章数量
        System.out.println("队列中的文章数量: " + queue.size());
        System.out.println("测试文章列表:");

        for (SocialMessage article : queue) {
            System.out.println("- ID: " + article.messageId());
            System.out.println("  标题: " + article.content().split("\\. ")[0]);
            System.out.println("  来源: " + article.source());
            System.out.println("  话题: " + article.topic());
            System.out.println("  时间: " + article.timestamp());
            System.out.println();
        }

        // 断言队列不为空且包含所有测试文章
        assert queue.size() == testArticles.size();
    }

    @Test
    void testScheduledBatchProcessingWithTestData() {
        // 将测试文章添加到队列
        ConcurrentLinkedQueue<SocialMessage> queue = rssHubDataCollector.getArticlesToScore();
        queue.addAll(testArticles);

        // 调用定时处理方法
        rssHubDataCollector.scheduledBatchProcessing();

        // 验证是否调用了kafkaTemplate发送消息
        verify(kafkaTemplate, atLeastOnce()).send(anyString(), anyString(), any(SocialMessage.class));

        System.out.println("测试完成: scheduledBatchProcessing成功处理了队列中的文章");
    }
}