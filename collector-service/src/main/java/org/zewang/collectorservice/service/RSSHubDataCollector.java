package org.zewang.collectorservice.service;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

            // 使用并行流处理消息转换和发送（内部使用ForkJoinPool）
            Map<Boolean, Long> stats = items.stream().parallel()
                .collect(Collectors.partitioningBy(item -> {
                    // 1. 确定唯一标识符（优先使用链接，如果链接为空则使用标题）
                    String uniqueIdentifier = item.getLink() + item.getTitle();
                    
                    // 2. 调用去重服务检测
                    boolean isNew = deduplicationService.isNewMessage(uniqueIdentifier);
                    
                    if (isNew) {
                        // 是新消息 -> 处理并发送
                        SocialMessage message = convertToSocialMessage(item, config);
                        kafkaTemplate.send(KafkaConstants.SOCIAL_MESSAGES_TOPIC,
                            message.messageId(), message);
                    }
                    return isNew;
                }, Collectors.counting()));
                
            newItemsCount = stats.getOrDefault(true, 0L).intValue();
            duplicateItemsCount = stats.getOrDefault(false, 0L).intValue();

            log.info("采集完成 [{}]: 共 {} 条, 新增 {} 条, 重复 {} 条",
                config.getName(), items.size(), newItemsCount, duplicateItemsCount);
        } catch (Exception e) {
            log.error("Error collecting RSSHub feed {}: {}", config.getName(), e.getMessage());
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
