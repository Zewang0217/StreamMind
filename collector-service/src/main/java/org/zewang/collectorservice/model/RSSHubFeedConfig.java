package org.zewang.collectorservice.model;


import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Data
public class RSSHubFeedConfig {
    private String name;
    private String route;
    private String source;
    private String category;
    private int fetchInterval; // 分钟
    private boolean enabled;
}
