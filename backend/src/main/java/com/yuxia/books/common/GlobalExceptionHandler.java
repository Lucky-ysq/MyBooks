package com.yuxia.books.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：所有接口抛出的异常统一转成 Result JSON，前端好处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数缺失等用法错误 */
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public Result badRequest(Exception e) {
        return Result.fail(e.getMessage());
    }

    /** 兜底：抓取失败、网络超时等都走这里 */
    @ExceptionHandler(Exception.class)
    public Result serverError(Exception e) {
        log.error("接口异常", e);
        return Result.fail("服务器开小差了：" + e.getMessage());
    }
}
