package com.chen.exception;

public class ThrowUtils {

    /**
     * 条件成立则抛异常
     *
     * @param condition        条件
     * @param runtimeException 异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 无条件抛异常
     *
     * @param errorCode 错误码
     */
    public static void throwIf(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }

    /**
     * 无条件抛异常
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public static void throwIf(ErrorCode errorCode, String message) {
        throw new BusinessException(errorCode, message);
    }

    /**
     * 条件成立则抛异常
     *
     * @param condition 条件
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立则抛异常
     *
     * @param condition 条件
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}
