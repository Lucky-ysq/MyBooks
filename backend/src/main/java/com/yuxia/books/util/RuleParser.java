package com.yuxia.books.util;

import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则解析器：把「选择器规则」应用到元素上取值。
 *
 * 规则写法：
 *   "a"          -> 取元素 a 的文本
 *   "a@href"     -> 取元素 a 的 href 属性（自动补全为完整 URL）
 *   "href"       -> 取当前元素自身的 href 属性（用于目录列表里的 a 标签）
 *   "text"       -> 取当前元素自身的文本
 *   "@own"       -> 只取元素自身文本（不含子标签，如 <a><span>免费</span>第1章</a> 只取"第1章"）
 *   "onclick:url"-> 从 onclick 属性值里抠出链接（移动站常见写法：
 *                   onclick="newWebView('/b/236324.html', '', '')"，自动补全为完整 URL）
 *   多个规则可用逗号分隔，依次尝试，第一个取到值的生效（适配同模板不同镜像站）
 *
 * 清洗语法（沿用 Legado 的写法，接在规则后面）：
 *   "规则##正则##替换文本"  取到值后做正则替换，成对出现
 *   "规则##正则"            末尾只有一个正则时表示「删掉匹配到的内容」
 *   例：".v-words span:eq(0)##作者："  ->  "作者：青铜" 变成 "青铜"
 */
public class RuleParser {

    /** 属性值里第一个引号包裹的内容 */
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");

    /** 属性值里带 .html 后缀的那一段（优先匹配，避免误抓 onclick 里的其它参数） */
    private static final Pattern HTML_LINK =
            Pattern.compile("['\"]([^'\"]*\\.html?[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);

    private RuleParser() {
    }

    public static String text(Element root, String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return "";
        }
        // 先切出清洗段：规则##正则##替换##正则##替换...
        String[] seg = rule.split("##");
        String value = "";
        for (String one : seg[0].split(",")) {
            String v = extractOne(root, one.trim());
            if (v != null && !v.isEmpty()) {
                value = v;
                break;
            }
        }
        // 再按对做正则替换；末尾落单的正则表示删除匹配内容
        for (int i = 1; i < seg.length; i += 2) {
            String pattern = seg[i];
            if (pattern.isEmpty()) {
                continue;
            }
            String replacement = (i + 1 < seg.length) ? seg[i + 1] : "";
            try {
                value = value.replaceAll(pattern, replacement);
            } catch (Exception ignored) {
                // 正则写错就跳过清洗，别让整条规则失败
            }
        }
        return clean(value);
    }

    private static String extractOne(Element root, String one) {
        // "text"：元素自身文本
        if ("text".equals(one)) {
            return clean(root.text());
        }
        // 纯属性名：元素自身属性（href/src 等）
        if (one.matches("href|src|title|alt|content")) {
            return clean(root.attr(one));
        }

        String selector = one;
        String attr = null;
        int at = one.lastIndexOf('@');
        if (at >= 0) {
            selector = one.substring(0, at).trim();
            attr = one.substring(at + 1).trim();
        }

        // "@href" 这种只写属性：作用于元素自身
        if (selector.isEmpty()) {
            if ("own".equals(attr)) {
                return clean(root.ownText()); // 只取元素自身文本，不含子标签文本
            }
            if (isUrlAttr(attr)) {
                return attrUrl(root, urlAttrName(attr));
            }
            return attr == null ? clean(root.text()) : absolute(root, attr);
        }

        Element el = root.selectFirst(selector);
        if (el == null) {
            return "";
        }
        if ("own".equals(attr)) {
            return clean(el.ownText());
        }
        if (isUrlAttr(attr)) {
            return attrUrl(el, urlAttrName(attr));
        }
        return attr == null ? clean(el.text()) : absolute(el, attr);
    }

    /** attr 是否形如 "onclick:url"（要求从属性值里抠链接） */
    private static boolean isUrlAttr(String attr) {
        return attr != null && attr.length() > 4 && attr.endsWith(":url");
    }

    private static String urlAttrName(String attr) {
        return attr.substring(0, attr.length() - 4);
    }

    /**
     * 从属性值里抠出链接。移动端站点常把链接藏在 onclick 里（没有 a[href]），
     * 如 onclick="newWebView('/b/236324.html', '', '')"。
     * 先找带 .html 的那段，找不到再退化为第一个引号内容（且必须像路径）。
     */
    private static String attrUrl(Element el, String attr) {
        String raw = el.attr(attr);
        if (raw.isEmpty()) {
            return "";
        }
        Matcher m = HTML_LINK.matcher(raw);
        String rel = m.find() ? m.group(1) : null;
        if (rel == null) {
            m = QUOTED.matcher(raw);
            if (m.find()) {
                String v = m.group(1);
                if (v.startsWith("/") || v.startsWith("http")
                        || v.startsWith("./") || v.startsWith("../")) {
                    rel = v;
                }
            }
        }
        return (rel == null || rel.isEmpty()) ? "" : toAbsolute(el, rel);
    }

    /** 相对链接补成绝对 URL（基于当前页面的 baseUri） */
    private static String toAbsolute(Element el, String rel) {
        if (rel.startsWith("http")) {
            return rel;
        }
        try {
            String base = el.baseUri();
            if (base == null || base.isEmpty()) {
                return rel;
            }
            return URI.create(base).resolve(rel).toString();
        } catch (Exception e) {
            return rel;
        }
    }

    /**
     * 相对地址转完整 URL。
     * 注意：只有值看起来像地址（http/./…开头）才补全，
     * 否则像 <meta content="梦入斗破"> 这种文本值会被 absUrl 拼成 "域名/梦入斗破"。
     */
    private static String absolute(Element el, String attr) {
        String raw = el.attr(attr);
        boolean looksLikeUrl = raw.startsWith("http") || raw.startsWith("/")
                || raw.startsWith("./") || raw.startsWith("../");
        if (!looksLikeUrl) {
            return clean(raw);
        }
        String abs = el.absUrl(attr);
        return abs.isEmpty() ? clean(raw) : abs;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u00A0', ' ').trim();
    }
}
