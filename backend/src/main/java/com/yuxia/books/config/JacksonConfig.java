package com.yuxia.books.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置：把所有 Long 输出成 JSON 字符串。
 *
 * 为什么要这么做：
 * 书源主键由 MyBatis-Plus 雪花算法生成，是 19 位长整型（如 2110518753123545091）。
 * 而 JavaScript 的 Number 安全整数上限只有 2^53-1 = 9007199254740991（16 位），
 * App 端 uni.request 内部 JSON.parse 之后末几位会被抹平（2110518753123545091 → 2110518753123545000），
 * 再把这个"变形的 id"传回后端，就查不到书源，报「书源不存在：id=xxx」。
 *
 * 序列化成字符串后，App 拿到的是原样的 "2110518753123545091"，原样透传回来，
 * 后端 @RequestParam / @PathVariable 声明为 Long 依然能自动转换，前端一行都不用改。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
