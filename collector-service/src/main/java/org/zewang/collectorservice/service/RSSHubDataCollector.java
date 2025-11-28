package org.zewang.collectorservice.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;
import org.zewang.common.constant.ContentFetchStatus;
import org.zewang.common.constant.KafkaConstants;
import org.zewang.common.dto.social_message.SocialMessage;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: RSSHub数据收集器
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 13:13
 */

@Slf4j
@Service
@Profile("rsshub-data")
@RequiredArgsConstructor
public class RSSHubDataCollector {

    private final WebClient webClient;
    private final KafkaTemplate<String, SocialMessage> kafkaTemplate;
    private final RSSHubRssParser rssParser;
    private final RSSHubConfig rssHubConfig;
    private final DeduplicationService deduplicationService;

    // 批量收集的文章队列
    private final ConcurrentLinkedQueue<SocialMessage> articlesToScore = new ConcurrentLinkedQueue<>();
    private final int batchSize = 20;

    // 用于测试的访问方法
    public ConcurrentLinkedQueue<SocialMessage> getArticlesToScore() {
        return articlesToScore;
    }

    // 火山方舟API配置
    // 修改RSSHubDataCollector.java中的配置获取方式
    @Value("${volcengine.api.key:${VOLCENGINE_API_KEY:}}")
    private String volcengineApiKey;
    @Value("${volcengine.api.secret:${VOLCENGINE_API_SECRET:}}")
    private String volcengineApiSecret;
    @Value("${volcengine.api.endpoint:${VOLCENGINE_API_ENDPOINT:https://ark.cn-beijing.volces.com/api/v3/chat/completions}}")
    private String volcengineApiEndpoint;
    @Value("${volcengine.model.id:${VOLCENGINE_MODEL_ID:doubao-seed-1-6-flash-250828}}")
    private String modelId;

    // 记录每个feed的上次抓取时间，用于控制抓取频率
    private final Map<String, LocalDateTime> lastFetchTime = new ConcurrentHashMap<>();
    
