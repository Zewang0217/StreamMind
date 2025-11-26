      
# StreamMind项目数据流程图：RSSHub数据收集与处理

## 1. 整体架构与数据流

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ 配置加载阶段    │     │ 数据采集阶段    │     │ 数据处理与发送  │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
┌────────▼────────┐     ┌────────▼────────┐     ┌────────▼────────┐
│ RSSHubConfig    │────▶│ RSSHubDataCollector│────▶│ SocialMessage  │
│ (全局配置)      │     │ (定时调度/并行采集)│     │ (发送到Kafka)   │
└────────┬────────┘     └────────┬────────┘     └─────────────────┘
         │                       │
┌────────▼────────┐     ┌────────▼────────┐     ┌─────────────────┐
│ RSSHubFeedConfig│     │ RSSHubItem      │     │ DeduplicationService
│ (单个源配置)    │     │ (解析后的条目)  │────▶│ (去重服务)      │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## 2. 实体类结构与关系

### 配置类层次结构
```
RSSHubConfig (根配置)
├── enabled: boolean (总开关)
├── url: String (RSSHub服务地址)
└── feeds: List<RSSHubFeedConfig> (多个订阅源配置列表)
        └── RSSHubFeedConfig (单个订阅源)
            ├── name: String (源名称)
            ├── route: String (RSSHub路由路径)
            ├── source: String (信息源标识)
            ├── category: String (分类)
            ├── fetchInterval: int (抓取间隔，分钟)
            └── enabled: boolean (单个源开关)
```

### 数据处理流程
```
RSSHubItem (解析后的数据条目)
├── title: String
├── link: String
├── description: String
├── author: String
├── pubDate: LocalDateTime
├── extras: Map<String, Object>
└── ttl: int
          ▼ 转换
SocialMessage (发送到Kafka的消息)
├── messageId: String (唯一标识)
├── source: String (来源，来自RSSHubFeedConfig)
├── topic: String (话题，通过extractTopic方法生成)
├── userId: String (作者，来自RSSHubItem)
├── timestamp: LocalDateTime (发布时间)
├── content: String (标题+描述)
├── interactionCount: int (估算的互动数)
├── contentFetchStatus: ContentFetchStatus (初始为NOT_FETCHED)
└── url: String (链接，来自RSSHubItem)
```

## 3. 详细数据流转过程

1. **配置初始化**
   - Spring Boot启动时，通过`@ConfigurationProperties`从application.yml加载`datasources.rsshub`下的配置
   - 自动映射到`RSSHubConfig`对象，包含全局开关、服务地址和多个`RSSHubFeedConfig`配置

2. **定时触发采集**
   - `RSSHubDataCollector`中的`@Scheduled(fixedDelay = 3600000)`方法每小时执行一次
   - 首先检查`RSSHubConfig`的`enabled`状态，确定是否进行采集

3. **筛选需要抓取的Feed**
   - 遍历所有`RSSHubFeedConfig`配置
   - 通过`shouldFetchFeed`方法判断是否需要抓取：
     * 检查单个Feed是否启用
     * 检查距离上次抓取时间是否超过配置的`fetchInterval`

4. **并行抓取数据**
   - 对筛选出的Feed，使用`ExecutorService`线程池创建多个`CompletableFuture`并行执行
   - 线程池配置：核心线程数=CPU核心数×2，最大线程数=CPU核心数×4，有界队列

5. **数据获取与解析**
   - 每个并行任务调用`collectFeed`方法
   - 构建完整URL：`rsshubUrl + config.getRoute()`
   - 使用`WebClient`发起HTTP请求获取RSS内容
   - 通过`RSSHubRssParser`将XML内容解析为`RSSHubItem`列表

6. **去重处理与转换**
   - 使用并行流`items.stream().parallel()`处理每个`RSSHubItem`
   - 调用`deduplicationService.isNewMessage()`进行去重
   - 对新消息，调用`convertToSocialMessage()`转换为`SocialMessage`对象
   - 使用`extractTopic()`和`estimateInteractionCount()`提取话题和估算互动数

7. **发送到Kafka**
   - 使用`KafkaTemplate`将`SocialMessage`发送到`SOCIAL_MESSAGES_TOPIC`主题
   - 记录统计信息：总数、新增数、重复数

## 4. 实体类间数据映射关系

| 源字段 (RSSHubItem) | 目标字段 (SocialMessage) | 映射方式 |
|-------------------|------------------------|--------|
| title + description | content | 直接拼接 |
| link | url | 直接映射 |
| author | userId | 直接映射，为空时设为"unknown" |
| pubDate | timestamp | 直接映射，为空时使用当前时间 |
| - | messageId | 生成UUID |
| - | source | 从RSSHubFeedConfig获取 |
| - | topic | 通过extractTopic()方法生成 |
| - | interactionCount | 通过estimateInteractionCount()方法生成 |
| - | contentFetchStatus | 固定设为NOT_FETCHED |

## 5. 技术亮点

1. **并行处理机制**
   - 两级并行：Feed级并行采集 + Item级并行处理
   - 使用ThreadPoolExecutor自定义线程池参数
   - CompletableFuture管理异步任务和错误处理

2. **配置驱动设计**
   - 基于Spring Boot ConfigurationProperties实现配置热加载
   - 支持全局和单个Feed的启用/禁用控制
   - 每个Feed可独立配置抓取间隔

3. **状态管理**
   - 使用ConcurrentHashMap记录抓取时间，确保线程安全
   - 使用ContentFetchStatus枚举管理内容抓取状态

4. **去重机制**
   - 基于Redis的分布式去重服务
   - 使用标题+链接作为唯一标识符

这个流程图展示了从配置加载到数据采集、处理、转换和发送的完整流程，清晰描述了各实体类之间的关系和数据转换过程。