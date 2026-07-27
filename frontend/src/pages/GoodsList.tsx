import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { fetchGoodsList } from '../api/goods';
import { GoodsGrid } from '../components/goods/GoodsGrid';
import { ROUTINE_STEPS } from '../features/routine/steps';
import './GoodsList.css';

/** 한 번에 불러오는 최대 건수. 페이지네이션은 목록이 실제로 커지는 웨이브에서 붙인다. */
const PAGE_SIZE = 40;

/**
 * 카테고리 목록 페이지. `/goods?category=C002` 형태로 진입한다.
 *
 * 루틴 섹션의 "○○ 전체 보기"가 갈 곳이다 — 섹션은 4개만 보여주므로
 * 나머지를 볼 경로가 없으면 그 더보기는 죽은 링크가 된다.
 */
export function GoodsList() {
  const [searchParams] = useSearchParams();
  const category = searchParams.get('category');
  // 태그 pill 클릭(Tag의 `to` prop) 진입점 — `/goods?tag=<slug>`. 카테고리와 동시에 걸리지 않으므로
  // 서로 독립적으로 params에 얹는다.
  const tag = searchParams.get('tag');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['goods-list', category, tag],
    queryFn: () =>
      fetchGoodsList({
        page: 0,
        size: PAGE_SIZE,
        ...(category ? { categoryCode: category } : {}),
        ...(tag ? { tag } : {}),
      }),
  });

  // 루틴 단계 코드로 들어왔으면 그 단계 이름을 그대로 제목에 쓴다.
  // 매핑에 없는 코드는 이름을 지어내지 않는다 — 코드만 부제로 노출한다.
  const matchedStep = ROUTINE_STEPS.find((step) => step.categoryCode === category);
  const title = matchedStep?.label ?? (tag ? '태그 상품' : category ? '카테고리 상품' : '전체 상품');

  return (
    <div className="bb-goods-list">
      <header className="bb-goods-list__head">
        <h1 className="bb-goods-list__title">{title}</h1>
        {!isLoading && !isError && data && data.totalElements > 0 && (
          <p className="bb-goods-list__count">{data.totalElements}개의 상품</p>
        )}
      </header>

      {isError ? (
        <p className="bb-goods-list__error">상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      ) : (
        /* 카테고리로 들어온 목록만 문맥을 안다 — `/goods?tag=` 진입은 categoryCode가 null이라
           카드가 찜을 기록하지 않는다(설계 §6.1의 "문맥 없는 찜은 버린다"). */
        <GoodsGrid
          items={data?.content ?? []}
          loading={isLoading}
          skeletonCount={10}
          categoryCode={category ?? undefined}
        />
      )}
    </div>
  );
}
