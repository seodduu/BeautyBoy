---
version: alpha
name: Runwai-design-analysis
description: An inspired interpretation of Runwai's design language — an editorial, gallery-grade marketing system for an AI creative-tools company. Cinematic photographic heroes give way to crisp white reading surfaces, a tight monochrome neutral ladder, and a single proprietary sans (abcNormal) carrying every level of the hierarchy. The system reads like a film festival programme more than a SaaS site: black ink on paper-white, generous air, hairline dividers, and reserved use of restrained slate-blue for secondary text. Pure black solid pills serve every primary action, with no accent colour competing for attention.

colors:
  primary: "#000000"
  on-primary: "#ffffff"
  ink: "#030303"
  ink-soft: "#1a1a1a"
  graphite: "#404040"
  slate: "#676f7b"
  slate-soft: "#727a85"
  mute: "#6b7280"
  stone: "#939393"
  ash: "#999999"
  # ash보다 한 단계 밝다. 검정 배경 위 보조 카피 전용 —
  # 검정 위에서 ash(#999)를 쓰면 디스플레이 타이포와 위계 차이가 과하게 벌어진다.
  ash-soft: "#b0b0b0"
  hairline: "#e7eaf0"
  hairline-soft: "#c9ccd1"
  surface-cool: "#d0d4d4"
  # 캔버스는 순백이 아니라 오프화이트다 — 그래야 그 위에 놓인 surface(카드·패널)가
  # 테두리나 그림자 없이 면의 명도차만으로 분리된다. 순백 위에서는 그 차이가 안 보인다.
  canvas: "#f7f7f7"
  surface: "#ebebeb"          # 카드·패널 면. canvas보다 한 단계 어둡다
  canvas-warm: "#fefefe"
  scrim: "#1a1a1a"
  footer: "#030303"
  # --- 커머스 확장 (뷰티보이) ---
  # 원본 Runwai 시스템은 시그널 색을 의도적으로 배제했다. 커머스에서는 그럴 수 없다 —
  # 할인율·품절·성분 경고는 "읽어서 아는 것"이 아니라 "훑어서 아는 것"이어야 한다.
  # 대신 개수를 최소로 묶고 전부 저채도 잉크톤으로 낮춰 편집디자인 톤을 깨지 않는다.
  signal-sale: "#b42318"      # 할인율, SALE 배지 — 유일하게 시선을 끄는 색
  signal-danger: "#b42318"    # 폼 에러, 성분 CONFLICT (sale과 같은 값을 의도적으로 공유)
  signal-caution: "#8a5300"   # 성분 CAUTION, 재고 임박
  signal-success: "#146c43"   # 성분 SYNERGY, 주문 완료, 오늘드림 가능
  signal-muted: "#939393"     # 품절 — stone과 동일값, 의미 이름으로 별도 노출

typography:
  # 디스플레이는 의도적으로 매우 크다. 이 시스템의 인상은 "거대한 제목 ↔ 작은 라벨"의
  # 스케일 대비에서 나온다 — 중간 크기를 늘리는 대신 양 끝을 벌린다.
  # 아래 px는 데스크톱 상한이며, 실제 렌더는 뷰포트에 따라 clamp로 줄어든다.
  display-hero:
    # 랜딩 워드마크 전용. 화면 하나를 통째로 쓰는 자리에만 허용하고 본문 흐름에는 쓰지 않는다.
    fontFamily: abcNormal
    fontSize: 272px
    fontWeight: 400
    lineHeight: 0.92
    letterSpacing: -1.2px
  display:
    fontFamily: abcNormal
    fontSize: 112px
    fontWeight: 400
    lineHeight: 1
    letterSpacing: -1.2px
  display-sm:
    fontFamily: abcNormal
    fontSize: 72px
    fontWeight: 400
    lineHeight: 1
    letterSpacing: -1px
  heading-md:
    fontFamily: abcNormal
    fontSize: 36px
    fontWeight: 400
    lineHeight: 1
    letterSpacing: -0.9px
  heading-sm:
    fontFamily: abcNormal
    fontSize: 24px
    fontWeight: 400
    lineHeight: 1
  subtitle:
    fontFamily: abcNormal
    fontSize: 20px
    fontWeight: 400
    lineHeight: 1
  nav-link:
    # 헤더 내비 전용. body(16px)는 헤더에서 작고 subtitle(20px)은 본문 위계를 침범한다.
    fontFamily: abcNormal
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.4
  body-lg:
    # body(16px)와 subtitle(20px) 사이. 히어로 보조 카피처럼 본문보다 한 단계 큰 자리.
    fontFamily: abcNormal
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.7
  body:
    fontFamily: abcNormal
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
  body-strong:
    fontFamily: abcNormal
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.5
  body-tight:
    fontFamily: abcNormal
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: -0.16px
  link-sm:
    fontFamily: abcNormal
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.43
  meta:
    fontFamily: abcNormal
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: -0.26px
  eyebrow:
    fontFamily: abcNormal
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: 0.35px
  micro-caps:
    fontFamily: abcNormal
    fontSize: 11px
    fontWeight: 450
    lineHeight: 1.3
    letterSpacing: 0.2px
  button:
    fontFamily: abcNormal
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.43

rounded:
  none: 0px
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px        # 카드·패널 면의 기본 라운딩
  xl: 16px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 64px
  section-lg: 96px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: 12px
    height: 40px
  button-primary-on-dark:
    backgroundColor: "{colors.on-primary}"
    textColor: "{colors.primary}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: 12px
    height: 40px
  button-ghost:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: 12px
    height: 40px
  button-text-link:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.link-sm}"
    rounded: "{rounded.xs}"
    padding: 4px
  nav-bar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.link-sm}"
    height: 64px
    padding: 24px
  nav-link:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-soft}"
    typography: "{typography.link-sm}"
    padding: 8px
  pricing-card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 24px
    width: 224px
  pricing-card-featured:
    backgroundColor: "{colors.hairline}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 24px
    width: 224px
  pricing-tier-name:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.heading-md}"
  pricing-amount:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.display}"
  research-card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: 16px
  media-thumbnail:
    backgroundColor: "{colors.surface-cool}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  hero-photo:
    backgroundColor: "{colors.scrim}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.lg}"
    padding: 48px
  studios-tile:
    backgroundColor: "{colors.canvas-warm}"
    textColor: "{colors.ink}"
    typography: "{typography.body-tight}"
    rounded: "{rounded.md}"
    padding: 16px
  studios-tag:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.slate}"
    typography: "{typography.micro-caps}"
    rounded: "{rounded.full}"
    padding: 6px
  form-field:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 12px
  form-field-focused:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 12px
  alert-banner:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-tight}"
    rounded: "{rounded.lg}"
    padding: 16px
  footer:
    backgroundColor: "{colors.footer}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body}"
    padding: 64px
  footer-link:
    backgroundColor: "{colors.footer}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body}"
  footer-eyebrow:
    backgroundColor: "{colors.footer}"
    textColor: "{colors.stone}"
    typography: "{typography.eyebrow}"
  # --- 커머스 확장 (뷰티보이) ---
  goods-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body-tight}"
    rounded: "{rounded.lg}"
    padding: "{spacing.sm}"
  goods-thumbnail:
    backgroundColor: "{colors.surface-cool}"
    rounded: "{rounded.md}"
  badge-sale:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.signal-sale}"
    typography: "{typography.micro-caps}"
    rounded: "{rounded.none}"
    padding: 4px
  badge-neutral:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.graphite}"
    typography: "{typography.micro-caps}"
    rounded: "{rounded.none}"
    padding: 4px
  badge-today-dream:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.signal-success}"
    typography: "{typography.micro-caps}"
    rounded: "{rounded.none}"
    padding: 4px
  price-discount-rate:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.signal-sale}"
    typography: "{typography.body-strong}"
  price-sale:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-strong}"
  price-list-struck:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ash}"
    typography: "{typography.meta}"
  form-field-error:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 12px
  # --- 루틴 조합기 (메인 개인화 v2) ---
  pick-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body-tight}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  pick-card-eyebrow:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.stone}"
    typography: "{typography.eyebrow}"
  pick-card-name:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.heading-sm}"
  pick-card-reason:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body-strong}"
  pick-card-soldout:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.signal-muted}"
    typography: "{typography.meta}"
  routine-bulk-cta:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: 12px
    height: 48px
---

## Overview

Runwai treats its marketing site as a curatorial space — closer in feeling to the programme guide of a film festival than to a typical AI-tooling site. Photography sets the temperature: cinematic, atmospheric stills (a forest at dusk, a lone figure under an indigo night sky) anchor full-bleed hero modules in `{colors.scrim}`, while the rest of the document drops onto pure `{colors.canvas}` for unbroken reading. The colour story is restraint to the point of austerity — black ink on paper-white, with five tiers of grey carrying every nuance from caption to divider, and a single slate-blue (`{colors.slate-soft}` / `{colors.slate}`) reserved for secondary text on rare occasions.

