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

    /**
     * 阅友小说（m.suixkan.com）实测适配规则。
     * 来源：xiu2/yuedu 的 Legado 书源订阅里同名书源，按本项目规则格式重写（该订阅里唯一
     * 纯 CSS、无 JS、无 XPath、国内可直连的源）。
     *
     * 三个特殊点，也是本项目规则引擎为此新增的能力：
     * 1) 搜索结果的书链接藏在 onclick 里（没有 a[href]）-> 用 "@onclick:url" 抠出来
     * 2) 详情页只给前 3 章预览，完整目录在 /c/{id}.html -> 用 toc.urlRule 从「查看目录」链接跳过去
     * 3) 章内分页（1/5~5/5）全塞在同一个 HTML 的多个 .con 里 -> 靠后端「多容器拼接」还原完整章节
     */
    public static final String YUEYOU_RULES = "{\n" +
            "  \"charset\": \"utf-8\",\n" +
            "  \"search\": {\n" +
            "    \"url\": \"/s/1.html?keyword={{key}}\",\n" +
            "    \"method\": \"GET\",\n" +
            "    \"list\": \".v-list-item\",\n" +
            "    \"name\": \".v-title\",\n" +
            "    \"bookUrl\": \"@onclick:url\",\n" +
            "    \"author\": \".v-author\",\n" +
            "    \"cover\": \"img@src\",\n" +
            "    \"intro\": \".v-intro\"\n" +
            "  },\n" +
            "  \"detail\": {\n" +
            "    \"name\": \".face-info-title\",\n" +
            "    \"author\": \".face-info .v-words span:eq(0)##作者：\",\n" +
            "    \"cover\": \".face-cover img@src\",\n" +
            "    \"intro\": \"#intro\"\n" +
            "  },\n" +
            "  \"toc\": {\n" +
            "    \"urlRule\": \".sumchapter a\",\n" +
            "    \"list\": \".catalog_ls li a\",\n" +
            "    \"title\": \"@own\",\n" +
            "    \"chapterUrl\": \"href\"\n" +
            "  },\n" +
            "  \"content\": {\n" +
            "    \"selector\": \".con\",\n" +
            "    \"remove\": \"script, style\"\n" +
            "  }\n" +
            "}";
}
