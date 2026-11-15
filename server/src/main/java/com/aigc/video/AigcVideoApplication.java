package com.aigc.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 封装项目中的业务数据与功能。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.aigc.video.mapper")
public class AigcVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AigcVideoApplication.class, args);
    }
}
