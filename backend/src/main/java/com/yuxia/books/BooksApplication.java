package com.yuxia.books;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 遇夏小说后端启动类
 */
@SpringBootApplication
@MapperScan("com.yuxia.books.mapper")
public class BooksApplication {

    public static void main(String[] args) {
        // SQLite 数据文件放在运行目录的 data/ 下，目录不存在则先创建
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            throw new RuntimeException("创建 data 目录失败", e);
        }
        SpringApplication.run(BooksApplication.class, args);
    }
}
