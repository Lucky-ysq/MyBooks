package com.yuxia.books.controller;

import com.yuxia.books.common.Result;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.service.BookService;
import com.yuxia.books.service.LegadoSourceImporter;
import com.yuxia.books.service.SourceService;
import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书源管理接口
 */
@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

    /** 保存后自动测试用的固定关键字：「斗破」是小说站最常见的样本词 */
    private static final String AUTO_TEST_KEYWORD = "斗破";

    private final SourceService sourceService;
    private final BookService bookService;
    private final LegadoSourceImporter legadoSourceImporter;

    /** 书源列表 */
    @GetMapping
    public Result list() {
        List<BookSource> list = sourceService.list();
        return Result.ok(list);
    }

    /**
     * 新增书源：**后台自动判断书源类型**，用户不用先选对入口。
     * - 网址返回的是书源订阅文件（JSON 数组含 bookSourceUrl）→ 走订阅解析，批量导入
     * - 其余（小说网站，如 .com 站点）→ 走规则解析，套模板保存并自动试搜
     *
     * @return type=subscription 时返回导入统计；type=website 时返回 {source, test}
     */
    @PostMapping
    public Result save(@RequestBody BookSource source) {
        String rawUrl = source.getBaseUrl() == null ? "" : source.getBaseUrl().trim();

        // 先取一次内容做类型识别（30s 超时，订阅文件可能几百 KB）
        String body = legadoSourceImporter.fetchRawBody(rawUrl);
        if (legadoSourceImporter.looksLikeSubscription(body)) {
            Map<String, Object> stats = legadoSourceImporter.importFromJson(body);
            stats.put("type", "subscription");
            return Result.ok(stats);
        }
        // 地址形态像订阅但没取到内容：明确报错，别存成一个用不了的「网站源」
        if (body == null && legadoSourceImporter.looksLikeSubscriptionUrl(rawUrl)) {
            throw new IllegalArgumentException("这个地址看起来是书源订阅，但下载失败，请检查网络后重试");
        }

        // 网站源：名称留空时用域名兜底，省得用户被「名称必填」卡住
        if (source.getName() == null || source.getName().trim().isEmpty()) {
            source.setName(deriveName(source.getBaseUrl()));
        }
        BookSource saved = sourceService.save(source);
        Map<String, Object> out = withAutoTest(saved, saved.getId());
        out.put("type", "website");
        return Result.ok(out);
    }

    /** 从网址里取个可读的书源名：www.example.com/book → example.com */
    private String deriveName(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        int idx = u.indexOf("://");
        if (idx >= 0) {
            u = u.substring(idx + 3);
        }
        int slash = u.indexOf('/');
        if (slash >= 0) {
            u = u.substring(0, slash);
        }
        if (u.startsWith("www.")) {
            u = u.substring(4);
        }
        return u.isEmpty() ? "未命名书源" : u;
    }

    /** 修改书源 */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody BookSource source) {
        source.setId(id);
        sourceService.update(source);
        BookSource updated = sourceService.getById(id);
        return Result.ok(withAutoTest(updated, id));
    }

    /** 删除书源（至少保留一个） */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        sourceService.delete(id);
        return Result.ok(null);
    }

    /** 设为默认源 */
    @PostMapping("/{id}/default")
    public Result setDefault(@PathVariable Long id) {
        sourceService.setDefault(id);
        return Result.ok(null);
    }

    /** 测试书源：用关键字搜一次，能返回结果说明书源可用 */
    @GetMapping("/{id}/test")
    public Result test(@PathVariable Long id, @RequestParam(defaultValue = "斗破") String keyword) {
        return Result.ok(bookService.search(id, keyword));
    }

    /**
     * 导入 Legado（「阅读」App）书源订阅。
     * 传订阅地址（返回书源 JSON 数组），按 baseUrl 去重后批量入库。
     */
    @PostMapping("/import")
    public Result importSubscription(@RequestParam String url) {
        return Result.ok(legadoSourceImporter.importFromUrl(url));
    }

    // ---------- 保存后自动测试 ----------

    /** 组装保存接口返回：{ source: 书源对象, test: { ok, resultCount, message } } */
    private Map<String, Object> withAutoTest(BookSource saved, Long id) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("source", saved);
        out.put("test", autoTest(id));
        return out;
    }

    /**
     * 保存后自动跑一次搜索，给出人话诊断。
     * 新源套默认模板搜不到是正常的（网站不适配笔趣阁结构），不阻断保存，只提示。
     */
    private Map<String, Object> autoTest(Long id) {
        Map<String, Object> test = new LinkedHashMap<String, Object>();
        test.put("ok", Boolean.FALSE);
        test.put("resultCount", Integer.valueOf(0));
        test.put("message", "");
        try {
            List<Map<String, Object>> list = bookService.search(id, AUTO_TEST_KEYWORD);
            int count = list == null ? 0 : list.size();
            test.put("ok", Boolean.valueOf(count > 0));
            test.put("resultCount", Integer.valueOf(count));
            if (count > 0) {
                test.put("message", "测试通过，搜到 " + count + " 条结果");
            } else {
                test.put("message", "能访问但没搜到结果：默认规则适配的是笔趣阁结构，"
                        + "该站可能不适配，请用高级模式修改规则，或用「从订阅批量导入」");
            }
        } catch (Exception e) {
            // 保存已成功，测试失败只反馈原因，不阻断
            test.put("message", friendlyTestError(e));
        }
        return test;
    }

    /**
     * 把测试异常翻译成人话。
     * 判定依据是异常类型/HTTP 状态码，而不是 BookService.search() 重包后的文案
     * （重包文案统一带"访问不了"字样，靠它匹配会把 405 误判成"网站连不上"）。
     * BookService 重包异常时不保留 cause 链，所以先走类型链，再从文案中
     * 结构化提取 "Status=xxx" 作为兜底。
     */
    private String friendlyTestError(Exception e) {
        // 1) 沿 cause 链找具体异常类型
        Throwable t = e;
        while (t != null) {
            if (t instanceof HttpStatusException) {
                return rejectMessage(((HttpStatusException) t).getStatusCode());
            }
            if (t instanceof UnsupportedMimeTypeException) {
                return "返回的不是网页（可能是接口型书源，需要高级规则）";
            }
            if (t instanceof SocketTimeoutException || t instanceof ConnectException
                    || t instanceof UnknownHostException) {
                return "网站连不上（可能失效或需要梯子）";
            }
            t = t.getCause();
        }
        // 2) cause 链被重包丢失时，从文案里结构化识别（Jsoup 的
        //    HttpStatusException.getMessage() 形如 "HTTP error fetching URL. Status=405, URL=[...]"）
        String msg = e.getMessage() == null ? "" : e.getMessage();
        Matcher m = Pattern.compile("Status=(\\d{3})").matcher(msg);
        if (m.find()) {
            return rejectMessage(Integer.parseInt(m.group(1)));
        }
        String lower = msg.toLowerCase();
        if (lower.contains("unhandled content type") || lower.contains("unsupported mime")) {
            return "返回的不是网页（可能是接口型书源，需要高级规则）";
        }
        if (lower.contains("timed out") || lower.contains("unable to resolve")
                || lower.contains("connection refused") || lower.contains("connection reset")
                || lower.contains("connect")) {
            return "网站连不上（可能失效或需要梯子）";
        }
        return "测试失败：" + msg;
    }

    /** 站点的 HTTP 响应是错误状态码：4xx/5xx 都算"拒绝"，带上具体码方便排查 */
    private String rejectMessage(int statusCode) {
        return "网站拒绝了请求（HTTP " + statusCode
                + "，被反爬或路径不对），请用高级模式修改规则";
    }
}
