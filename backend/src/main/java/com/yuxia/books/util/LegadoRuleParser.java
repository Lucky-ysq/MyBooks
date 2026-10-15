package com.yuxia.books.util;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legado（「阅读」App）书源规则解析器。
 *
 * 为什么需要它：xiu2/yuedu 这类书源订阅里的规则全是 Legado 私有语法，
 * 标准 CSS 解析器读不懂。本类实现 Legado 的核心语法子集：
 *
 * 一、选择器链（用 @ 分段，逐级缩小范围）
 *   class.newbox.0@tag.ul.0@tag.li   先找 class=newbox 的第 0 个，再找其中的 ul 第 0 个，再找全部 li
 *   - class.xxx  -> .xxx          tag.xxx -> xxx        id.xxx -> #xxx
 *   - text.xxx   -> 按文本内容匹配（*:contains(xxx)）
 *   - 每段可带下标：.0 第1个 / .-1 最后一个 / 不带则全取
 *   - 标准 CSS 直接透传（.item / #list a / dt a 等，本项目原有规则照常工作）
 *
 * 二、取值符（写在 @ 后面，不写默认取文本）
 *   @text @textNodes @ownText @html @outerHtml @href @src @content
 *   @onclick:url  本项目扩展：从 onclick 属性里抠链接（移动站常见）
 *
 * 三、清洗 ## （沿用 Legado 写法）
 *   规则##正则##替换文本   末尾落单的正则表示删除匹配内容
 *
 * 四、多规则 && （结果拼接；前一个为空则用后一个）
 *
 * 五、XPath（// 开头 或 @XPath: 前缀）—— 常见模式转成等价 CSS
 *
 * 六、JS 脚本（&lt;js&gt; 整条 / @js: 后缀）—— 交给 {@link JsRuleEngine}
 */
public class LegadoRuleParser {

    private static final int NO_INDEX = Integer.MIN_VALUE;

    /** 段尾下标，如 class.newbox.0 / tag.a.-1 */
    private static final Pattern TAIL_INDEX = Pattern.compile("\\.(-?\\d+)$");

    /** XPath 的 [@attr='value'] */
    private static final Pattern X_ATTR =
            Pattern.compile("\\[\\s*@([\\w:-]+)\\s*=\\s*['\"]([^'\"]*)['\"]\\s*\\]");

    /** XPath 的 [数字] */
    private static final Pattern X_INDEX = Pattern.compile("\\[(\\d+)\\]");

    /** XPath 结尾的 /@attr */
    private static final Pattern X_TAIL_ATTR = Pattern.compile("/@([\\w-]+)\\s*$");

    /** 属性值里的第一个引号内容 */
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");

    /** 属性值里带 .html 的那一段 */
    private static final Pattern HTML_LINK =
            Pattern.compile("['\"]([^'\"]*\\.html?[^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);

    private static final String[] VALUE_TOKENS = {
            "text", "textNodes", "ownText", "own", "html", "all", "outerHtml",
            "href", "src", "content", "value", "title", "alt", "data-src",
            "data-original", "onclick"
    };

    private LegadoRuleParser() {
    }

    /** 这条规则是否用到了 Legado 私有语法（纯标准 CSS 不在此列，交给原 RuleParser） */
    public static boolean matches(String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return false;
        }
        String r = rule.trim();
        return r.startsWith("//")
                || r.startsWith("@XPath:")
                || r.contains("<js>")
                || r.contains("@js:")
                || r.contains("&&")
                // Legado 的取值符：@text / @textNodes / @ownText / @html
                // （不能漏，否则 ".v-title@text" 会被当成「取名为 text 的属性」，取到空值）
                || r.contains("@text")
                || r.contains("@ownText")
                || r.contains("@html")
                || r.contains("@class.") || r.contains("@tag.")
                || r.contains("@id.") || r.contains("@text.")
                || r.startsWith("class.") || r.startsWith("tag.")
                || r.startsWith("id.") || r.startsWith("text.");
    }

    /** 一条规则求值，返回字符串（空串表示没取到） */
    public static String getString(Element root, String rule, JsContext ctx) {
        if (rule == null || rule.trim().isEmpty()) {
            return "";
        }
        String r = rule.trim();

        // 整条就是一段 JS
        if (r.startsWith("<js>")) {
            return JsRuleEngine.run(extractJsBlock(r), "", root, ctx);
        }

        // 先切出清洗段：规则##正则##替换##正则##替换
        String[] seg = r.split("##");
        StringBuilder value = new StringBuilder();
        for (String one : seg[0].split("&&")) {
            String v = evalOne(root, one.trim(), ctx);
            if (v != null && !v.isEmpty()) {
                value.append(v);
            }
        }
        String out = value.toString();
        for (int i = 1; i < seg.length; i += 2) {
            String pattern = seg[i];
            if (pattern.isEmpty()) {
                continue;
            }
            String replacement = (i + 1 < seg.length) ? seg[i + 1] : "";
            try {
                out = out.replaceAll(pattern, replacement);
            } catch (Exception ignored) {
                // 正则写错就跳过清洗，别让整条规则失败
            }
        }
        return clean(out);
    }

