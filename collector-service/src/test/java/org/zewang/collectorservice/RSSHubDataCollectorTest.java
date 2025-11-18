// src/test/java/org/zewang/collectorservice/RSSHubDataCollectorTest.java
package org.zewang.collectorservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;
import org.zewang.collectorservice.service.RSSHubDataCollector;
import org.zewang.common.dto.social_message.SocialMessage;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: RSSHubDataCollector单元测试类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/18 18:24
 */
@ExtendWith(MockitoExtension.class)
public class RSSHubDataCollectorTest {

    @Mock
    private WebClient webClient;

    @Mock
    private KafkaTemplate<String, SocialMessage> kafkaTemplate;

    @Mock
    private RSSHubRssParser rssParser;

    @Mock
    private RSSHubConfig rssHubConfig;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private RSSHubDataCollector rssHubDataCollector;

    private RSSHubFeedConfig feedConfig;

    @BeforeEach
    void setUp() {
        feedConfig = new RSSHubFeedConfig();
        feedConfig.setName("test-feed");
        feedConfig.setRoute("/test/route");
        feedConfig.setSource("test-source");
        feedConfig.setCategory("test-category");
        feedConfig.setFetchInterval(10);
        feedConfig.setEnabled(true);
    }

    /**
     * 测试shouldFetchFeed方法：当feed从未被抓取过时，应该返回true
     * 场景：新添加的feed，还没有被处理过
     */
    @Test
    void shouldFetchFeed_WhenNeverFetched_ShouldReturnTrue() {
        // Given - 设置测试前提条件
        feedConfig.setEnabled(true);

        // When - 执行被测试的方法
        boolean result = rssHubDataCollector.shouldFetchFeed(feedConfig);

        // Then - 验证结果
        assert result;
    }

    /**
     * 测试shouldFetchFeed方法：当feed被禁用时，应该返回false
     * 场景：管理员禁用了某个feed源
     */
    @Test
    void shouldFetchFeed_WhenDisabled_ShouldReturnFalse() {
        // Given - 设置测试前提条件：禁用feed
        feedConfig.setEnabled(false);

        // When - 执行被测试的方法
        boolean result = rssHubDataCollector.shouldFetchFeed(feedConfig);

        // Then - 验证结果
        assert !result;
    }

    /**
     * 测试shouldFetchFeed方法：当抓取间隔未到达时，应该返回false
     * 场景：刚刚抓取过feed，还未到达下次抓取时间
     */
    @Test
    void shouldFetchFeed_WhenIntervalNotReached_ShouldReturnFalse() {
        // Given - 设置测试前提条件：5分钟前刚抓取过
        feedConfig.setEnabled(true);
        ReflectionTestUtils.setField(rssHubDataCollector, "lastFetchTime",
            new HashMap<String, LocalDateTime>() {{
                put("test-feed", LocalDateTime.now().minusMinutes(5)); // 5分钟前抓取
            }});

        // When - 执行被测试的方法
        boolean result = rssHubDataCollector.shouldFetchFeed(feedConfig);

        // Then - 验证结果：不应该抓取
        assert !result;
    }

    /**
     * 测试shouldFetchFeed方法：当抓取间隔已到达时，应该返回true
     * 场景：上次抓取时间已超过配置的间隔时间
     */
    @Test
    void shouldFetchFeed_WhenIntervalReached_ShouldReturnTrue() {
        // Given - 设置测试前提条件：15分钟前抓取过，超过10分钟间隔
        feedConfig.setEnabled(true);
        ReflectionTestUtils.setField(rssHubDataCollector, "lastFetchTime",
            new HashMap<String, LocalDateTime>() {{
                put("test-feed", LocalDateTime.now().minusMinutes(15)); // 15分钟前抓取
            }});

        // When - 执行被测试的方法
        boolean result = rssHubDataCollector.shouldFetchFeed(feedConfig);

        // Then - 验证结果：应该抓取
        assert result;
    }

    /**
     * 测试collectFeed方法：当feed被禁用时，不应该执行抓取操作
     * 场景：尝试抓取一个已禁用的feed
     */
    @Test
    void collectFeed_WhenFeedDisabled_ShouldNotCollect() {
        // Given - 设置测试前提条件：禁用feed
        feedConfig.setEnabled(false);

        // When - 执行被测试的方法
        rssHubDataCollector.collectFeed(feedConfig, "http://localhost:1200");

        // Then - 验证结果：不应该调用webClient.get()
        verify(webClient, never()).get();
    }

    /**
     * 测试collectFeed方法：当成功抓取时，应该将消息发送到Kafka
     * 场景：正常抓取流程，验证数据是否正确发送到Kafka
     */
    @Test
    void collectFeed_WhenSuccessful_ShouldSendMessagesToKafka() {
        // Given - 设置测试前提条件和mock对象
        feedConfig.setEnabled(true); // 确保feed是启用的

        // Mock WebClient
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("<rss></rss>"));

        // Mock RSS parser
        org.zewang.collectorservice.model.RSSHubItem rssItem = new org.zewang.collectorservice.model.RSSHubItem();
        rssItem.setTitle("Test Title");
        rssItem.setLink("http://test.com");
        rssItem.setDescription("Test Description");
        rssItem.setAuthor("Test Author");
        rssItem.setPubDate(LocalDateTime.now());
        when(rssParser.parseRss(anyString())).thenReturn(Arrays.asList(rssItem));

        // Mock Kafka template并捕获发送的数据
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SocialMessage> messageCaptor = ArgumentCaptor.forClass(SocialMessage.class);

