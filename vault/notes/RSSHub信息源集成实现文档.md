# RSSHub信息源集成实现文档

## 一、项目现状分析

### 1.1 现有架构
- **collector-service**: 纯后台任务服务，不处理HTTP请求
- **数据收集机制**: 基于Spring Scheduling的定时任务
- **消息格式**: SocialMessage (record类型，包含messageId, source, topic, userId, timestamp, content, interactionCount)
- **Kafka集成**: 使用KafkaTemplate发送消息到social-messages-topic

### 1.2 现有RSSHub集成
- 已创建RSSHubDataCollector基础类
- 配置文件中已预留RSSHub相关配置项
- 支持通过Profile("rsshub-data")激活RSSHub数据收集

## 二、RSSHub信息源集成方案

### 2.1 目标信息源

#### B站信息源
1. **每周必看**: `/bilibili/weekly/:embed?`
2. **综合热门**: `/bilibili/popular/all/:embed?`
3. **热搜**: `/bilibili/hot-search`

#### 知乎信息源
1. **热榜**: `/zhihu/hot/:category?`
2. **想法-24小时新闻汇总**: `/zhihu/pin/daily`

### 2.2 技术实现方案

#### 2.2.1 数据模型设计
```java
// RSSHubFeedConfig.java - 配置类
public class RSSHubFeedConfig {
    private String name;           // 信息源名称
    private String route;          // RSSHub路由
    private String source;         // 数据来源 (bilibili/zhihu)
    private String category;       // 分类
    private int fetchInterval;     // 抓取间隔(分钟)
    private boolean enabled;       // 是否启用
}

// RSSHubItem.java - RSS项数据模型
public class RSSHubItem {
    private String title;
    private String link;
    private String description;
    private String author;
    private LocalDateTime pubDate;
    private Map<String, Object> extras; // 额外字段
}
```

#### 2.2.2 核心组件设计

```java
// RSSHubFeedScheduler.java - 定时任务调度器
@Component
@Profile("rsshub-data")
public class RSSHubFeedScheduler {
    
    @Autowired
    private RSSHubFeedCollector collector;
    
    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void scheduleFeeds() {
        // 根据配置的间隔时间触发各个信息源的抓取
    }
}

// RSSHubFeedCollector.java - 信息源抓取器
@Service
public class RSSHubFeedCollector {
    
    private final WebClient webClient;
    private final KafkaTemplate<String, SocialMessage> kafkaTemplate;
    
    public void collectFeed(RSSHubFeedConfig config) {
        // 1. 构建RSSHub URL
        // 2. 发送HTTP请求获取RSS数据
        // 3. 解析RSS/XML数据
        // 4. 转换为SocialMessage格式
        // 5. 发送到Kafka
    }
}
```

#### 2.2.3 RSS解析器设计
```java
// RSSHubRssParser.java - RSS解析器
@Component
public class RSSHubRssParser {
    
    public List<RSSHubItem> parseRss(String xmlContent) {
        // 使用JAXB或DOM解析RSS XML
        // 提取title, link, description, author, pubDate等字段
        // 处理不同信息源的特殊字段
    }
    
    public SocialMessage convertToSocialMessage(RSSHubItem item, String source) {
        // 根据信息源类型进行字段映射
        // 生成唯一的messageId
        // 设置合适的interactionCount（如需要可调用额外API获取）
    }
}
```

## 三、详细实现步骤

### 3.1 配置文件更新

#### 3.1.1 application.yml 配置
```yaml
datasources:
  rsshub:
    enabled: true
    url: http://localhost:1200
    feeds:
      # B站信息源
      - name: "bilibili-weekly"
        route: "/bilibili/weekly"
        source: "bilibili"
        category: "weekly"
        fetch-interval: 60  # 60分钟抓取一次
        enabled: true
      - name: "bilibili-popular"
        route: "/bilibili/popular/all"
        source: "bilibili"
        category: "popular"
        fetch-interval: 30  # 30分钟抓取一次
        enabled: true
      - name: "bilibili-hot-search"
        route: "/bilibili/hot-search"
        source: "bilibili"
        category: "hot-search"
        fetch-interval: 15  # 15分钟抓取一次
        enabled: true
      
      # 知乎信息源
      - name: "zhihu-hot"
        route: "/zhihu/hot"
        source: "zhihu"
        category: "hot"
        fetch-interval: 20  # 20分钟抓取一次
        enabled: true
      - name: "zhihu-pin-daily"
        route: "/zhihu/pin/daily"
        source: "zhihu"
        category: "pin-daily"
        fetch-interval: 60  # 60分钟抓取一次
        enabled: true
```

### 3.2 核心代码实现

#### 3.2.1 数据模型类创建

**RSSHubFeedConfig.java**
```java
package org.zewang.collectorservice.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "datasources.rsshub")
public class RSSHubConfig {
    private boolean enabled;
    private String url;
    private List<RSSHubFeedConfig> feeds;
}

@Data
public class RSSHubFeedConfig {
    private String name;
    private String route;
    private String source;
    private String category;
    private int fetchInterval; // 分钟
    private boolean enabled;
}
```

