import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { fetchGoodsList, type GoodsSort } from '../api/goods';
import { GoodsGrid } from '../components/goods/GoodsGrid';
import { ListToolbar, PRICE_BAND_RANGE, type PriceBand } from '../components/goods/ListToolbar';
import { ROUTINE_STEPS } from '../features/routine/steps';
import './GoodsList.css';

/** 한 번에 불러오는 최대 건수. 페이지네이션은 목록이 실제로 커지는 웨이브에서 붙인다. */
const PAGE_SIZE = 40;

const SORT_VALUES: readonly GoodsSort[] = [
  'popular',
  'new',
  'sales',
  'priceAsc',
  'discount',
  'review',
];
const PRICE_BAND_VALUES: readonly PriceBand[] = ['UNDER_10K', 'FROM_10K_TO_30K', 'OVER_30K'];

/** URL의 sort는 사용자가 손으로 칠 수 있다 — 미지값은 popular로 정규화해 서버 400을 막는다. */
function normalizeSort(raw: string | null): GoodsSort {
  return SORT_VALUES.includes(raw as GoodsSort) ? (raw as GoodsSort) : 'popular';
}

function normalizePriceBand(raw: string | null): PriceBand | null {
  return PRICE_BAND_VALUES.includes(raw as PriceBand) ? (raw as PriceBand) : null;
}

/**
 * 카테고리 목록 페이지. `/goods?category=C002` 형태로 진입한다.
 * 정렬·가격대는 URL 쿼리(`sort`·`price`)가 상태의 진실이다 — 새로고침·공유·뒤로가기에서
 * 같은 목록이 나와야 하므로 컴포넌트 상태로 들지 않는다.
 *
 * 루틴 섹션의 "○○ 전체 보기"가 갈 곳이다 — 섹션은 4개만 보여주므로
 * 나머지를 볼 경로가 없으면 그 더보기는 죽은 링크가 된다.
 */
export function GoodsList() {
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get('category');
  // 태그 pill 클릭(Tag의 `to` prop) 진입점 — `/goods?tag=<slug>`. 카테고리와 동시에 걸리지 않으므로
  // 서로 독립적으로 params에 얹는다.
  const tag = searchParams.get('tag');
  const sort = normalizeSort(searchParams.get('sort'));
  const priceBand = normalizePriceBand(searchParams.get('price'));

  const { data, isLoading, isError } = useQuery({
    queryKey: ['goods-list', category, tag, sort, priceBand],
    queryFn: () =>
      fetchGoodsList({
        page: 0,
        size: PAGE_SIZE,
        sort,
        ...(category ? { categoryCode: category } : {}),
        ...(tag ? { tag } : {}),
        // 가격대는 URL의 pill 값을 서버 경계값으로 풀어 싣는다 — 클라이언트에서 거르지 않는다.
        ...(priceBand ? PRICE_BAND_RANGE[priceBand] : {}),
      }),
  });

  const handleSortChange = (next: GoodsSort) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      // 기본값은 URL에서 지운다 — `?sort=popular`와 무파라미터가 같은 화면의 두 주소가 되지 않게.
      if (next === 'popular') params.delete('sort');
      else params.set('sort', next);
      return params;
    });
  };

  const handlePriceBandChange = (next: PriceBand | null) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (next) params.set('price', next);
      else params.delete('price');
      return params;
    });
  };

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

      <ListToolbar
        category={category}
        sort={sort}
        priceBand={priceBand}
        onSortChange={handleSortChange}
        onPriceBandChange={handlePriceBandChange}
      />

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
