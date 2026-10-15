package com.yuxia.books.entity;

import lombok.Data;

/**
 * 书源解析规则（rules 字段的 JSON 结构）。
 *
 * 选择器写法（保持简单，全部基于 CSS 选择器）：
 *   "a"          -> 元素 a 的文本
 *   "a@href"     -> 元素 a 的 href 属性（相对地址后端自动补全成完整 URL）
 *   "href"       -> 列表元素自身的 href 属性（不写选择器、直接写属性名）
 *   "text"       -> 元素自身的文本
 * 搜索关键字占位符：{{key}}
 */
@Data
public class SourceRules {

    private String charset = "utf-8"; // 站点编码，utf-8 / gbk

    private SearchRule search;
    private DetailRule detail;
    private TocRule toc;
    private ContentRule content;

    @Data
    public static class SearchRule {
        private String url;     // 搜索地址（可相对 baseUrl），可含 {{key}}
        private String method;  // GET 或 POST，默认 GET
        private String body;    // POST 时的表单，如 searchkey={{key}}
        private String list;    // 每条搜索结果的选择器
        private String name;    // 书名
        private String bookUrl; // 详情页链接
        private String author;  // 作者
        private String cover;   // 封面图
        private String intro;   // 简介
    }

    @Data
    public static class DetailRule {
        private String name;
        private String author;
        private String cover;
        private String intro;
        private String latestChapter; // 最新章节
        private String updateTime;    // 更新时间
    }

    @Data
    public static class TocRule {
        private String list;       // 每个章节链接的选择器
        private String title;      // 章节名（默认取元素自身文本）
        private String chapterUrl; // 章节链接（默认取元素自身 href）
        private String url;        // 目录页地址（可选）。有的站完整目录不在详情页，
                                   // 如 /book/1/index_1.html，填相对地址会基于 bookUrl 补全
        private String nextPage;   // 目录翻页链接选择器（可选，目录分页时用）
    }

    @Data
    public static class ContentRule {
        private String selector;     // 正文容器选择器
        private String remove;       // 要剔除的元素（广告等），逗号分隔
        private Boolean scriptDecode; // 正文是 script 里的 base64 时填 true（自动解码拼接）
        private String nextPage;     // 章内分页「下一页」选择器（可选；指向别的章节会自动停止）
    }
}
