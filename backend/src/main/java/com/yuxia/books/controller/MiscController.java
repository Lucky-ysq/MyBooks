package com.yuxia.books.controller;

import com.yuxia.books.common.Result;
import com.yuxia.books.util.JsoupFetcher;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 杂项接口：健康检查、封面图片代理
 */
@Slf4j
@RestController
public class MiscController {

    /** 健康检查，App 用来验证服务器地址是否填对 */
    @GetMapping("/api/health")
    public Result health() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("app", "遇夏小说后端");
        m.put("version", "1.0.0");
        m.put("time", System.currentTimeMillis());
        return Result.ok(m);
    }

    /**
     * 封面图片代理：小说网站图片多有防盗链（校验 Referer），
     * App 直接加载会 403，统一走后端代理。
     * App 端使用：封面 URL 原样传给 /api/image?url=xxx
     */
    @GetMapping("/api/image")
    public ResponseEntity<byte[]> image(@RequestParam String url) {
        if (url == null || !url.startsWith("http")) {
            throw new IllegalArgumentException("仅支持 http(s) 图片地址");
        }
        try {
            Connection.Response resp = Jsoup.connect(url)
                    .userAgent(JsoupFetcher.UA)
                    .referrer(JsoupFetcher.originOf(url)) // 用目标站自己的首页当 Referer，绕过大部分防盗链
                    .ignoreContentType(true)
                    .timeout(15000)
                    .execute();
            String contentType = resp.contentType();
            if (contentType == null || !contentType.startsWith("image")) {
                throw new IllegalArgumentException("目标地址不是图片：" + contentType);
            }
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(resp.bodyAsBytes());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片代理失败 url={}", url, e);
            throw new IllegalArgumentException("图片加载失败：" + e.getMessage());
        }
    }
}
