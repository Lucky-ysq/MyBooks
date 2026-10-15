package com.yuxia.books.common;

/**
 * 内置书源规则模板。
 * 「笔趣阁通用模板」：大量笔趣阁镜像站结构相同，添加书源时只需填名称+网址，
 * 规则会用这份模板填充；个别站点不同的话，在高级模式里微调即可。
 */
public class SourceTemplates {

    public static final String MOCK_RULES = "{\n" +
            "  \"charset\": \"utf-8\",\n" +
            "  \"search\":   { \"url\": \"/search\", \"method\": \"GET\", \"list\": \".book\",\n" +
            "                 \"name\": \".name\", \"bookUrl\": \".name@href\", \"author\": \".author\", \"intro\": \".intro\" },\n" +
            "  \"detail\":   { \"name\": \"h1\", \"author\": \".author\", \"intro\": \".intro\",\n" +
            "                 \"cover\": \".cover img@src\", \"latestChapter\": \".latest a\" },\n" +
            "  \"toc\":      { \"list\": \".chapter-list a\" },\n" +
            "  \"content\":  { \"selector\": \".content\", \"remove\": \".ad\" }\n" +
            "}";

    public static final String BIQUGE_RULES = "{\n" +
            "  \"charset\": \"utf-8\",\n" +
            "  \"search\": {\n" +
            "    \"url\": \"/search.php\",\n" +
            "    \"method\": \"POST\",\n" +
            "    \"body\": \"searchkey={{key}}\",\n" +
            "    \"list\": \".result-list .result-item, .novelslist2 li\",\n" +
            "    \"name\": \"a\",\n" +
            "    \"bookUrl\": \"a@href\",\n" +
            "    \"author\": \".book-info p:eq(0), .author\",\n" +
            "    \"intro\": \".book-info p.intro, .intro\"\n" +
            "  },\n" +
            "  \"detail\": {\n" +
            "    \"name\": \"#info h1\",\n" +
            "    \"author\": \"#info p:eq(0)\",\n" +
            "    \"intro\": \"#intro\",\n" +
            "    \"cover\": \"#fmimg img@src\",\n" +
            "    \"latestChapter\": \"#info p:eq(3) a\"\n" +
            "  },\n" +
            "  \"toc\": {\n" +
            "    \"list\": \"#list dd a\",\n" +
            "    \"title\": \"text\",\n" +
            "    \"chapterUrl\": \"href\"\n" +
            "  },\n" +
            "  \"content\": {\n" +
            "    \"selector\": \"#content\",\n" +
            "    \"remove\": \"script, style, .ad, .contentadv\"\n" +
            "  }\n" +
            "}";

    /**
     * bqg70.com 实测适配规则（开发期真实联调用）：
     * - 搜索 POST /search.html，参数 s
     * - 详情字段走 og: meta，最干净
     * - 完整目录在 /book/{id}/index_N.html 分页（每页100章）
     * - 正文在 #content 的 script 里用 base64 加密，且章内分页 _N.html
     */
    public static final String BQG70_RULES = "{\n" +
            "  \"charset\": \"utf-8\",\n" +
            "  \"search\": {\n" +
            "    \"url\": \"/search.html\",\n" +
            "    \"method\": \"POST\",\n" +
            "    \"body\": \"s={{key}}\",\n" +
            "    \"list\": \".item\",\n" +
            "    \"name\": \"dt a\",\n" +
            "    \"bookUrl\": \"dt a@href\",\n" +
            "    \"author\": \".btm@own\",\n" +
            "    \"intro\": \"dd\",\n" +
            "    \"cover\": \".image img@data-original\"\n" +
            "  },\n" +
            "  \"detail\": {\n" +
            "    \"name\": \"meta[property=og:novel:book_name]@content\",\n" +
            "    \"author\": \"meta[property=og:novel:author]@content\",\n" +
            "    \"cover\": \"meta[property=og:image]@content\",\n" +
            "    \"intro\": \"#intro\",\n" +
            "    \"latestChapter\": \"meta[property=og:novel:latest_chapter_name]@content\",\n" +
            "    \"updateTime\": \"meta[property=og:novel:update_time]@content\"\n" +
            "  },\n" +
            "  \"toc\": {\n" +
            "    \"url\": \"index_1.html\",\n" +
            "    \"list\": \"#list a[rel=chapter]\",\n" +
            "    \"nextPage\": \"#list a.index-container-btn\"\n" +
            "  },\n" +
            "  \"content\": {\n" +
            "    \"selector\": \"#content\",\n" +
            "    \"scriptDecode\": true,\n" +
            "    \"nextPage\": \"a.next\"\n" +
            "  }\n" +
            "}";
}