Typography does almost all of the heavy lifting. A single proprietary sans, `abcNormal`, carries every level from 11px micro-caps to 48px editorial display, with negative letter-spacing on every heading (`-0.9px` to `-1.2px`) tightening the headline silhouette into something that reads as deliberate and quiet rather than punchy. There is no decorative ornament, no card glow, no gradient buttons — every primary action is a black solid pill (`{colors.primary}` background, `{colors.on-primary}` text, `{rounded.full}` corners), reused with absolute consistency across hero CTAs, pricing subscriptions, and form submissions.

The layout discipline is editorial: hairline dividers (`{colors.hairline}`), uppercase eyebrows (`{typography.eyebrow}`), and an 8-px spacing grid that resolves to large 64–96px section gutters. Sections cycle through a tight rhythm — dark photographic hero → white reading band → research grid on canvas → photographic full-width interlude → dark CTA strip → black footer — letting black ink and black-and-white photography do the dramatic work that other sites delegate to colour.

**Key Characteristics:**
- Cinematic dark photographic heroes (`{colors.scrim}` over editorial stills) bookending crisp `{colors.canvas}` reading bands
- A single proprietary sans (`abcNormal`) covering every typographic role, with tight negative tracking on display sizes
- Black-only primary action language: every CTA is `{button-primary}` (`{colors.primary}` pill with `{rounded.full}` corners and 14px/600 button text)
- Five-tier neutral ladder (`{colors.ink}` → `{colors.graphite}` → `{colors.slate}` → `{colors.stone}` → `{colors.hairline}`) carries the entire UI without accent colour
- 5-column pricing grid where the featured tier is signalled by a `{colors.hairline}` infill rather than a coloured border
- Hairline dividers and uppercase `{typography.eyebrow}` lock-ups give marketing sections an editorial, exhibition-catalogue cadence
- Photography is treated as content, not decoration — full-bleed, cinematic, and tonal rather than vivid

## Colors

### Brand & Accent
- **Black** (`{colors.primary}`): The single brand action colour. Every primary CTA, every pricing-tier subscription button, every form submit pill resolves to this exact black. Used as the footer canvas as well, which extends the brand voice through the bottom of every page.
- **Paper White** (`{colors.on-primary}`): Type colour on `{colors.primary}` surfaces; canvas of every reading section.

### Surface
- **Canvas** (`{colors.canvas}`): Primary reading-page background.
- **Canvas Warm** (`{colors.canvas-warm}`): Near-imperceptible off-white used to lift studios-page tiles a half-tone above pure white without losing the paper feel.
- **Featured Surface** (`{colors.hairline}`): The infill behind the featured pricing tier ("Pro") and behind certain table-style banners — chosen for its near-zero saturation so it reads as a tonal step rather than a fill.
- **Hairline Soft** (`{colors.hairline-soft}`): 1-pixel column dividers in the pricing grid and table separators.
- **Cool Surface** (`{colors.surface-cool}`): Default placeholder fill for media thumbnails and image-loading frames before the asset paints.
- **Scrim** (`{colors.scrim}`): The atmospheric dark layer that cinematic hero photography is laid into; behaves as the "stage" colour for full-bleed image modules.
- **Footer** (`{colors.footer}`): Near-pure black footer canvas, one notch warmer than `{colors.primary}` so it sits visually distinct when the two stack.

### Text
- **Ink** (`{colors.ink}`): Primary heading and body text on `{colors.canvas}`; closest the system gets to absolute black for type.
- **Ink Soft** (`{colors.ink-soft}`): Nav links, secondary headings, body emphasis — one click softer than ink.
- **Graphite** (`{colors.graphite}`): Standard body copy across marketing sections, balancing readability with calm.
- **Slate** (`{colors.slate}`) / **Slate Soft** (`{colors.slate-soft}`): The system's only tinted neutrals — barely-blue greys reserved for tertiary metadata, footer-section headings on dark, and small-caps labels.
- **Mute** (`{colors.mute}`): Lighter neutral for inline disabled or fine-print copy.
- **Stone** (`{colors.stone}`): Footer eyebrow caps and field placeholders.
- **Ash** (`{colors.ash}`): The lightest readable neutral — captions on tiles, pricing fine-print.

### Semantic (뷰티보이 커머스 확장)

원본 Runwai 시스템은 시그널 색을 전면 배제했다. **커머스에서는 그 규칙을 그대로 지킬 수 없다** —
할인율·품절·성분 경고는 문장을 읽어서 아는 정보가 아니라 스캔하면서 아는 정보여야 하고,
무채색만으로는 상품 카드 수십 개가 깔린 목록에서 그 층위가 사라진다.

대신 **개수를 5개로 묶고, 전부 저채도 잉크톤으로 낮춰** 편집디자인 톤을 깨지 않는다.

| 토큰 | 용도 | 규칙 |
|---|---|---|
| `{colors.signal-sale}` | 할인율 수치, SALE 배지 | **한 화면에서 시선을 끄는 유일한 색.** 상품 카드에서 이 색이 칠해지는 것은 할인율 하나뿐 |
| `{colors.signal-danger}` | 폼 검증 실패, 성분 CONFLICT 경고 | `signal-sale`과 같은 값을 공유한다 — 팔레트를 늘리지 않기 위한 의도적 중복 |
| `{colors.signal-caution}` | 성분 CAUTION, 재고 임박 | 배경 채움 금지, 글자·아이콘·1px 테두리로만 |
| `{colors.signal-success}` | 성분 SYNERGY, 오늘드림 가능, 주문 완료 | 동일 |
| `{colors.signal-muted}` | 품절, 비활성 | `{colors.stone}`과 같은 값. 품절은 색이 아니라 **딤 + 취소선**이 주 신호다 |

**시그널 색 사용 규칙:**
- **배경으로 칠하지 않는다.** 시그널은 글자색·아이콘·1px 테두리로만 표현한다. 빨간 배경의 SALE 딱지는
  이 시스템에서 가장 이질적인 요소가 된다 — 배지는 `{badge-sale}`처럼 흰 바탕 + 색 글자다.
- **한 뷰포트에 시그널 색 종류는 2개까지.** 상품 목록이면 할인율(sale) 하나로 끝나야 한다.
- **색 단독으로 의미를 전달하지 않는다.** 품절은 색 + 취소선 + "품절" 텍스트, 성분 경고는
  색 + 아이콘 + 사유 문장을 함께 낸다 (색각 이상 사용자에게 색은 없는 것과 같다).
- 폼 검증 실패는 `{colors.signal-danger}` 밑줄 + 같은 색 헬퍼 텍스트. 필드 배경은 바뀌지 않는다.

프로모션 배지 4종 중 **SALE만 색을 갖는다.** COUPON·GIFT·1+1은 `{badge-neutral}`(graphite 글자)로
두어, 배지가 3개 이상 붙은 카드에서도 시선이 한 곳에만 모이게 한다.

### 태그 컬러 (뷰티보이) — 무채색 규칙의 유일한 예외

**이 절이 태그 pill 색의 유일한 출처다.** 아래 표 밖의 색을 태그에 임의로 만들지 않는다 —
새 slug가 추가되면 이 표에 먼저 hex를 적고 나서 코드(`Tag.css`)로 옮긴다.

원본 Runwai 시스템과 위 "Do's and Don'ts"의 "배경으로 칠하지 않는다·`signal-*` 5종 밖 액센트
금지" 규칙은 **태그 pill에 한해 사용자 지시로 해제한다.** 상품 태그는 종류가 18종까지 늘고
상세 화면에 한 상품당 여러 개가 동시에 나열되므로, 무채색 하나로는 모공·트러블·보습·항산화 같은
서로 다른 효과가 시각적으로 구분되지 않는다. 태그 pill은 **틴트 배경 + 같은 계열의 진한 글자**로
렌더한다(카드·배지·버튼 등 다른 모든 컴포넌트는 기존 무채색·배경-금지 규칙을 그대로 따른다).

| slug | bg | text | | slug | bg | text |
|---|---|---|---|---|---|---|
| moisture | `#E0F2FE` | `#0369A1` | | anti-aging | `#FAE8FF` | `#86198F` |
| soothe | `#DCFCE7` | `#15803D` | | firm | `#FFE4E6` | `#BE123C` |
| cleanse | `#DBEAFE` | `#1D4ED8` | | antioxidant | `#FEF3C7` | `#B45309` |
| exfoliate | `#FFEDD5` | `#C2410C` | | barrier | `#FEF9C3` | `#A16207` |
| sebum | `#CCFBF1` | `#0F766E` | | scalp | `#F5F5F4` | `#57534E` |
| uv | `#FEF08A` | `#854D0E` | | pore | `#CFFAFE` | `#0E7490` |
| bright | `#EDE9FE` | `#6D28D9` | | trouble | `#FEE2E2` | `#B91C1C` |
| gentle | `#FCE7F3` | `#BE185D` | | | | |

