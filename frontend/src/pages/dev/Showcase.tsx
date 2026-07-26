import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchGoodsList } from '../../api/goods';
import type { GoodsListItem } from '../../types/goods';
import { Button } from '../../components/ui/Button';
import { Badge, TodayDreamBadge } from '../../components/ui/Badge';
import { Field } from '../../components/ui/Field';
import { Skeleton } from '../../components/ui/Skeleton';
import { Rating } from '../../components/ui/Rating';
import { Price } from '../../components/ui/Price';
import { GoodsGrid } from '../../components/goods/GoodsGrid';
import './Showcase.css';

/**
 * DESIGN.md front matter `colors:` 정의를 그대로 옮긴 라벨 목록.
 * 값은 CSS가 아니라 이 배열(JS)에만 존재한다 — 스와치 자체는 var(--color-*)로 칠하고,
 * hex 문자열은 대조용 라벨 텍스트로만 쓴다.
 */
const COLOR_TOKENS: Array<{ token: string; varName: string; hex: string }> = [
  { token: '{colors.primary}', varName: '--color-primary', hex: '#000000' },
  { token: '{colors.on-primary}', varName: '--color-on-primary', hex: '#ffffff' },
  { token: '{colors.ink}', varName: '--color-ink', hex: '#030303' },
  { token: '{colors.ink-soft}', varName: '--color-ink-soft', hex: '#1a1a1a' },
  { token: '{colors.graphite}', varName: '--color-graphite', hex: '#404040' },
  { token: '{colors.slate}', varName: '--color-slate', hex: '#676f7b' },
  { token: '{colors.slate-soft}', varName: '--color-slate-soft', hex: '#727a85' },
  { token: '{colors.mute}', varName: '--color-mute', hex: '#6b7280' },
  { token: '{colors.stone}', varName: '--color-stone', hex: '#939393' },
  { token: '{colors.ash}', varName: '--color-ash', hex: '#999999' },
  { token: '{colors.hairline}', varName: '--color-hairline', hex: '#e7eaf0' },
  { token: '{colors.hairline-soft}', varName: '--color-hairline-soft', hex: '#c9ccd1' },
  { token: '{colors.surface-cool}', varName: '--color-surface-cool', hex: '#d0d4d4' },
  { token: '{colors.canvas}', varName: '--color-canvas', hex: '#f7f7f7' },
  { token: '{colors.surface}', varName: '--color-surface', hex: '#ebebeb' },
  { token: '{colors.canvas-warm}', varName: '--color-canvas-warm', hex: '#fefefe' },
  { token: '{colors.scrim}', varName: '--color-scrim', hex: '#1a1a1a' },
  { token: '{colors.footer}', varName: '--color-footer', hex: '#030303' },
  { token: '{colors.signal-sale}', varName: '--color-signal-sale', hex: '#b42318' },
  { token: '{colors.signal-danger}', varName: '--color-signal-danger', hex: '#b42318' },
  { token: '{colors.signal-caution}', varName: '--color-signal-caution', hex: '#8a5300' },
  { token: '{colors.signal-success}', varName: '--color-signal-success', hex: '#146c43' },
  { token: '{colors.signal-muted}', varName: '--color-signal-muted', hex: '#939393' },
];

const TYPOGRAPHY_TOKENS: Array<{ token: string; size: string; weight: string; sample: string }> = [
  { token: '{typography.display}', size: '112px', weight: '400', sample: '뷰티보이' },
  { token: '{typography.display-sm}', size: '72px', weight: '400', sample: '디스플레이 SM' },
  { token: '{typography.heading-md}', size: '36px', weight: '400', sample: '섹션 헤딩' },
  { token: '{typography.heading-sm}', size: '24px', weight: '400', sample: '카드 타이틀' },
  { token: '{typography.body}', size: '16px', weight: '400', sample: '기본 본문 텍스트입니다.' },
  { token: '{typography.body-strong}', size: '16px', weight: '600', sample: '강조 본문 텍스트입니다.' },
  { token: '{typography.eyebrow}', size: '14px', weight: '500', sample: 'EYEBROW LABEL' },
  { token: '{typography.meta}', size: '13px', weight: '400', sample: '메타 정보 텍스트' },
  { token: '{typography.micro-caps}', size: '11px', weight: '450', sample: 'MICRO CAPS' },
];

const SAMPLE_ITEM_WITH_REVIEW: GoodsListItem = {
  goodsNo: 9001,
  brandName: '어반메일',
  name: '어반메일 올인원 스킨 토너',
  thumbnailUrl: '',
  listPrice: 20000,
  salePrice: 15000,
  discountRate: 25,
  badges: ['SALE'],
  rating: 4.3,
  reviewCount: 128,
  wished: false,
  todayDreamAvailable: false,
  tags: [],
};