        when(kafkaTemplate.send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture()))
            .thenReturn(null);

        // When - 执行被测试的方法
        rssHubDataCollector.collectFeed(feedConfig, "http://localhost:1200");

        // 输出调试信息
        System.out.println("发送到Kafka的数据:");
        System.out.println("Topic: " + topicCaptor.getValue());
        System.out.println("Key: " + keyCaptor.getValue());
        System.out.println("Message: " + messageCaptor.getValue());

        // 验证解析后的数据
        SocialMessage capturedMessage = messageCaptor.getValue();
        System.out.println("解析后的SocialMessage内容:");
        System.out.println("MessageId: " + capturedMessage.messageId());
        System.out.println("Source: " + capturedMessage.source());
        System.out.println("Topic: " + capturedMessage.topic());
        System.out.println("UserId: " + capturedMessage.userId());
        System.out.println("Timestamp: " + capturedMessage.timestamp());
        System.out.println("Content: " + capturedMessage.content());
        System.out.println("InteractionCount: " + capturedMessage.interactionCount());

        // Then - 验证结果：应该调用webClient.get()和kafkaTemplate.send()
        verify(webClient, times(1)).get();
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(SocialMessage.class));
    }


    /**
     * 测试scheduleFeeds方法：当配置被禁用时，不应该执行抓取操作
     * 场景：整个RSSHub功能被禁用
     */
    @Test
    void scheduleFeeds_WhenConfigDisabled_ShouldNotCollect() {
        // Given - 设置测试前提条件：禁用RSSHub配置
        when(rssHubConfig.isEnabled()).thenReturn(false);

        // When - 执行被测试的方法
        rssHubDataCollector.scheduleFeeds();

        // Then - 验证结果：应该检查isEnabled()但不应该检查getFeeds()
        verify(rssHubConfig, times(1)).isEnabled();
        verify(rssHubConfig, never()).getFeeds();
    }

    /**
     * 测试scheduleFeeds方法：当配置启用时，应该执行抓取操作
     * 场景：正常调度流程
     */
    @Test
    void scheduleFeeds_WhenConfigEnabled_ShouldCollectFeeds() {
        // Given - 设置测试前提条件和mock对象
        when(rssHubConfig.isEnabled()).thenReturn(true);
        when(rssHubConfig.getFeeds()).thenReturn(Arrays.asList(feedConfig));

        // Mock WebClient
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("<rss></rss>"));

        // Mock RSS parser
        org.zewang.collectorservice.model.RSSHubItem rssItem = new org.zewang.collectorservice.model.RSSHubItem();
        rssItem.setTitle("Test Title");
        rssItem.setLink("http://test.com");
        rssItem.setDescription("Test Description");
        rssItem.setAuthor("Test Author");
        rssItem.setPubDate(LocalDateTime.now());
        when(rssParser.parseRss(anyString())).thenReturn(Arrays.asList(rssItem));

        // When - 执行被测试的方法
        rssHubDataCollector.scheduleFeeds();

        // Then - 验证结果：应该调用webClient.get()
        verify(webClient, times(1)).get();
    }

    // 测试collectFeed方法：使用真实的RSSHub数据进行测试
    @Test
    void collectFeed_WithRealRSSHubData() {
        // Given - 使用真实的RSSHub服务
        WebClient realWebClient = WebClient.builder().build();

        // 使用ReflectionTestUtils替换被mock的webClient
        ReflectionTestUtils.setField(rssHubDataCollector, "webClient", realWebClient);

        RSSHubFeedConfig realConfig = new RSSHubFeedConfig();
        realConfig.setName("bilibili-popular");
        realConfig.setRoute("/bilibili/popular/all");
        realConfig.setSource("bilibili");
        realConfig.setCategory("popular");
        realConfig.setFetchInterval(5);
        realConfig.setEnabled(true);

        // Mock其他依赖，但不mockWebClient
        org.zewang.collectorservice.model.RSSHubItem rssItem = new org.zewang.collectorservice.model.RSSHubItem();
        rssItem.setTitle("Test Title");
        rssItem.setLink("http://test.com");
        rssItem.setDescription("Test Description");
        rssItem.setAuthor("Test Author");
        rssItem.setPubDate(LocalDateTime.now());
        when(rssParser.parseRss(anyString())).thenReturn(Arrays.asList(rssItem));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SocialMessage> messageCaptor = ArgumentCaptor.forClass(SocialMessage.class);

        when(kafkaTemplate.send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture()))
            .thenReturn(null);

        // When - 使用真实的RSSHub URL
        rssHubDataCollector.collectFeed(realConfig, "http://localhost:1200");

        // Then - 验证结果
        // 注意：这里不验证webClient.get()的调用，因为我们使用的是真实的WebClient
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(SocialMessage.class));

        // 输出实际获取的数据
        System.out.println("实际发送到Kafka的数据:");
        System.out.println("Topic: " + topicCaptor.getValue());
        System.out.println("Key: " + keyCaptor.getValue());
        if (!messageCaptor.getAllValues().isEmpty()) {
            SocialMessage capturedMessage = messageCaptor.getValue();
            System.out.println("Message: " + capturedMessage);
            System.out.println("解析后的SocialMessage内容:");
            System.out.println("MessageId: " + capturedMessage.messageId());
            System.out.println("Source: " + capturedMessage.source());
            System.out.println("Topic: " + capturedMessage.topic());
            System.out.println("UserId: " + capturedMessage.userId());
            System.out.println("Content: " + capturedMessage.content());
        }
    }

}