    private static String evalOne(Element root, String rule, JsContext ctx) {
        if (rule.isEmpty()) {
            return "";
        }
        // @js: 后缀：先用前半段取出 result，再交给 JS 加工
        int jsAt = rule.indexOf("@js:");
        if (jsAt >= 0) {
            String head = rule.substring(0, jsAt).trim();
            String code = rule.substring(jsAt + 4);
            String base = head.isEmpty() ? "" : evalOne(root, head, ctx);
            return JsRuleEngine.run(code, base, root, ctx);
        }
        // XPath
        if (rule.startsWith("//") || rule.startsWith("@XPath:")) {
            return evalXPath(root, rule);
        }
        // 选择器链
        return evalChain(root, rule);
    }

    // ---------- 选择器链 ----------

    private static String evalChain(Element root, String rule) {
        // "@href" / "@onclick" 这种只写属性名的写法：直接取元素自身的属性
        if (rule.startsWith("@")) {
            return readValue(root, rule.substring(1));
        }
        String chain = rule;
        String attr = null;
        int at = rule.lastIndexOf('@');
        if (at > 0) {
            String tail = rule.substring(at + 1).trim();
            if (isValueToken(tail)) {
                chain = rule.substring(0, at);
                attr = tail;
            }
        }
        Elements els = selectChain(root, chain);
        if (els.isEmpty()) {
            return "";
        }
        return readValue(els.first(), attr);
    }

