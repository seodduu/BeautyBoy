import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { cancelOrder, fetchOrderDetail, type OrderDetailItem } from '../../api/order';
import { QuantityStepper } from '../goods/QuantityStepper';
import { Button } from '../ui/Button';
import { formatWon } from '../ui/Price';
import { Skeleton } from '../ui/Skeleton';
import { useToast } from '../ui/useToast';
import './CancelOrderModal.css';

interface CancelOrderModalProps {
  open: boolean;
  orderNo: string;
  onClose: () => void;
}

/** 사유 선택지. "기타"만 직접 입력을 연다 — 나머지는 고른 문구가 그대로 사유가 된다. */
const REASONS = ['단순 변심', '주문 실수', '기타'] as const;
const REASON_MAX_LENGTH = 200;

function remainingOf(item: OrderDetailItem): number {
  return item.quantity - item.canceledQuantity;
}

/**
 * 수량 단위 주문 취소 모달. 상세를 다시 조회해 항목별 잔여 수량을 보여주고, 고른 수량만 취소한다.
 *
 * 화면이 계산하는 환불액은 어디까지나 예상치다 — 확정 금액은 서버가 주문 시점 스냅샷 단가로
 * 다시 계산한다(계약 §3-8: 요청 바디에 금액 필드가 없는 이유). 그래서 성공 토스트도 화면의
 * 예상액이 아니라 서버 응답의 refundAmount를 읽어준다.
 *
 * 접근성은 CautionPanel과 같은 규약이다 — role=dialog + aria-modal, Esc·오버레이로 닫힘,
 * 열릴 때 포커스 이동·닫힐 때 트리거 복귀, 열려 있는 동안 배경 스크롤 잠금.
 */
