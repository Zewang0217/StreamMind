package org.zewang.common.dto;


import lombok.Getter;
import lombok.Setter;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 预警信息结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:21
 */

@Getter
@Setter
public class WarningAlert {
    private String userId;
    private long windowEnd; // 窗口结束时间戳
    private double averagedScore;
    private String alertMessage;

}
