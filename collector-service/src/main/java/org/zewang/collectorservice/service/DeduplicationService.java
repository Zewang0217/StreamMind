package org.zewang.collectorservice.service;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 去重服务类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/24 13:20
 */

@Slf4j
@Service
public class DeduplicationService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    // Redis Key 前缀，方便管理
    private static final String KEY_PREFIX = "dedup:rss:";
    // 过期时间：1天，这意味着如果一条新闻1天没被抓到，再次出现会被当做新闻
    private static final Duration EXPIRE_TIME = Duration.ofDays(1);

    /**
     * 核心方法：判断是否重复
     * @param uniqueContent 用于生成指纹的唯一内容 （标题+链接）
     * @return 是否是新消息
     */
    public boolean isNewMessage(String uniqueContent) {
        if (uniqueContent == null || uniqueContent.isEmpty()) {
            return false;
        }

        // 1. 生成MD5指纹
        // Spring 自带的 DigestUtils 工具类，不额外引入
        String md5Hex = DigestUtils.md5DigestAsHex(uniqueContent.getBytes(StandardCharsets.UTF_8));

        // 2. 构造 redis key
        String key = KEY_PREFIX + md5Hex;

        // 3. 原子操作：SETNX（Set if Not exists)
        // 如果Key不存在，写入“1”并返回true，同时设置过期时间
        // 如果key存在，什么都不做，返回false
        Boolean isAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", EXPIRE_TIME);

        // 放置空指针
        return Boolean.TRUE.equals(isAbsent);
    }

}
