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
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

        // 完整目录常常不在详情页（详情页只给前几章预览），两种跳转方式：
        //   1) urlRule：用选择器从详情页里找「查看目录」链接 —— 适配目录页路径不固定的站
        //   2) url    ：写死相对路径，基于 bookUrl 补全（如 bqg70 的 index_1.html）
        String firstPage = bookUrl;
        if (r.getUrlRule() != null && !r.getUrlRule().trim().isEmpty()) {
            String href = firstValidHref(fetchPage(s, bookUrl, "详情页"), r.getUrlRule());
            if (href != null) {
                firstPage = href;
            }
        } else if (r.getUrl() != null && !r.getUrl().trim().isEmpty()) {
            firstPage = resolveUrl(bookUrl, r.getUrl().trim());
        }

        // 目录规则缺省：章节名取自身文本、链接取自身 href
        String titleRule = (r.getTitle() == null || r.getTitle().isEmpty()) ? "text" : r.getTitle();
        String urlRule = (r.getChapterUrl() == null || r.getChapterUrl().isEmpty()) ? "href" : r.getChapterUrl();

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Set<String> visited = new HashSet<String>();
        String current = firstPage;
        for (int page = 0; page < 100; page++) {
            if (!visited.add(current)) {
                break; // 防止死循环
            }
            Document doc = fetchPage(s, current, "目录页");
            for (Element a : doc.select(r.getList())) {
                String title = RuleParser.text(a, titleRule);
                String chapterUrl = RuleParser.text(a, urlRule);
                if (title.isEmpty() || chapterUrl.isEmpty()) {
                    continue;
                }
                // 章节链接补全成完整 URL：App 端要原样保存并回传，相对地址会导致后续抓不到
                chapterUrl = resolveUrl(current, chapterUrl);
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("title", title);
                m.put("chapterUrl", chapterUrl);
                list.add(m);
            }
            // 目录翻页
            if (r.getNextPage() == null || r.getNextPage().trim().isEmpty()) {
                break;
            }
            String next = firstValidHref(doc, r.getNextPage());
            if (next == null) {
                break;
            }
            current = next;
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

        boolean decode = Boolean.TRUE.equals(r.getScriptDecode());
        StringBuilder htmlBuf = new StringBuilder();
        String title = null;
        Set<String> visited = new HashSet<String>();
        String current = chapterUrl;
        for (int page = 0; page < 30; page++) {
            if (!visited.add(current)) {
                break;
            }
            Document doc = fetchPage(s, current, "正文页");
            if (title == null) {
                title = pickTitle(doc);
            }
            // 一页里可能有多个正文容器：有的站把章内分页（1/5~5/5）全塞在同一个 HTML 里，
            // 所以取全部匹配依次拼接，而不是只取第一个（只取第一个会丢 4/5 的内容）
            Elements boxes = doc.select(r.getSelector());
            if (boxes.isEmpty() && page == 0) {
                throw new IllegalArgumentException("正文选择器没匹配到内容：" + r.getSelector());
            }
            for (Element box : boxes) {
                if (r.getRemove() != null && !r.getRemove().trim().isEmpty()) {
                    for (String sel : r.getRemove().split(",")) {
                        if (!sel.trim().isEmpty()) {
                            box.select(sel.trim()).remove();
                        }
                    }
                }
                if (decode) {
                    htmlBuf.append(decodeScriptHtml(box));
                } else {
                    htmlBuf.append(box.html());
                }
                htmlBuf.append('\n');
            }
            // 章内翻页：没有规则就结束
            if (r.getNextPage() == null || r.getNextPage().trim().isEmpty()) {
                break;
            }
            String next = firstValidHref(doc, r.getNextPage());
            // 安全停止：链接无效 / 指向别的章节（不再带本章节文件名）就停，防止把整本书拼进来
            if (next == null || !isSameChapterPage(chapterUrl, next)) {
                break;
            }
            current = next;
        }

        List<String> paragraphs = splitParagraphsHtml(htmlBuf.toString());

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", title == null ? "" : title);
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

    /** 相对地址补全成完整 URL（基于站点根） */
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

    /** 相对地址基于「某个页面」补全：/x 开头拼域名，否则拼到该页面的目录下 */
    private String resolveUrl(String pageUrl, String link) {
        if (link.startsWith("http")) {
            return link;
        }
        if (link.startsWith("/")) {
            return absolute(originOf(pageUrl), link);
        }
        // 去掉页面文件名，保留目录
        int slash = pageUrl.lastIndexOf('/');
        String dir = slash >= 0 ? pageUrl.substring(0, slash + 1) : pageUrl;
        return dir + link;
    }

    private String originOf(String url) {
        int idx = url.indexOf("://");
        if (idx < 0) {
            return url;
        }
        int slash = url.indexOf('/', idx + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    /**
     * 取「下一页」链接：选择器匹配的元素里，第一个 href 是正常网址的
     * （跳过 javascript:void(0) 这类禁用按钮）
     */
    private String firstValidHref(Document doc, String selector) {
        for (String one : selector.split(",")) {
            for (Element a : doc.select(one.trim())) {
                String href = a.attr("abs:href");
                if (href.startsWith("http")) {
                    return href;
                }
            }
        }
        return null;
    }

    /** 章节标题：优先 h1，其次网页标题 */
    private String pickTitle(Document doc) {
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().trim().isEmpty()) {
            return h1.text().trim();
        }
        return doc.title();
    }

    /**
     * 判断 next 是否还是本章的分页。
     * 本章 /book/1/5027049.html 的分页形如 5027049_2.html；
     * 若 next 不匹配「章文件名(_数字)?.html」说明已经指到别的章节，停止拼接。
     */
    private boolean isSameChapterPage(String chapterUrl, String next) {
        String name = chapterUrl.substring(chapterUrl.lastIndexOf('/') + 1); // 5027049.html
        String stem = name.replaceAll("\\.html?$", "");                      // 5027049
        if (stem.isEmpty()) {
            return false;
        }
        String nextName = next.substring(next.lastIndexOf('/') + 1);
        return nextName.matches(Pattern.quote(stem) + "(_\\d+)?\\.html?$");
    }

    /** 把容器里 script 中的 base64 片段解码并拼接（部分站正文用 JS+base64 防抓） */
    private String decodeScriptHtml(Element box) {
        StringBuilder buf = new StringBuilder();
        Pattern p = Pattern.compile("['\\\"]([A-Za-z0-9+/=]{16,})['\\\"]");
        for (Element script : box.select("script")) {
            String data = script.data();
            if (data == null || data.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher m = p.matcher(data);
            while (m.find()) {
                try {
                    byte[] bytes = java.util.Base64.getDecoder().decode(m.group(1));
                    buf.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    buf.append('\n');
                } catch (IllegalArgumentException ignore) {
                    // 不是合法 base64 就跳过这段
                }
            }
        }
        return buf.toString();
    }

    /** html 字符串转段落列表：<br>、</p> 都视为换行 */
    private List<String> splitParagraphsHtml(String html) {
        String text = Entities.unescape(html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<p[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<[^>]+>", ""));

        List<String> paras = new ArrayList<String>();
        for (String line : text.split("\n+")) {
            String t = line.replace('\u00A0', ' ').replace('\r', ' ').trim();
            if (!t.isEmpty()) {
                paras.add(t);
            }
        }
        return paras;
    }
}
