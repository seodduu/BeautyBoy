package com.beautyboy;

import com.beautyboy.config.TossProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TossProperties.class)
public class BeautyboyApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeautyboyApplication.class, args);
    }
}
