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

        BookSource bqg = new BookSource();
        bqg.setName("笔趣阁·真实源(测试)");
        bqg.setBaseUrl("https://www.bqg70.com");
        bqg.setEnabled(true);
        bqg.setIsDefault(false);
        bqg.setRemark("开发期实测联调用的真实站点；站点失效可复制改其它镜像站");
        bqg.setRules(SourceTemplates.BQG70_RULES);
        save(bqg);

        BookSource yueyou = new BookSource();
        yueyou.setName("阅友小说");
        yueyou.setBaseUrl("http://m.suixkan.com");
        yueyou.setEnabled(true);
        yueyou.setIsDefault(false);
        yueyou.setRemark("移动站，国内可直连；规则取自 xiu2/yuedu 书源订阅并重写");
        yueyou.setRules(SourceTemplates.YUEYOU_RULES);
        save(yueyou);

        log.info("已初始化内置书源 3 条");
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

    /**
     * 校验并规范化书源：
     * 1. 名称、网址非空；mock:// 内置源特例放行；
     * 2. baseUrl 规范化（trim、去误粘的查询串/锚点、去末尾"/"、缺协议时自动补 https://）；
     * 3. 同名或同 baseUrl 已存在时报明确异常，引导用户去编辑而不是重复添加。
     */
    private void checkValid(BookSource s) {
        if (s.getName() == null || s.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("书源名称不能为空");
        }
        if (s.getBaseUrl() == null || s.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("书源网址不能为空");
        }
        s.setName(s.getName().trim());
        s.setBaseUrl(normalizeBaseUrl(s.getBaseUrl().trim()));
        if (s.getEnabled() == null) s.setEnabled(true);
        if (s.getIsDefault() == null) s.setIsDefault(false);

        // 重复检测：同名或同网址都视为"已添加"，避免用户误加出重复源
        Long selfId = s.getId();
        List<BookSource> all = mapper.selectList(null);
        for (BookSource exist : all) {
            if (exist.getId().equals(selfId)) {
                continue; // 编辑自己时跳过自身
            }
            if (exist.getName().equals(s.getName())) {
                throw new IllegalArgumentException("已存在同名书源（id=" + exist.getId() + "），请直接编辑它");
            }
            if (exist.getBaseUrl().equalsIgnoreCase(s.getBaseUrl())) {
                throw new IllegalArgumentException("该网址已添加为书源「" + exist.getName() + "」");
            }
        }
    }

    /**
     * 规范化书源网址。用户从浏览器粘贴时常带上查询串/锚点或漏掉协议：
     * 去掉误粘的 "?xxx"/"#xxx"、去掉末尾多余 "/"；不以 http 开头但含 "."
     * （看起来像域名）时自动补 "https://"，不再报错——降低添加门槛。
     */
    private String normalizeBaseUrl(String url) {
        String u = url.trim();
        if (MOCK_BASE_URL.equals(u)) {
            return u; // 内置测试源不走这套规则
        }
        // 书源地址只应保留"站点根"，误粘的查询串/锚点会干扰 {{key}} 拼接
        int q = u.indexOf('?');
        if (q > 0) {
            u = u.substring(0, q);
        }
        int h = u.indexOf('#');
        if (h > 0) {
            u = u.substring(0, h);
        }
        // 去末尾斜杠，但不能把 "https://" 的协议头也吃掉
        while (u.endsWith("/") && !u.endsWith("://")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.startsWith("http")) {
            if (u.contains(".")) {
                u = "https://" + u;
            } else {
                throw new IllegalArgumentException("书源网址格式不对，请检查（不带 http 也可以，如 www.xxx.com）");
            }
        }
        return u;
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
