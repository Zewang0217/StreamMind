package org.zewang.common.constant;


/**
 * @author "Zewang"
 * @version 1.0
 * @description: 网页内容抓取状态
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/26 21:27
 */

public enum ContentFetchStatus {

    NOT_FETCHED, // 未抓取
    FETCHING, // 正在抓取
    FETCHED, // 已抓取
    FETCH_FAILED; // 抓取失败
}