export function Showcase() {
  const [wishedMap, setWishedMap] = useState<Record<number, boolean>>({});

  const { data, isLoading, isError } = useQuery({
    queryKey: ['goods-showcase'],
    queryFn: () => fetchGoodsList({ page: 0, size: 40 }),
  });

  const items = useMemo<GoodsListItem[]>(() => {
    if (!data) return [];
    return data.content.map((item) => ({
      ...item,
      wished: wishedMap[item.goodsNo] ?? item.wished,
    }));
  }, [data, wishedMap]);

  const handleWishToggle = (goodsNo: number) => {
    setWishedMap((prev) => ({ ...prev, [goodsNo]: !prev[goodsNo] }));
  };

  return (
    <div className="bb-showcase">
      <header className="bb-showcase__intro">
        <span className="bb-showcase__eyebrow">DEV ONLY</span>
        <h1 className="bb-showcase__title">컴포넌트 쇼케이스</h1>
        <p className="bb-showcase__lead">
          DESIGN.md 토큰 팔레트와 UI 프리미티브·상품 카드의 모든 상태를 한 화면에서 대조한다.
          이 페이지는 <code>/dev/components</code>에서만 열리며 상용 라우트가 아니다.
        </p>
      </header>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">색 팔레트</h2>
        <div className="bb-showcase__swatch-grid">
          {COLOR_TOKENS.map(({ token, varName, hex }) => (
            <div className="bb-showcase__swatch" key={token}>
              <div
                className="bb-showcase__swatch-fill"
                style={{ background: `var(${varName})` }}
                aria-hidden="true"
              />
              <span className="bb-showcase__swatch-token">{token}</span>
              <span className="bb-showcase__swatch-hex">{hex}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">타이포그래피</h2>
        <div className="bb-showcase__type-list">
          {TYPOGRAPHY_TOKENS.map(({ token, size, weight, sample }) => (
            <div className="bb-showcase__type-row" key={token}>
              <span className="bb-showcase__type-token">
                {token} · {size} / {weight}
              </span>
              <span className="bb-showcase__type-sample">{sample}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Button — 4종 × 상태</h2>
        <div className="bb-showcase__row">
          <Button variant="primary">기본</Button>
          <Button variant="primary" loading>
            로딩 중
          </Button>
          <Button variant="primary" disabled>
            비활성
          </Button>
        </div>
        <div className="bb-showcase__row">
          <Button variant="ghost">기본</Button>
          <Button variant="ghost" loading>
            로딩 중
          </Button>
          <Button variant="ghost" disabled>
            비활성
          </Button>
        </div>
        <div className="bb-showcase__row">
          <Button variant="text-link">텍스트 링크</Button>
          <Button variant="text-link" disabled>
            비활성 링크
          </Button>
        </div>
        <div className="bb-showcase__row bb-showcase__row--dark">
          <Button variant="primary-on-dark">다크 배경 위 기본</Button>
          <Button variant="primary-on-dark" loading>
            로딩 중
          </Button>
          <Button variant="primary-on-dark" disabled>
            비활성
          </Button>
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Badge — 4종 + 오늘드림</h2>
        <div className="bb-showcase__row">
          <Badge type="SALE" />
          <Badge type="COUPON" />
          <Badge type="GIFT" />
          <Badge type="ONE_PLUS_ONE" />
          <TodayDreamBadge />
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Field — 기본 / 값 있음 / 포커스 / 에러 / 힌트</h2>
        <div className="bb-showcase__field-grid">
          <Field id="sc-field-default" label="닉네임" value="" onChange={() => {}} />
          <Field id="sc-field-value" label="닉네임" value="민수" onChange={() => {}} />
          <Field
            id="sc-field-focused"
            label="닉네임(포커스)"
            value="민수"
            onChange={() => {}}
            autoFocus
          />
          <Field
            id="sc-field-error"
            label="이메일"
            value="not-an-email"
            onChange={() => {}}
            error="이메일 형식이 아닙니다"
          />
          <Field
            id="sc-field-hint"
            label="비밀번호"
            type="password"
            value=""
            onChange={() => {}}
            hint="8자 이상, 영문+숫자 조합"
          />
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Skeleton</h2>
        <div className="bb-showcase__row">
          <Skeleton />
          <Skeleton ratio="16 / 9" className="bb-showcase__skeleton-wide" />
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Rating — 리뷰 없음 / 있음</h2>
        <div className="bb-showcase__row">
          <Rating rating={0} reviewCount={0} />
          <Rating rating={4.3} reviewCount={128} />
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">Price — 할인 없음 / 있음</h2>
        <div className="bb-showcase__row">
          <Price listPrice={15000} salePrice={15000} discountRate={0} />
          <Price
            listPrice={SAMPLE_ITEM_WITH_REVIEW.listPrice}
            salePrice={SAMPLE_ITEM_WITH_REVIEW.salePrice}
            discountRate={SAMPLE_ITEM_WITH_REVIEW.discountRate}
          />
        </div>
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">상품 카드 그리드 — mock 40건</h2>
        {isError && <p className="bb-showcase__lead">mock 서버 요청이 실패했습니다.</p>}
        <GoodsGrid items={items} loading={isLoading} onWishToggle={handleWishToggle} />
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">스켈레톤 그리드</h2>
        <GoodsGrid items={[]} loading skeletonCount={5} />
      </section>

      <section className="bb-showcase__section">
        <h2 className="bb-showcase__section-title">빈 상태</h2>
        <GoodsGrid items={[]} />
      </section>
    </div>
  );
}