    /** 按规则选元素：Legado 语法走选择器链，标准 CSS 直接 select */
    public static Elements select(Element root, String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return new Elements();
        }
        if (matches(rule)) {
            return selectChain(root, rule);
        }
        try {
            return root.select(rule.trim());
        } catch (Exception e) {
            return new Elements();
        }
    }

    /** 选择器链求值，返回命中的元素列表 */
    public static Elements selectChain(Element root, String chain) {
        List<Step> steps = parseChain(chain);
        Elements cur = new Elements();
        cur.add(root);
        for (Step s : steps) {
            Elements next = new Elements();
            for (Element e : cur) {
                Elements found;
                try {
                    found = e.select(s.css);
                } catch (Exception ex) {
                    found = new Elements();
                }
                next.addAll(applyIndex(found, s.index));
            }
            cur = next;
            if (cur.isEmpty()) {
                break;
            }
        }
        return cur;
    }

    private static List<Step> parseChain(String chain) {
        List<Step> steps = new ArrayList<Step>();
        for (String seg : chain.split("@")) {
            String s = seg.trim();
            if (!s.isEmpty()) {
                steps.add(parseStep(s));
            }
        }
        return steps;
    }

    private static Step parseStep(String seg) {
        int index = NO_INDEX;
        Matcher m = TAIL_INDEX.matcher(seg);
        if (m.find()) {
            index = Integer.parseInt(m.group(1));
            seg = seg.substring(0, m.start());
        }
        String css;
        if (seg.startsWith("class.")) {
            css = "." + seg.substring(6);
        } else if (seg.startsWith("tag.")) {
            css = seg.substring(4);
        } else if (seg.startsWith("id.")) {
            css = "#" + seg.substring(3);
        } else if (seg.startsWith("text.")) {
            css = "*:contains(" + seg.substring(5) + ")";
        } else {
            css = seg; // 标准 CSS 透传
        }
        return new Step(css, index);
    }

    private static Elements applyIndex(Elements els, int index) {
        if (index == NO_INDEX || els.isEmpty()) {
            return els;
        }
        Elements r = new Elements();
        int i = index >= 0 ? index : els.size() + index;
        if (i >= 0 && i < els.size()) {
            r.add(els.get(i));
        }
        return r;
    }

    private static boolean isValueToken(String tail) {
        if (tail.isEmpty()) {
            return false;
        }
        if (tail.endsWith(":url")) {
            return true;
        }
        for (String t : VALUE_TOKENS) {
            if (t.equals(tail)) {
                return true;
            }
        }
        return false;
    }

    private static String readValue(Element el, String attr) {
        if (attr == null || attr.isEmpty() || "text".equals(attr)) {
            return clean(el.text());
        }
        if ("textNodes".equals(attr)) {
            return clean(el.wholeText());
        }
        if ("ownText".equals(attr) || "own".equals(attr)) {
            return clean(el.ownText());
        }
        if ("html".equals(attr) || "all".equals(attr)) {
            return el.html();
        }
        if ("outerHtml".equals(attr)) {
            return el.outerHtml();
        }
        if (attr.endsWith(":url")) {
            return attrUrl(el, attr.substring(0, attr.length() - 4));
        }
        return absolute(el, attr);
    }

    /** 从属性值里抠链接，如 onclick="newWebView('/b/236324.html', '', '')" */
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

    // ---------- XPath（常见模式转等价 CSS） ----------

    private static String evalXPath(Element root, String rule) {
        String xp = rule.startsWith("@XPath:") ? rule.substring(7) : rule;
        xp = xp.trim();

        String attr = null;
        Matcher am = X_TAIL_ATTR.matcher(xp);
        if (am.find()) {
            attr = am.group(1);
            xp = xp.substring(0, am.start());
        }
        boolean wantText = false;
        if (xp.endsWith("/text()")) {
            wantText = true;
            xp = xp.substring(0, xp.length() - 7);
        }

        String css = xpathToCss(xp);
        if (css == null) {
            return "";
        }
        Elements els;
        try {
            els = root.select(css);
        } catch (Exception e) {
            return "";
        }
        if (els.isEmpty()) {
            return "";
        }
        if (wantText) {
            return clean(els.first().text());
        }
        return readValue(els.first(), attr);
    }

    /**
     * 把常见 XPath 转成等价 CSS 选择器；不支持的复杂表达式返回 null。
     * 覆盖订阅里实际用到的写法，如：
     *   //meta[@property='og:image']/@content
     *   //div[@id="lbks"]/dl/dt[last()]/following-sibling::dd/a
     */
    private static String xpathToCss(String xp) {
        if (xp.contains("following-sibling") || xp.contains("preceding-sibling")
                || xp.contains("ancestor") || xp.contains("contains(")
                || xp.contains("position()") || xp.contains("|")) {
            return null; // 这些暂不支持
        }
        String s = xp;
        // 去掉开头的 / 或 //
        if (s.startsWith("//")) {
            s = s.substring(2);
        } else if (s.startsWith("/")) {
            s = s.substring(1);
        }
        // // 表示后代
        s = s.replace("//", " ");
        // 按 / 分层 -> CSS 后代选择器
        String[] parts = s.split("/");
        StringBuilder css = new StringBuilder();
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            // [last()] -> :last-child
            p = p.replace("[last()]", ":last-child");
            // [@attr='v'] -> [attr=v]
            Matcher am = X_ATTR.matcher(p);
            if (am.find()) {
                p = am.replaceAll("[$1=$2]");
            }
            // [N] -> :eq(N-1)
            Matcher im = X_INDEX.matcher(p);
            if (im.find()) {
                int n = Integer.parseInt(im.group(1));
                p = im.replaceAll("");
                p = p + ":eq(" + (n - 1) + ")";
            }
            if (p.isEmpty()) {
                continue;
            }
            if (css.length() > 0) {
                css.append(' ');
            }
            css.append(p);
        }
        return css.length() == 0 ? null : css.toString();
    }

    /** 从 <js> ... </js> 里取出脚本正文 */
    private static String extractJsBlock(String s) {
        int start = s.indexOf("<js>");
        int end = s.indexOf("</js>");
        if (start < 0) {
            return s;
        }
        if (end < 0) {
            return s.substring(start + 4);
        }
        return s.substring(start + 4, end);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u00A0', ' ').trim();
    }

    /** 选择器链的一节 */
    private static class Step {
        final String css;
        final int index;

        Step(String css, int index) {
            this.css = css;
            this.index = index;
        }
    }

    /**
     * JS 规则执行上下文：把「当前文档 / 当前页面地址 / 书源地址」交给脚本，
     * 供 java.ajax、java.getString 这类 API 使用。
     */
    public static class JsContext {
        public final String baseUrl;     // 书源 baseUrl
        public final String pageUrl;     // 当前页面地址（java.ajax 补全相对地址用）
        public final Element docRoot;    // 当前文档根（java.getString 用）

        public JsContext(String baseUrl, String pageUrl, Element docRoot) {
            this.baseUrl = baseUrl;
            this.pageUrl = pageUrl;
            this.docRoot = docRoot;
        }
    }
}
