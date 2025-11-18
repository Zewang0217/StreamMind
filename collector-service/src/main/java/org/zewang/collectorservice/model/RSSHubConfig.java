package org.zewang.collectorservice.model;


import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component; /**
 * @author "Zewang"
 * @version 1.0
 * @description: RSSHub配置类 用于管理和存储RSSHub相关的所有配置信息
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 13:31
 */

@Data
@Component
@ConfigurationProperties(prefix = "datasources.rsshub") // 自动从 application.yml 文件中读取配置，将 datasources.rsshub 下的配置项自动映射到对应的字段
public class RSSHubConfig {
    private boolean enabled; // 控制整个RSSHub服务是否启用
    private String url; // RSSHub服务的地址 例如：http://localhost:1200
    private List<RSSHubFeedConfig> feeds;
}
