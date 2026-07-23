# BeautyBoy Frontend

뷰티보이(남성 화장품 커머스) 프론트엔드. React SPA (Vite + TypeScript).

## 실행법

```bash
npm install

# 개발 서버 (백엔드 API 연동)
npm run dev

# 개발 서버 + MSW mock (백엔드 없이 상품 목록/상세 확인, 쇼케이스 포함)
VITE_USE_MOCK=true npm run dev
```

`VITE_USE_MOCK=true`로 실행하면 `import.meta.env.DEV && VITE_USE_MOCK === 'true'`일 때만
MSW(Mock Service Worker)가 켜져, 백엔드 없이도 `/`(홈)과 `/dev/components`(컴포넌트 쇼케이스)에서
실제 데이터 흐름을 확인할 수 있다. 실 API로 붙일 때는 이 환경변수를 끄기만 하면 된다.

```bash
npm test        # vitest 전체 테스트
npm run build   # tsc -b && vite build (타입 체크 포함)
npm run lint    # oxlint
```

## 디렉터리 구조

```
src/
  api/            axios 클라이언트(client.ts)와 도메인별 API 함수(goods.ts, auth.ts)
                  ApiEnvelope({code,message,data})를 벗겨 data만 반환한다
  components/
    ui/           디자인시스템 프리미티브 (Button, Field, Badge, Price, Rating, Skeleton)
    goods/        상품 카드 계열 (GoodsCard, GoodsCardSkeleton, GoodsGrid)
    layout/       Header, Footer, Layout (Outlet 기반 공통 레이아웃)
    signup/       회원가입 전용 컴포넌트 (SkinProfileStep)
  mocks/          MSW 핸들러(handlers.ts), 픽스처(fixtures/goods.ts), 브라우저/서버 워커
  pages/          라우트 단위 화면 (Home, Login, Signup, dev/Showcase)
  stores/         zustand 스토어 (authStore)
  types/          도메인 타입 (goods.ts — GoodsListItem 등, 백엔드 DTO와 형태 동결)
  router.tsx      react-router 라우트 정의
  App.tsx / main.tsx  앱 엔트리
```

## 디자인 토큰

- 루트의 `DESIGN.md`가 색·타이포·간격·컴포넌트 사양의 **유일한 진실**이다.
- `src/index.css`가 그 값을 CSS 커스텀 프로퍼티(토큰)로 정의하는 곳이다.
- 컴포넌트 CSS를 작성할 때는 `var(--color-ink)`, `var(--space-lg)`처럼 **토큰을 참조**한다.
  hex 값이나 px 값을 손으로 옮겨 적지 않는다 — 옮겨 적는 순간 문서와 코드가 갈라진다.
- 문서에 없는 색·간격·컴포넌트가 필요하면 임의로 만들지 말고 `DESIGN.md`를 먼저 갱신한다.

## 상품 카드 사용 예시

```tsx
import { useQuery } from '@tanstack/react-query';
import { GoodsGrid } from './components/goods/GoodsGrid';
import { fetchGoodsList } from './api/goods';

function GoodsListSection() {
  const { data, isLoading } = useQuery({
    queryKey: ['goods', 'list'],
    queryFn: () => fetchGoodsList({ page: 0, size: 20 }),
  });

  return <GoodsGrid items={data?.content ?? []} loading={isLoading} />;
}
```

낱장 카드가 필요하면 `GoodsCard`를 직접 사용한다:

```tsx
import { GoodsCard } from './components/goods/GoodsCard';

<GoodsCard item={goodsListItem} onWishToggle={(goodsNo) => {}} />
```
