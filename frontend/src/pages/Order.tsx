import { useEffect, useState } from 'react';
import { isAxiosError } from 'axios';
import { useQuery } from '@tanstack/react-query';
import { fetchCartItems } from '../api/cart';
import { queryKeys } from '../api/queryKeys';
import { fetchAddresses } from '../api/member';
import { createOrder } from '../api/order';
import { requestTossPayment } from '../features/payment/toss';
import { AddressSection, type ManualAddress } from '../components/order/AddressSection';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { formatWon } from '../components/ui/Price';
import { useAuthStore } from '../stores/authStore';
import './Order.css';

const EMPTY_MANUAL: ManualAddress = {
  receiverName: '',
  receiverPhone: '',
  zipcode: '',
  address1: '',
  address2: '',
};

/** 담긴 상품으로 결제창에 넘길 주문명을 만든다. "그린티 토너 외 1건" 형태 — 목록 화면 표기와 맞춘다. */
function buildOrderName(goodsNames: string[]): string {
  if (goodsNames.length === 0) {
    return '주문 상품';
  }
  if (goodsNames.length === 1) {
    return goodsNames[0];
  }
  return `${goodsNames[0]} 외 ${goodsNames.length - 1}건`;
}

/** 받는 분 정보가 다 찼는지 확인하고, 비었으면 사용자에게 보여줄 첫 번째 에러 문구를 돌려준다. */
function validateReceiver(v: ManualAddress): string | null {
  if (!v.receiverName.trim()) {
    return '받는 분을 입력해 주세요.';
  }
  if (!v.receiverPhone.trim()) {
    return '연락처를 입력해 주세요.';
  }
  if (!v.zipcode.trim() || !v.address1.trim()) {
    return '배송지 주소를 입력해 주세요.';
  }
  return null;
}

/**
 * 주문서 `/order` — 설계 7장. 장바구니에서 넘어온 항목을 읽기 전용으로 보여주고,
 * 배송지(기본배송지 자동 선택, 없으면 직접 입력) → 결제하기 → `createOrder` →
 * 서버가 돌려준 payableAmount로 토스 결제창을 연다.
 *
 * 화면 합계는 안내용이고, 결제창에 넘기는 금액은 항상 `POST /orders` 응답의 payableAmount다
 * (project law: 돈은 서버가 계산한다).
 */
export function Order() {
  const member = useAuthStore((state) => state.member);

  const cartQuery = useQuery({ queryKey: queryKeys.cart(), queryFn: fetchCartItems });
  const addressQuery = useQuery({ queryKey: ['addresses'], queryFn: fetchAddresses });

  const [selectedId, setSelectedId] = useState<number | 'manual' | null>(null);
  const [manual, setManual] = useState<ManualAddress>(EMPTY_MANUAL);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // 배송지 목록이 도착하면 한 번만 기본값을 정한다 — 기본배송지가 있으면 그걸, 없으면 직접 입력을 연다.
  useEffect(() => {
    if (selectedId !== null || !addressQuery.data) {
      return;
    }
    const defaultAddress = addressQuery.data.find((address) => address.isDefault);
    setSelectedId(defaultAddress ? defaultAddress.id : 'manual');
  }, [addressQuery.data, selectedId]);

  if (cartQuery.isLoading || addressQuery.isLoading || selectedId === null) {
    return (
      <div className="bb-order">
        <Skeleton ratio="16 / 9" />
      </div>
    );
  }

  if (cartQuery.isError || addressQuery.isError) {
    return (
      <div className="bb-order">
        <p className="bb-order__error">주문서를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      </div>
    );
  }

  const items = cartQuery.data ?? [];
  const addresses = addressQuery.data ?? [];
  const total = items.reduce((sum, item) => sum + item.lineAmount, 0);

  async function handleSubmit() {
    if (submitting) {
      return;
    }

    const receiver: ManualAddress =
      selectedId === 'manual'
        ? manual
        : (() => {
            const address = addresses.find((a) => a.id === selectedId);
            return address
              ? {
                  receiverName: address.receiver,
                  receiverPhone: address.phone,
                  zipcode: address.zipcode,
                  address1: address.address1,
                  address2: address.address2,
                }
              : EMPTY_MANUAL;
          })();

    const validationError = validateReceiver(receiver);
    if (validationError) {
      setError(validationError);
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const result = await createOrder({
        items: items.map((item) => ({
          goodsNo: item.goodsNo,
          optionNo: item.optionNo,
          quantity: item.quantity,
        })),
        receiverName: receiver.receiverName,
        receiverPhone: receiver.receiverPhone,
        zipcode: receiver.zipcode,
        address1: receiver.address1,
        address2: receiver.address2,
        deliveryType: 'NORMAL',
      });

      await requestTossPayment({
        orderNo: result.orderNo,
        orderName: buildOrderName(items.map((item) => item.goodsName)),
        amount: result.payableAmount,
        // customerKey는 회원 식별자를 그대로 노출하지 않도록 접두사를 붙인다. 토스가 요구하는
        // 비-추측성은 이 프로젝트 범위에서 과한 요구라 단순 접두사로 둔다.
        customerKey: `bb-${member?.id ?? 0}`,
      });
    } catch (err) {
      if (isAxiosError(err) && err.response?.data?.message) {
        setError(err.response.data.message as string);
      } else {
        setError('주문을 만들지 못했어요. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="bb-order">
      <h1 className="bb-order__title">주문서</h1>

      <section className="bb-order__section">
        <h2 className="bb-order__section-title">주문 상품</h2>
        <ul className="bb-order__items">
          {items.map((item) => (
            <li key={item.cartItemId} className="bb-order__item">
              <div className="bb-order__item-info">
                <span className="bb-order__item-name">{item.goodsName}</span>
                {item.optionName && <span className="bb-order__item-option">{item.optionName}</span>}
              </div>
              <div className="bb-order__item-meta">
                <span>{item.quantity}개</span>
                <span>{formatWon(item.lineAmount)}</span>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <AddressSection
        addresses={addresses}
        selectedId={selectedId}
        manualValue={manual}
        onSelect={setSelectedId}
        onManualChange={(patch) => setManual((prev) => ({ ...prev, ...patch }))}
      />

      <section className="bb-order__section">
        <h2 className="bb-order__section-title">배송유형</h2>
        {/* 오늘드림은 1차 범위 밖 — 값은 하나뿐이라 선택지 없이 고정 표기만 한다. */}
        <p className="bb-order__delivery-type">일반배송</p>
      </section>

      {error && (
        <p className="bb-order__alert" role="alert">
          {error}
        </p>
      )}

      <div className="bb-order__summary">
        <span className="bb-order__summary-label">합계(안내용)</span>
        <span className="bb-order__summary-total">{formatWon(total)}</span>
      </div>

      <Button
        className="bb-order__cta"
        variant="primary"
        onClick={handleSubmit}
        disabled={submitting}
        loading={submitting}
      >
        결제하기
      </Button>
    </div>
  );
}
