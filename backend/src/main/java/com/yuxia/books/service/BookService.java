package com.yuxia.books.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.entity.SourceRules;
import com.yuxia.books.util.JsoupFetcher;
import com.yuxia.books.util.RuleParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图书业务：搜索 / 详情 / 目录 / 正文。
 * baseUrl 为 mock://local 的书源走内置假数据；否则按规则抓真实网站。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final SourceService sourceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 搜索 ----------

    public List<Map<String, Object>> search(Long sourceId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索关键字不能为空");
        }
        BookSource s = sourceService.getById(sourceId);
        if (isMock(s)) {
            return MockBookData.search(keyword.trim());
        }

        SourceRules rules = parseRules(s);
        SourceRules.SearchRule r = rules.getSearch();
        if (r == null || r.getUrl() == null || r.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("该书源没有配置搜索规则，请到高级模式补充");
        }

        String key = encodeKeyword(keyword.trim(), rules.getCharset());
        String url = absolute(s.getBaseUrl(), r.getUrl().replace("{{key}}", key));
        String body = r.getBody() == null ? null : r.getBody().replace("{{key}}", key);

        Document doc;
        try {
            doc = JsoupFetcher.fetch(url, r.getMethod(), body);
        } catch (Exception e) {
            log.warn("搜索抓取失败 url={}", url, e);
            throw new IllegalArgumentException("搜索失败，网站可能访问不了或规则不适配：" + e.getMessage());
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (r.getList() != null && !r.getList().trim().isEmpty()) {
            for (Element item : doc.select(r.getList())) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", RuleParser.text(item, r.getName()));
                m.put("author", RuleParser.text(item, r.getAuthor()));
                m.put("intro", RuleParser.text(item, r.getIntro()));
                m.put("coverUrl", RuleParser.text(item, r.getCover()));
                m.put("bookUrl", RuleParser.text(item, r.getBookUrl()));
                // 书名和链接都没有的项是脏数据，跳过
                if (m.get("name").toString().isEmpty() || m.get("bookUrl").toString().isEmpty()) {
                    continue;
                }
                result.add(m);
            }
        }
        return result;
    }

    // ---------- 详情 ----------

    public Map<String, Object> detail(Long sourceId, String bookUrl) {
        checkUrl(bookUrl);
        BookSource s = sourceService.getById(sourceId);
        if (isMock(s)) {
            return MockBookData.detail(bookUrl);
        }

        SourceRules.DetailRule r = parseRules(s).getDetail();
        Document doc = fetchPage(s, bookUrl, "详情页");

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", r != null ? RuleParser.text(doc, r.getName()) : "");
        m.put("author", r != null ? RuleParser.text(doc, r.getAuthor()) : "");
        m.put("coverUrl", r != null ? RuleParser.text(doc, r.getCover()) : "");
        m.put("intro", r != null ? RuleParser.text(doc, r.getIntro()) : "");
        m.put("latestChapter", r != null ? RuleParser.text(doc, r.getLatestChapter()) : "");
        m.put("updateTime", r != null ? RuleParser.text(doc, r.getUpdateTime()) : "");
        if (m.get("name").toString().isEmpty()) {
            m.put("name", doc.title()); // 兜底用网页标题
        }
        return m;
    }

    // ---------- 目录 ----------

    public List<Map<String, Object>> chapters(Long sourceId, String bookUrl) {
        checkUrl(bookUrl);
        BookSource s = sourceService.getById(sourceId);
        if (isMock(s)) {
            return MockBookData.chapters(bookUrl);
        }

        SourceRules.TocRule r = parseRules(s).getToc();
        if (r == null || r.getList() == null || r.getList().trim().isEmpty()) {
            throw new IllegalArgumentException("该书源没有配置目录规则，请到高级模式补充");
        }
        Document doc = fetchPage(s, bookUrl, "目录页");

        // 目录规则缺省：章节名取自身文本、链接取自身 href
        String titleRule = (r.getTitle() == null || r.getTitle().isEmpty()) ? "text" : r.getTitle();
        String urlRule = (r.getChapterUrl() == null || r.getChapterUrl().isEmpty()) ? "href" : r.getChapterUrl();

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Element a : doc.select(r.getList())) {
            String title = RuleParser.text(a, titleRule);
            String chapterUrl = RuleParser.text(a, urlRule);
            if (title.isEmpty() || chapterUrl.isEmpty()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("title", title);
            m.put("chapterUrl", chapterUrl);
            list.add(m);
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("目录规则没有匹配到任何章节，请检查选择器");
        }
        return list;
    }

    // ---------- 正文 ----------

    public Map<String, Object> content(Long sourceId, String chapterUrl) {
        checkUrl(chapterUrl);
        BookSource s = sourceService.getById(sourceId);
        if (isMock(s)) {
            return MockBookData.content(chapterUrl);
        }

        SourceRules.ContentRule r = parseRules(s).getContent();
        if (r == null || r.getSelector() == null || r.getSelector().trim().isEmpty()) {
            throw new IllegalArgumentException("该书源没有配置正文规则，请到高级模式补充");
        }
        Document doc = fetchPage(s, chapterUrl, "正文页");

        Element box = doc.selectFirst(r.getSelector());
        if (box == null) {
            throw new IllegalArgumentException("正文选择器没匹配到内容：" + r.getSelector());
        }
        if (r.getRemove() != null && !r.getRemove().trim().isEmpty()) {
            for (String sel : r.getRemove().split(",")) {
                if (!sel.trim().isEmpty()) {
                    box.select(sel.trim()).remove();
                }
            }
        }

        List<String> paragraphs = splitParagraphs(box);

        // 章节标题：优先 h1，其次网页标题
        String title = "";
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().trim().isEmpty()) {
            title = h1.text().trim();
        } else {
            title = doc.title();
        }

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", title);
        m.put("paragraphs", paragraphs);
        // 上一章/下一章 V1 不抓取，App 端按目录索引翻章
        m.put("prevUrl", null);
        m.put("nextUrl", null);
        return m;
    }

    // ---------- 私有工具 ----------

    private boolean isMock(BookSource s) {
        return SourceService.MOCK_BASE_URL.equals(s.getBaseUrl());
    }

    private void checkUrl(String url) {
        if (url == null || !(url.startsWith("http") || url.startsWith("mock://"))) {
            throw new IllegalArgumentException("链接参数不合法");
        }
    }

    private SourceRules parseRules(BookSource s) {
        try {
            SourceRules rules = objectMapper.readValue(s.getRules(), SourceRules.class);
            if (rules.getCharset() == null || rules.getCharset().isEmpty()) {
                rules.setCharset("utf-8");
            }
            return rules;
        } catch (Exception e) {
            throw new IllegalArgumentException("书源规则 JSON 解析失败：" + e.getMessage());
        }
    }

    private Document fetchPage(BookSource s, String url, String what) {
        try {
            return JsoupFetcher.fetch(url, "GET", null);
        } catch (Exception e) {
            log.warn("抓取{}失败 url={}", what, url, e);
            throw new IllegalArgumentException(what + "抓取失败，网站可能访问不了：" + e.getMessage());
        }
    }

    /** 按站点编码对关键字转 URL 编码（gbk 站点必须用 gbk 编） */
    private String encodeKeyword(String keyword, String charset) {
        String cs = charset;
        if (cs == null || cs.isEmpty()) {
            cs = "utf-8";
        }
        try {
            return URLEncoder.encode(keyword, cs);
        } catch (UnsupportedEncodingException e) {
            // 未知编码名时退回 UTF-8
            try {
                return URLEncoder.encode(keyword, "utf-8");
            } catch (UnsupportedEncodingException ignore) {
                return keyword; // UTF-8 一定存在，理论走不到
            }
        }
    }

    /** 相对地址补全成完整 URL */
    private String absolute(String baseUrl, String url) {
        if (url.startsWith("http")) {
            return url;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (url.startsWith("/")) {
            return base + url;
        }
        return base + "/" + url;
    }

    /** 正文容器的 html 转段落列表：<br>、</p> 都视为换行 */
    private List<String> splitParagraphs(Element box) {
        String html = box.html()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<p[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<[^>]+>", "");
        String text = Entities.unescape(html);

        List<String> paras = new ArrayList<String>();
        for (String line : text.split("\n+")) {
            String t = line.replace('\u00A0', ' ').replace('\r', ' ').trim();
            if (!t.isEmpty()) {
                paras.add(t);
            }
        }
        if (paras.isEmpty() && !box.text().trim().isEmpty()) {
            paras.add(box.text().trim());
        }
        return paras;
    }
}
