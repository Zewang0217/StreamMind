package org.zewang.common.util;


import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.zewang.common.exception.BusinessException;
import org.zewang.common.exception.ErrorCode;

/**
 * @author "Zewang"
 * @version 1.0
 * @description: 参数校验工具类
 * @email "Zewang0217@outlook.com"
 * @date 2025/11/05 20:48
 */

public class ValidationUtils {

    // 校验对象不为null
    public static void notNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BusinessException(errorCode);
        }
    }

    // 校验集合不为空
    public static void notEmpty(Collection<?> collection, ErrorCode errorCode) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(errorCode);
        }
    }

    // 校验字符串不为空
    public static void notEmpty(String str, ErrorCode errorCode) {
        if (str == null || str.isEmpty()) {
            throw new BusinessException(errorCode);
        }
    }

    // 校验Map不为空
    public static void notEmpty(Map<?, ?> map, ErrorCode errorCode) {
        if (map == null || map.isEmpty()) {
            throw new BusinessException(errorCode);
        }
    }

    // 校验数值在指定范围内
    public static void inRange(double value, double min, double max, ErrorCode errorCode) {
        if (value < min || value > max) {
            throw new BusinessException(errorCode);
        }
    }
}
