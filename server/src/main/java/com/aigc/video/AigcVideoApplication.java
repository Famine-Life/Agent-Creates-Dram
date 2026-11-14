package com.aigc.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.aigc.video.mapper")
public class AigcVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AigcVideoApplication.class, args);
    }
}
