package com.yuxia.books.util;

import org.jsoup.nodes.Element;

/**
 * 规则解析器：把「选择器规则」应用到元素上取值。
 *
 * 规则写法：
 *   "a"        -> 取元素 a 的文本
 *   "a@href"   -> 取元素 a 的 href 属性（自动补全为完整 URL）
 *   "href"     -> 取当前元素自身的 href 属性（用于目录列表里的 a 标签）
 *   "text"     -> 取当前元素自身的文本
 *   多个规则可用逗号分隔，依次尝试，第一个取到值的生效（适配同模板不同镜像站）
 */
public class RuleParser {

    private RuleParser() {
    }

    public static String text(Element root, String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return "";
        }
        for (String one : rule.split(",")) {
            String v = extractOne(root, one.trim());
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
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
            return attr == null ? clean(root.text()) : absolute(root, attr);
        }

        Element el = root.selectFirst(selector);
        if (el == null) {
            return "";
        }
        if ("own".equals(attr)) {
            return clean(el.ownText());
        }
        return attr == null ? clean(el.text()) : absolute(el, attr);
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
