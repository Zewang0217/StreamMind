package org.zewang.collectorservice.model;


import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: RSSHub项数据模型
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 13:32
 */

@Data
@NoArgsConstructor
public class RSSHubItem {
    private String title;
    private String link;
    private String description;
    private String author;
    private LocalDateTime pubDate;
    private Map<String, Object> extras; // 额外字段
    private int ttl;
}