**TEXTURE 3종 (사용감)**

| slug | bg | text |
|---|---|---|
| `fresh` 산뜻함 | `#ECFEFF` | `#155E75` |
| `dewy` 촉촉함 | `#EFF6FF` | `#1E40AF` |
| `matte` 매트 | `#F1F5F9` | `#334155` |

저채도 3종(청록·파랑·회청)으로 묶어 효과 태그의 다채로움과 축이 다름을 유지한다. 효과 태그
일부와 색상환에서 가까우나 두 그룹은 항상 별도 `fieldset`에 있어 나란히 놓이지 않는다.

- `gentle`(저자극)은 `kind: 'PROPERTY'`이지만 색 배정은 slug 기준이다 — kind가 아니라 slug로
  클래스를 매핑하므로 PROPERTY도 여기 정의된 색을 그대로 받는다(무채색 폴백이 아니다).
- 표에 없는 slug(향후 추가된 태그가 아직 색이 배정되지 않은 경우)는 **무채색 폴백**
  (`{colors.graphite}` 글자, 배경 없음)으로 렌더한다 — 팔레트가 없다고 렌더가 깨지면 안 된다.

### 피부 프로필 색 배정

피부타입 4종은 태그가 아니라 위 팔레트에 행이 없다. 신호 매핑(설계 §6.2)의 **대표 태그에서
색을 빌린다** — 색이 곧 "이 선택이 어떤 추천으로 이어지는지"를 보여준다.

| 피부타입 | 대표 태그 | bg | text |
|---|---|---|---|
| DRY 건성 | `moisture` | `#E0F2FE` | `#0369A1` |
| OILY 지성 | `sebum` | `#CCFBF1` | `#0F766E` |
| COMBINATION 복합성 | `barrier` | `#FEF9C3` | `#854D0E` |
| SENSITIVE 민감성 | `gentle` | `#FCE7F3` | `#BE185D` |

COMBINATION의 신호 태그는 `[moisture, sebum]`이지만 그 두 색은 DRY·OILY가 이미 쓰므로 색만
`barrier`에서 빌린다 — 네 카드가 서로 구분되는 것이 우선이다. text는 `barrier`의 미선택 글자색
(`#A16207`)이 아니라 아래 "선택 상태" 절의 반전 보정값(`#854D0E`)을 쓴다 — 카드는 이 색을
좌측 바·테두리·제목에 함께 쓰므로 더 진한 쪽으로 통일한다.

### 선택 상태 — 색 반전

미선택에도 색이 있으므로 "색 유무"로는 선택을 표현할 수 없다. **반전**으로 표현한다.

| 요소 | 미선택 | 선택 |
|---|---|---|
| 고민·사용감 칩 | 틴트 배경(`bg`) + 같은 계열 진한 글자(`text`) | 배경 = `text` 색, 글자 = 흰색, 앞에 `✓` |
| 피부타입 카드 | 흰 배경 + 좌측 3px 컬러 바(`text` 색) | 틴트 배경(`bg`) + 2px 테두리(`text` 색) + 제목이 `text` 색 + `✓` |

카드는 면적이 넓어 칩과 같은 채도로 채우면 화면이 요란해진다 — 한 단계 절제한다.

**반전 배경 대비 보정** — 흰 글자 대비 4.5:1(WCAG AA) 미달 2종만 한 단계 진한 값을 쓴다.
나머지는 `text` 색을 그대로 반전 배경에 쓴다.

| slug | `text` | 반전 배경 | 사유 |
|---|---|---|---|
| `barrier` | `#A16207` | `#854D0E` | `#A16207` 위 흰 글자 ≈ 4.05:1 |
| `soothe` | `#15803D` | `#166534` | `#15803D` 위 흰 글자 ≈ 4.35:1 |

색은 상태의 유일한 단서가 아니다 — `✓`와 `aria-pressed`에 얹히는 보강 단서다.

## Typography

### Font Family
The entire system runs on a single proprietary sans, **abcNormal**, with `abcNormal Fallback` declared as the substitute. It is a humanist neo-grotesque in the lineage of ABC Diatype — uniform stroke contrast, flat terminals, slightly compressed counters, and a confident lowercase that suits Runwai's all-lowercase wordmark. The face is used at every level; there is no second display font, no monospace, no italic specimen across marketing pages.

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.display}` | 48px | 400 | 1.0 | -1.2px | Page-level editorial display ("Runwai Pricing", "Looking to get in touch?") |
| `{typography.display-sm}` | 40px | 400 | 1.0 | -1px | Pricing tier amount, hero secondary headlines |
| `{typography.heading-md}` | 36px | 400 | 1.0 | -0.9px | Section headlines ("Our latest Research and Products"), tier names |
| `{typography.heading-sm}` | 24px | 400 | 1.0 | 0 | Card titles, sub-section heads, link text in featured cards |
| `{typography.subtitle}` | 20px | 400 | 1.0 | 0 | Hero sub-copy and lead paragraphs |
| `{typography.body}` | 16px | 400 | 1.5 | 0 | Default body copy, form fields, footer link list |
| `{typography.body-strong}` | 16px | 600 | 1.5 | 0 | Inline emphasis, "Get Started"-class label text |
| `{typography.body-tight}` | 16px | 400 | 1.3 | -0.16px | Tight-leading body for marketing cards and CTA cards |
| `{typography.link-sm}` | 14px | 600 | 1.43 | 0 | Nav links, button labels, "Learn More" text links |
| `{typography.eyebrow}` | 14px | 500 | 1.43 | 0.35px | Uppercase eyebrows above section headings |
| `{typography.meta}` | 13px | 400 | 1.3 | -0.26px | Tertiary metadata (dates, fine print, table footnotes) |
| `{typography.micro-caps}` | 11px | 450 | 1.3 | 0.2px | Footer column headings, small-caps tags ("PRESS", "RESOURCES") |
| `{typography.button}` | 14px | 600 | 1.43 | 0 | Every button label across the system |

### Principles
- **One face, every level.** Hierarchy is articulated through size, weight, and tracking — never through a contrasting display family. The result is a uniform editorial cadence that reads as confident rather than expressive.
- **Negative tracking on display, neutral tracking on body.** Headings 24–48px sit at -0.9 to -1.2px to tighten silhouettes; body copy stays at 0 for legibility.
- **Tight leading on display, generous leading on body.** Display sizes lock to `line-height: 1.0`; body relaxes to `1.5`. The contrast gives sections a clear "headline-then-paragraph" rhythm.
- **Uppercase reserved for two roles.** `{typography.eyebrow}` for section labels, `{typography.micro-caps}` for footer columns and small tags. Body copy is never set in uppercase.
- **본문 한 줄 길이는 45–75자**로 잡는다(한글은 25–40자). 리딩 밴드가 컨테이너 폭(~1280px)을 꽉 채우면
  줄이 너무 길어 눈이 다음 줄을 놓친다 — 긴 본문 블록은 `max-width`로 가둔다.
- **본문 최소 크기는 `{typography.body}`(16px).** 모바일에서 16px 미만은 iOS가 입력 시 확대해 레이아웃이
  튀고, 가독성도 떨어진다. 13px·11px 토큰은 메타/캡션 전용이며 본문에 쓰지 않는다.

### Note on Font Substitutes
If `abcNormal` is unavailable, the closest open-source substitutes are **ABC Diatype** (commercial) or **Inter** at -0.02em tracking on display sizes. When using Inter, lift display sizes by ~1px and pull `letter-spacing` slightly tighter (-1.4px at 48px) to recover the compressed silhouette of the original.

### 한글 적용 (뷰티보이)

`abcNormal`은 독점 서체이고 **한글 글리프가 없다.** Inter도 마찬가지다. 이 프로젝트는 본문이 한국어이므로
아래 스택을 쓴다 — 라틴은 Inter, 한글은 Pretendard가 받는다.

```css
--font-body: Inter, 'Pretendard Variable', Pretendard, -apple-system,
             'Apple SD Gothic Neo', system-ui, sans-serif;
