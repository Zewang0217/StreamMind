package org.zewang.collectorservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;
import org.zewang.collectorservice.service.interfaces.FeedFetcher;

import java.util.List;

/**
 * 负责单个feed的抓取和解析实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFetcherImpl implements FeedFetcher {

    private final WebClient webClient;
    private final RSSHubRssParser rssParser;

    @Override
    public List<RSSHubItem> fetch(RSSHubFeedConfig config, String rsshubUrl) {
        if (!config.isEnabled()) {
            log.info("Feed {} is disabled, skipping", config.getName());
            return List.of();
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

            // 解析RSS
            List<RSSHubItem> items = rssParser.parseRss(rssContent);

            // 添加调试信息
            log.info("解析到 {} 条数据项", items.size());
            if (!items.isEmpty()) {
                // 显示前3条数据的详细信息
                items.stream().limit(3).forEach(item -> {
                    log.info("数据项: 标题='{}', 链接='{}', 作者='{}'",
                            item.getTitle(), item.getLink(), item.getAuthor());
                });
            }

            return items;
        } catch (Exception e) {
            log.error("Error collecting RSSHub feed {}: {}", config.getName(), e.getMessage());
            return List.of();
        }
    }
}
