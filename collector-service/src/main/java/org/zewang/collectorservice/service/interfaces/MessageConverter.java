package org.zewang.collectorservice.service.interfaces;

import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.common.dto.social_message.SocialMessage;

/**
 * 负责将RSS条目转换为SocialMessage
 */
public interface MessageConverter {
    /**
     * 将RSS条目转换为SocialMessage
     * @param item RSSHub解析后的条目
     * @param config feed配置
     * @return SocialMessage对象
     */
    SocialMessage convertToSocialMessage(RSSHubItem item, RSSHubFeedConfig config);
}
