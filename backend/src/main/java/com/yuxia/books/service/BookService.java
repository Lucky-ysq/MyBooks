package com.yuxia.books.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.entity.SourceRules;
import com.yuxia.books.util.JsoupFetcher;
import com.yuxia.books.util.JsRuleEngine;
import com.yuxia.books.util.LegadoRuleParser;
import com.yuxia.books.util.LegadoRuleParser.JsContext;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
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
        String rawUrl = r.getUrl().trim();
        String method = r.getMethod();
        String body = r.getBody();

        // @js: 动态搜索地址（Legado 里这类源用脚本拼 API 地址，如思路客、顶点小说）：
        // 先把 {{key}} 换成实际关键字，再跑脚本算出真实 URL
        if (rawUrl.startsWith("@js:")) {
            JsContext jsCtx = new JsContext(s.getBaseUrl(), s.getBaseUrl(), null);
            String generated = JsRuleEngine.run(
                    rawUrl.substring(4).replace("{{key}}", key), "", null, jsCtx);
            if (generated == null || generated.trim().isEmpty()) {
                throw new IllegalArgumentException("该书源的搜索地址由 JS 动态生成，但脚本没返回有效地址");
            }
            DynamicUrl du = parseDynamicUrl(generated);
            rawUrl = du.path;
            if (du.method != null) {
                method = du.method;
            }
            if (du.body != null) {
                body = du.body;
            }
        }
        if (rawUrl.isEmpty()) {
            throw new IllegalArgumentException("该书源的搜索地址为空，请到高级模式补充");
        }

        String url = absolute(s.getBaseUrl(), rawUrl.replace("{{key}}", key));
        if (body != null) {
            body = body.replace("{{key}}", key);
        }

        Document doc;
        try {
            doc = JsoupFetcher.fetch(url, method, body);
        } catch (Exception e) {
            log.warn("搜索抓取失败 url={}", url, e);
            throw new IllegalArgumentException("搜索失败，网站可能访问不了或规则不适配：" + e.getMessage());
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (r.getList() != null && !r.getList().trim().isEmpty()) {
            // JS 规则要用到书源地址和当前页面地址，构造上下文传下去
            JsContext ctx = new JsContext(s.getBaseUrl(), url, doc);
            for (Element item : LegadoRuleParser.select(doc, r.getList())) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", RuleParser.text(item, r.getName(), ctx));
                m.put("author", RuleParser.text(item, r.getAuthor(), ctx));
                m.put("intro", RuleParser.text(item, r.getIntro(), ctx));
                m.put("coverUrl", RuleParser.text(item, r.getCover(), ctx));
                String bookUrl = RuleParser.text(item, r.getBookUrl(), ctx);
                // JS 规则常只返回相对路径（如 /b/236324.html），补成绝对地址，
                // 否则 App 存进书架后详情/目录都抓不到
                if (!bookUrl.isEmpty() && !bookUrl.startsWith("http")) {
                    bookUrl = resolveUrl(url, bookUrl);
                }
                m.put("bookUrl", bookUrl);
                // 书名和链接都没有的项是脏数据，跳过
                if (m.get("name").toString().isEmpty() || bookUrl.isEmpty()) {
                    continue;
                }
                result.add(m);
            }
        }
        return result;
    }

    // ---------- 全站聚合搜索 ----------

    /** 聚合搜索总超时：到点未返回的书源标记为超时失败，接口最多 25s 必返回 */
    private static final long SEARCH_ALL_TIMEOUT_MS = 25000L;

    /** 聚合搜索最多返回的结果条数 */
    private static final int SEARCH_ALL_MAX_RESULTS = 100;

    /** 聚合搜索并发线程数：书源再多也只有 8 个同时抓，避免把对方站和自己站都拖垮 */
    private static final int SEARCH_ALL_POOL_SIZE = 8;

    /**
     * 全站聚合搜索：并发搜索所有启用的书源。
     * - 单源失败/超时只记录原因（failedSources），不影响其他源；
     * - mock 演示源始终返回全部 3 本演示书兜底（真实源全挂时用户也有东西可看）；
     * - 结果按相关度打分降序排列，按 name+author 归一化去重，最多返回 100 条。
     */
    public Map<String, Object> searchAll(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索关键字不能为空");
        }
        final String key = keyword.trim();

        // 只搜启用的书源；顺序沿用书源列表（默认源优先、id 升序），保证同分时排序稳定
        List<BookSource> enabled = new ArrayList<BookSource>();
        for (BookSource s : sourceService.list()) {
            if (Boolean.TRUE.equals(s.getEnabled())) {
                enabled.add(s);
            }
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> failedSources = new ArrayList<Map<String, Object>>();
        int successSources = 0;

        ThreadPoolExecutor executor =
                (ThreadPoolExecutor) Executors.newFixedThreadPool(SEARCH_ALL_POOL_SIZE);
        try {
            // 每个书源一个任务。任务内自己 try 之外的异常交给 Future 收集，
            // 单个书源失败绝不影响其他书源（单源隔离）。
            List<Callable<List<Map<String, Object>>>> tasks =
                    new ArrayList<Callable<List<Map<String, Object>>>>();
            for (final BookSource s : enabled) {
                tasks.add(new Callable<List<Map<String, Object>>>() {
                    public List<Map<String, Object>> call() {
                        if (isMock(s)) {
                            // 传空关键字 = 不做关键字过滤，聚合场景下演示源固定出全部演示书
                            return MockBookData.search("");
                        }
                        return search(s.getId(), key);
                    }
                });
            }
            // invokeAll + 超时：25s 到点没跑完的任务会被取消，接口不会被慢站拖死
            List<Future<List<Map<String, Object>>>> futures =
                    executor.invokeAll(tasks, SEARCH_ALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            for (int i = 0; i < futures.size(); i++) {
                BookSource s = enabled.get(i);
                Future<List<Map<String, Object>>> f = futures.get(i);
                try {
                    if (f.isCancelled()) {
                        // 超时被 invokeAll 取消
                        failedSources.add(failedSource(s, "搜索超时（超过 25 秒）"));
                        continue;
                    }
                    List<Map<String, Object>> list = f.get();
                    successSources++;
                    for (Map<String, Object> item : list) {
                        // search() 返回的条目不带书源信息，聚合层自己补上，前端要展示来源
                        item.put("sourceId", s.getId());
                        item.put("sourceName", s.getName());
                        item.put("score", Integer.valueOf(searchScore(item, key)));
                        results.add(item);
                    }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    failedSources.add(failedSource(s,
                            cause != null ? cause.getMessage() : "未知错误"));
                } catch (Exception e) {
                    failedSources.add(failedSource(s, e.getMessage()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("聚合搜索被中断", e);
        } finally {
            executor.shutdown();
        }

        // 同分时保持书源列表的先后顺序（List.sort 稳定排序），即"书源自定义顺序"
        List<Map<String, Object>> merged = mergeDuplicates(results);
        Collections.sort(merged, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return ((Integer) b.get("score")).compareTo((Integer) a.get("score"));
            }
        });
        if (merged.size() > SEARCH_ALL_MAX_RESULTS) {
            merged = new ArrayList<Map<String, Object>>(merged.subList(0, SEARCH_ALL_MAX_RESULTS));
        }

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("keyword", key);
        out.put("total", Integer.valueOf(merged.size()));
        out.put("successSources", Integer.valueOf(successSources));
        out.put("failedSources", failedSources);
        out.put("results", merged);
        return out;
    }

    /** 组装一条失败书源记录 */
    private Map<String, Object> failedSource(BookSource s, String reason) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", s.getId());
        m.put("sourceName", s.getName());
        m.put("reason", reason == null || reason.isEmpty() ? "未知错误" : reason);
        return m;
    }

    /**
     * 相关性打分（忽略大小写；"完全相等"额外忽略空格）：
     * 书名与关键字完全相同 1000；书名包含关键字 500（位置越前分越高）；
     * 作者完全相同 300；作者包含 150；简介包含 50。
     */
    private int searchScore(Map<String, Object> item, String keyword) {
        String kw = keyword.toLowerCase();
        String kwCompact = removeSpaces(kw);
        String name = str(item.get("name")).toLowerCase();
        String author = str(item.get("author")).toLowerCase();
        String intro = str(item.get("intro")).toLowerCase();

        int score = 0;
        if (removeSpaces(name).equals(kwCompact)) {
            score += 1000;
        } else {
            int pos = name.indexOf(kw);
            if (pos >= 0) {
                // 关键字出现在书名越前面，越可能是用户想找的那本
                score += 500 + Math.max(0, 100 - pos * 10);
            }
        }
        if (removeSpaces(author).equals(kwCompact)) {
            score += 300;
        } else {
            int pos = author.indexOf(kw);
            if (pos >= 0) {
                score += 150 + Math.max(0, 50 - pos * 5);
            }
        }
        if (!kw.isEmpty() && intro.contains(kw)) {
            score += 50;
        }
        return score;
    }

    /**
     * 按 name+author 归一化（trim + 小写 + 去空格）去重：
     * 保留分数高的条目；落选来源的书源名并入 alsoFrom，前端可提示"另有来源 N 个"。
     */
    private List<Map<String, Object>> mergeDuplicates(List<Map<String, Object>> all) {
        Map<String, Map<String, Object>> best = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> item : all) {
            String dedupKey = removeSpaces(str(item.get("name")).trim().toLowerCase())
                    + "|" + removeSpaces(str(item.get("author")).trim().toLowerCase());
            Map<String, Object> cur = best.get(dedupKey);
            if (cur == null) {
                best.put(dedupKey, item);
            } else if (((Integer) item.get("score")).intValue() > ((Integer) cur.get("score")).intValue()) {
                mergeSourceName(cur, item);
                best.put(dedupKey, item);
            } else {
                mergeSourceName(item, cur);
            }
        }
        return new ArrayList<Map<String, Object>>(best.values());
    }

    /** 把落选来源的书源名并入胜出者的 alsoFrom */
    @SuppressWarnings("unchecked")
    private void mergeSourceName(Map<String, Object> loser, Map<String, Object> winner) {
        String from = str(loser.get("sourceName"));
        if (from.isEmpty() || from.equals(str(winner.get("sourceName")))) {
            return;
        }
        Object also = winner.get("alsoFrom");
        List<String> names;
        if (also == null) {
            names = new ArrayList<String>();
            winner.put("alsoFrom", names);
        } else {
            names = (List<String>) also;
        }
        if (!names.contains(from)) {
            names.add(from);
        }
    }

    /** Object 安全转字符串 */
    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** 去掉所有空白字符（用于"忽略空格"的相等判断） */
    private static String removeSpaces(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
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
        JsContext ctx = new JsContext(s.getBaseUrl(), bookUrl, doc);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", r != null ? RuleParser.text(doc, r.getName(), ctx) : "");
        m.put("author", r != null ? RuleParser.text(doc, r.getAuthor(), ctx) : "");
        m.put("coverUrl", r != null ? RuleParser.text(doc, r.getCover(), ctx) : "");
        m.put("intro", r != null ? RuleParser.text(doc, r.getIntro(), ctx) : "");
        m.put("latestChapter", r != null ? RuleParser.text(doc, r.getLatestChapter(), ctx) : "");
        m.put("updateTime", r != null ? RuleParser.text(doc, r.getUpdateTime(), ctx) : "");
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
            JsContext ctx = new JsContext(s.getBaseUrl(), current, doc);
            for (Element a : LegadoRuleParser.select(doc, r.getList())) {
                String title = RuleParser.text(a, titleRule, ctx);
                String chapterUrl = RuleParser.text(a, urlRule, ctx);
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
            Elements boxes = LegadoRuleParser.select(doc, r.getSelector());
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

    /**
     * 解析 JS 生成的搜索地址。脚本返回的可能是
     *   https://m.siluke.com/search.php,{'body':'...','method':'POST'}
     * 这种「地址 + 配置」形式，这里把配置拆出来。
     */
    private static DynamicUrl parseDynamicUrl(String raw) {
        DynamicUrl d = new DynamicUrl();
        String s = raw.trim();
        int brace = s.indexOf(",{");
        if (brace < 0) {
            d.path = s;
            return d;
        }
        d.path = s.substring(0, brace).trim();
        String conf = s.substring(brace + 1);
        Matcher m = Pattern.compile("'([\\w-]+)'\\s*:\\s*'([^']*)'").matcher(conf);
        while (m.find()) {
            String k = m.group(1);
            String v = m.group(2);
            if ("method".equalsIgnoreCase(k)) {
                d.method = v.toUpperCase();
            } else if ("body".equals(k)) {
                d.body = v;
            }
        }
        return d;
    }

    /** JS 动态生成的搜索请求 */
    private static class DynamicUrl {
        String path = "";
        String method = null;
        String body = null;
    }

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
            String sel = one.trim();
            // 规则可能写成 ".sumchapter a@href"（Legado 风格），把取值后缀去掉
            int at = sel.lastIndexOf('@');
            if (at > 0) {
                String tail = sel.substring(at + 1).trim();
                if ("href".equals(tail) || "src".equals(tail)) {
                    sel = sel.substring(0, at).trim();
                }
            }
            for (Element a : LegadoRuleParser.select(doc, sel)) {
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
