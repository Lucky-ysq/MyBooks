package com.yuxia.books.controller;

import com.yuxia.books.common.Result;
import com.yuxia.books.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图书接口：搜索 / 详情 / 目录 / 正文
 * 路径与 docs/02 §4 的 API 表保持一致
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /** 全站聚合搜索：并发搜所有启用的书源，按相关度排序、去重后返回 */
    @GetMapping("/search/all")
    public Result searchAll(@RequestParam String keyword) {
        return Result.ok(bookService.searchAll(keyword));
    }

    /** 搜索图书 */
    @GetMapping("/search")
    public Result search(@RequestParam Long sourceId, @RequestParam String keyword) {
        return Result.ok(bookService.search(sourceId, keyword));
    }

    /** 书籍详情 */
    @GetMapping("/book/detail")
    public Result detail(@RequestParam Long sourceId, @RequestParam String bookUrl) {
        return Result.ok(bookService.detail(sourceId, bookUrl));
    }

    /** 章节目录 */
    @GetMapping("/book/chapters")
    public Result chapters(@RequestParam Long sourceId, @RequestParam String bookUrl) {
        return Result.ok(bookService.chapters(sourceId, bookUrl));
    }

    /** 章节正文 */
    @GetMapping("/book/content")
    public Result content(@RequestParam Long sourceId, @RequestParam String chapterUrl) {
        return Result.ok(bookService.content(sourceId, chapterUrl));
    }
}
