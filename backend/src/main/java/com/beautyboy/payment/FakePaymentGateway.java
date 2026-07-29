package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * e2e 프로필 전용 결제 게이트웨이. 요청한 금액을 그대로 승인한 것처럼 답한다.
 *
 * <p>왜 필요한가: Playwright E2E가 토스 결제창을 자동화할 수 없다(결정 5). 대신 토스가 성공 시
 * 보내는 리다이렉트를 재현하고, 그 뒤의 승인 검증 경로는 실제 PaymentService 코드로 돌린다.
 * <b>금액 대조 로직은 가짜가 아니다</b> — 가짜인 것은 네트워크 호출뿐이다.
 *
 * <p>취소는 호출을 기록한다({@link #getRecordedCancels()}) — 유닛테스트가 "무엇이 얼마나
 * 취소됐는가"를 네트워크 없이 단언하기 위해서다. {@link #failNextCancel}로 다음 취소 호출에
 * 던질 예외를 주입해 §5-2의 실패 판정(VOID/UNVERIFIED)을 결정적으로 재현한다.
 */
@Component
@Profile("e2e")
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    /** 취소 호출 한 건. amount는 전액 취소면 null, 부분 취소면 요청한 금액. */
    public record RecordedCancel(String paymentKey, Integer amount, String reason) {
    }

    private final List<RecordedCancel> recordedCancels = new CopyOnWriteArrayList<>();
    private final AtomicReference<PaymentGatewayException> nextCancelFailure = new AtomicReference<>();

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        String rawJson = "{\"e2e\":true,\"paymentKey\":\"" + paymentKey
                + "\",\"orderId\":\"" + orderNo
                + "\",\"totalAmount\":" + amount
                + ",\"status\":\"DONE\"}";
        return new PaymentApproval(paymentKey, amount, "DONE", rawJson);
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        log.info("[e2e] 가짜 결제 취소 호출: paymentKey={}, reason={}", paymentKey, reason);
        취소를_기록하고_주입된_예외를_던진다(new RecordedCancel(paymentKey, null, reason));
    }

    @Override
    public void cancelPartial(String paymentKey, String reason, int cancelAmount) {
        log.info("[e2e] 가짜 부분 취소 호출: paymentKey={}, amount={}, reason={}",
                paymentKey, cancelAmount, reason);
        취소를_기록하고_주입된_예외를_던진다(new RecordedCancel(paymentKey, cancelAmount, reason));
    }

    /** 기록이 먼저다 — "시도했는가"는 실패해도 남아야 단언할 수 있다. */
    private void 취소를_기록하고_주입된_예외를_던진다(RecordedCancel call) {
        recordedCancels.add(call);
        PaymentGatewayException injected = nextCancelFailure.getAndSet(null);
        if (injected != null) {
            throw injected;
        }
    }

    public List<RecordedCancel> getRecordedCancels() {
        return List.copyOf(recordedCancels);
    }

    /** 다음 취소 호출 한 번만 이 예외로 실패한다(그 뒤 호출은 다시 성공한다 — 재시도 검증용). */
    public void failNextCancel(PaymentGatewayException e) {
        nextCancelFailure.set(e);
    }

    /** 테스트 간 상태 격리. 빈이 싱글턴이므로 각 테스트가 스스로 비운다. */
    public void reset() {
        recordedCancels.clear();
        nextCancelFailure.set(null);
    }
}
