package com.beautyboy.payment;

import com.beautyboy.payment.dto.PaymentApproval;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * test 프로필용 취소 게이트웨이 더블. {@code FakePaymentGateway}(e2e 프로필 전용)와 같은
 * 역할이지만, 유닛테스트가 프로필을 바꾸지 않고 쓸 수 있도록 별도로 둔다.
 *
 * <p>{@link #failNext}로 다음 취소 호출 한 번에 던질 예외를 주입해 설계 §5-2의 실패 판정
 * (VOID/UNVERIFIED)을 결정적으로 재현한다. 기록이 먼저다 — "시도했는가"는 실패해도 남아야 한다.
 */
public class FakeCancelGateway implements PaymentGateway {

    /** 취소 호출 한 건. amount는 전액 취소면 null, 부분 취소면 요청한 금액. */
    public record RecordedCancel(String paymentKey, Integer amount, String reason) {
    }

    private final List<RecordedCancel> recorded = new CopyOnWriteArrayList<>();
    private final AtomicReference<PaymentGatewayException> nextFailure = new AtomicReference<>();

    @Override
    public PaymentApproval confirm(String paymentKey, String orderNo, int amount) {
        return new PaymentApproval(paymentKey, amount, "DONE", "{\"fake\":true}");
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        기록하고_주입된_예외를_던진다(new RecordedCancel(paymentKey, null, reason));
    }

    @Override
    public void cancelPartial(String paymentKey, String reason, int cancelAmount) {
        기록하고_주입된_예외를_던진다(new RecordedCancel(paymentKey, cancelAmount, reason));
    }

    private void 기록하고_주입된_예외를_던진다(RecordedCancel call) {
        recorded.add(call);
        PaymentGatewayException injected = nextFailure.getAndSet(null);
        if (injected != null) {
            throw injected;
        }
    }

    public List<RecordedCancel> recorded() {
        return List.copyOf(recorded);
    }

    /** 다음 취소 호출 한 번만 이 예외로 실패한다(그 뒤 호출은 다시 성공한다 — 재시도 검증용). */
    public void failNext(PaymentGatewayException e) {
        nextFailure.set(e);
    }

    /** 테스트 간 상태 격리. 빈이 싱글턴이므로 각 테스트가 스스로 비운다. */
    public void reset() {
        recorded.clear();
        nextFailure.set(null);
    }
}
