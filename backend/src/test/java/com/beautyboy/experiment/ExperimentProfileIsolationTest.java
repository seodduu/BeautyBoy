package com.beautyboy.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실험 코드가 기본 기동에 섞여 들어가지 않는다는 계약(goal DoD 5).
 *
 * <p>실험 엔드포인트는 인증 없이 열려 있으므로({@link ExperimentSecurityConfig}) 프로필 격리가
 * 무너지면 그냥 "쓰지 않는 코드"가 아니라 **인증 없는 공개 경로가 배포 구성에 생긴다.**
 * 그래서 이 테스트는 편의가 아니라 보안 회귀 가드다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExperimentProfileIsolationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void experiment_프로필이_아니면_실험_빈이_생기지_않는다() {
        assertThat(context.getBeanNamesForType(ExperimentAffinityController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AffinityChainService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ExperimentSecurityConfig.class)).isEmpty();
    }

    @Test
    void 실험_코드가_있어도_기본_컨텍스트는_정상_기동한다() {
        // 위 테스트가 통과하는 가장 쉬운 방법은 "컨텍스트가 안 뜨는 것"이다. 그것을 배제한다.
        assertThat(context.getBeanNamesForType(com.beautyboy.routine.FlowRuleService.class))
                .isNotEmpty();
    }
}
