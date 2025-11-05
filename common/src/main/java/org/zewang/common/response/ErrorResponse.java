package org.zewang.common.response;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 错误相应结构
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 18:26
 */

@RequiredArgsConstructor
@Getter
public class ErrorResponse {
    private String code;
    private String message;

}
