package com.yuxia.books.util;

import org.jsoup.Connection;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Legado 书源 JS 规则执行器（基于 Rhino）。
 *
 * Legado 的规则里有大量 {@code <js>...</js>} 脚本块和 {@code @js:} 内联脚本，
 * 用来做正则提取、条件判断、调接口取数据等。Rhino 是纯 Java 的 JS 引擎，
 * Legado 本身就是用它跑的，所以用它兼容性最好。
 *
 * 脚本里可用的 API（对齐 Legado 的 java 对象）：
 *   result                 上一个规则取到的值（@js: 形式的输入）
 *   baseUrl / src          书源地址 / 当前页面地址
 *   java.ajax(url)         GET 请求，返回响应原文
 *   java.get(url)          同 ajax
 *   java.post(url, body)   POST 请求，返回响应原文
 *   java.getString(rule)   在当前页面上按规则取值
 *   java.getElement(rule)  在当前页面上按规则取元素（数组，有 .length）
 *   java.log(msg)          打日志
 *   java.base64Decode(s)   base64 解码
 *
 * 安全：ClassShutter 已禁止脚本直接访问任意 Java 类，只保留注入的 java 对象；
 * 并且限制了执行时长与指令数，防止恶意/写错的脚本把服务线程挂死。
 */
public class JsRuleEngine {

    private static final Logger LOG = LoggerFactory.getLogger(JsRuleEngine.class);

    /** 单条脚本最长执行时间 */
    private static final long TIMEOUT_MS = 8000;

    /** 每执行这么多条指令就检查一次超时 */
    private static final int CHECK_INTERVAL = 100000;

    private static final ThreadLocal<Long> START_AT = new ThreadLocal<Long>();

    /** 请求配置里的 'key':'value' 对，如 'method':'POST' */
    private static final java.util.regex.Pattern CONF_PAIR =
            java.util.regex.Pattern.compile("'([\\w-]+)'\\s*:\\s*'([^']*)'");

    static {
        // 自定义 ContextFactory，用来给 JS 加执行超时保护
        ContextFactory.initGlobal(new ContextFactory() {
            @Override
            protected void observeInstructionCount(Context cx, int instructionCount) {
                Long start = START_AT.get();
                if (start != null && System.currentTimeMillis() - start > TIMEOUT_MS) {
                    throw new Error("JS 规则执行超时（超过 " + TIMEOUT_MS + "ms）");
                }
            }
        });
    }

    private JsRuleEngine() {
    }

    /**
     * 执行一段 JS 规则。
     *
     * @param code   脚本正文（已去掉 &lt;js&gt; 标签）
     * @param result 上游规则取到的值，脚本里用 result 访问
     * @param root   当前页面根元素，供 java.getString / java.getElement 使用
     * @param ctx    上下文（书源地址、页面地址）
     * @return 脚本返回值；执行失败时回退返回 result
     */
    public static String run(String code, String result, Element root,
                             LegadoRuleParser.JsContext ctx) {
        String fallback = result == null ? "" : result;
        if (code == null || code.trim().isEmpty()) {
            return fallback;
        }
        START_AT.set(System.currentTimeMillis());
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);          // 解释执行，避免生成字节码
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(CHECK_INTERVAL);
            // 只放行注入的 JS API 对象、Jsoup 元素类型，以及基础类型（String 等，
            // Rhino 包装 Java 字符串时会检查）。其余 Java 类一律禁止，
            // 防止不可信的书源脚本去读写文件、执行命令。
            cx.setClassShutter(className ->
                    className.startsWith("com.yuxia.books.util.JsRuleEngine$JsApi")
                            || className.startsWith("org.jsoup.")
                            || "java.lang.String".equals(className)
                            || "java.lang.Object".equals(className)
                            || "java.lang.Number".equals(className)
                            || "java.lang.Integer".equals(className)
                            || "java.lang.Long".equals(className)
                            || "java.lang.Double".equals(className)
                            || "java.lang.Boolean".equals(className));