```

**한글에 적용할 때 원본 사양에서 조정할 것:**

- **음수 자간을 그대로 쓰지 않는다.** 48px에서 `-1.2px`는 라틴 대문자 실루엣을 조이는 값이고,
  한글은 같은 값에서 자소가 서로 붙어 가독성이 떨어진다. 디스플레이 크기는 **`-0.02em` 상당까지만**
  적용하고 본문(16px 이하)은 `0`으로 둔다.
- **`line-height: 1.0`을 한글 제목에 쓰지 않는다.** 한글은 라틴보다 글자틀이 높아 1.0에서 상하가 잘린다.
  디스플레이·헤딩은 **1.15~1.25**로 올린다. 원본의 "타이트한 리딩" 의도는 이 범위에서도 유지된다.
- **`word-break: keep-all`을 전역으로 건다.** 없으면 한국어가 어절 중간에서 끊긴다
  (Wave 0에서 "관리할 시 / 간"으로 깨진 원인이 정확히 이것이다). 긴 URL·영문 토큰 대비로
  `overflow-wrap: anywhere`를 함께 준다.
- 대문자 사용 규칙(`eyebrow`, `micro-caps`)은 **라틴 문자에만** 적용한다. 한글에는 대문자가 없으므로
  해당 자리는 영문 레이블(`SKINCARE`, `RANKING`)을 쓰거나 자간만 벌린 한글로 대체한다.
- **영문/한글 혼용 규칙 — 영문은 두 자리에만 허용한다.** ① 섹션 아이브로우·타이틀 장식
  (`TODAY'S PICK`, `STEP 01`, `DAILY ROUTINE` — `eyebrow`/`micro-caps` 타이포가 걸리는 자리),
  ② 배지·워드마크(`SALE`, `COUPON`, `GIFT`, `BEAUTY BOY.`). 그 밖의 UI 문구(버튼, 본문, 안내,
  placeholder, 폼 라벨)는 전부 한글이다. 이 두 자리 밖에서 영문 라벨이 필요해 보이면
  이 규칙에 먼저 추가하고 쓴다.

## Layout

### Spacing System
- **Base unit**: 8px (with 4px and 6px micro-steps for inline element gaps).
- **Tokens (front matter)**: `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 16px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 64px · `{spacing.section-lg}` 96px.
- Card internal padding sits at `{spacing.lg}` (24px). Section vertical rhythm alternates between `{spacing.section}` (64px) for tight reading bands and `{spacing.section-lg}` (96px) for editorial breaks between major modules. Inline button padding is `{spacing.sm}` vertical / `{spacing.lg}` horizontal.

### Grid & Container
- Marketing pages render inside a centred container that caps near 1280px on widescreen breakpoints; the document maintains generous left/right gutters (~`{spacing.xxl}`) at every breakpoint above 1024px.
- The pricing surface is a 5-column equal-width grid (Free / Standard / Pro / Unlimited / Enterprise) on widescreen; each column is a vertical strip separated by 1-pixel `{colors.hairline-soft}` rules rather than gaps.
- Research/products listings use a 12-column underlying grid where each row presents a 5/7 split: media thumbnail on the left (5 columns), aligned text block on the right (7 columns).
- Studios pages break the discipline deliberately: a dense, irregular masonry of editorial poster tiles, captioned in `{typography.body-tight}`, with no consistent column count — the page is meant to read as a programme grid.

### Whitespace Philosophy
Whitespace at Runwai is structural, not decorative. Sections are separated by 64–96px verticals; cards inside a section are separated by 16–24px gaps. There are no card shadows or coloured surfaces standing in for layout — `{colors.canvas}` carries through, and rhythm comes from line-height and section spacing alone. The studios pages are the exception; their dense poster grids feel almost cluttered by contrast, which is the point — they read like a printed catalogue.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| Flat | No shadow, optional 1px `{colors.hairline}` divider | Default state for cards, pricing columns, research rows, footer surfaces |
| Photographic | Full-bleed image laid into `{colors.scrim}`, no border, `{rounded.lg}` corners on contained variants | Hero modules, "We are building foundational simulation World Models" interlude, mid-page CTA panels |
| Subtle Surface Lift | `{colors.hairline}` infill behind a card on a `{colors.canvas}` page | The featured pricing tier ("Pro") — the only "elevation" cue in the entire pricing module |

The system avoids drop shadows entirely. Depth is created by photographic layering and tonal surface shifts, never by blurred shadows. This is a deliberate aesthetic choice — Runwai communicates polish through editorial restraint, not material affordance.

### Decorative Depth
- **Cinematic photography as backdrop.** The hero on the homepage uses an indigo night-sky photograph; the mid-page interlude uses a fog-and-trees forest scene rendered into `{colors.scrim}`. Both function as atmospheric surfaces that the next white reading band breaks against, creating a perceived "stage" depth without any CSS effect.
- **Tonal surface stepping.** Pricing's featured-tier infill (`{colors.hairline}` against `{colors.canvas}`) is the system's quietest possible "this one is special" cue — perceptible, never loud.

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.none}` | 0px | Pricing-grid cells, table rows, form fields, footer link blocks |
| `{rounded.xs}` | 4px | Small inline accents, focus rings, secondary link chips |
| `{rounded.sm}` | 6px | Tag chips, secondary link buttons |
| `{rounded.md}` | 8px | Research-card thumbnails, studios poster tiles, media containers |
| `{rounded.lg}` | 16px | Alert banners, hero-photograph containers, full-bleed CTA panels |
| `{rounded.full}` | 9999px | Every primary button (CTA pills), studios tag pills |

### Photography Geometry
- **Hero stills** are full-bleed, no rounding — they extend to the page edges to feel cinematic rather than card-like.
- **Contained hero panels** (mid-page interludes) take `{rounded.lg}` corners, signalling "module" rather than "page".
- **Research thumbnails** are 16:9 with `{rounded.md}` corners and a `{colors.surface-cool}` placeholder fill.
- **Studios poster tiles** vary in aspect ratio (square, 4:5, landscape) and use `{rounded.md}` corners; the deliberate aspect-ratio inconsistency is what gives the studios grid its programme-catalogue feel.
- **Avatar/logo lockups** in the partner row are rendered without rounding, in flat black wordmarks on `{colors.canvas}`, evenly spaced.

## Components

### Buttons

**`button-primary`** — every primary CTA across the marketing surface ("Try Runwai", "Get Started", "Subscribe Now", "Send Message", "Learn More" filled variant)
- Background `{colors.primary}`, text `{colors.on-primary}`, type `{typography.button}`, padding `{spacing.sm}` × `{spacing.lg}`, rounded `{rounded.full}`, height 40px.
- The system uses the same pill at every scale; no large/small distinction.

**`button-primary-on-dark`** — the inverse used when the surface itself is `{colors.scrim}` (dark hero CTAs)
- Background `{colors.on-primary}`, text `{colors.primary}`, otherwise identical token set to `{button-primary}`.

**`button-ghost`** — secondary actions on light surfaces ("Schedule a Demo", "Sign Up" on the Free tier)
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.button}`, rounded `{rounded.full}`, with a 1px `{colors.ink}` border.

**`button-text-link`** — inline secondary actions, table-row "Subscribe Now" labels, and "View More" links
- Background `{colors.canvas}`, text `{colors.ink}`, underline-on-active, type `{typography.link-sm}`.

### Navigation

**`nav-bar`** — the persistent top bar
- Background `{colors.canvas}`, height ~64px, padding `{spacing.lg}` horizontal, `{typography.link-sm}` for menu items.
- Layout: lowercase `runwai` wordmark left → centred 5-item primary menu (Research, Product, Resources, Solutions, Company) → right cluster (`Enterprise Sales` text link, `Log In` text link, `Try Runwai` `{button-primary}` pill).
- The bar sits flush against the document top and is divided from the page only by spacing, not by a hairline.

**`nav-link`** — top-bar menu items
- Background `{colors.canvas}`, text `{colors.ink-soft}`, type `{typography.link-sm}`, padding `{spacing.xs}` vertical.

### Cards & Containers

**`pricing-card`** — every standard tier (Free, Standard, Unlimited, Enterprise)
- Background `{colors.canvas}`, text `{colors.ink}`, padding `{spacing.lg}`, no rounding, separated from neighbouring tiers by 1px `{colors.hairline-soft}` column rules.
- Internal stack: tier name (`{typography.heading-md}`) → one-line description (`{typography.body}` in `{colors.graphite}`) → amount (`{typography.display-sm}`) → unit caption (`{typography.meta}` in `{colors.stone}`) → action button (`{button-primary}` for paid tiers, `{button-ghost}` for Free) → feature list (`{typography.body}` bullets).

**`pricing-card-featured`** — the "Pro" tier
- Identical structure to `{pricing-card}` but the column infill is `{colors.hairline}` instead of `{colors.canvas}`. No coloured border, no badge, no shadow — just the surface-step.

**`pricing-tier-name`** — header line of each pricing column
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.heading-md}` set in title-case ("Free", "Standard", "Pro").

