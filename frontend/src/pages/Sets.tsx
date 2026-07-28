import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchMe } from '../api/member';
import type { SkinType } from '../api/auth';
import { SetBand } from '../components/sets/SetBand';
import { useToast } from '../components/ui/useToast';
import { addSetToCart } from '../features/routine/addSetToCart';
import { deriveSetConcepts, SET_LETTERS } from '../features/affinity/setConcepts';
import { useComposer } from '../features/affinity/useComposer';
import { useAuthStore } from '../stores/authStore';
import './Sets.css';

/**
 * `/sets` 세트 비교 페이지 — DESIGN.md `sets-page` 사양의 구현.
 *
 * 컨셉이 다른 세트 3개를 세로로 나란히 쌓아 같은 단계끼리 비교할 수 있게 한다.
 * 세트 구성 자체(단계별 픽)는 Task 3 `SetBand`가 그리고, 조합 로직은 `useComposer`
 * (concernOverride로 세트 컨셉을 단독 대체)가 맡는다. 이 페이지는 셋을 묶는 접합부다.
 */
export function Sets() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const meQuery = useQuery({ queryKey: ['me'], queryFn: fetchMe, enabled: !!accessToken });
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [addingIndex, setAddingIndex] = useState<number | null>(null);

  // me가 확정되기 전에 컨셉을 정하면 프로필 도착 순간 밴드가 갈아엎어진다.
  const meSettled = !accessToken || meQuery.isSuccess || meQuery.isError;
  const concepts = useMemo(
    () =>
      deriveSetConcepts(
        meQuery.data?.concerns ?? [],
        (meQuery.data?.skinType ?? null) as SkinType | null,
      ),
    [meQuery.data],
  );

  // 훅은 루프에 넣을 수 없다(호출 순서가 고정돼야 한다). setConcepts.ts의 SET_COUNT(=3)가
  // 정확히 3이라는 전제를 여기서 펼쳐 쓴다 — SET_COUNT를 바꾸면 이 줄들도 함께 늘려야 한다.
  const setA = useComposer(meSettled ? concepts[0].slug : null);
  const setB = useComposer(meSettled ? concepts[1].slug : null);
  const setC = useComposer(meSettled ? concepts[2].slug : null);
  const states = [setA, setB, setC];

  async function handleAddSet(index: number) {
    setAddingIndex(index);
    try {
      const goodsNos = states[index]
        .map((step) => step.composition?.pick?.goodsNo)
        .filter((no): no is number => no !== undefined);
      const { added, skipped } = await addSetToCart(queryClient, goodsNos);
      if (added === 0) {
        toast('담지 못했어요. 잠시 후 다시 시도해 주세요', { tone: 'danger' });
      } else if (skipped === 0) {
        toast(`${added}개 담았어요`);
      } else {
        toast(`${added}개 담았어요 — ${skipped}개는 품절로 제외`);
      }
    } finally {
      setAddingIndex(null);
    }
  }

  // 세 컨셉이 전부 고정 폴백일 때만 — 하나라도 실제 프로필에서 왔으면 이미 맞춤 세트다.
  const allFallback = concepts.every((concept) => !concept.personalized);

  return (
    <div className="bb-sets">
      <div className="bb-sets__intro">
        <p className="bb-sets__eyebrow">PERSONAL SETS</p>
        <h1 className="bb-sets__title">당신을 위한 세트 3가지</h1>
        {allFallback && (
          <p className="bb-sets__fallback-notice">
            프로필을 등록하면 맞춤 세트로 바뀌어요.{' '}
            <Link to="/mypage/profile" className="bb-sets__fallback-link">
              프로필 등록하러 가기
            </Link>
          </p>
        )}
      </div>

      {concepts.map((concept, index) => (
        <SetBand
          key={concept.slug}
          concept={concept}
          letter={SET_LETTERS[index]}
          picks={states[index].map((step) => step.composition?.pick ?? null)}
          loading={
            !meSettled ||
            states[index].some((step) => step.composition === undefined && !step.isError)
          }
          adding={addingIndex === index}
          onAddSet={() => handleAddSet(index)}
        />
      ))}
    </div>
  );
}