**RSSHubItem.java**
```java
package org.zewang.collectorservice.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RSSHubItem {
    private String title;
    private String link;
    private String description;
    private String author;
    private LocalDateTime pubDate;
    private Map<String, Object> extras; // 存储额外字段
}
```

#### 3.2.2 RSSHubFeedCollector实现

```java
package org.zewang.collectorservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.social_message.SocialMessage;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RSSHubFeedCollector {
    
    private final WebClient webClient;
    private final KafkaTemplate<String, SocialMessage> kafkaTemplate;
    private final RSSHubRssParser rssParser;
    
    public void collectFeed(RSSHubFeedConfig config, String rsshubUrl) {
        if (!config.isEnabled()) {
            log.info("Feed {} is disabled, skipping", config.getName());
            return;
        }
        
        try {
            String fullUrl = rsshubUrl + config.getRoute();
            log.info("Collecting RSSHub feed: {}", fullUrl);
            
            // 获取RSS数据
            String rssContent = fetchRssContent(fullUrl);
            
            // 解析RSS
            List<RSSHubItem> items = rssParser.parseRss(rssContent);
            
            // 转换并发送到Kafka
            for (RSSHubItem item : items) {
                SocialMessage message = convertToSocialMessage(item, config);
                kafkaTemplate.send(KafkaConstants.SOCIAL_MESSAGES_TOPIC, 
                                 message.messageId(), message);
                log.info("Sent RSSHub message: {}", message.messageId());
            }
            
            log.info("Successfully collected {} items from {}", items.size(), config.getName());
            
        } catch (Exception e) {
            log.error("Error collecting RSSHub feed {}: {}", config.getName(), e.getMessage());
        }
    }
    
    private String fetchRssContent(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
    
    private SocialMessage convertToSocialMessage(RSSHubItem item, RSSHubFeedConfig config) {
        // 根据信息源类型进行不同的字段映射
        String topic = extractTopic(item, config);
        int interactionCount = estimateInteractionCount(item, config);
        
        return SocialMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .source(config.getSource())
                .topic(topic)
                .userId(item.getAuthor() != null ? item.getAuthor() : "unknown")
                .timestamp(item.getPubDate() != null ? item.getPubDate() : LocalDateTime.now())
                .content(item.getTitle() + " - " + item.getDescription())
                .interactionCount(interactionCount)
                .build();
    }
    
    private String extractTopic(RSSHubItem item, RSSHubFeedConfig config) {
        // 根据配置和项目内容提取话题
        String category = config.getCategory();
        String title = item.getTitle();
        
        // 简单的关键词提取逻辑
        if (category.equals("hot-search")) {
            return title.length() > 20 ? title.substring(0, 20) : title;
        } else if (category.equals("weekly")) {
            return "B站每周必看";
        } else if (category.equals("popular")) {
            return "B站热门";
        } else if (category.equals("hot")) {
            return "知乎热榜";
        } else if (category.equals("pin-daily")) {
            return "知乎想法日报";
        }
        
        return category;
    }
    
    private int estimateInteractionCount(RSSHubItem item, RSSHubFeedConfig config) {
        // 根据信息源类型估算互动数
        // 这里可以根据实际需求调用额外的API获取真实的互动数据
        // 或者基于发布时间、内容长度等因素进行估算
        
        Random random = new Random();
        switch (config.getCategory()) {
            case "hot-search":
                return 10000 + random.nextInt(90000); // 1万-10万
            case "popular":
                return 5000 + random.nextInt(45000);  // 5千-5万
            case "weekly":
                return 1000 + random.nextInt(9000);   // 1千-1万
            case "hot":
                return 5000 + random.nextInt(45000);  // 5千-5万
            case "pin-daily":
                return 500 + random.nextInt(4500);    // 5百-5千
            default:
                return 100 + random.nextInt(900);     // 1百-1千
        }
    }
}
```

#### 3.2.3 RSS解析器实现

```java
package org.zewang.collectorservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.zewang.collectorservice.model.RSSHubItem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RSSHubRssParser {
    
    private static final DateTimeFormatter RSS_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
    
    public List<RSSHubItem> parseRss(String xmlContent) {
        List<RSSHubItem> items = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));
            
            NodeList itemNodes = document.getElementsByTagName("item");
            
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element itemElement = (Element) itemNodes.item(i);
                RSSHubItem item = parseItem(itemElement);
                items.add(item);
            }
            
        } catch (Exception e) {
            log.error("Error parsing RSS content: {}", e.getMessage());
        }
        
        return items;
    }
    
    private RSSHubItem parseItem(Element itemElement) {
        RSSHubItem item = new RSSHubItem();
        
        item.setTitle(getElementText(itemElement, "title"));
        item.setLink(getElementText(itemElement, "link"));
        item.setDescription(getElementText(itemElement, "description"));
        item.setAuthor(getElementText(itemElement, "author"));
        
        String pubDateStr = getElementText(itemElement, "pubDate");
        if (pubDateStr != null) {
            try {
                LocalDateTime pubDate = LocalDateTime.parse(pubDateStr, RSS_DATE_FORMATTER);
                item.setPubDate(pubDate);
            } catch (Exception e) {
                log.warn("Failed to parse pubDate: {}", pubDateStr);
                item.setPubDate(LocalDateTime.now());
            }
        }
        
        // 解析额外字段
        Map<String, Object> extras = new HashMap<>();
        NodeList childNodes = itemElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                Element element = (Element) childNodes.item(i);
                String tagName = element.getTagName();
                if (!isStandardRssField(tagName)) {
                    extras.put(tagName, element.getTextContent());
                }
            }
        }
        item.setExtras(extras);
        
        return item;
    }
    
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
    
    private boolean isStandardRssField(String tagName) {
        return tagName.equals("title") || tagName.equals("link") || 
               tagName.equals("description") || tagName.equals("author") || 
               tagName.equals("pubDate") || tagName.equals("guid");
    }
}
```

