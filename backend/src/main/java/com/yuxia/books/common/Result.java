package com.yuxia.books.common;

import lombok.Data;

/**
 * 统一返回格式：{ code, msg, data }
 * code = 0 成功；非 0 失败，msg 为人话错误信息
 */
@Data
public class Result {

    private int code;
    private String msg;
    private Object data;

    public static Result ok(Object data) {
        Result r = new Result();
        r.code = 0;
        r.msg = "ok";
        r.data = data;
        return r;
    }

    public static Result fail(String msg) {
        Result r = new Result();
        r.code = 1;
        r.msg = msg;
        return r;
    }
}
