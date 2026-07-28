import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchRoutine, type SkinType } from '../api/routine';
import { fetchMe } from '../api/member';
import { addCartItemsBulk, type CartBulkAddItem } from '../api/cart';
import { checkCompat } from '../api/compat';
import { queryKeys } from '../api/queryKeys';
import { promoteLocalSkinTypeIfNeeded, readLocalSkinType, writeLocalSkinType } from '../features/routine/skinProfile';
import { SkinTypeQuiz } from '../components/routine/SkinTypeQuiz';
import { RoutineStepCard } from '../components/routine/RoutineStepCard';
import { CompatBanner } from '../components/compat/CompatBanner';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/useToast';
import { useAuthStore } from '../stores/authStore';
import './Routine.css';

/** 시간대 선택 UI는 1차 범위 밖 — 항상 기본(아침) 루틴을 조회한다(설계 8장 "1차: 단순 룩업"). */
const DEFAULT_TIME = 'BASIC';

/**
 * 루틴 가이드 `/routine` — 설계 8장. 프로필/퀴즈로 피부타입을 정하고, 그 타입으로 받은 5단계
 * 루틴에서 단계마다 추천 상품을 고른 뒤(기본 선택) 궁합을 검사하고 한 번에 담는다.
 *
 * 로그인 여부와 무관하게 접근 가능하다(`GET /routines`는 SecurityConfig가 permitAll) —
 * 비회원은 퀴즈 결과를 localStorage(`bb.skinType`)에만 남기고, 로그인 상태에서 서버 프로필이
 * 비어 있으면 그 결과를 한 번 승격한다(설계 8장 "가입 시 승격").
 */
export function Routine() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);

  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });

  // 로컬 퀴즈 결과 — 초기값은 마운트 시점 localStorage 스냅샷이고, 퀴즈를 마치면 즉시 갱신한다
  // (localStorage 자체는 이벤트를 발생시키지 않으므로 state로 한 번 더 들고 있어야 리렌더된다).
  const [localSkinType, setLocalSkinType] = useState<SkinType | null>(() => readLocalSkinType());

  // 로그인 + 서버 프로필 조회 완료 후 딱 한 번만 승격을 시도한다(재실행 방지 가드).
  const promotedRef = useRef(false);
  useEffect(() => {
    if (promotedRef.current || !accessToken || !meQuery.data) {
      return;
    }
    promotedRef.current = true;
    void promoteLocalSkinTypeIfNeeded(meQuery.data).then(() => {
      setLocalSkinType(readLocalSkinType());
      queryClient.invalidateQueries({ queryKey: ['me'] });
    });
  }, [accessToken, meQuery.data, queryClient]);

  // 서버 프로필이 우선이다(회원=프로필) — 없으면 로컬 퀴즈 결과로 대체한다(비회원=퀴즈).
  const serverSkinType = (meQuery.data?.skinType ?? null) as SkinType | null;
  const skinType = serverSkinType ?? localSkinType;
  const meLoading = !!accessToken && meQuery.isLoading;

  const routineQuery = useQuery({
    queryKey: ['routine', skinType],
    queryFn: () => fetchRoutine(skinType ?? undefined, DEFAULT_TIME),
    enabled: skinType !== null,
  });

  // 단계별 선택 — stepOrder → goodsNo. 새 루틴이 도착하면 단계마다 첫 추천을 기본 선택한다
  // (설계 8장 "추천 2~3개, 기본 선택" — 아무것도 안 고른 상태로는 전체 담기 플로우가 끊긴다).
  const [selections, setSelections] = useState<Record<number, number>>({});
  useEffect(() => {
    if (!routineQuery.data) {
      return;
    }
    const initial: Record<number, number> = {};
    for (const step of routineQuery.data.steps) {
      const first = step.recommendations[0];
      if (first) {
        initial[step.stepOrder] = first.goodsNo;
      }
    }
    setSelections(initial);
  }, [routineQuery.data]);

  const selectedGoodsNos = Object.values(selections);

  // 선택 조합(goodsNo 집합)이 바뀔 때만 재조회 — queryKeys.compat이 정렬·중복제거해
  // 고른 순서와 무관하게 같은 조합이면 같은 캐시 엔트리를 쓴다.
  const compatQuery = useQuery({
    queryKey: queryKeys.compat(selectedGoodsNos),
    queryFn: () => checkCompat(selectedGoodsNos),
    enabled: selectedGoodsNos.length > 0,
  });

  const bulkAddMutation = useMutation({
    mutationFn: (items: CartBulkAddItem[]) => addCartItemsBulk(items),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.cart() });
      toast('루틴을 장바구니에 담았어요');
      navigate('/cart');
    },
    onError: () => toast('담기에 실패했어요. 다시 시도해 주세요', { tone: 'danger' }),
  });

  function handleQuizComplete(result: SkinType) {
    writeLocalSkinType(result);
    setLocalSkinType(result);
  }

  function handleSelect(stepOrder: number, goodsNo: number) {
    setSelections((prev) => ({ ...prev, [stepOrder]: goodsNo }));
  }

  function handleAddAll() {
    // 카드에는 옵션 정보가 없으므로 optionNo는 항상 null로 보낸다.
    const items: CartBulkAddItem[] = selectedGoodsNos.map((goodsNo) => ({
      goodsNo,
      optionNo: null,
      quantity: 1,
    }));
    bulkAddMutation.mutate(items);
  }

  if (meLoading) {
    return (
      <div className="bb-routine">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (skinType === null) {
    return (
      <div className="bb-routine">
        <h1 className="bb-routine__title">루틴 가이드</h1>
        <SkinTypeQuiz onComplete={handleQuizComplete} />
      </div>
    );
  }

  if (routineQuery.isLoading) {
    return (
      <div className="bb-routine">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (routineQuery.isError || !routineQuery.data) {
    return (
      <div className="bb-routine">
        <p className="bb-routine__error">루틴을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      </div>
    );
  }

  const routine = routineQuery.data;
  const allSelected = routine.steps.every((step) => selections[step.stepOrder] !== undefined);

  return (
    <div className="bb-routine">
      <h1 className="bb-routine__title">루틴 가이드</h1>
      <p className="bb-routine__description">{routine.description}</p>

      {compatQuery.data && <CompatBanner result={compatQuery.data} />}

      <div className="bb-routine__steps">
        {routine.steps.map((step) => (
          <RoutineStepCard
            key={step.stepOrder}
            step={step}
            selectedGoodsNo={selections[step.stepOrder] ?? null}
            onSelect={(goodsNo) => handleSelect(step.stepOrder, goodsNo)}
          />
        ))}
      </div>

      <Button
        className="bb-routine__cta"
        variant="primary"
        onClick={handleAddAll}
        disabled={!allSelected}
        loading={bulkAddMutation.isPending}
      >
        루틴 전체 담기
      </Button>
    </div>
  );
}
