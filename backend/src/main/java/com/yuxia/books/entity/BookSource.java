package com.yuxia.books.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 书源实体，对应 book_source 表。
 * rules 字段存解析规则 JSON（结构见 SourceRules 类）。
 */
@Data
@TableName("book_source")
public class BookSource {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;       // 书源名称
    private String baseUrl;    // 站点地址，如 https://www.xx.com；内置测试源固定为 mock://local
    private Boolean enabled;   // 是否启用
    private Boolean isDefault; // 是否默认源（搜索页默认选中）
    private String remark;     // 备注
    private String rules;      // 解析规则 JSON 字符串

    private String createTime;
    private String updateTime;
}
