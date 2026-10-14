package com.yuxia.books.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuxia.books.common.SourceTemplates;
import com.yuxia.books.entity.BookSource;
import com.yuxia.books.mapper.BookSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 书源管理：增删改查 + 启动时初始化内置书源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {

    public static final String MOCK_BASE_URL = "mock://local";

    private final BookSourceMapper mapper;

    /** 启动时调用：表为空则插入内置书源（本地测试源 + 笔趣阁模板示例） */
    public void initDefaultSources() {
        Long count = mapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        BookSource mock = new BookSource();
        mock.setName("本地测试源");
        mock.setBaseUrl(MOCK_BASE_URL);
        mock.setEnabled(true);
        mock.setIsDefault(true);
        mock.setRemark("内置假数据源，无需联网，用于开发调试和兜底");
        mock.setRules(SourceTemplates.MOCK_RULES);
        save(mock);

        BookSource biquge = new BookSource();
        biquge.setName("笔趣阁模板示例");
        biquge.setBaseUrl("https://www.xbiquge.so");
        biquge.setEnabled(false);
        biquge.setIsDefault(false);
        biquge.setRemark("笔趣阁通用规则模板，可复制改成其它镜像站");
        biquge.setRules(SourceTemplates.BIQUGE_RULES);
        save(biquge);

        log.info("已初始化内置书源 2 条");
    }

    public List<BookSource> list() {
        return mapper.selectList(new QueryWrapper<BookSource>().orderByDesc("is_default").orderByAsc("id"));
    }

    public BookSource getById(Long id) {
        BookSource s = mapper.selectById(id);
        if (s == null) {
            throw new IllegalArgumentException("书源不存在：id=" + id);
        }
        return s;
    }

    public BookSource save(BookSource s) {
        checkValid(s);
        // 不填规则时默认套笔趣阁模板（简单模式）
        if (s.getRules() == null || s.getRules().trim().isEmpty()) {
            s.setRules(SourceTemplates.BIQUGE_RULES);
        }
        s.setCreateTime(now());
        s.setUpdateTime(now());
        mapper.insert(s);
        return s;
    }

    public void update(BookSource s) {
        BookSource old = getById(s.getId());
        checkValid(s);
        if (s.getRules() == null || s.getRules().trim().isEmpty()) {
            s.setRules(old.getRules()); // 没传规则则保留原规则
        }
        s.setCreateTime(old.getCreateTime());
        s.setUpdateTime(now());
        mapper.updateById(s);
    }

    public void delete(Long id) {
        List<BookSource> all = list();
        if (all.size() <= 1) {
            throw new IllegalArgumentException("至少保留一个书源，不能删除");
        }
        mapper.deleteById(id);
    }

    /** 设为默认源（其他源取消默认） */
    public void setDefault(Long id) {
        for (BookSource s : list()) {
            if (s.getId().equals(id)) {
                s.setIsDefault(true);
                s.setEnabled(true); // 默认源必须启用
            } else {
                s.setIsDefault(false);
            }
            s.setUpdateTime(now());
            mapper.updateById(s);
        }
    }

    private void checkValid(BookSource s) {
        if (s.getName() == null || s.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("书源名称不能为空");
        }
        if (s.getBaseUrl() == null || s.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("书源网址不能为空");
        }
        if (!MOCK_BASE_URL.equals(s.getBaseUrl()) && !s.getBaseUrl().startsWith("http")) {
            throw new IllegalArgumentException("书源网址必须以 http 开头");
        }
        if (s.getEnabled() == null) s.setEnabled(true);
        if (s.getIsDefault() == null) s.setIsDefault(false);
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
