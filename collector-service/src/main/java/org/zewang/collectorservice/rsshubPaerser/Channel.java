package org.zewang.collectorservice.rsshubPaerser;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 通道
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 22:09
 */

// 2. Channel通道
public class Channel {
    @JacksonXmlProperty(localName = "title")
    private String title;

    @JacksonXmlProperty(localName = "ttl")
    private int ttl = 60; // 默认60分钟

    @JacksonXmlElementWrapper(useWrapping = false) // 关键！避免<item><item>...
    @JacksonXmlProperty(localName = "item")
    private List<RSSItem> items = new ArrayList<>();

    // Getters & Setters
    public String getTitle() { return title; }
    public int getTtl() { return ttl; }
    public List<RSSItem> getItems() { return items; }
}
