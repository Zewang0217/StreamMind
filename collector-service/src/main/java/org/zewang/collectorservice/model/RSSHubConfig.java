package org.zewang.collectorservice.model;


import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component; /**
 * @author "Zewang"
 * @version 1.0
 * @description: RSSHub配置类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 13:31
 */

@Data
@Component
@ConfigurationProperties(prefix = "datasources.rsshub")
public class RSSHubConfig {
    private boolean enabled;
    private String url;
    private List<RSSHubFeedConfig> feeds;
}