#### 3.2.4 定时任务调度器

```java
package org.zewang.collectorservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Profile("rsshub-data")
@RequiredArgsConstructor
public class RSSHubFeedScheduler {
    
    private final RSSHubConfig rsshubConfig;
    private final RSSHubFeedCollector feedCollector;
    
    // 记录上次抓取时间
    private final Map<String, LocalDateTime> lastFetchTime = new ConcurrentHashMap<>();
    
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void scheduleFeeds() {
        if (!rsshubConfig.isEnabled()) {
            log.info("RSSHub data collection is disabled");
            return;
        }
        
        log.info("Checking RSSHub feeds to collect...");
        
        for (RSSHubFeedConfig feedConfig : rsshubConfig.getFeeds()) {
            if (shouldFetchFeed(feedConfig)) {
                log.info("Triggering collection for feed: {}", feedConfig.getName());
                
                try {
                    feedCollector.collectFeed(feedConfig, rsshubConfig.getUrl());
                    lastFetchTime.put(feedConfig.getName(), LocalDateTime.now());
                } catch (Exception e) {
                    log.error("Error collecting feed {}: {}", feedConfig.getName(), e.getMessage());
                }
            }
        }
    }
    
    private boolean shouldFetchFeed(RSSHubFeedConfig feedConfig) {
        if (!feedConfig.isEnabled()) {
            return false;
        }
        
        LocalDateTime lastFetch = lastFetchTime.get(feedConfig.getName());
        if (lastFetch == null) {
            return true; // 从未抓取过
        }
        
        // 检查是否达到抓取间隔
        LocalDateTime nextFetchTime = lastFetch.plusMinutes(feedConfig.getFetchInterval());
        return LocalDateTime.now().isAfter(nextFetchTime);
    }
}
```

### 3.3 依赖配置

#### 3.3.1 Maven依赖
在collector-service的pom.xml中添加：
```xml
<!-- WebFlux for WebClient -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- XML解析 -->
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.3.1</version>
</dependency>
```

#### 3.3.2 WebClient配置

```java
package org.zewang.collectorservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }
}
```

## 四、部署和测试

### 4.1 启动配置
1. 确保RSSHub服务运行在`http://localhost:1200`
2. 启动collector-service时激活rsshub-data profile:
   ```bash
   java -jar collector-service.jar --spring.profiles.active=rsshub-data
   ```

### 4.2 测试验证
1. **日志验证**: 观察日志中是否有RSSHub数据收集的相关日志
2. **Kafka验证**: 使用Kafka工具检查social-messages-topic是否有新消息
3. **数据验证**: 检查消息内容是否符合预期格式

### 4.3 监控和调优
1. **抓取频率调优**: 根据RSSHub服务负载调整fetch-interval
2. **错误处理**: 监控异常日志，及时处理抓取失败
3. **性能监控**: 监控Kafka消息发送性能

## 五、扩展建议

### 5.1 增强功能
1. **去重机制**: 基于link字段实现消息去重
2. **增量更新**: 记录已处理的消息link，避免重复发送
3. **失败重试**: 实现指数退避重试机制
4. **数据清洗**: 对content进行敏感信息过滤

### 5.2 其他信息源
可按照相同模式添加其他RSSHub信息源：
- 微博热搜: `/weibo/search/hot`
- 豆瓣热门: `/douban/movie/weekly`
- GitHub Trending: `/github/trending/:since`

### 5.3 性能优化
1. **批量处理**: 一次抓取多个信息源，批量发送到Kafka
2. **异步处理**: 使用异步方式发送Kafka消息
3. **缓存机制**: 对RSSHub响应进行本地缓存

## 六、注意事项

1. **RSSHub服务可用性**: 确保RSSHub服务稳定运行
2. **抓取频率控制**: 避免过于频繁的抓取导致RSSHub服务被封禁
3. **数据格式兼容性**: 不同RSSHub路由返回的数据格式可能有差异，需要适配
4. **错误处理完善**: 完善的异常捕获和日志记录
5. **配置灵活性**: 通过配置文件灵活控制各个信息源的启用状态

---

本文档提供了完整的RSSHub信息源集成方案，包括B站和知乎相关热点的实时抓取和处理。通过配置文件可以灵活控制各个信息源的抓取频率和启用状态，确保系统的可扩展性和可维护性。