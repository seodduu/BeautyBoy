package com.beautyboy;

import com.beautyboy.config.TossProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(TossProperties.class)
// 조회수 Redis 버퍼의 1분 플러시(ViewCountFlushScheduler)를 돌리기 위해 켠다.
// 스케줄러 빈 자체는 beautyboy.view-count.redis=true일 때만 뜨므로, 꺼진 기본값에서는 도는 작업이 없다.
@EnableScheduling
public class BeautyboyApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeautyboyApplication.class, args);
    }
}
