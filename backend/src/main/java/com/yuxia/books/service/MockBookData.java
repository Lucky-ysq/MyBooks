package com.yuxia.books.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置测试源的假数据：3 本书、每本 30 章、正文为自动生成的占位文字。
 * 用途：无外网/书源不可用时也能全流程调试（搜索→加书架→目录→阅读→下载）。
 */
public class MockBookData {

    /** 书的信息（name -> 作者/简介） */
    private static final List<String[]> BOOKS = Arrays.asList(
            new String[]{"测试修仙传", "测试作者甲", "一个少年在山中捡到一枚玉佩，从此踏上修行之路。"},
            new String[]{"测试都市录", "测试作者乙", "平凡上班族意外获得神奇能力，都市生活从此不同。"},
            new String[]{"测试星海记", "测试作者丙", "人类舰队驶出太阳系的那一年，少年在废弃空间站醒来。"}
    );

    private static final int CHAPTER_COUNT = 30;

    private MockBookData() {
    }

    public static List<Map<String, Object>> search(String keyword) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < BOOKS.size(); i++) {
            String[] b = BOOKS.get(i);
            if (keyword == null || keyword.trim().isEmpty()
                    || b[0].contains(keyword) || b[1].contains(keyword)) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", b[0]);
                m.put("author", b[1]);
                m.put("intro", b[2]);
                m.put("bookUrl", bookUrl(i));
                m.put("coverUrl", "");
                result.add(m);
            }
        }
        return result;
    }

    public static Map<String, Object> detail(String bookUrl) {
        int idx = parseIndex(bookUrl);
        String[] b = BOOKS.get(idx);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", b[0]);
        m.put("author", b[1]);
        m.put("intro", b[2] + "（本地测试源数据，仅供联调使用）");
        m.put("coverUrl", "");
        m.put("latestChapter", chapterTitle(CHAPTER_COUNT - 1));
        m.put("updateTime", "2026-10-13");
        return m;
    }

    public static List<Map<String, Object>> chapters(String bookUrl) {
        int idx = parseIndex(bookUrl);
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (int c = 0; c < CHAPTER_COUNT; c++) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("title", chapterTitle(c));
            m.put("chapterUrl", chapterUrl(idx, c));
            list.add(m);
        }
        return list;
    }

    public static Map<String, Object> content(String chapterUrl) {
        String[] p = chapterUrl.replace("mock://book/", "").split("/");
        int idx = Integer.parseInt(p[0]);
        int c = Integer.parseInt(p[1]);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", chapterTitle(c));
        List<String> paras = new ArrayList<String>();
        for (int i = 1; i <= 16; i++) {
            paras.add("第" + c + "章的第" + i
                    + "段。少年握紧了手中的信物，远处山风掠过林梢，带来一阵漫长的回响。"
                    + "他数着自己的呼吸，一步一步向山门走去，心里既有忐忑，也有说不清的期待。"
                    + "（这是本地测试源自动生成的占位正文，用于验证阅读、翻页、下载等全部功能。）");
        }
        m.put("paragraphs", paras);
        m.put("prevUrl", c > 0 ? chapterUrl(idx, c - 1) : null);
        m.put("nextUrl", c < CHAPTER_COUNT - 1 ? chapterUrl(idx, c + 1) : null);
        return m;
    }

    // ---------- 私有工具 ----------

    private static String bookUrl(int idx) {
        return "mock://book/" + idx;
    }

    private static String chapterUrl(int idx, int c) {
        return "mock://book/" + idx + "/" + c;
    }

    private static int parseIndex(String bookUrl) {
        // "mock://book/0" -> 0，越界兜底为 0
        try {
            int idx = Integer.parseInt(bookUrl.replace("mock://book/", "").split("/")[0]);
            return (idx >= 0 && idx < BOOKS.size()) ? idx : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String chapterTitle(int c) {
        return "第" + (c + 1) + "章 " + (c % 2 == 0 ? "初入山门" : "夜雨独行");
    }
}
