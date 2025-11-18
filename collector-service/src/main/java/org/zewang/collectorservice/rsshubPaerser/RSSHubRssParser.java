package org.zewang.collectorservice.rsshubPaerser;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.zewang.collectorservice.model.RSSHubItem;
import org.jsoup.Jsoup;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: TODO
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 22:12
 */

@Slf4j
@Component
public class RSSHubRssParser {

    private final XmlMapper xmlMapper;
    private static final DateTimeFormatter RSS_DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);

    public RSSHubRssParser() {
        this.xmlMapper = XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY) // 允许单个值作为数组
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // 忽略未知属性
            .build();
    }

    // 核心方法：解析xml字符串
    public List<RSSHubItem> parseRss(String xmlContent) {
        try {
//            // 添加调试输出，查看原始XML内容
//            log.debug("原始RSS内容: {}", xmlContent);

            // 原始解析
            RSSHubResponse response = xmlMapper.readValue(xmlContent, RSSHubResponse.class);
            Channel channel = response.getChannel();

            if (channel == null || channel.getItems().isEmpty()) {
                log.warn("RSS解析结果为空");
                return List.of();
            }

            // 转换并清洗
            return channel.getItems().stream()
                .map(item -> convertToRSSHubItem(item, channel.getTtl()))
                .toList();
        } catch (Exception e) {
            log.error("RSS解析失败", e.getMessage());
            return List.of();
        }
    }

    // Item -> RSSHubItem
    private RSSHubItem convertToRSSHubItem(RSSItem item, int channelTtl) {
        RSSHubItem result = new RSSHubItem();
        result.setTitle(cleanHtml(item.getTitle())); // 标题也可能带HTML
        result.setLink(item.getLink());
        result.setDescription(extractContent(item)); // 差异化处理
        result.setAuthor(item.getAuthor() != null ? item.getAuthor() : "anonymous");
        result.setPubDate(parsePubDate(item.getPubDate()));
        result.setExtras(createExtras(item)); // 存储原始数据
        result.setTtl(channelTtl);
        return result;
    }

    private String extractContent(RSSItem item) {
        String rawDesc = item.getDescription();
        if (rawDesc == null || rawDesc.isBlank()) {
            return "";
        }

        // B站：description里有iframe+图片+文字，只保留文字
        if (item.getLink().contains("bilibili.com")) {
            // 先解码HTML实体
        String decodedDesc = org.jsoup.parser.Parser.unescapeEntities(rawDesc, true);
        // 用Jsoup提取纯文本
        String text = Jsoup.parse(decodedDesc).text();

        // 查找"br"后的文本内容（通常是实际内容）
        int brIndex = text.indexOf("br");
        if (brIndex != -1 && brIndex < text.length() - 3) {
            String content = text.substring(brIndex + 3).trim();
            // 如果还有作者信息，进一步处理
            if (item.getAuthor() != null && content.contains(item.getAuthor())) {
                int authorIndex = content.indexOf(item.getAuthor());
                if (authorIndex != -1) {
                    content = content.substring(authorIndex + item.getAuthor().length()).trim();
                }
            }
            return content.substring(0, Math.min(200, content.length()));
        }
        return text.substring(0, Math.min(200, text.length()));

        }

        // 知乎：已经是纯文本，只截取前300字
        if (item.getLink().contains("zhihu.com")) {
            String plain = rawDesc.replaceAll("<[^>]*>", "");
            return plain.substring(0, Math.min(300, plain.length()));
        }

        // 默认：返回纯文本
        return Jsoup.parse(rawDesc).text();
    }

    // RSS日期格式解析
    private LocalDateTime parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.trim().isEmpty()) {
//            log.warn("日期字符串为空，使用当前时间");
            return LocalDateTime.now();
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(pubDateStr, RSS_DATE_FORMAT);
            return zdt.toLocalDateTime();
        } catch (Exception e) {
            log.warn("日期解析失败: {}, 使用当前时间", pubDateStr);
            return LocalDateTime.now();
        }
    }

    // 生成额外字段
    private Map<String, Object> createExtras(RSSItem item) {
        Map<String, Object> extras = new HashMap<>();
        extras.put("guid", item.getGuid());
        extras.put("originalDescriptionLength", item.getDescription().length());
        return extras;
    }

    // 简单HTML清洗（用于标题）
    private String cleanHtml(String raw) {
        return raw != null ? Jsoup.parse(raw).text() : "";
    }

}
