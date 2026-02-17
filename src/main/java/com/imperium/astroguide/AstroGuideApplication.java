package com.imperium.astroguide;

import com.imperium.astroguide.config.DotenvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.imperium.astroguide.mapper")
public class AstroGuideApplication {

    public static void main(String[] args) {
        DotenvLoader.load(); // 加载 .env 到系统属性，供 application.yaml 中的 ${VAR} 使用
        SpringApplication.run(AstroGuideApplication.class, args);
    }
}