**`pricing-amount`** — large monetary display in each pricing card
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.display}` paired with a `{typography.meta}` "per user/month" caption beside it.

**`research-card`** — each row of "Our latest Research and Products"
- Layout: `{media-thumbnail}` left (16:9) + text block right.
- Right block: title (`{typography.heading-sm}`) → description (`{typography.body}` in `{colors.graphite}`) → footer link (`{typography.link-sm}`, underlined on active).

**`studios-tile`** — poster cards on the studios index
- Background `{colors.canvas-warm}`, image fills the tile, optional caption strip below in `{typography.body-tight}` (`{colors.graphite}`).
- Tiles are deliberately heterogeneous in aspect ratio.

**`studios-tag`** — small-caps category pills on studios cards
- Background `{colors.canvas}`, text `{colors.slate}`, type `{typography.micro-caps}`, padding `{spacing.xxs}` × `{spacing.sm}`, rounded `{rounded.full}`.

**`hero-photo`** — full-bleed cinematic hero blocks
- `{colors.scrim}` background carrying a photographic still, padding `{spacing.xxl}`, rounded `{rounded.lg}` on contained variants and `{rounded.none}` on edge-to-edge variants.
- Internal stack: optional eyebrow (`{typography.eyebrow}` in `{colors.on-primary}` at 70% opacity) → display headline (`{typography.display}` in `{colors.on-primary}`) → optional sub-copy (`{typography.subtitle}` in `{colors.on-primary}`) → `{button-primary-on-dark}` CTA.

**`media-thumbnail`** — image placeholder
- Background `{colors.surface-cool}`, rounded `{rounded.md}`, ratio 16:9 by default, image lazy-loads on top.

### Inputs & Forms

**`form-field`** — every contact-form input (select, text, textarea)
- Background `{colors.canvas}`, text `{colors.ink}`, label above field in `{typography.body}` `{colors.ink}`, helper text in `{typography.meta}` `{colors.stone}`.
- The field itself is a 1px bottom rule in `{colors.hairline-soft}` (no full-border box) — placeholder ("Type your full name") sits in `{colors.stone}`.
- Padding `{spacing.sm}` vertical, no rounding.

**`form-field-focused`** — focused state
- Bottom rule deepens to `{colors.ink}`. No glow, no colour shift on the field background.

**폼 규칙 (뷰티보이)** — 회원가입·로그인·배송지·결제 폼이 전부 따른다.

- **모든 입력에 보이는 라벨을 둔다.** placeholder는 라벨을 대체하지 않는다 — 값을 입력하면 사라져
  맥락이 증발한다. 라벨은 필드 위 `{typography.body}` `{colors.ink}`, 힌트는 아래 `{typography.meta}` `{colors.stone}`.
  라벨은 `for`로 필드와 연결하거나 필드를 감싼다.
- **에러는 관련 필드 바로 아래**에 `{colors.signal-danger}` 헬퍼 텍스트로 낸다. 필드 자체는
  `{form-field-error}`로 하단 rule만 `{colors.signal-danger}`가 되고 **배경은 바뀌지 않는다**.
  에러 컨테이너에 `role="alert"`(제출 시 요약) 또는 `aria-live="polite"`(인라인)를 건다.
- **검증은 blur 시점에.** 타이핑 중 매 글자마다 빨갛게 하지 않는다. 제출 실패 후에는 재입력 시 즉시 재검증한다.
- **필수 필드는 명시**한다(라벨에 `*` + `aria-required`). 색만으로 필수를 표시하지 않는다.
- **비밀번호는 표시/숨김 토글**을 준다(`{button-text-link}` 계열, `aria-pressed`로 상태).
- **입력 타입·키보드를 맞춘다.** 이메일·전화·숫자는 `type`과 `inputmode`를 지정해 모바일에서 알맞은
  키보드가 뜨게 하고, `autocomplete`를 채운다(이름·주소·카드).
- **제출 피드백**: 버튼을 로딩 상태로 잠그고(중복 제출 방지) → 성공/실패를 낸다. 성공은
  `{colors.signal-success}` 확인 문구, 실패는 상단 요약 + 실패 필드로 포커스 이동.

**`alert-banner`** — privacy/cookie disclosure copy
- Background `{colors.canvas}`, text `{colors.ink}`, `{typography.body-tight}`, padding `{spacing.md}`, rounded `{rounded.lg}`, 1px `{colors.hairline-soft}` border.

### Footer

**`footer`** — the system's terminal surface
- Background `{colors.footer}`, text `{colors.on-primary}`, padding `{spacing.section}` vertical, `{spacing.lg}` horizontal.
- Layout: 6-column link grid → bottom strip with the lowercase `runwai` wordmark left and legal/copyright links right.

**`footer-eyebrow`** — small-caps column headings ("Product", "Initiatives", "Company")
- Background `{colors.footer}`, text `{colors.stone}`, type `{typography.eyebrow}`.

**`footer-link`** — link-list items
- Background `{colors.footer}`, text `{colors.on-primary}`, type `{typography.body}`.

**`footer-beautyboy`** — 뷰티보이 실제 푸터 (위 runwai 원형의 축소 적용)
- 배경 `{colors.footer}`, 텍스트 `{colors.on-primary}`, 상하 `{spacing.section}` / 좌우 `{spacing.lg}`.
  **랜딩(`/`)에서는 렌더하지 않는다** — 풀블리드 히어로 한 장이 그 화면의 전부다.
- 6열 그리드 대신 **2단 스택**: ① 안내 링크 줄(이용약관 · 개인정보처리방침 · 고객센터 —
  대상 화면이 생기기 전까지는 `{colors.stone}` 텍스트로 두고 링크로 위장하지 않는다) →
  ② 사업자 표기 블록(`{typography.meta}` / `{colors.stone}`, 전자상거래 필수 표기 항목 형태:
  상호·대표·사업자등록번호·통신판매업 신고번호·주소·이메일).
- **사업자 표기 값은 실존 정보처럼 쓰지 않는다.** 포트폴리오 데모임을 표기 블록 첫 줄에 명시하고
  ("본 사이트는 취업 포트폴리오용 데모입니다. 아래 표기는 형식 예시입니다."), 등록번호류는
  자릿수만 맞춘 자리표시 값(`000-00-00000` 등)을 쓴다.
- 하단 스트립: 좌측 `BEAUTY BOY.` 워드마크(헤더와 동일 표기) + 우측 `© 2026 BeautyBoy — Portfolio Demo`.

### 커머스 컴포넌트 (뷰티보이)

**`goods-card`** — 목록·검색·랭킹·추천·루틴이 **전부 재사용하는 단일 상품 카드**. 이 시스템에서 가장 많이
반복되는 요소이므로 규칙을 여기서 못 박는다.

- 배경 `{colors.surface}`, 라운딩 `{rounded.lg}`, 안쪽 여백 `{spacing.sm}`.
  **테두리 없음, 그림자 없음** — 카드를 구분하는 것은 면의 명도차와 여백이지 선이나 그림자가 아니다.
  `{colors.canvas}`(오프화이트) 위에 한 단계 어두운 `{colors.surface}`를 얹어 분리한다.
  그림자로 띄우는 순간 편집디자인 리듬이 깨지고 원본 시스템의 "카드 그림자 없음" 원칙과 충돌한다.
- 내부 스택(위→아래): `{goods-thumbnail}` (1:1, `{rounded.md}`, `{colors.surface-cool}` 플레이스홀더,
  lazy load) → 배지 줄 → 브랜드명(`{typography.meta}` / `{colors.slate}`) →
  상품명(`{typography.body-tight}` / `{colors.ink}`, **2줄 고정 말줄임**) →
  가격 줄 → 평점 줄(아래 "평점 줄" 규칙)
- **가격 줄**: `{price-discount-rate}`(할인율, signal-sale) → `{price-sale}`(판매가, ink) →
  `{price-list-struck}`(정가 취소선, ash). 할인율이 0이면 **정가 노드를 렌더하지 않는다.**
- **평점 줄**: 리뷰가 있으면 `★`(별 글리프 1개) + 점수 소수 1자리 + `(리뷰수)` —
  `{typography.meta}` / `{colors.graphite}`. 별 5개를 그리지 않는다(카드 밀도에 비해 과함).
  리뷰가 없으면 "첫 리뷰를 기다려요"(같은 토큰) — "리뷰 없음"은 죽은 정보처럼 읽히므로 쓰지 않는다.
- **카드(surface) 위 보조 텍스트의 최저선은 `{colors.graphite}`다.** `{colors.ash}`(#999999)는
  `{colors.surface}`(#ebebeb) 위에서 대비 약 2.6:1, `{colors.slate}`도 약 4.2:1로 WCAG AA(4.5:1)에
  못 미친다. graphite는 약 8.7:1. 브랜드명·평점 줄 모두 graphite로 통일하고, 위계는 색이 아니라
  크기(`{typography.meta}` vs `{typography.body-tight}`)와 굵기로 만든다.
  `{colors.canvas}`(#f7f7f7) 위에서는 slate(약 4.7:1)까지 허용한다.
- **높이 안정성**: 평점 영역은 값이 없어도 자리를 유지한다. 리뷰 기능이 나중에 붙어 값이 채워질 때
  카드 높이가 흔들리면 그리드 전체가 다시 흐른다.
- 찜 버튼은 썸네일 우상단. **카드 링크 안에 중첩하지 않고 형제로 배치**해 클릭이 링크로 새지 않게 한다.
  아이콘만 두고 `aria-pressed`로 상태를 낸다.
- 품절: 썸네일 `opacity: 0.45` + 상품명 위 "품절" 라벨(`{colors.signal-muted}`). 색만으로 알리지 않는다.

**배지 줄** — `{badge-sale}`(SALE만) + `{badge-neutral}`(COUPON/GIFT/1+1) + `{badge-today-dream}`.
전부 흰 바탕 + 색 글자 + `{typography.micro-caps}`. 배지는 **썸네일 위에 겹치지 않고 아래 줄에 놓는다** —
겹치면 상품 사진을 가리고, 이 시스템에서 사진은 장식이 아니라 콘텐츠다.

**상품 그리드** — 1440px 5열 / 1024px 4열 / 768px 3열 / 640px 2열. 열 간격 `{spacing.lg}`,
행 간격 `{spacing.xl}`. 구분선 없음.

**`list-toolbar`** — 카테고리 목록의 제목 아래·그리드 위 한 줄
- 좌측: **카테고리 탭** — 루틴 5단계(클렌징~선크림) pill. 현재 카테고리가 5단계 중 하나일 때만
  렌더하고, 선택 상태는 "선택 상태 — 색 반전" 규칙을 따른다(검정 채움 + 흰 글자).
  탭 전환은 같은 화면의 쿼리스트링(`?category=`)만 바꾼다 — 루틴 STEP 맥락이 유지된다.
- 우측: **정렬 `<select>`** — 네이티브 셀렉트, `{typography.body-tight}` / `{colors.ink}`,
  1px `{colors.hairline-soft}` 테두리, 배경 `{colors.canvas}`. 라벨은 시각적으로 숨기고
  `aria-label="정렬"`. 항목과 순서: 인기순 / 최신순 / 판매량순 / 낮은 가격순 / 높은 할인율순 /
  리뷰 많은 순 (서버 `GoodsSort` 6종 popular/new/sales/priceAsc/discount/review와 1:1 —
  임의로 늘리지 않는다).
- 정렬 아래 **가격대 필터** pill 3종: 1만원 미만 / 1~3만원 / 3만원 이상. 다중 선택 없음(단일 토글),
  선택 상태는 색 반전. 해제하면 전체. 서버 `minPrice`/`maxPrice`로만 거른다 — 클라이언트에서
  거르지 않는다(돈은 서버).
- 모바일(768px 미만): 탭이 가로 스크롤, 정렬 셀렉트는 줄바꿈해 우측 정렬 유지.

**`set-tabs`** — /main 히어로 아래 "당신을 위한 세트" 섹션
- 구성: 아이브로우 "PERSONAL SETS"(`{typography.eyebrow}`) + 섹션 제목 "당신을 위한 세트"
  (`{typography.heading-sm}`) + pill 탭 3개 한 줄. 탭 라벨은 "세트 A · 모공" 형식
  (세트 문자 + 고민 태그와 같은 문구).
- 탭 pill은 `{rounded.full}`. 타이포·크기·간격은 **list-toolbar 카테고리 탭 pill과 동일 사양**을
  그대로 쓴다(패딩 `{spacing.xs}` × `{spacing.md}`, 1px 테두리, 타이포 `{typography.link-sm}`) —
  같은 조작(카테고리 전환/세트 전환)이 화면마다 다르게 생기면 안 된다.
  선택 상태는 "선택 상태 — 색 반전" 규칙(배경 `{colors.primary}` + 글자 `{colors.on-primary}`),
  비선택은 1px `{colors.hairline-soft}` 테두리 + `{colors.ink}` 글자 + 배경 `{colors.canvas}`.
- 액센트(signal-*) 금지 — 이 섹션도 무채색 사다리 안이다. 개인화 여부를 색으로 구분하지 않는다.
- 폴백 상태(전부 비개인화): 탭 아래 안내 한 줄 "프로필을 등록하면 맞춤 세트로 바뀌어요" —
  `{typography.meta}` / `{colors.slate}`(list-toolbar·goods-card의 메타 텍스트와 동일 조합,
  본문보다 한 단 낮은 무채색). 문구 끝에 `{typography.link-sm}` / `{colors.ink}` 텍스트 링크
  (밑줄, list-toolbar "전체 보기"류 링크와 동일 사양)로 프로필/가입 화면을 연결한다.
- 로딩(프로필 확정 전): 같은 크기의 스켈레톤 pill 3개(공용 `{Skeleton}` 컴포넌트를 `{rounded.full}`로
  덮어써 pill 모양을 유지), 조작 불가. 확정 후 높이가 흔들리지 않는다.
- 모바일(768px 미만): 탭 가로 스크롤(list-toolbar와 동일). 한글이므로 `word-break: keep-all`.

**`pager`** — 페이지 이동 공용 컴포넌트 (상품 그리드 아래 중앙 / admin 표 아래)
- **페이지를 나누는 화면은 전부 이 하나를 쓴다.** 목록·검색은 그리드 아래 중앙,
  admin 표는 표 아래 중앙 — 같은 컴포넌트다. 화면마다 "이전/다음"만 있는 페이저를 따로 만들면
  오래된 항목으로 건너뛸 방법이 없고, 같은 조작이 화면마다 다르게 생긴다.
- 구성: `이전` 텍스트 버튼 · 페이지 번호(현재 페이지 ±2, 최대 5개) · `다음` 텍스트 버튼.
  번호는 `{typography.body-tight}` / `{colors.ink}`, **현재 페이지만 색 반전 pill**
  ("선택 상태 — 색 반전" 규칙). 이전/다음 비활성은 `{colors.stone}` + `disabled` — 색만으로
  알리지 않는다(버튼 자체가 비활성).
- 그리드와의 사이 `{spacing.xl}`, 상단 hairline 없음(그리드 자체가 구분선 없는 시스템이다).
- **1페이지뿐이면 렌더하지 않는다.** 자리도 남기지 않는다 — 카드 그리드의 행 간격 리듬이
  그대로 페이지 끝이 된다.
- 페이지 이동은 URL(`?page=N`, 1부터)로만 한다 — URL이 상태의 진실. 이동 시 목록 상단으로
  스크롤한다(문서 최상단이 아니라 툴바가 보이는 위치).
- 접근성: `<nav aria-label="페이지 이동">`, 현재 페이지에 `aria-current="page"`.

**`compat-banner`** — 장바구니 성분 궁합 안내 (설계 8장 적용 지점 ③)
- **verdict마다 별도 섹션으로 분리해 위→아래로 쌓는다**: CONFLICT → CAUTION → SYNERGY 순.
  경고와 긍정을 한 박스에 섞으면 메시지가 상쇄된다 — 같은 박스 안에 다른 verdict의 항목을 넣지 않는다.
- 섹션 공통: 배경 `{colors.canvas}`, 1px `{colors.hairline-soft}` 테두리, `{rounded.md}`,
  안쪽 여백 `{spacing.md}`. 톤은 **좌측 4px 보더 + 원형 아이콘 + 텍스트로만** 표현한다
  (`{colors.signal-danger}` / `{colors.signal-caution}` / `{colors.signal-success}`) — 배경 채움 금지 규칙 그대로.
- 접근성: CONFLICT·CAUTION 섹션은 `role="alert"`, SYNERGY 섹션은 경고가 아니므로 `role="status"`.
  색 단독 금지 — verdict는 색 외에 제목 문구와 아이콘(`!` / `✓`)으로도 구분한다.
- overall이 OK면 아무것도 렌더하지 않는다. 궁합은 조언이지 금지가 아니므로 어떤 verdict도
  버튼·주문을 비활성화하지 않는다.

### 루틴 조합기 컴포넌트 (메인 개인화 v2)

메인의 각 루틴 단계는 "인기순 4칸"에서 **대표 픽 1개 + 대안 3개**로 바뀐다. 픽에는 왜 그것이
뽑혔는지가 함께 붙고(근거), 카드에서 바로 장바구니로 갈 수 있다.

**`pick-card`** — 단계당 1개. 그 단계에서 이 사용자에게 뽑힌 대표 상품.

- **배치: 대안 그리드 위에 놓는 가로형 별도 카드** (그리드 첫 칸을 2칸 폭으로 넓히지 않는다).
  그리드는 1440px 5열 / 1024px 4열이라 첫 칸을 2칸으로 늘리면 한 줄이 픽1(2칸)+대안3 = 5칸이
  되어 4열에서 대안 하나가 다음 줄로 밀리고, 5열에서는 정확히 차되 1024px에서만 깨진다 —
  **열 수에 따라 리듬이 무너진다.** 별도 카드는 어느 폭에서도 "픽 한 장 → 대안 한 줄"로 읽히고,
  섹션 헤드(타이포↔이미지 비대칭)와 같은 가로 2분할이라 섹션 안에서 형태가 반복된다.
- 배경 `{colors.surface}`, 라운딩 `{rounded.lg}`, 안쪽 여백 `{spacing.lg}`.
  **테두리·그림자 없음** — `{goods-card}`와 같은 규칙이다. 픽 카드가 더 중요하다는 신호는
  **면적과 위치**가 내지, 테두리나 액센트 색이 내지 않는다.
- 좌우 2분할: 썸네일(1:1, `{rounded.md}`, `{colors.surface-cool}` 플레이스홀더) **320px 고정** +
  정보 열이 나머지. 비율로 나누면 넓은 화면에서 1:1 썸네일이 함께 커져 카드 하나가 480px를 넘고
  대안 세 개가 화면 밖으로 밀린다 — **픽과 대안이 한 화면에 보이는 것이 이 섹션의 요점**이다.
  900px 이하에서 세로 스택(썸네일 위, 정보 아래)으로 접힌다.
- 정보 열 스택(위→아래): `{pick-card-eyebrow}`(단계 안에서의 역할 라벨) → 브랜드명
  (`{typography.meta}` / `{colors.slate}`) → `{pick-card-name}`(2줄 고정 말줄임) →
  **근거 블록** → 가격 줄(`{price-*}` 3종, `{goods-card}`와 동일) → `[바로 담기]`.
- **근거 블록**(설계 §5)은 두 층이다:
  1. **reason 문장** — `{pick-card-reason}`. 좌측 2px `{colors.ink}` 규칙선 + `{spacing.sm}`
     들여쓰기. 단계 설명(`{typography.body-lg}` / `{colors.graphite}`)은 누구에게나 같은 문장이고
     이 줄만 "당신에게" 달린 문장이라, 잉크색·두께·규칙선으로 한 단계 앞세운다.
     **문구는 규칙 테이블(reason)이 유일한 출처다 — 화면이 만들지 않는다.** 발동한 규칙이 없으면
     이 줄 자체를 렌더하지 않는다(빈 자리를 문구로 메우지 않는다).
  2. **근거 칩** — 점수에 실제 기여한 태그만 `{Tag}` pill로 낸다. 칩은 문구가 아니라 **태그 데이터**라
     "태그 컬러" 절의 팔레트를 그대로 쓴다(이 화면에서 색이 허용되는 유일한 자리다).
     한 카드에 최대 4개, 넘치면 자른다.
- **`[바로 담기]`는 `{button-primary}` 파생** — 같은 검정 알약, 정보 열 폭을 꽉 채운다(`width: 100%`).
  라운딩·타이포·높이는 `{button-primary}` 그대로이고 폭만 다르다.
- **전 옵션 품절이면 버튼을 `disabled`로 두고** 그 아래 `{pick-card-soldout}`으로 "일시품절"을 낸다.
  색만으로 알리지 않는다(비활성 + 문구 두 단서) — `{goods-card}` 품절 규칙과 같은 원칙이다.

**대안 그리드** — 픽 카드 아래 `{goods-card}` 3개. 상품 그리드 사양을 따르되 **데스크톱에서 3열로
고정**한다(1024px↑ 3열 / 768px↑ 3열 / 640px 2열). 기본 그리드의 4·5열을 그대로 쓰면 3개짜리 줄이
오른쪽에 빈 칸을 남겨 "덜 불러온 줄"로 읽힌다.

**`routine-bulk-cta`** — STEP 05 아래 "루틴 전체 담기" 한 개(설계 §4.3).

- 위에 1px `{colors.hairline}` 구분선을 두고 `{spacing.section}` 여백 안에 가운데 정렬.
- **`{button-ghost}` 계열**(흰 배경 + 1px `{colors.ink}` 테두리 + `{rounded.full}`, 높이 48px)로
  두고 **검정 채움을 쓰지 않는다.** Iteration Guide 6의 "한 뷰포트에 검정 알약은 하나" 규칙 때문이다 —
  스크롤 위치에 따라 STEP 05의 픽 카드 `[바로 담기]`(검정 채움)와 같은 화면에 들어올 수 있고,
  그때 검정이 둘이면 어느 쪽이 그 화면의 행동인지 흐려진다. 개별 담기가 원자적 행동이고
  전체 담기는 그것의 묶음이므로, 채움을 갖는 쪽은 개별 담기다.
- 결과는 토스트로만 알린다(UX 계약 "비치명 알림은 토스트"). 일부 실패해도 성공분은 유지하고
  집계를 알린다 — 장바구니는 되돌릴 수 있는 중간 상태라 전부 롤백하지 않는다.

**스켈레톤(점진 렌더)** — 체인은 위 단계 픽이 확정돼야 아래 단계가 확정된다(설계 §3.4). 미확정
섹션은 픽 카드 자리를 비워 두지 않고 **`{goods-card}` 스켈레톤 한 줄**을 유지한다. 확정 순서가
스크롤 방향과 같아 사용자는 "위에서부터 채워지는" 것으로 읽고 지연을 기다림으로 체감하지 않는다.

### Signature Components

**Pricing 5-Column Slab** — Runwai's pricing module is unusually flat: a 5-tier slab with no coloured borders, no shadow, no badge ribbon. The featured tier is signalled by a single tonal step (`{colors.hairline}` infill) and a slightly heavier action button. The decision to render Free → Enterprise as one continuous slab instead of separate floating cards is the page's central design move.

**Editorial Eyebrow + Display Lockup** — Across the site, headline modules follow a fixed three-part rhythm: uppercase `{typography.eyebrow}` label → 36–48px `{typography.display}` headline → `{typography.body}` lead paragraph. Section spacing locks to `{spacing.section}` between modules. The lockup is what gives marketing pages their festival-programme cadence.

**Cinematic Atmospheric Interlude** — Mid-document interludes (the "We are building foundational simulation World Models" forest scene, the "We are building AI to simulate the world…" closing strip) use a contained `{hero-photo}` panel with `{rounded.lg}` corners. They function as pacing breaks between research grids and CTA bands rather than promotional units.

## Do's and Don'ts

### Do
- Reserve `{colors.primary}` for primary actions and the footer; use `{button-primary}` for every primary CTA without varying corner radius or fill.
- Stack uppercase `{typography.eyebrow}` over `{typography.display}` for every major section opener — it is the system's signature lockup.
- Use `{colors.hairline}` infill — never a coloured border — when one item in a comparison must read as featured.
- Set body copy in `{colors.graphite}` against `{colors.canvas}` for paragraphs, and reserve `{colors.ink}` for headings and emphasis only.
- Treat photography as content: full-bleed, cinematic, aligned to the page edge in heroes; `{rounded.lg}` only when the photo is contained inside a section.
- Lock display headings to negative letter-spacing (`-0.9px` to `-1.2px`) — the tight tracking is core to the brand voice.
- Use `{rounded.full}` pills for buttons and `{rounded.none}` for table/grid cells. Never mix.
- 제목은 `h1→h2→h3` 순차로 쓴다. **크기를 맞추려고 레벨을 건너뛰지 않는다** — 큰 글씨가 필요하면
  타이포 토큰으로 키우고 시맨틱 레벨은 문서 구조를 따른다(스크린리더·SEO가 이 순서를 읽는다).
- 색으로만 전달하는 정보가 하나도 없게 한다. 시그널 색은 항상 아이콘·텍스트·취소선 등 두 번째 단서와 함께.

### Don't
- Don't introduce accent colours beyond the five `signal-*` tokens. The voice is monochrome plus photography; the signals are a commerce concession, not an invitation to a palette. Never fill a surface with a signal colour — text, icon, and 1px rules only.
- Don't set Korean text at `line-height: 1.0` or at the original display tracking (`-1.2px`) — see 한글 적용.
- Don't apply drop shadows or glows to cards. Depth is photographic and tonal, not material.
- Don't badge the featured pricing tier with a coloured ribbon or border — the surface step is the badge.
- Don't break headings into bold + light contrast; every heading is regular weight (`400`) with tight tracking.
- Don't centre body paragraphs longer than one sentence — the system uses left-aligned reading bands almost exclusively.
- Don't use uppercase for body or button copy. Uppercase is reserved for `{typography.eyebrow}` (14px) and `{typography.micro-caps}` (11px).
- Don't render the runwai wordmark in title-case or with a brand colour. It is always lowercase, in `{colors.ink}` on light surfaces and `{colors.on-primary}` on dark.

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| 2xl | 1600px | Full editorial container; pricing 5-up; research rows 5/7 split |
| xl | 1536px | Same layout, marginally tighter gutters |
| lg | 1280px | Default desktop reading view |
| md | 1200px | Pricing grid still 5-up but tier text tightens |
| sm | 1024px | Pricing collapses to 3 → 2 tier rows; research rows stack at certain breakpoints |
| xs | 768px | Top nav collapses to a hamburger; section padding drops to `{spacing.section}` |
| xxs | 640px | Single-column reading; hero display drops to `{typography.display-sm}`; pricing tiers stack 1-up |

### Touch Targets
- Every `{button-primary}` is 40px tall — at the lower edge of the 44×44 WCAG target. On mobile the buttons grow to 48px height (still `{rounded.full}`, still `{typography.button}`).
- `{nav-link}` items get `{spacing.sm}` vertical padding inside the mobile menu, expanding the tap target without changing typography.
- Pricing-tier `{button-primary}` extends full-column-width on mobile.
- **인접 터치 타깃 사이는 최소 `{spacing.xs}`(8px) 간격**을 둔다 — `goods-card`의 찜 버튼처럼 작은
  아이콘 버튼이 다른 링크와 붙으면 오탭이 난다.
- 상품 그리드에서 카드 전체가 탭 타깃이고, 찜 버튼은 그 위에 겹치지 않고 형제로 두어(카드 규칙 참조)
  두 타깃이 서로를 삼키지 않게 한다.

### Collapsing Strategy
- **Nav.** Centred desktop menu collapses into a single hamburger that opens an overlay sheet; the right-side `{button-primary}` "Try Runwai" stays visible above the hamburger as the persistent action.
- **Pricing.** 5-column slab collapses to single-column stacked cards at xxs; the featured `{colors.hairline}` infill is preserved on the Pro card so the tonal cue survives the stack.
- **Research grid.** 5/7 split collapses to image-on-top, text-below at sm; thumbnail rounding (`{rounded.md}`) is preserved.
- **Footer.** 6-column link grid collapses to 2-column at sm and 1-column at xxs; the lowercase `runwai` wordmark stays bottom-left, legal links stack underneath.

### Image Behavior
- Hero photographs swap to a tighter crop on mobile (vertical-leaning) so the focal subject stays centred at xxs widths.
- `{media-thumbnail}` containers preserve their 16:9 ratio at every breakpoint; the `{colors.surface-cool}` placeholder fill paints during lazy-load.
- Studios poster tiles preserve their original aspect ratios at every breakpoint — the masonry simply re-flows into fewer columns.

## UX 계약 (뷰티보이)

이 절은 시각 토큰이 아니라 **화면이 지켜야 할 행동 규칙**이다. 커머스 UX 베스트 프랙티스 중
이 시스템의 시각 언어·컴포넌트와 직접 맞닿는 것만 골라 뷰티보이 토큰에 연결해 적었다.
(순수 성능·번들·빌드 규칙은 시각 계약의 범위가 아니므로 여기 넣지 않는다 — 코드 가이드에서 다룬다.)

각 규칙은 위 컴포넌트·토큰과 이미 정의된 규칙(색 단독 금지, 그림자 금지, `signal-*` 5종 등)을 재확인·확장한다.

### 접근성 (색으로만 알리지 않는다)

- **대비 4.5:1 이상**(본문 텍스트). 무채색 사다리에서 `{colors.ash}`·`{colors.stone}`을
  `{colors.canvas}` 위 본문으로 쓰지 않는다 — 캡션·메타 전용이다. 본문은 `{colors.graphite}` 이상 진하게.
- **icon-only 버튼에는 `aria-label`.** `goods-card`의 찜 버튼, 헤더의 검색·장바구니 아이콘이 해당.
- **의미 있는 이미지에는 서술형 `alt`.** 상품 썸네일 `alt`는 상품명, 장식 이미지는 `alt=""`.
- **폼 에러는 `role="alert"`/`aria-live`**, 상태 변화(장바구니 담김, 재고 없음)는 스크린리더에 알린다.
- **키보드만으로 전체 기능 도달**, tab 순서 = 시각 순서. 모달·드로어(장바구니, 필터)는 포커스 트랩 + `Esc` 닫기.
- **skip-link**("본문 바로가기")를 헤더 앞에 둔다.
- **`prefers-reduced-motion` 준수.** 이 시스템은 원래 모션이 절제돼 있지만, 어떤 트랜지션도 이 설정에서
  즉시 완료로 축약한다.

### 상태: 로딩·빈 상태·진행

- **로딩(>300ms)은 스켈레톤/스피너로 표시.** 상품 목록·검색 결과는 `goods-card` 형태의 스켈레톤을
  `{colors.surface-cool}` 톤으로 깔아 레이아웃 점프를 막는다(`goods-thumbnail` 플레이스홀더와 동일 원리).
- **빈 상태는 안내 + 행동을 함께.** 빈 장바구니·찜·검색 무결과·주문 없음은 회색 일러스트+짧은 문구
  (`{typography.body}` `{colors.graphite}`) + `{button-ghost}` 다음 행동("상품 둘러보기")을 낸다.
  빈 화면을 흰 여백으로 방치하지 않는다.
- **다단계 진행은 스텝 표시.** 회원가입(피부 프로필 스텝)·결제 플로우는 현재 단계/전체를 상단에 표시한다.

### 피드백: 토스트·확인·에러 복구

- **비치명 알림은 토스트로 3–5초 자동 소멸.** 찜 추가, 쿠폰 적용 같은 확인은 토스트. 배경은
  `{colors.canvas}` + 1px `{colors.hairline-soft}` + 좌측 `{colors.signal-success}` 아이콘(배경 채움 금지).
- **성공은 짧게 확인**(주문 완료는 전용 페이지, 소소한 액션은 토스트).
- **에러는 복구 경로를 함께 준다.** "결제 실패" 단독이 아니라 원인 + 다시 시도/카드 변경 버튼.
  돈·재고 관련 에러 문구는 서버 재검증 결과를 그대로 전한다(추측 금지 — CLAUDE.md "돈과 재고는 서버").

### 네비게이션·검색

- **현재 위치를 active로 표시.** 헤더 카테고리, 사이드 필터의 선택 상태를 시각적으로 명확히.
- **깊이 3단계 이상이면 브레드크럼**(홈 > 스킨케어 > 토너). `{typography.meta}` `{colors.slate}`.
- **sticky 헤더가 콘텐츠를 가리지 않게** 오프셋을 준다(앵커 스크롤 시 헤더 높이만큼 여백).
- **URL이 상태를 반영**한다 — 카테고리·필터·정렬·검색어가 URL에 담겨 공유·뒤로가기가 예측대로 동작.
- **검색은 자동완성 + 무결과 상태**를 갖춘다. 무결과는 빈 상태 규칙을 따르고, 대체 제안(인기 검색어)을 낸다.

### 콘텐츠 포맷

- **상품명은 2줄 고정 말줄임**(`goods-card` 규칙 재확인) — 카드 높이가 흔들리지 않게.
- **가격·숫자는 천 단위 구분**(`19,900원`), 날짜는 일관된 포맷(`2026.07.23`).
- **placeholder를 실제 콘텐츠로 오인하지 않게** 한다 — 스켈레톤은 텍스트가 아니라 회색 블록으로.

## Iteration Guide

1. Focus on ONE component at a time. Start with `{button-primary}` and `{nav-bar}` — they appear on every page and anchor the system.
2. Reference component names and tokens directly (`{colors.ink}`, `{button-primary-on-dark}`, `{rounded.full}`) — do not paraphrase or substitute hex values.
3. Run `npx @google/design.md lint DESIGN.md` after edits — `broken-ref`, `contrast-ratio`, and `orphaned-tokens` warnings flag drift automatically.
4. Add new variants as separate `components:` entries (`-pressed`, `-disabled`, `-focused`) — never bury them inside prose.
5. Default body copy to `{typography.body}` and emphasis to `{typography.body-strong}`. Reserve `{typography.eyebrow}` and `{typography.micro-caps}` for their two specific roles (section openers and footer columns).
6. Keep `{colors.primary}` scarce — if more than one black-pill action appears in a single viewport, neutralise the secondary one to `{button-ghost}`.
7. When introducing photography, lay it into `{colors.scrim}` and let the next white band break against it. Avoid mid-section photographic accents that don't span the full content width — they read as off-system.
