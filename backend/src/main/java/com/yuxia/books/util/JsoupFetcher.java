package com.yuxia.books.util;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

/**
 * 网页抓取工具：统一超时、UA、Referer，支持 GET/POST。
 */
public class JsoupFetcher {

    public static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private JsoupFetcher() {
    }

    /**
     * 抓取一个页面并解析成 Document。
     *
     * @param url    完整地址（关键字必须已经编码好）
     * @param method GET / POST（POST 时 body 为表单串，如 searchkey=%B6%B7%C6%C6）
     * @param body   POST 表单，GET 传 null
     */
    public static Document fetch(String url, String method, String body) throws IOException {
        Connection conn = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(15000)
                .referrer(originOf(url));
        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
            conn.requestBody(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(Connection.Method.POST);
        } else {
            conn.method(Connection.Method.GET);
        }
        // execute().parse() 会按页面 meta 自动识别编码（utf-8/gbk 都能正确处理）
        return conn.execute().parse();
    }

    /** 取一个网址的首页（scheme://host），给 Referer 用 */
    public static String originOf(String url) {
        int idx = url.indexOf("://");
        if (idx < 0) {
            return url;
        }
        int slash = url.indexOf('/', idx + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }
}
