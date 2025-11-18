package org.zewang.collectorservice.rsshubPaerser;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: rsshub响应  rss根对象
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/17 22:07
 */

@JacksonXmlRootElement(localName = "rss")
public class RSSHubResponse {
    @JacksonXmlProperty(localName = "channel")
    private Channel channel;

    public Channel getChannel() {
        return channel;
    }
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

}
