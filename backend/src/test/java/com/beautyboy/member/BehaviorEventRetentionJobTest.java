package com.beautyboy.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BehaviorEventRetentionJobTest {

    @Autowired
    BehaviorEventRetentionJob behaviorEventRetentionJob;

    @Autowired
    BehaviorEventRepository behaviorEventRepository;

    @Value("${beautyboy.behavior-event.retention-cron}")
    String retentionCron;

    @Test
    void 구십일보다_오래된_행만_지운다() {
        LocalDateTime 지금 = LocalDateTime.now();
        저장(지금.minusDays(91));
        저장(지금.minusDays(91));
        저장(지금.minusDays(89));
        저장(지금.minusDays(89));

        behaviorEventRetentionJob.purge();

        List<BehaviorEvent> 남은것 = behaviorEventRepository.findAll();
        assertThat(남은것).hasSize(2);
        assertThat(남은것).allMatch(event -> event.getCreatedAt().isAfter(지금.minusDays(90)));
    }

    @Test
    void 배치크기보다_많아도_전부_지운다() {
        LocalDateTime 지금 = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            저장(지금.minusDays(91));
        }
        BehaviorEventRetentionJob 작은배치_잡 = new BehaviorEventRetentionJob(behaviorEventRepository, 90, 2);

        작은배치_잡.purge();

        assertThat(behaviorEventRepository.findAll()).isEmpty();
    }

    @Test
    void 지울_것이_없으면_아무_일도_없다() {
        behaviorEventRetentionJob.purge();

        assertThat(behaviorEventRepository.findAll()).isEmpty();
    }

    @Test
    void 테스트프로필에서는_스케줄이_돌지_않는다() {
        assertThat(retentionCron).isEqualTo("0 0 4 1 1 *");
    }

    private void 저장(LocalDateTime createdAt) {
        behaviorEventRepository.save(new BehaviorEvent(1L, 217L, BehaviorKind.VIEW, createdAt));
    }
}
