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
}
