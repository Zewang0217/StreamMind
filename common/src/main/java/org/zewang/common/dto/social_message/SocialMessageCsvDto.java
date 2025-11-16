package org.zewang.common.dto.social_message;


import com.opencsv.bean.CsvBindByName;
import lombok.Data;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 映射 CSV 内容
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/10 19:12
 */

@Data
public class SocialMessageCsvDto {
    @CsvBindByName(column = "userId")
    private String userId;

    @CsvBindByName(column = "content")
    private String content;

    @CsvBindByName(column = "interactionCount")
    private String interactionCount;

    @CsvBindByName(column = "topic")
    private String topic;
}
