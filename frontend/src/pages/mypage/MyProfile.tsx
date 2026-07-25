import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createAddress,
  deleteAddress,
  fetchAddresses,
  fetchMe,
  updateAddress,
  updateProfile,
  type Address,
  type AddressInput,
} from '../../api/member';
import type { AgeBand, Concern, SkinType } from '../../api/auth';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Skeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/useToast';
import './MyProfile.css';

/**
 * 가입 2스텝(`SkinProfileStep`)과 같은 값·한글 라벨 세트를 쓰되, 이 화면 전용으로 따로 둔다.
 * `SkinProfileStep`의 라디오는 `<label>`이 desc 문구까지 감싸 접근성 이름이
 * "건성 당김·각질이 신경 쓰이는 편"처럼 길어진다 — 이 화면 테스트는
 * `getByRole('radio', { name: '건성' })`처럼 라벨 단독 이름을 요구하므로,
 * `aria-label`로 이름을 라벨 텍스트만으로 고정하는 별도 구현이 필요했다.
 */
const SKIN_TYPES: { value: SkinType; label: string; desc: string }[] = [
  { value: 'DRY', label: '건성', desc: '당김·각질이 신경 쓰이는 편' },
  { value: 'OILY', label: '지성', desc: '번들거림·모공이 신경 쓰이는 편' },
  { value: 'COMBINATION', label: '복합성', desc: 'T존은 유분, 볼은 건조한 편' },
  { value: 'SENSITIVE', label: '민감성', desc: '자극·트러블에 예민한 편' },
];

const CONCERNS: { value: Concern; label: string }[] = [
  { value: 'PORE', label: '모공' },
  { value: 'TROUBLE', label: '트러블' },
  { value: 'WRINKLE', label: '주름' },
  { value: 'DARK_SPOT', label: '색소침착' },
];

const AGE_BANDS: AgeBand[] = ['10s', '20s', '30s', '40s', '50s+'];

const EMPTY_ADDRESS_INPUT: AddressInput = {
  receiver: '',
  phone: '',
  zipcode: '',
  address1: '',
  address2: '',
  isDefault: false,
};

/**
 * 마이페이지 프로필 `/mypage/profile` — 피부 프로필(가입 2스텝 `SkinProfileStep` 재사용) +
 * 배송지 관리 섹션. 배송지는 추가·수정·삭제·기본 지정을 전부 `PUT/POST/DELETE
 * /members/me/addresses`로 처리한다.
 *
 * 기본배송지 지정 전용 엔드포인트는 없다 — `updateAddress`(api/member.ts)가 이미 문서화한 대로
 * `isDefault: true`를 채운 `AddressInput`을 그 배송지 id로 PUT하는 형태다. 다중 기본화는
 * 4-2의 DB 유니크 제약이 막아준다(project law) — 프론트는 낙관적 갱신 없이 재조회로만 반영한다.
 */
