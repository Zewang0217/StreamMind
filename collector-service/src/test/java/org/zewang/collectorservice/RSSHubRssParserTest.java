package org.zewang.collectorservice;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zewang.collectorservice.model.RSSHubItem;
import org.zewang.collectorservice.rsshubPaerser.RSSHubRssParser;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: test RSSHubRssParser
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 22:23
 */

@SpringBootTest(classes = {RSSHubRssParser.class})
class RSSHubRssParserTest {
    @Autowired
    private RSSHubRssParser parser;

    @Test
    void testParseBilibiliRss() {
        // 用你的B站RSS片段
        String bilibiliXml = """ 
            <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0">
            <channel><title>bilibili 排行榜</title><ttl>5</ttl>
            <item><title>电竞离职后第一次线下看S赛…</title>
            <link>https://www.bilibili.com/video/BV1dHCdBoEBi</link>
            <description>&lt;iframe src="..."&gt;&lt;/iframe&gt; &lt;br&gt; &lt;img src="..."&gt;&lt;br&gt; 快乐LPL和哄柴西西收藏夹...</description>
            <author>超Carry的柴西</author>
            <pubDate>Sun, 16 Nov 2025 12:44:56 GMT</pubDate>
            </item></channel></rss>
            """;

        List<RSSHubItem> items = parser.parseRss(bilibiliXml);
        assertEquals(1, items.size());
        RSSHubItem item = items.get(0);
        assertEquals("https://www.bilibili.com/video/BV1dHCdBoEBi", item.getLink());
        assertTrue(item.getDescription().contains("快乐LPL")); // 清洗后应包含正文
        assertFalse(item.getDescription().contains("iframe")); // 不应有iframe
        System.out.println(item);
    }

    @Test
    void testParseZhihuRss() {
        String zhihuXml = """
            <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0">
            <channel><title>知乎热榜</title><ttl>5</ttl>
            <item><title>如何评价T1官宣Gumayusi离队？</title>
            <link>https://www.zhihu.com/question/1973848743590794899</link>
            <description>&lt;p&gt;T1官方：Gumayusi选手将离开T1...&lt;/p&gt;</description>
            <pubDate>Mon, 17 Nov 2025 12:23:32 GMT</pubDate>
            </item></channel></rss>
            """;

        List<RSSHubItem> items = parser.parseRss(zhihuXml);
        assertEquals("如何评价T1官宣Gumayusi离队？", items.get(0).getTitle());
    }

    @Test
    void testParseRealBilibiliPopularRss() {
        // Fetch real data from Bilibili popular endpoint
    String realBilibiliXml = """
        <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0">
        <channel>
        <title>bilibili 综合热门</title>
        <link>https://www.bilibili.com</link>
        <atom:link href="http://localhost:1200/bilibili/popular/all/:embed?" rel="self" type="application/rss+xml"/>
        <description>bilibili 综合热门 - Powered by RSSHub</description>
        <generator>RSSHub</generator>
        <webMaster>contact@rsshub.app (RSSHub)</webMaster>
        <language>en</language>
        <lastBuildDate>Mon, 17 Nov 2025 14:47:32 GMT</lastBuildDate>
        <ttl>5</ttl>
        <item>
        <title>THANK YOU, LEE 'GUMAYUSI' MIN-HYUNG</title>
        <description>&lt;img src="https://i0.hdslb.com/bfs/archive/c0bb7f0239c459a1334b81775f9124f5a54229fe.jpg" referrerpolicy="no-referrer"&gt;&lt;br&gt; 'Gumayusi'李珉炯选手将离开T1，踏上新的征程。 在共同奋斗的这些岁月里，他举起过的每一座奖杯、留下的每一道足迹，都将长久地闪耀在队伍和粉丝的心中。 T1向Gumayusi选手的奉献、付出，以及他的耀眼表现，致以由衷的敬意与感谢。 愿Gumayusi选手在即将展开的新篇章中一路顺遂，T1也将始终如一地为他送上支持与应援。</description>
        <link>https://www.bilibili.com/video/BV15vCqBTEyW</link>
        <guid isPermaLink="false">https://www.bilibili.com/video/BV15vCqBTEyW</guid>
        <pubDate>Mon, 17 Nov 2025 12:01:59 GMT</pubDate>
        <author>T1电子竞技俱乐部</author>
        </item>
        <item>
        <title>方大同的20年，给soulboy一首"新歌"丨HOPICO</title>
        <description>&lt;img src="https://i2.hdslb.com/bfs/archive/f2e677fd610d442fdf2615069302c0db21eeaae3.jpg" referrerpolicy="no-referrer"&gt;&lt;br&gt; 亲爱的大同，亲爱的soulboy，这一期送给你。</description>
        <link>https://www.bilibili.com/video/BV1PQCvBmE1X</link>
        <guid isPermaLink="false">https://www.bilibili.com/video/BV1PQCvBmE1X</guid>
        <pubDate>Mon, 17 Nov 2025 11:10:00 GMT</pubDate>
        <author>HOPICO</author>
        </item>
        </channel></rss>
        """;

    List<RSSHubItem> items = parser.parseRss(realBilibiliXml);

    // Verify we have parsed items
    assertFalse(items.isEmpty(), "Should parse at least one item");

    // Check first item details
    RSSHubItem firstItem = items.get(0);
    assertEquals("https://www.bilibili.com/video/BV15vCqBTEyW", firstItem.getLink());
    assertEquals("T1电子竞技俱乐部", firstItem.getAuthor());
    assertTrue(firstItem.getDescription().contains("Gumayusi"), "Description should contain 'Gumayusi'");
    assertTrue(firstItem.getDescription().contains("T1"), "Description should contain 'T1'");
    assertFalse(firstItem.getDescription().contains("<img"), "Should not contain HTML img tag");

    // Check second item details
    RSSHubItem secondItem = items.get(1);
    assertEquals("https://www.bilibili.com/video/BV1PQCvBmE1X", secondItem.getLink());
    assertEquals("HOPICO", secondItem.getAuthor());
    assertTrue(secondItem.getDescription().contains("大同"), "Description should contain '大同'");

    System.out.println("Parsed items: " + items);
    }
}
