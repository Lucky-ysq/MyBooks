package com.yuxia.books.config;

import com.yuxia.books.service.SourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后初始化：书源表为空时插入内置书源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInit implements ApplicationRunner {

    private final SourceService sourceService;

    @Override
    public void run(ApplicationArguments args) {
        sourceService.initDefaultSources();
    }
}