            Scriptable scope = cx.initStandardObjects();
            JsApi api = new JsApi(root, ctx);
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(api, scope));
            ScriptableObject.putProperty(scope, "result", fallback);
            ScriptableObject.putProperty(scope, "baseUrl",
                    ctx == null || ctx.baseUrl == null ? "" : ctx.baseUrl);
            ScriptableObject.putProperty(scope, "src",
                    ctx == null || ctx.pageUrl == null ? "" : ctx.pageUrl);
            ScriptableObject.putProperty(scope, "cookie", "");

            Object out = cx.evaluateString(scope, code, "rule", 1, null);
            if (out == null) {
                return "";
            }
            String s = Context.toString(out);
            return "undefined".equals(s) ? fallback : s;
        } catch (Throwable e) {
            LOG.warn("JS 规则执行失败：{}", e.getMessage());
            return fallback;
        } finally {
            Context.exit();
            START_AT.remove();
        }
    }

    /**
     * 暴露给脚本的 java 对象。方法名对齐 Legado，方便书源原样迁移。
     */
    public static class JsApi {

        private final Element root;
        private final LegadoRuleParser.JsContext ctx;

        public JsApi(Element root, LegadoRuleParser.JsContext ctx) {
            this.root = root;
            this.ctx = ctx;
        }

        /** GET 请求，返回响应原文 */
        public String ajax(String url) {
            return request(url, "GET", null);
        }

        /** 同 ajax（Legado 里两个都在用） */
        public String get(String url) {
            return request(url, "GET", null);
        }

        /** POST 请求，返回响应原文 */
        public String post(String url, String body) {
            return request(url, "POST", body);
        }

        /** 在当前页面上按规则取值 */
        public String getString(String rule) {
            if (root == null || rule == null) {
                return "";
            }
            return LegadoRuleParser.getString(root, rule, ctx);
        }

        /** 在当前页面上按规则取元素，返回数组（脚本里用 .length 判断有没有） */
        public Element[] getElement(String rule) {
            if (root == null || rule == null) {
                return new Element[0];
            }
            Elements els = LegadoRuleParser.selectChain(root, rule);
            return els.toArray(new Element[0]);
        }

        /** 同上，返回文本数组 */
        public String[] getElements(String rule) {
            if (root == null || rule == null) {
                return new String[0];
            }
            Elements els = LegadoRuleParser.selectChain(root, rule);
            String[] out = new String[els.size()];
            for (int i = 0; i < els.size(); i++) {
                out[i] = els.get(i).text();
            }
            return out;
        }

        public void log(String msg) {
            LOG.info("[书源JS] {}", msg);
        }

        /**
         * 人机验证相关接口：Legado 会弹浏览器让用户点一下验证，
         * 服务端没有 UI，这里做成空实现。关键是**方法必须存在**，
         * 否则脚本一调到就 TypeError 中断，后面的 result 就返回不出来了。
         */
        public void toast(String msg) {
        }

        public void longToast(String msg) {
        }

        public void startBrowser(String url) {
        }

        public void startBrowserAwait(String url, String title) {
        }

        public String getCookie(String url) {
            return "";
        }

        public void setCookie(String url, String cookie) {
        }

        public String base64Decode(String s) {
            if (s == null || s.isEmpty()) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(s.trim()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        public String base64Encode(String s) {
            if (s == null) {
                return "";
            }
            return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
        }

        public String htmlFormat(String s) {
            return s == null ? "" : s;
        }

        /** 按正则替换（Legado 也有这个 API） */
        public String sourceRegex(String s, String regex, String replacement) {
            if (s == null) {
                return "";
            }
            try {
                return s.replaceAll(regex, replacement == null ? "" : replacement);
            } catch (Exception e) {
                return s;
            }
        }

        private String request(String url, String method, String body) {
            if (url == null || url.isEmpty()) {
                return "";
            }
            // Legado 的写法：地址后面可以挂配置，如
            //   https://x.com/search.php,{'body':'keyword=xx','method':'POST','charset':'gbk'}
            // 这里要把真实地址和配置拆开，不能把整串当 URL 请求
            String raw = url.replace("<js>", "").replace("</js>", "");
            String path = raw;
            String m = method;
            String b = body;
            int brace = raw.indexOf(",{");
            if (brace >= 0) {
                path = raw.substring(0, brace).trim();
                String conf = raw.substring(brace + 1);
                java.util.regex.Matcher mm = CONF_PAIR.matcher(conf);
                while (mm.find()) {
                    String k = mm.group(1);
                    String v = mm.group(2);
                    if ("method".equalsIgnoreCase(k)) {
                        m = v.toUpperCase();
                    } else if ("body".equals(k)) {
                        b = v;
                    }
                }
            }
            String full = resolve(path);
            try {
                Connection conn = org.jsoup.Jsoup.connect(full)
                        .userAgent(JsoupFetcher.UA)
                        .timeout(20000)
                        .referrer(JsoupFetcher.originOf(full))
                        .ignoreContentType(true);
                if ("POST".equalsIgnoreCase(m) && b != null && !b.isEmpty()) {
                    conn.requestBody(b)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .method(Connection.Method.POST);
                }
                return conn.execute().body();
            } catch (IOException e) {
                LOG.warn("JS 里请求失败 {}：{}", full, e.getMessage());
                return "";
            }
        }

        /** 脚本里给的地址可能是相对路径，按当前页面地址补全 */
        private String resolve(String url) {
            if (url.startsWith("http")) {
                return url;
            }
            String base = (ctx != null && ctx.pageUrl != null && !ctx.pageUrl.isEmpty())
                    ? ctx.pageUrl
                    : (ctx != null ? ctx.baseUrl : null);
            if (base == null || base.isEmpty()) {
                return url;
            }
            try {
                return new java.net.URI(base).resolve(url).toString();
            } catch (Exception e) {
                return url;
            }
        }
    }
}