export function CancelOrderModal({ open, orderNo, onClose }: CancelOrderModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<Element | null>(null);
  const queryClient = useQueryClient();
  const { toast } = useToast();

  /** 선택 상태: orderItemId → 취소 수량. 키가 없으면 미선택이다(수량 0을 따로 두지 않는다). */
  const [selected, setSelected] = useState<Map<number, number>>(new Map());
  const [reasonChoice, setReasonChoice] = useState<string>(REASONS[0]);
  const [reasonDetail, setReasonDetail] = useState('');

  // 목록 행에서도 열리므로 모달이 직접 상세를 조회한다. 키는 상세 화면과 같은 것을 쓴다 —
  // 취소 후 무효화 한 번으로 두 화면이 함께 갱신되게 하려는 것이다.
  const detailQuery = useQuery({
    queryKey: ['myOrderDetail', orderNo],
    queryFn: () => fetchOrderDetail(orderNo),
    enabled: open,
  });

  useEffect(() => {
    if (!open) return;
    triggerRef.current = document.activeElement;
    closeRef.current?.focus();

    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = prevOverflow;
      if (triggerRef.current instanceof HTMLElement) triggerRef.current.focus();
    };
  }, [open, onClose]);

  const items = detailQuery.data?.items ?? [];

  const estimatedRefund = useMemo(
    () =>
      items.reduce((sum, item) => sum + item.unitPrice * (selected.get(item.orderItemId) ?? 0), 0),
    [items, selected],
  );

  const reason = reasonChoice === '기타' ? reasonDetail.trim() : reasonChoice;

  const cancelMutation = useMutation({
    mutationFn: () =>
      cancelOrder(orderNo, {
        items: [...selected].map(([orderItemId, quantity]) => ({ orderItemId, quantity })),
        reason,
      }),
    onSuccess: (result) => {
      toast(`취소가 완료됐어요 — 환불 ${formatWon(result.refundAmount)}`);
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      queryClient.invalidateQueries({ queryKey: ['myOrderDetail', orderNo] });
      onClose();
    },
    onError: (error) => {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined;
      if (status === 502) {
        // 서버가 전부 롤백했다 — 주문은 그대로이므로 모달을 열어둔 채 재시도할 수 있다.
        toast('결제사 통신에 실패했어요. 잠시 후 다시 시도해주세요', { tone: 'danger' });
        return;
      }
      if (status === 409) {
        // 화면이 들고 있던 잔여 수량이 낡았다는 뜻 — 다시 조회하게 만들고 닫는다.
        toast('이미 처리된 주문이에요', { tone: 'danger' });
        queryClient.invalidateQueries({ queryKey: ['myOrders'] });
        queryClient.invalidateQueries({ queryKey: ['myOrderDetail', orderNo] });
        onClose();
        return;
      }
      toast('취소하지 못했어요. 잠시 후 다시 시도해주세요', { tone: 'danger' });
    },
  });

  if (!open) return null;

  const toggle = (item: OrderDetailItem) => {
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(item.orderItemId)) next.delete(item.orderItemId);
      else next.set(item.orderItemId, 1);
      return next;
    });
  };

  const changeQuantity = (orderItemId: number, quantity: number) => {
    setSelected((prev) => new Map(prev).set(orderItemId, quantity));
  };

  const titleId = 'bb-cancel-modal-title';
  const submittable = selected.size > 0 && reason.length > 0 && !cancelMutation.isPending;

  return (
    <div className="bb-cancel-modal-overlay" onClick={onClose}>
      <div
        className="bb-cancel-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="bb-cancel-modal__head">
          <h2 id={titleId} className="bb-cancel-modal__title">
            주문 취소
          </h2>
          <button
            ref={closeRef}
            type="button"
            className="bb-cancel-modal__close"
            onClick={onClose}
          >
            닫기
          </button>
        </header>

        {detailQuery.isLoading || !detailQuery.data ? (
          <div className="bb-cancel-modal__body">
            <Skeleton ratio="16 / 9" />
          </div>
        ) : (
          <div className="bb-cancel-modal__body">
            <ul className="bb-cancel-modal__items">
              {items.map((item) => {
                const remaining = remainingOf(item);
                const quantity = selected.get(item.orderItemId);
                return (
                  <li key={item.orderItemId} className="bb-cancel-modal__item">
                    <label className="bb-cancel-modal__pick">
                      <input
                        type="checkbox"
                        checked={quantity !== undefined}
                        disabled={remaining === 0}
                        onChange={() => toggle(item)}
                      />
                      <span className="bb-cancel-modal__pick-text">
                        <span className="bb-cancel-modal__item-name">{item.goodsName}</span>
                        {item.optionName && (
                          <span className="bb-cancel-modal__item-option">{item.optionName}</span>
                        )}
                        {/* 잔여는 색이 아니라 문장으로 알린다 — 0인 항목이 왜 못 눌리는지 읽혀야 한다. */}
                        <span className="bb-cancel-modal__item-remaining">
                          {remaining === 0 ? '취소 완료' : `취소 가능 ${remaining}개`}
                        </span>
                      </span>
                    </label>
                    {quantity !== undefined && (
                      <QuantityStepper
                        quantity={quantity}
                        max={remaining}
                        onChange={(next) => changeQuantity(item.orderItemId, next)}
                      />
                    )}
                  </li>
                );
              })}
            </ul>

            <div className="bb-cancel-modal__reason">
              <label className="bb-cancel-modal__label" htmlFor="bb-cancel-reason">
                취소 사유
              </label>
              <select
                id="bb-cancel-reason"
                className="bb-cancel-modal__select"
                value={reasonChoice}
                onChange={(e) => setReasonChoice(e.target.value)}
              >
                {REASONS.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
              {reasonChoice === '기타' && (
                <>
                  <label className="bb-cancel-modal__label" htmlFor="bb-cancel-reason-detail">
                    사유 직접 입력
                  </label>
                  <textarea
                    id="bb-cancel-reason-detail"
                    className="bb-cancel-modal__textarea"
                    maxLength={REASON_MAX_LENGTH}
                    value={reasonDetail}
                    onChange={(e) => setReasonDetail(e.target.value)}
                  />
                </>
              )}
            </div>

            <div className="bb-cancel-modal__refund">
              <span className="bb-cancel-modal__refund-label">예상 환불액</span>
              <span className="bb-cancel-modal__refund-value" data-testid="estimated-refund">
                {formatWon(estimatedRefund)}
              </span>
            </div>
            <p className="bb-cancel-modal__caption">
              화면에 보이는 금액은 예상치예요. 실제 환불액은 주문 시점 가격으로 서버가 확정합니다.
            </p>

            <Button
              className="bb-cancel-modal__submit"
              disabled={!submittable}
              loading={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate()}
            >
              취소 확정
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
