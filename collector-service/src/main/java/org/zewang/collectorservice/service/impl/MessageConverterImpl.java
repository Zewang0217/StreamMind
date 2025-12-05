package org.zewang.collectorservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.collectorservice.service.interfaces.MessageConverter;
import org.zewang.common.constant.ContentFetchStatus;
import org.zewang.common.dto.social_message.SocialMessage;

import java.util.UUID;
import java.time.LocalDateTime;

/**
 * 负责将RSS条目转换为SocialMessage的实现类
 */
@Slf4j
@Service
public class MessageConverterImpl implements MessageConverter {

    @Override
    public SocialMessage convertToSocialMessage(RSSHubItem item, RSSHubFeedConfig config) {
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
    private String extractTopic(RSSHubItem item, RSSHubFeedConfig config) {
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
    private int estimateInteractionCount(RSSHubItem item, RSSHubFeedConfig config) {
        // 根据信息源类型估算互动数
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
