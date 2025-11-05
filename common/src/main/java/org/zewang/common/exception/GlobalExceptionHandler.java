package org.zewang.common.exception;


import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 全局异常处理器
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 20:52
 */

@Slf4j
@RestController
public class GlobalExceptionHandler {

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("code", e.getCode());
        response.put("message", e.getMessage());
        response.put("success", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 处理参数校验异常
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数校验异常: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("code", ErrorCode.PARAMETER_INVALID.getCode());
        response.put("message", e.getMessage());
        response.put("success", false);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 处理系统异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常: ", e);

        Map<String, Object> response = new HashMap<>();
        response.put("code", ErrorCode.SYSTEM_ERROR.getCode());
        response.put("message", ErrorCode.SYSTEM_ERROR.getMessage());
        response.put("success", false);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
