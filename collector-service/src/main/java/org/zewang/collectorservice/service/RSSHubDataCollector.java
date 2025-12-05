package org.zewang.collectorservice.service;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.zewang.collectorservice.model.RSSHubConfig;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.collectorservice.model.ScoredArticleMessage;
import org.zewang.collectorservice.service.interfaces.AIScoringService;
import org.zewang.collectorservice.service.interfaces.FeedFetcher;
import org.zewang.collectorservice.service.interfaces.MessageConverter;
import org.zewang.collectorservice.service.interfaces.ResponseProcessor;
import org.zewang.collectorservice.service.interfaces.ScoredArticlePublisher;
import org.zewang.common.dto.social_message.SocialMessage;

/**
 * @author "Zewang"
 * @version 2.0
 * @description: RSSHub数据收集器 - 核心调度和协调组件
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 13:13
 */

@Slf4j
@Service
@Profile("rsshub-data")
@RequiredArgsConstructor
public class RSSHubDataCollector {

    private final RSSHubConfig rssHubConfig;
    private final FeedFetcher feedFetcher;
    private final MessageConverter messageConverter;
    private final DeduplicationService deduplicationService;
    private final AIScoringService aiScoringService;
    private final ResponseProcessor responseProcessor;
    private final ScoredArticlePublisher scoredArticlePublisher;

    // 批量收集的文章队列
    private final ConcurrentLinkedQueue<SocialMessage> articlesToScore = new ConcurrentLinkedQueue<>();
    private final int batchSize = 20;

    // 用于测试的访问方法
    public ConcurrentLinkedQueue<SocialMessage> getArticlesToScore() {
        return articlesToScore;
    }

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
            // 抓取并解析feed
            var items = feedFetcher.fetch(config, rsshubUrl);

            // 去重计数器
            int newItemsCount = 0;
            int duplicateItemsCount = 0;

            // 使用并行流处理消息转换和发送（内部使用ForkJoinPool）
            Map<Boolean, Long> stats = items.stream().parallel()
                .collect(Collectors.partitioningBy(item -> {
                    // 1. 确定唯一标识符（优先使用链接，如果链接为空则使用标题）
                    String uniqueIdentifier = item.getLink() + item.getTitle();
                    
                    // 2. 调用去重服务检测
                    boolean isNew = deduplicationService.isNewMessage(uniqueIdentifier);
                    
                    if (isNew) {
                        // 是新消息 -> 转换为SocialMessage，并添加到待评分列表
                        SocialMessage message = messageConverter.convertToSocialMessage(item, config);
                        synchronized (articlesToScore) {
                            articlesToScore.add(message);
                        }
                    }
                    return isNew;
                }, Collectors.counting()));

            // 检查是否达到批量处理阈值
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
            log.info("定时任务触发，处理队列中的{}篇文章", articlesToScore.size());
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

            try {
                // 1. 调用AI服务进行评分和分类
                String response = aiScoringService.scoreArticles(batch);
                log.debug("AI服务响应: {}", response);

                // 2. 解析AI服务响应
                List<ScoredArticleMessage> scoredArticles = responseProcessor.processResponse(response, batch);

                // 3. 发布评分结果到Kafka和数据库
                scoredArticlePublisher.publish(scoredArticles, batch);

                log.info("AI评分和分类处理完成，成功处理{}篇文章", batch.size());
            } catch (Exception e) {
                log.error("处理文章评分和分类时出错: {}", e.getMessage(), e);
                // 出错时，将文章重新加入队列以便重试
                batch.forEach(articlesToScore::offer);
            }
        }
    }

}