export function MyProfile() {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe });
  const addressesQuery = useQuery({ queryKey: ['addresses'], queryFn: fetchAddresses });

  const [skinType, setSkinType] = useState<SkinType | undefined>(undefined);
  const [concerns, setConcerns] = useState<Concern[]>([]);
  const [ageBand, setAgeBand] = useState<AgeBand | undefined>(undefined);
  const [initialized, setInitialized] = useState(false);

  // 회원 정보가 도착하면 한 번만 로컬 상태를 채운다(이후 사용자의 선택을 덮어쓰지 않는다).
  useEffect(() => {
    if (initialized || !meQuery.data) {
      return;
    }
    setSkinType((meQuery.data.skinType as SkinType | null) ?? undefined);
    setConcerns((meQuery.data.concerns as Concern[]) ?? []);
    setAgeBand((meQuery.data.ageBand as AgeBand | null) ?? undefined);
    setInitialized(true);
  }, [meQuery.data, initialized]);

  const profileMutation = useMutation({
    mutationFn: () =>
      updateProfile({
        ...(skinType ? { skinType } : {}),
        ...(concerns.length > 0 ? { concerns } : {}),
        ...(ageBand ? { ageBand } : {}),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me'] });
      toast('프로필을 저장했어요');
    },
  });

  function toggleConcern(value: Concern) {
    setConcerns((prev) => (prev.includes(value) ? prev.filter((c) => c !== value) : [...prev, value]));
  }

  if (meQuery.isLoading || addressesQuery.isLoading) {
    return (
      <div className="bb-my-profile">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (meQuery.isError || addressesQuery.isError) {
    return (
      <div className="bb-my-profile">
        <p className="bb-my-profile__error">프로필을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      </div>
    );
  }

  return (
    <div className="bb-my-profile">
      <section className="bb-my-profile__section">
        <h2 className="bb-my-profile__section-title">피부 프로필</h2>

        <fieldset className="bb-skin-profile__group">
          <legend className="bb-skin-profile__legend">피부타입</legend>
          <div className="bb-skin-profile__type-grid">
            {SKIN_TYPES.map((type) => (
              <label
                key={type.value}
                className={`bb-skin-type-card${skinType === type.value ? ' bb-skin-type-card--active' : ''}`}
              >
                <input
                  type="radio"
                  name="skinType"
                  aria-label={type.label}
                  checked={skinType === type.value}
                  onChange={() => setSkinType(type.value)}
                />
                <span className="bb-skin-type-card__label">{type.label}</span>
                <span className="bb-skin-type-card__desc">{type.desc}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="bb-skin-profile__group">
          <legend className="bb-skin-profile__legend">고민 (중복 선택 가능)</legend>
          <div className="bb-skin-profile__chip-row">
            {CONCERNS.map((concern) => {
              const active = concerns.includes(concern.value);
              return (
                <button
                  key={concern.value}
                  type="button"
                  className={`bb-chip${active ? ' bb-chip--active' : ''}`}
                  aria-pressed={active}
                  onClick={() => toggleConcern(concern.value)}
                >
                  {concern.label}
                </button>
              );
            })}
          </div>
        </fieldset>

        <fieldset className="bb-skin-profile__group">
          <legend className="bb-skin-profile__legend">연령대</legend>
          <div className="bb-skin-profile__chip-row">
            {AGE_BANDS.map((band) => (
              <button
                key={band}
                type="button"
                className={`bb-chip${ageBand === band ? ' bb-chip--active' : ''}`}
                aria-pressed={ageBand === band}
                onClick={() => setAgeBand(band)}
              >
                {band}
              </button>
            ))}
          </div>
        </fieldset>

        <Button
          className="bb-my-profile__save"
          variant="primary"
          onClick={() => profileMutation.mutate()}
          disabled={profileMutation.isPending}
          loading={profileMutation.isPending}
        >
          저장
        </Button>
      </section>

      <AddressManager addresses={addressesQuery.data ?? []} />
    </div>
  );
}

/** Address(id 포함) → AddressInput(PUT 바디용, id 제외)으로 좁힌다. 수정 폼 초기값 채우기용. */
function toAddressInput(address: Address): AddressInput {
  return {
    receiver: address.receiver,
    phone: address.phone,
    zipcode: address.zipcode,
    address1: address.address1,
    address2: address.address2,
    isDefault: address.isDefault,
  };
}

function AddressManager({ addresses }: { addresses: Address[] }) {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState<AddressInput>(EMPTY_ADDRESS_INPUT);
  // 한 번에 하나만 수정한다 — 수정 중인 배송지 id. null이면 수정 폼이 전부 닫혀 있다.
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<AddressInput>(EMPTY_ADDRESS_INPUT);

  const invalidateAddresses = () => queryClient.invalidateQueries({ queryKey: ['addresses'] });

  const createMutation = useMutation({
    mutationFn: (input: AddressInput) => createAddress(input),
    onSuccess: () => {
      invalidateAddresses();
      toast('배송지를 추가했어요');
      setDraft(EMPTY_ADDRESS_INPUT);
      setAdding(false);
    },
  });

  const setDefaultMutation = useMutation({
    mutationFn: (address: Address) =>
      updateAddress(address.id, { ...toAddressInput(address), isDefault: true }),
    onSuccess: () => {
      invalidateAddresses();
      toast('기본배송지로 설정했어요');
    },
  });

  // 필드 수정 저장 — updateAddress(id, fullInput)을 그대로 쓴다. isDefault는 수정 폼이
  // 건드리지 않는 값이므로(그 역할은 "기본으로 설정" 버튼) editDraft에 담긴 값 그대로 보낸다.
  const editMutation = useMutation({
    mutationFn: (id: number) => updateAddress(id, editDraft),
    onSuccess: () => {
      invalidateAddresses();
      toast('배송지를 수정했어요');
      setEditingId(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteAddress(id),
    onSuccess: () => {
      invalidateAddresses();
      toast('배송지를 삭제했어요');
    },
  });

  function startEdit(address: Address) {
    setAdding(false);
    setEditingId(address.id);
    setEditDraft(toAddressInput(address));
  }

  return (
    <section className="bb-my-profile__section">
      <h2 className="bb-my-profile__section-title">배송지 관리</h2>

      {addresses.length === 0 && <p className="bb-my-profile__empty">등록된 배송지가 없어요.</p>}

      <ul className="bb-address-manager__list">
        {addresses.map((address) => (
          <li key={address.id} className="bb-address-manager__item">
            {editingId === address.id ? (
              <div className="bb-address-manager__form">
                <Field
                  id={`edit-address-receiver-${address.id}`}
                  label="받는 분"
                  value={editDraft.receiver}
                  onChange={(value) => setEditDraft((prev) => ({ ...prev, receiver: value }))}
                  required
                />
                <Field
                  id={`edit-address-phone-${address.id}`}
                  label="연락처"
                  type="tel"
                  inputMode="tel"
                  value={editDraft.phone}
                  onChange={(value) => setEditDraft((prev) => ({ ...prev, phone: value }))}
                  required
                />
                <Field
                  id={`edit-address-zipcode-${address.id}`}
                  label="우편번호"
                  inputMode="numeric"
                  value={editDraft.zipcode}
                  onChange={(value) => setEditDraft((prev) => ({ ...prev, zipcode: value }))}
                  required
                />
                <Field
                  id={`edit-address-address1-${address.id}`}
                  label="주소"
                  value={editDraft.address1}
                  onChange={(value) => setEditDraft((prev) => ({ ...prev, address1: value }))}
                  required
                />
                <Field
                  id={`edit-address-address2-${address.id}`}
                  label="상세주소"
                  value={editDraft.address2}
                  onChange={(value) => setEditDraft((prev) => ({ ...prev, address2: value }))}
                  hint="선택 입력"
                />
                <div className="bb-address-manager__form-actions">
                  <Button variant="ghost" onClick={() => setEditingId(null)}>
                    취소
                  </Button>
                  <Button
                    variant="primary"
                    onClick={() => editMutation.mutate(address.id)}
                    disabled={editMutation.isPending}
                    loading={editMutation.isPending}
                  >
                    저장
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <div className="bb-address-manager__info">
                  <strong>
                    {address.isDefault ? '집' : '배송지'} · {address.address1}
                  </strong>
                  <span className="bb-address-manager__meta">
                    {address.receiver} · {address.phone}
                  </span>
                </div>
                <div className="bb-address-manager__actions">
                  <button
                    type="button"
                    className="bb-address-manager__action"
                    onClick={() => startEdit(address)}
                  >
                    수정
                  </button>
                  {!address.isDefault && (
                    <button
                      type="button"
                      className="bb-address-manager__action"
                      onClick={() => setDefaultMutation.mutate(address)}
                    >
                      기본으로 설정
                    </button>
                  )}
                  <button
                    type="button"
                    className="bb-address-manager__action"
                    onClick={() => deleteMutation.mutate(address.id)}
                  >
                    삭제
                  </button>
                </div>
              </>
            )}
          </li>
        ))}
      </ul>

      {adding ? (
        <div className="bb-address-manager__form">
          <Field
            id="new-address-receiver"
            label="받는 분"
            value={draft.receiver}
            onChange={(value) => setDraft((prev) => ({ ...prev, receiver: value }))}
            required
          />
          <Field
            id="new-address-phone"
            label="연락처"
            type="tel"
            inputMode="tel"
            value={draft.phone}
            onChange={(value) => setDraft((prev) => ({ ...prev, phone: value }))}
            required
          />
          <Field
            id="new-address-zipcode"
            label="우편번호"
            inputMode="numeric"
            value={draft.zipcode}
            onChange={(value) => setDraft((prev) => ({ ...prev, zipcode: value }))}
            required
          />
          <Field
            id="new-address-address1"
            label="주소"
            value={draft.address1}
            onChange={(value) => setDraft((prev) => ({ ...prev, address1: value }))}
            required
          />
          <Field
            id="new-address-address2"
            label="상세주소"
            value={draft.address2}
            onChange={(value) => setDraft((prev) => ({ ...prev, address2: value }))}
            hint="선택 입력"
          />
          <div className="bb-address-manager__form-actions">
            <Button variant="ghost" onClick={() => setAdding(false)}>
              취소
            </Button>
            <Button
              variant="primary"
              onClick={() => createMutation.mutate(draft)}
              disabled={createMutation.isPending}
              loading={createMutation.isPending}
            >
              배송지 추가
            </Button>
          </div>
        </div>
      ) : (
        <Button
          variant="ghost"
          onClick={() => {
            setEditingId(null);
            setAdding(true);
          }}
        >
          새 배송지 추가
        </Button>
      )}
    </section>
  );
}
