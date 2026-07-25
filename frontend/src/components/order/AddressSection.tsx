import type { Address } from '../../api/member';
import { Field } from '../ui/Field';
import './AddressSection.css';

/** 배송지 직접 입력 폼 필드. */
export interface ManualAddress {
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2: string;
}

interface AddressSectionProps {
  addresses: Address[];
  selectedId: number | 'manual';
  manualValue: ManualAddress;
  onSelect: (id: number | 'manual') => void;
  onManualChange: (patch: Partial<ManualAddress>) => void;
}

/**
 * 배송지 선택 — 저장된 배송지 라디오 목록 + "새 배송지 입력" 라디오.
 * 백엔드 AddressResponse에는 "집/회사" 같은 별칭 필드가 없어, 기본배송지는 관례적으로
 * "집"으로, 그 외는 "배송지"로 라벨링한다(판단 근거: 별칭 체계는 이 태스크 범위 밖).
 */
export function AddressSection({
  addresses,
  selectedId,
  manualValue,
  onSelect,
  onManualChange,
}: AddressSectionProps) {
  return (
    <div className="bb-address">
      <h2 className="bb-address__title">배송지</h2>

      <div className="bb-address__radios" role="radiogroup" aria-label="배송지 선택">
        {addresses.map((address) => (
          <label key={address.id} className="bb-address__radio">
            <input
              type="radio"
              name="order-address"
              checked={selectedId === address.id}
              onChange={() => onSelect(address.id)}
            />
            <span className="bb-address__radio-text">
              <strong>{address.isDefault ? '집' : '배송지'} · {address.address1}</strong>
              <span className="bb-address__radio-meta">
                {address.receiver} · {address.phone}
              </span>
            </span>
          </label>
        ))}

        <label className="bb-address__radio">
          <input
            type="radio"
            name="order-address"
            checked={selectedId === 'manual'}
            onChange={() => onSelect('manual')}
          />
          <span className="bb-address__radio-text">
            <strong>새 배송지 입력</strong>
          </span>
        </label>
      </div>

      {selectedId === 'manual' && (
        <div className="bb-address__form">
          <Field
            id="order-receiver-name"
            label="받는 분"
            value={manualValue.receiverName}
            onChange={(value) => onManualChange({ receiverName: value })}
            required
          />
          <Field
            id="order-receiver-phone"
            label="연락처"
            type="tel"
            inputMode="tel"
            value={manualValue.receiverPhone}
            onChange={(value) => onManualChange({ receiverPhone: value })}
            required
          />
          <Field
            id="order-zipcode"
            label="우편번호"
            inputMode="numeric"
            value={manualValue.zipcode}
            onChange={(value) => onManualChange({ zipcode: value })}
            required
          />
          <Field
            id="order-address1"
            label="주소"
            value={manualValue.address1}
            onChange={(value) => onManualChange({ address1: value })}
            required
          />
          <Field
            id="order-address2"
            label="상세주소"
            value={manualValue.address2}
            onChange={(value) => onManualChange({ address2: value })}
            hint="선택 입력"
          />
        </div>
      )}
    </div>
  );
}
