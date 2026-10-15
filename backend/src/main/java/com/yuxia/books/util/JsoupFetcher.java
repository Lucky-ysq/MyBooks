package com.yuxia.books.util;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 网页抓取工具：统一超时、UA、Referer，支持 GET/POST。
 * 小说镜像站证书链普遍不全，这里全局信任所有证书（只抓公开网页，无敏感信息）。
 */
public class JsoupFetcher {

    public static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HostnameVerifier trustAny = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(trustAny);
        } catch (Exception e) {
            // 初始化失败时退回默认校验（大部分正常站点不受影响）
        }
    }

    private JsoupFetcher() {
    }

    /**
     * 抓取一个页面并解析成 Document（失败自动重试 1 次，镜像站偶发抽风）。
     *
     * @param url    完整地址（关键字必须已经编码好）
     * @param method GET / POST（POST 时 body 为表单串，如 searchkey=%B6%B7%C6%C6）
     * @param body   POST 表单，GET 传 null
     */
    public static Document fetch(String url, String method, String body) throws IOException {
        IOException last = null;
        for (int i = 0; i < 2; i++) {
            try {
                return fetchOnce(url, method, body);
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last;
    }

    private static Document fetchOnce(String url, String method, String body) throws IOException {
        Connection conn = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(20000)
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
