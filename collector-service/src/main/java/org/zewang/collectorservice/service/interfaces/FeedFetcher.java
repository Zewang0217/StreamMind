package org.zewang.collectorservice.service.interfaces;

import org.zewang.collectorservice.model.RSSHubFeedConfig;
import org.zewang.collectorservice.model.RSSHubItem;

import java.util.List;

/**
 * 负责单个feed的抓取和解析
 */
public interface FeedFetcher {
    /**
     * 抓取并解析指定的feed
     * @param config feed配置
     * @param rsshubUrl RSSHub服务地址
     * @return 解析后的RSS条目列表
     */
    List<RSSHubItem> fetch(RSSHubFeedConfig config, String rsshubUrl);
}