    // 并行处理线程池
    private final ExecutorService executorService = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2, // 核心线程数（I/O密集型任务设为CPU核心数的2倍）
            Runtime.getRuntime().availableProcessors() * 4, // 最大线程数
            60L, TimeUnit.SECONDS,                          // 空闲线程超时时间
            new LinkedBlockingQueue<>(100),                // 工作队列
            new ThreadPoolExecutor.CallerRunsPolicy()      // 拒绝策略（调用者执行）
    );

    // 定时调度，每小时一次，检查并抓取需要更新的RSSHub feeds
    @Scheduled(fixedDelay = 3600000) // 每小时检查一次
    public void scheduleFeeds() {
        // 检查RSSHub数据收集是否启用
        if (!rssHubConfig.isEnabled()) {
            log.info("RSSHub 数据收集器不可用");
            return;
        }

        log.info("检查RSSHub feeds");

        // 收集需要抓取的feed
        List<RSSHubFeedConfig> feedsToFetch = rssHubConfig.getFeeds().stream()
                .filter(this::shouldFetchFeed)
                .collect(Collectors.toList());
        
        log.info("发现 {} 个需要抓取的feed", feedsToFetch.size());
        
        if (feedsToFetch.isEmpty()) {
            return;
        }
        
        // 使用CompletableFuture并行处理每个feed
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (RSSHubFeedConfig feedConfig : feedsToFetch) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    try {
                        log.info("正在抓取 feed: {}", feedConfig.getName());
                        // 抓取feed
                        collectFeed(feedConfig, rssHubConfig.getUrl());
                        // 更新上次抓取时间
                        lastFetchTime.put(feedConfig.getName(), LocalDateTime.now());
                    } catch (Exception e) {
                        log.error("收集feed {} 发生错误 ： {}", feedConfig.getName(), e.getMessage());
                    }
                }, 
                executorService
            );
            futures.add(future);
        }
        
        // 等待所有并行任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("所有feed抓取任务已完成");
        } catch (Exception e) {
            log.error("并行任务执行过程中发生异常: {}", e.getMessage());
        }
    }

    // 检查 feed 是否应该被抓取
    public boolean shouldFetchFeed(RSSHubFeedConfig feedConfig) {
        // 检查feed是否启用
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


    /**
     * 收集单个RSSHub feed的数据
     * @param config feed配置
     * @param rsshubUrl RSSHub服务地址
     */
    public void collectFeed(RSSHubFeedConfig config, String rsshubUrl) {
        if (!config.isEnabled()) {
            log.info("Feed {} is disabled, skipping", config.getName());
            return;
        }

        try {
            // 构建完整的RSSHub feed URL
            String fullUrl = rsshubUrl + config.getRoute();
            log.info("Collecting RSSHub feed: {}", fullUrl);

            // 使用WebClient获取RSS数据
             String rssContent = webClient.get()
                .uri(fullUrl)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            // 添加RSS内容长度日志
//            log.info("获取到RSS内容长度: {}", rssContent != null ? rssContent.length() : 0);

            // 解析RSS
            var items = rssParser.parseRss(rssContent);

            // 去重计数器
            int newItemsCount = 0;
            int duplicateItemsCount = 0;

            // 添加调试信息
            log.info("解析到 {} 条数据项", items.size());
            if (!items.isEmpty()) {
                // 显示前3条数据的详细信息
                items.stream().limit(3).forEach(item -> {
                    log.info("数据项: 标题='{}', 链接='{}', 作者='{}'",
                        item.getTitle(), item.getLink(), item.getAuthor());
                });
            }

//            List<SocialMessage> articlesToScore = new ArrayList<>();

            // 使用并行流处理消息转换和发送（内部使用ForkJoinPool）
            Map<Boolean, Long> stats = items.stream().parallel()
                .collect(Collectors.partitioningBy(item -> {
                    // 1. 确定唯一标识符（优先使用链接，如果链接为空则使用标题）
                    String uniqueIdentifier = item.getLink() + item.getTitle();
                    
                    // 2. 调用去重服务检测
                    boolean isNew = deduplicationService.isNewMessage(uniqueIdentifier);
                    
                    if (isNew) {
                        // 是新消息 -> 转换为SocialMessage，并添加到待评分列表
                        SocialMessage message = convertToSocialMessage(item, config);
                        synchronized (articlesToScore) {
                            articlesToScore.add(message);
                        }
                    }
                    return isNew;
                }, Collectors.counting()));


                // TODO: 实现批量调用AI服务进行评分和分类
                // 1. 按批次（例如每20篇）处理
                // 2. 构建提示词，包含文章标题、描述等信息
                // 3. 调用OpenAI API进行批量评分和分类
                // 4. 将评分结果发送到scored-articles-topic


            if (articlesToScore.size() >= batchSize) {
                processBatchScoring();
            }

            newItemsCount = stats.getOrDefault(true, 0L).intValue();
            duplicateItemsCount = stats.getOrDefault(false, 0L).intValue();

            log.info("采集完成 [{}]: 共 {} 条, 新增 {} 条, 重复 {} 条",
                config.getName(), items.size(), newItemsCount, duplicateItemsCount);
        } catch (Exception e) {
            log.error("Error collecting RSSHub feed {}: {}", config.getName(), e.getMessage());
        }
    }

    // 定时处理队列中的文章
    @Scheduled(fixedDelay = 300000)
    public void scheduledBatchProcessing() {
        if (!articlesToScore.isEmpty()) {
            log.info("定时任务出伏，处理队列中的{}篇文章", articlesToScore.size());
            processBatchScoring();
        }
    }

    // 批量处理文章评分
    private void processBatchScoring() {
        List<SocialMessage> batch = new ArrayList<>(Math.min(batchSize, articlesToScore.size()));

        // 队列中取出最多batchSize条消息
        for (int i = 0; i < batchSize && !articlesToScore.isEmpty(); i++) {
            SocialMessage message = articlesToScore.poll(); // 从队列中取出一条消息
            if (message != null) {
                batch.add(message);
            }
        }

        if (!batch.isEmpty()) {
            log.info("批量处理{}篇文章，准备发送给AI评分和分类", batch.size());

            // 第一步，先将文章发送到原topic
            batch.forEach(message -> {
                kafkaTemplate.send(KafkaConstants.SOCIAL_MESSAGES_TOPIC, message.messageId(), message);
            });

            // 第二步，构建批量评分提示词
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请对以下技术文章进行评分、分类和关键词提取：\n");
            promptBuilder.append("1. 评分：对每篇文章质量进行1-10分制评分\n");
            promptBuilder.append("2. 分类：从[人工智能,后端,前端,产品,开源项目,移动开发,区块链,网络安全,DevOps,云计算]中选择\n");
            promptBuilder.append("3. 关键词：提取3-5个核心关键词\n");
            promptBuilder.append("4. 严格按照以下JSON格式输出，不要添加其他内容：\n");
            promptBuilder.append("[\n  {\n    \"article_identifier\": \"文章的messageId\",\n    \"score\": 8,\n    \"category\": \"人工智能\",\n    \"keywords\": [\"机器学习\", \"深度学习\", \"神经网络\"]\n  }\n]\n\n");
            promptBuilder.append("文章列表：\n\n");

            // 添加文章内容到提示词
            for (int i = 0; i < batch.size(); i++) {
                SocialMessage message = batch.get(i);
                promptBuilder.append("文章 " + (i + 1) + " (messageId: " + message.messageId() + "):\n");
                promptBuilder.append("标题和描述: " + message.content() + "\n\n");
            }

            try {
                // 第三步，调用火山方舟API进行评分和分类
                log.info("调用火山方舟{}模型对{}篇文章进行评分和分类", modelId, batch.size());

                // 构建请求
                String response = callVolcengineApi(promptBuilder.toString());
                log.debug("火山方舟API响应: {}", response);

                // 第四步，解析API响应并发送到scored-articles-topic
                processApiResponse(response, batch);

                log.info("AI评分和分类处理完成，成功处理{}篇文章", batch.size());
            } catch (Exception e) {
                log.error("处理文章评分和分类时出错: {}", e.getMessage(), e);
                // 出错时，将文章重新加入队列以便重试
                batch.forEach(articlesToScore::offer);
            }
        }
    }

    /**
     * 调用火山引擎AI
     * @param prompt 提示词
     * @return 响应结果
     */
    private String callVolcengineApi(String prompt) throws IOException, InterruptedException {
        // 如果没有配置API密钥，返回模拟数据
        if(volcengineApiKey == null || volcengineApiKey.isEmpty()) {
            log.warn("没有配置火山方舟API密钥，返回模拟数据");
            return generateMockResponse();
        }

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelId);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个专业的技术内容评估助手，擅长对文章进行评分、分类和关键词提取。");
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.0);

        // 转为json
        String jsonBody = new ObjectMapper().writeValueAsString(requestBody);

        // 创建HTTP客户端和请求
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(volcengineApiEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + volcengineApiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        // 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API调用失败：" + response.statusCode() + "-" + response.body());
        }

        return response.body();
    }

    /**
     * 处理API响应
     * @param response API响应
     * @param batch 文章列表
     * @throws IOException 输入输出异常
     */
    private void processApiResponse(String response, List<SocialMessage> batch) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // 尝试直接解析为文章评分数组（如果返回了数组）
            JsonNode rootNode = objectMapper.readTree(response);

            if (rootNode.isArray()) {
                // 直接是文章评分数组
                processArticlesArray(rootNode, batch);
            } else if (rootNode.has("choices") && rootNode.get("choices").isArray()) {
                // 火山方舟标准响应格式
                JsonNode choicesNode = rootNode.get("choices").get(0);
                if (choicesNode.has("message") && choicesNode.get("message").has("content")) {
                    String content = choicesNode.get("message").get("content").asText();
                    // 解析content中的JSON数组
                    JsonNode articlesArray = objectMapper.readTree(content);
                    if (articlesArray.isArray()) {
                        processArticlesArray(articlesArray, batch);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理API响应时出错: {}", e.getMessage(), e);
            extractAndProcessJson(response, batch);
        }
    }

    // 生成模拟响应数据（用于开发测试）
    private String generateMockResponse() {
        StringBuilder mockJson = new StringBuilder("[");
        Random random = new Random();
        String[] categories = {"人工智能", "后端", "前端", "产品", "开源项目", "移动开发", "区块链", "网络安全", "DevOps", "云计算"};
        
        // 生成模拟评分数据
        mockJson.append("\n  {")
               .append("\n    \"article_identifier\": \"mock-1\",").append("\n    \"score\": 8,")
               .append("\n    \"category\": \"人工智能\",").append("\n    \"keywords\": [\"机器学习\", \"深度学习\", \"神经网络\"]")
               .append("\n  },")
               .append("\n  {")
               .append("\n    \"article_identifier\": \"mock-2\",").append("\n    \"score\": 7,")
               .append("\n    \"category\": \"后端\",").append("\n    \"keywords\": [\"微服务\", \"Spring Boot\", \"性能优化\"]")
               .append("\n  }")
               .append("\n]");
        
        return mockJson.toString();
    }

    // 处理文章评分数组
    private void processArticlesArray(JsonNode articlesArray, List<SocialMessage> batch) {
        Map<String, SocialMessage> messageMap = batch.stream()
            .collect(Collectors.toMap(SocialMessage::messageId, msg -> msg));

        for (JsonNode articleNode : articlesArray) {
            if (articleNode.has("article_identifier") && articleNode.has("score") && articleNode.has("category")) {
                String messageId = articleNode.get("article_identifier").asText();
                SocialMessage originalMessage = messageMap.get(messageId);

                if (originalMessage != null) {
                    int score = articleNode.get("score").asInt();
                    String category = articleNode.get("category").asText();

                    // 提取关键词
                    List<String> keywords = new ArrayList<>();
                    if (articleNode.has("keywords") && articleNode.get("keywords").isArray()) {
                        for (JsonNode keywordNode : articleNode.get("keywords")) {
                            keywords.add(keywordNode.asText());
                        }
                    }

                    log.info("文章 {} 评分: {}, 分类: {}, 关键词: {}",
                        messageId, score, category, keywords);

                    // 这里应该创建ScoredArticleMessage并发送到Kafka
                    // 由于我们还没有创建ScoredArticleMessage类，暂时只记录日志
                    // TODO: 创建ScoredArticleMessage对象并发送到Kafka
                }
            }
        }
    }

    /**
     * 从文本中提取JSON并处理
     */
    private void extractAndProcessJson(String text, List<SocialMessage> batch) {
        try {
            // 简单的JSON提取逻辑
            int startIndex = text.indexOf('[');
            int endIndex = text.lastIndexOf(']');

            if (startIndex >= 0 && endIndex > startIndex) {
                String jsonPart = text.substring(startIndex, endIndex + 1);
                JsonNode articlesArray = new ObjectMapper().readTree(jsonPart);
                if (articlesArray.isArray()) {
                    processArticlesArray(articlesArray, batch);
                }
            }
        } catch (Exception e) {
            log.error("提取JSON失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将RSSHubItem转换为SocialMessage
     * @param item RSSHub解析后的条目
     * @param config feed配置
     * @return SocialMessage对象
     */
    private SocialMessage convertToSocialMessage(org.zewang.collectorservice.model.RSSHubItem item,
        RSSHubFeedConfig config) {
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
            .url(item.getLink())
            .contentFetchStatus(ContentFetchStatus.NOT_FETCHED)
            .build();
    }

    /**
     * 根据feed配置提取话题标签
     * @param item RSSHub条目
     * @param config feed配置
     * @return 话题标签
     */
    private String extractTopic(org.zewang.collectorservice.model.RSSHubItem item, RSSHubFeedConfig config) {
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

    /**
     * 估算互动数
     * @param item RSSHub条目
     * @param config feed配置
     * @return 估算的互动数
     */
    private int estimateInteractionCount(org.zewang.collectorservice.model.RSSHubItem item, RSSHubFeedConfig config) {
        // 根据信息源类型估算互动数
        // 这里可以根据实际需求调用额外的API获取真实的互动数据
        // 或者基于发布时间、内容长度等因素进行估算

        java.util.Random random = new java.util.Random();
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
