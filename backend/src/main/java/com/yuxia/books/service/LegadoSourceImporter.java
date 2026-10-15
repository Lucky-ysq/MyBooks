package com.yuxia.books.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.mapper.BookSourceMapper;
import com.yuxia.books.util.JsoupFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legado（「阅读」App）书源订阅导入器。
 *
 * 书源订阅是一个 JSON 数组，每个元素是一个书源的完整定义（bookSourceName、
 * searchUrl、ruleSearch/ruleBookInfo/ruleToc/ruleContent 等）。本类把它转换成本项目
 * book_source 表的记录：字段按对应关系映射，规则文本**原样保留**，
 * 由 {@link com.yuxia.books.util.LegadoRuleParser} 在运行时解析。
 *
 * 字段对应关系：
 *   search.url        <- searchUrl（路径部分；后面的 ,{'method':'POST','body':...} 配置会被拆出来）
 *   search.list       <- ruleSearch.bookList        search.name    <- ruleSearch.name
 *   search.bookUrl    <- ruleSearch.bookUrl         search.author  <- ruleSearch.author
 *   search.intro      <- ruleSearch.intro           search.cover   <- ruleSearch.coverUrl
 *   detail.*          <- ruleBookInfo.*
 *   toc.url           <- ruleBookInfo.tocUrl        toc.list       <- ruleToc.chapterList
 *   toc.title         <- ruleToc.chapterName        toc.chapterUrl <- ruleToc.chapterUrl
 *   toc.nextPage      <- ruleToc.nextTocUrl
 *   content.selector  <- ruleContent.content        content.nextPage <- ruleContent.nextContentUrl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegadoSourceImporter {

    private final BookSourceMapper mapper;
    private final SourceService sourceService;

    private final ObjectMapper json = new ObjectMapper();

    /** 从 searchUrl / bookSourceUrl 里拆出来的 {..., 'key':'value', ...} 配置 */
    private static final Pattern CONF_PAIR =
            Pattern.compile("'([\\w-]+)'\\s*:\\s*'([^']*)'");

    /**
     * 从订阅地址导入。
     *
     * @param url 订阅地址（http/https，返回 JSON 数组）
     * @return 统计：{ total, imported, updated, skipped, names, failed }
     */
    public Map<String, Object> importFromUrl(String url) {
        if (url == null || !url.startsWith("http")) {
            throw new IllegalArgumentException("订阅地址必须以 http 开头");
        }
        String body;
        try {
            Connection conn = Jsoup.connect(url)
                    .userAgent(JsoupFetcher.UA)
                    .timeout(30000)
                    .ignoreContentType(true);
            body = conn.execute().body();
        } catch (Exception e) {
            throw new IllegalArgumentException("下载订阅失败：" + e.getMessage());
        }
        return importFromJson(body);
    }

    /**
     * 判断一段响应内容是不是「书源订阅文件」。
     * 判定依据是**内容结构**而不是地址后缀：顶层 JSON 数组、且元素里带
     * bookSourceUrl / bookSourceName 字段。这样不管用户粘的是
     * `.../shuyuan`、`.../xxx.json` 还是别的路径，都能认出来。
     */
    public boolean looksLikeSubscription(String body) {
        if (body == null) {
            return false;
        }
        String b = body.trim();
        if (b.isEmpty() || b.charAt(0) != '[') {
            return false;
        }
        try {
            JsonNode root = json.readTree(b);
            if (!root.isArray() || root.size() == 0) {
                return false;
            }
            for (JsonNode n : root) {
                if (n.has("bookSourceUrl") || n.has("bookSourceName")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 不是合法 JSON，或不是数组：当普通网页处理
            return false;
        }
        return false;
    }

    /**
     * 取一个地址的原始响应体（不做 HTML 解析），用于「添加书源」时判断类型。
     * 失败返回 null（由调用方决定后续处理）。
     * 超时给到 30s：书源订阅文件可能有几百 KB，探测超时太短会把订阅误判成网站源。
     */
    public String fetchRawBody(String url) {
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        try {
            return Jsoup.connect(url)
                    .userAgent(JsoupFetcher.UA)
                    .timeout(30000)
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            log.info("探测书源类型时取不到内容 {}：{}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 地址形态上是否像书源订阅：以 shuyuan / .json / .txt 结尾，或路径里带 /raw/、/sources/。
     * 仅用于「取不到内容时」给出更准确的提示，真正的判定始终以内容为准。
     */
    public boolean looksLikeSubscriptionUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase();
        int q = u.indexOf('?');
        if (q > 0) {
            u = u.substring(0, q);
        }
        return u.endsWith("shuyuan") || u.endsWith(".json") || u.endsWith(".txt")
                || u.contains("/raw/") || u.contains("/sources/");
    }

    /** 从 JSON 文本导入（订阅内容直接粘贴时用） */
    public Map<String, Object> importFromJson(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("订阅内容为空");
        }
        JsonNode root;
        try {
            root = json.readTree(body.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("订阅不是合法 JSON：" + e.getMessage());
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("订阅格式不对：顶层应该是书源数组");
        }

        int total = 0, imported = 0, updated = 0, skipped = 0;
        List<String> names = new ArrayList<String>();
        List<String> failed = new ArrayList<String>();

        for (JsonNode node : root) {
            total++;
            String name = node.path("bookSourceName").asText("").trim();
            try {
                BookSource s = convert(node);
                if (s == null) {
                    skipped++;
                    failed.add(name + "（缺少名称或网址）");
                    continue;
                }
                BookSource exist = findByBaseUrl(s.getBaseUrl());
                if (exist != null) {
                    s.setId(exist.getId());
                    s.setCreateTime(exist.getCreateTime());
                    s.setEnabled(exist.getEnabled());
                    s.setIsDefault(exist.getIsDefault());
                    mapper.updateById(s);
                    updated++;
                } else {
                    sourceService.save(s);
                    imported++;
                }
                names.add(s.getName());
            } catch (Exception e) {
                skipped++;
                failed.add(name + "（" + e.getMessage() + "）");
                log.warn("导入书源失败 {}：{}", name, e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("total", total);
        out.put("imported", imported);
        out.put("updated", updated);
        out.put("skipped", skipped);
        out.put("names", names);
        out.put("failed", failed);
        log.info("订阅导入完成：共 {} 条，新增 {}，更新 {}，跳过 {}", total, imported, updated, skipped);
        return out;
    }

    // ---------- 单个书源转换 ----------

    private BookSource convert(JsonNode node) {
        String name = node.path("bookSourceName").asText("").trim();
        String rawUrl = node.path("bookSourceUrl").asText("").trim();
        if (name.isEmpty() || rawUrl.isEmpty()) {
            return null;
        }
        // bookSourceUrl 后面也可能挂配置，如 https://x.com,{'headers':{...}}
        String baseUrl = stripConfig(rawUrl).trim();
        if (!baseUrl.startsWith("http")) {
            return null; // 非网页源（如 mock://、特殊协议）本项目不支持
        }

        BookSource s = new BookSource();
        s.setName(name);
        s.setBaseUrl(baseUrl);
        boolean enabled = node.path("enabled").asBoolean(true);
        s.setEnabled(enabled);
        s.setIsDefault(false);
        String group = node.path("bookSourceGroup").asText("").trim();
        s.setRemark("Legado 订阅导入" + (group.isEmpty() ? "" : " · " + group));
        s.setRules(buildRules(node, baseUrl));
        return s;
    }

    /**
     * 归一化 Legado 的占位符与包装标签。
     * {{key}} 保留（运行时替换成实际关键字并编码）；
     * {{page}} 固定成 1（本项目搜索只取第一页）；
     * {{baseUrl}} 换成书源地址；
     * &lt;js&gt;...&lt;/js&gt; 标签去掉（Legado 用它标记「这段是 JS」，
     * 但很多源只是把静态路径包在里面，留着会让 URL 变成 https://x.com/&lt;js&gt;/path）。
     */
    private String normalizePlaceholders(String s, String baseUrl) {
        if (s == null) {
            return null;
        }
        return s.replace("<js>", "").replace("</js>", "")
                .replace("{{page}}", "1")
                .replace("{{baseUrl}}", baseUrl == null ? "" : baseUrl);
    }

    /** 把 Legado 的四个规则组映射成本项目的 rules JSON */
    private String buildRules(JsonNode node, String baseUrl) {
        ObjectNode rules = json.createObjectNode();

        SearchUrl su = parseSearchUrl(node.path("searchUrl").asText(""));
        rules.put("charset", su.charset);

        JsonNode rs = node.path("ruleSearch");
        ObjectNode search = rules.putObject("search");
        // @js: 的动态地址原样保留（运行时替换 {{key}} 后再执行），静态地址在这里归一化占位符
        search.put("url", su.dynamic ? su.path : normalizePlaceholders(su.path, baseUrl));
        if (su.method != null) {
            search.put("method", su.method);
        }
        if (su.body != null) {
            search.put("body", normalizePlaceholders(su.body, baseUrl));
        }
        copy(search, "list", rs, "bookList");
        copy(search, "name", rs, "name");
        copy(search, "bookUrl", rs, "bookUrl");
        copy(search, "author", rs, "author");
        copy(search, "intro", rs, "intro");
        copy(search, "cover", rs, "coverUrl");

        JsonNode rb = node.path("ruleBookInfo");
        ObjectNode detail = rules.putObject("detail");
        copy(detail, "name", rb, "name");
        copy(detail, "author", rb, "author");
        copy(detail, "cover", rb, "coverUrl");
        copy(detail, "intro", rb, "intro");
        copy(detail, "latestChapter", rb, "lastChapter");
        copy(detail, "updateTime", rb, "updateTime");

        JsonNode rt = node.path("ruleToc");
        ObjectNode toc = rules.putObject("toc");
        copy(toc, "list", rt, "chapterList");
        copy(toc, "title", rt, "chapterName");
        copy(toc, "chapterUrl", rt, "chapterUrl");
        copy(toc, "nextPage", rt, "nextTocUrl");
        // 目录页地址在 ruleBookInfo.tocUrl 里：可能是静态路径，也可能是「从详情页取链接」的选择器
        String tocUrl = rb.path("tocUrl").asText("").trim();
        if (!tocUrl.isEmpty()) {
            if (tocUrl.startsWith("http") || tocUrl.startsWith("/")) {
                toc.put("url", tocUrl);
            } else {
                toc.put("urlRule", tocUrl);
            }
        }

        JsonNode rc = node.path("ruleContent");
        ObjectNode content = rules.putObject("content");
        copy(content, "selector", rc, "content");
        // selector 必须是纯选择器，把 Legado 的取值后缀（@html / @text 等）去掉
        if (content.has("selector")) {
            content.put("selector", stripValueSuffix(content.get("selector").asText()));
        }
        copy(content, "nextPage", rc, "nextContentUrl");
        // 注意：Legado 的 replaceRegex 是「正文正则清洗」，本项目的 remove 是「要剔除的元素选择器」，
        // 两者语义不同，硬套会误删正文，所以不做映射

        return rules.toString();
    }

    /** 去掉 Legado 规则尾部的取值后缀，如 ".con@html" -> ".con" */
    private String stripValueSuffix(String rule) {
        if (rule == null) {
            return null;
        }
        int at = rule.lastIndexOf('@');
        if (at <= 0) {
            return rule;
        }
        String tail = rule.substring(at + 1).trim();
        if ("html".equals(tail) || "text".equals(tail) || "all".equals(tail)
                || "textNodes".equals(tail) || "ownText".equals(tail)) {
            return rule.substring(0, at).trim();
        }
        return rule;
    }

    /** 把 Legado 字段搬到本项目字段；值是数组时用逗号连接（本项目逗号=依次回退） */
    private void copy(ObjectNode target, String targetKey, JsonNode src, String srcKey) {
        JsonNode v = src.path(srcKey);
        if (v.isMissingNode() || v.isNull()) {
            return;
        }
        String text;
        if (v.isArray()) {
            List<String> parts = new ArrayList<String>();
            for (JsonNode one : v) {
                String t = one.asText("").trim();
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
            text = String.join(",", parts);
        } else {
            text = v.asText("").trim();
        }
        if (!text.isEmpty()) {
            target.put(targetKey, text);
        }
    }

    /**
     * 解析 searchUrl：形如
     *   /search.html
     *   /modules/article/search.php,{'charset':'gbk','body':'searchkey={{key}}','method':'POST'}
     */
    private SearchUrl parseSearchUrl(String raw) {
        SearchUrl r = new SearchUrl();
        if (raw == null || raw.trim().isEmpty()) {
            return r;
        }
        String s = raw.trim();
        if (s.startsWith("@js:")) {
            // 动态生成搜索地址：原样保留，运行时由 JS 引擎算出真实 URL（见 BookService.search）
            r.path = s;
            r.dynamic = true;
            return r;
        }
        int brace = s.indexOf(",{");
        String urlPart = s;
        if (brace >= 0) {
            urlPart = s.substring(0, brace);
            String conf = s.substring(brace + 1);
            Matcher m = CONF_PAIR.matcher(conf);
            while (m.find()) {
                String k = m.group(1);
                String v = m.group(2);
                if ("method".equalsIgnoreCase(k)) {
                    r.method = v.toUpperCase();
                } else if ("body".equals(k)) {
                    r.body = v;
                } else if ("charset".equals(k)) {
                    r.charset = v;
                }
            }
        }
        r.path = urlPart.trim();
        return r;
    }

    /** 去掉网址后面挂的 ,{...} 配置 */
    private String stripConfig(String url) {
        int brace = url.indexOf(",{");
        return brace >= 0 ? url.substring(0, brace) : url;
    }

    private BookSource findByBaseUrl(String baseUrl) {
        List<BookSource> list = mapper.selectList(
                new QueryWrapper<BookSource>().eq("base_url", baseUrl));
        return list.isEmpty() ? null : list.get(0);
    }

    private static class SearchUrl {
        String path = "";
        String method = null;
        String body = null;
        String charset = "utf-8";
        boolean dynamic = false;
    }
}
