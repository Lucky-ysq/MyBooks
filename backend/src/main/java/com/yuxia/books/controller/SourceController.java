package com.yuxia.books.controller;

import com.yuxia.books.common.Result;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.service.BookService;
import com.yuxia.books.service.SourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 书源管理接口
 */
@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;
    private final BookService bookService;

    /** 书源列表 */
    @GetMapping
    public Result list() {
        List<BookSource> list = sourceService.list();
        return Result.ok(list);
    }

    /** 新增书源（rules 不传时自动套笔趣阁模板） */
    @PostMapping
    public Result save(@RequestBody BookSource source) {
        return Result.ok(sourceService.save(source));
    }

    /** 修改书源 */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody BookSource source) {
        source.setId(id);
        sourceService.update(source);
        return Result.ok(null);
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
}
