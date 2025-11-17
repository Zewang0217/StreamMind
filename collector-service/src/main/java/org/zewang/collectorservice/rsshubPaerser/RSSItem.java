package org.zewang.collectorservice.rsshubPaerser;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: Item条目
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 22:10
 */

public class RSSItem {
    @JacksonXmlProperty(localName = "title")
    private String title;

    @JacksonXmlProperty(localName = "link")
    private String link;

    @JacksonXmlProperty(localName = "description")
    private String description; // 保留原始HTML，后续清洗

    @JacksonXmlProperty(localName = "author")
    private String author;

    @JacksonXmlProperty(localName = "pubDate")
    private String pubDate; // 先存原始字符串，避免解析失败丢失数据

    @JacksonXmlProperty(localName = "guid")
    private String guid; // 用于去重的唯一标识

    // Getters
    public String getTitle() { return title; }
    public String getLink() { return link; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getPubDate() { return pubDate; }
    public String getGuid() { return guid != null ? guid : link; } // guid不存在时用link
}