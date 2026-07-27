package com.beautyboy.routine;

import com.beautyboy.routine.dto.ConcernRuleView;
import com.beautyboy.routine.dto.FlowRuleView;
import com.beautyboy.routine.dto.FlowRulesResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 규칙 전량 배포(설계 §5.2). 규칙은 시드 전용이고 관리자 CRUD가 없다. 따라서 기동 시 1회 계산해
 * 캐싱하고, 요청마다 DB를 읽지 않는다 — 이 엔드포인트는 전 사용자가 앱 진입마다 부르는 자리라
 * 매 요청 조회는 그 자체가 이 설계가 없애려던 비용이다.
 */
@Service
public class FlowRuleService {

    /** 해시 앞 16자. 64자를 그대로 헤더에 실을 이유가 없고, 16자면 충돌 확률이 실질적으로 0이다. */
    private static final int VERSION_LENGTH = 16;

    private final RoutineFlowRuleRepository flowRuleRepository;
    private final ConcernTargetRuleRepository concernRuleRepository;

    /** 기동 시 한 번 쓰고 이후 읽기만 한다. 쓰기 스레드와 읽기 스레드가 달라 volatile로 가시성을 보장한다. */
    private volatile FlowRulesResponse cached;

    public FlowRuleService(RoutineFlowRuleRepository flowRuleRepository,
                           ConcernTargetRuleRepository concernRuleRepository) {
        this.flowRuleRepository = flowRuleRepository;
        this.concernRuleRepository = concernRuleRepository;
    }

    /**
     * 해시 입력은 두 테이블 전량을 "정렬된 순서로" 직렬화한 문자열이다. 정렬을 빼면 DB가 돌려주는
     * 순서에 따라 같은 데이터가 다른 ETag를 만들어, 내용이 그대로인데 304가 안 나가는 일이 생긴다.
     */
    @PostConstruct
    void loadRules() {
        List<FlowRuleView> flow = flowRuleRepository.findAll().stream()
                .map(FlowRuleView::from)
                .sorted(Comparator.comparing(FlowRuleView::fromCategoryCode)
                        .thenComparing(v -> v.fromTagSlug() == null ? "" : v.fromTagSlug())
                        .thenComparing(FlowRuleView::toCategoryCode))
                .toList();
        List<ConcernRuleView> concern = concernRuleRepository.findAll().stream()
                .map(ConcernRuleView::from)
                .sorted(Comparator.comparing(ConcernRuleView::concernTagSlug)
                        .thenComparing(ConcernRuleView::toCategoryCode))
                .toList();
        // 두 목록 사이에 구분자를 넣는다 — 없으면 경계가 모호해져 서로 다른 데이터가
        // 같은 문자열로 이어붙어 같은 해시를 만들 수 있다.
        String version = sha256Hex(serializeFlow(flow) + "##" + serializeConcern(concern))
                .substring(0, VERSION_LENGTH);
        this.cached = new FlowRulesResponse(version, flow, concern);
    }

    public FlowRulesResponse rules() {
        return cached;
    }

    private static String serializeFlow(List<FlowRuleView> flow) {
        return flow.stream()
                .map(v -> String.join("|", nullSafe(v.fromCategoryCode()), nullSafe(v.fromTagSlug()),
                        nullSafe(v.toCategoryCode()), nullSafe(v.toTagSlug()), nullSafe(v.edgeKind()),
                        nullSafe(v.reason()), String.valueOf(v.priority())))
                .collect(Collectors.joining("\n"));
    }

    private static String serializeConcern(List<ConcernRuleView> concern) {
        return concern.stream()
                .map(v -> String.join("|", nullSafe(v.concernTagSlug()), nullSafe(v.toCategoryCode()),
                        nullSafe(v.toTagSlug()), nullSafe(v.reason()), String.valueOf(v.priority())))
                .collect(Collectors.joining("\n"));
    }

    /** NULL과 빈 문자열을 같은 자리로 접는다. 두 값이 규칙 판정에서 갈리지 않으므로(태그 무관) 문제없다. */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JRE가 제공한다. 여기 오면 런타임이 망가진 것이라 살릴 방법이 없다.
            throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
        }
    }
}
