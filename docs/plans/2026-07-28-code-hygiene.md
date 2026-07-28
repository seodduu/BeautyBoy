# 구현 계획 — 코드 위생 마감 (2026-07-28)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는
> superpowers:executing-plans로 태스크 단위 실행. 스텝은 체크박스로 추적한다.
>
> 근거: `docs/plans/2026-07-26-다음-작업.md` §5(코드 위생)의 **남은 항목 전부**.
> **이 문서의 §2 "설계 결정"이 설계의 진실을 겸한다.**
> DESIGN.md에는 이 계획과 같은 커밋으로 `pager`가 **페이지 이동 공용 컴포넌트**임을 못 박아 뒀다.
> **마이그레이션 없음**(Flyway 현재 V84). 새 기능 없음 — 전부 기존 동작을 보존하는 정리다.

**Goal:** §5에 남은 위생 항목 4건을 닫는다 — 라벨 상수 중복, 페이저 중복(admin 도달성),
대표 옵션 비교자 NPE 함정, admin 문의 페이지 크기 고정. 마지막 하나(리뷰 미이수 커밋)는 W2에서 회고 리뷰로 닫는다.

**Architecture:** 프론트는 "한 곳에서만 정의한다"로 모은다(라벨·페이저). 백엔드는 방어적 비교자와
파라미터화로 끝난다. **공개 API 응답 형태·화면 문구는 바뀌지 않는다** — 기존 테스트가 곧 보존의 증거다.

**Tech Stack:** React(공용 모듈 추출, 컴포넌트 재배치) / Spring Boot(Comparator, @RequestParam).

## Global Constraints (CLAUDE.md 재확인)

- 자기 터미널의 Files 목록 밖 파일 수정 금지. `common` 패키지는 열지 않는다.
- **기존 테스트의 단언은 수정 금지.** 이 계획은 리팩토링이라 기존 테스트가 동작 보존의 증거다.
  import 경로 변경으로 깨지는 것은 경로만 고친다. 단언을 바꿔야 통과한다면 중단·보고.
- CSS는 `var(--color-*)` 토큰만. 이번엔 새 CSS를 거의 쓰지 않는다(기존 `Pager.css` 이동).
- 프론트 전체 판정 `npm test` + `npx tsc -p tsconfig.app.json --noEmit`. 백엔드 `./gradlew test`.
- 화면이 바뀌는 태스크(T1-B: admin 문의 페이저)는 스크린샷 DoD.
- 커밋 메시지·주석 한국어, 태스크 단위 원자 커밋.

---

## 0. 사람이 할 일 (사전 조건 2줄)

터미널을 열기 전에 프로젝트 루트에서:

```
git log --oneline -1     # 이 계획 커밋(docs(plan): 코드 위생 마감 계획)인지 확인
git status               # 깨끗한지 확인
```

---

## 1. 남은 항목 판정표

| §5 항목 | 판정 | 배치 |
|---|---|---|
| `STATUS_LABEL` 중복 정의 (QnaList / AdminQna) | 수정 | T1-A |
| admin 문의 `DEFAULT_PAGE_SIZE=10` — 오래된 문의 도달성 | 수정(페이저 + size 파라미터) | T1-B(프론트) · T2-B(서버) |
| `GoodsService` 대표 옵션 비교자 NPE 함정 | 수정(방어) | T2-A |
| compose `REDIS_HOST`/`depends_on` 미배선 | **이미 완료** (`9a41690`, 실스택 실증) | W2에서 문서 소거만 |
| 리뷰 없이 머지된 커밋 `5268693` | W2 회고 리뷰로 종결 | W2 |

---

## 2. 설계 결정

### 결정 1 — Q&A 상태 라벨: `features/qna/status.ts` 한 곳

- 자리는 `features/<도메인>/` 관례를 따른다(`features/routine/steps.ts`가 선례).
  컴포넌트 폴더가 아니라 features인 이유: 이 값은 **화면이 아니라 도메인 어휘**이고,
  지금도 상품 상세(QnaList)와 admin 두 화면이 함께 쓴다.
- **미지 상태 코드는 코드를 그대로 반환한다** — `IngredientCategoryLabels`(백엔드)와 같은 규약이다.
  라벨 누락으로 칸이 비면 "상태가 없는 문의"로 읽히고, 코드가 보이면 눈에 띄어 고쳐진다.
  현재 두 파일 모두 `STATUS_LABEL[status] ?? status` 형태가 아니므로 이 규약은 **개선**이다
  (동작 변화이지만 기존 두 값 ANSWERED·WAITING에서는 결과가 같아 테스트는 그대로 통과한다).
- 백엔드 상태 문자열(`Qna.java`의 WAITING/ANSWERED)이 진실이라는 기존 주석은 새 모듈로 옮긴다.

### 결정 2 — 페이저는 하나만 둔다: `components/ui/Pager.tsx`로 이동 + admin 재사용

- 지금 페이저가 두 벌이다: 번호 페이저(`components/goods/Pager.tsx`, 이번 웨이브 신설)와
  admin 문의의 이전/다음 버튼(`AdminQna.tsx` 인라인). **후자를 지우고 전자를 쓴다.**
- 이것이 §5의 "admin 문의 도달성" 항목의 실질적 해법이다. 페이지 크기를 키우는 것보다
  **번호로 건너뛸 수 있게 하는 것**이 오래된 문의에 닿는 직접적인 방법이다.
- 위치를 `components/goods/` → `components/ui/`로 옮긴다 — 상품 전용이 아니게 됐다.
  `Pager.css`도 함께 옮긴다. **컴포넌트 인터페이스(props)는 한 글자도 바꾸지 않는다.**
- admin의 page 상태는 0-based이고 Pager는 1-based다 — **변환은 호출부(AdminQna)가 한다.**
  Pager의 계약을 admin 때문에 흔들지 않는다.

### 결정 3 — admin 문의 페이지 크기: `size` 파라미터(기본 10, 상한 100)

- 상수 하나를 20으로 바꾸는 것은 "몇이 옳은가"를 다시 묻게 만들 뿐이다. 호출자가 정하게 하고
  **상한으로 서버를 지킨다** — `GoodsController.MAX_PAGE_SIZE = 100` 관례와 같은 값·같은 방식이다.
- **기본값 10은 유지한다.** 파라미터를 생략한 기존 호출(프론트 포함)의 동작이 바뀌지 않아야
  이 작업이 위생 작업으로 남는다.
- 상품 상세의 Q&A 목록(`list`)은 그대로 둔다 — 화면이 페이지네이션 UI를 갖고 있지 않다.
  admin 목록(`adminList`)만 파라미터를 받는다. 필요해지기 전에 넓히지 않는다(YAGNI).

### 결정 4 — 대표 옵션 비교자: `nullsLast`로 방어하고, 함정 주석은 사실에 맞게 고친다

- `thenComparing(GoodsOption::getId)` → `thenComparing(GoodsOption::getId, Comparator.nullsLast(Comparator.naturalOrder()))`.
- **왜 nullsLast인가**: id가 null인 옵션은 "아직 저장되지 않은 것"이다. 저장된 옵션들이 먼저
  결정적으로 정렬되고 미저장분이 뒤로 밀리는 편이, 미저장분이 앞에 끼어 대표 옵션을 바꾸는 것보다 안전하다.
- 기존 주석의 "도달 불가능하지만 admin 옵션 생성이 생기면 되살아난다"는 **더 이상 사실이 아니게 되므로**
  "null이면 뒤로 민다"는 새 사실로 바꾼다. 다만 "옵션 추가 뒤에는 flush/재조회로 id를 확정하라"는
  권고는 남긴다 — 정렬이 안전해졌을 뿐, 미저장 엔티티를 흘려보내는 것이 좋은 코드는 아니다.

---

## 3. 공유 계약 — 코드 전량

### 3-1. `frontend/src/features/qna/status.ts` (신규 — 전량)

```ts
/**
 * Q&A 상태 표시명. 상태 문자열의 진실은 백엔드다(`backend/.../qna/Qna.java` — WAITING/ANSWERED,
 * PENDING이 아니다). 상품 상세(QnaList)와 admin 문의 목록이 같은 어휘를 쓰므로 여기 한 곳에만 둔다
 * — 예전에 두 파일에 흩어져 있어 PENDING→WAITING 수정을 두 번 해야 했다.
 *
 * 미지 코드는 코드를 그대로 돌려준다 — 칸이 비면 "상태 없는 문의"로 읽히지만,
 * 코드가 보이면 눈에 띄어 고쳐진다(백엔드 IngredientCategoryLabels와 같은 규약).
 */
const QNA_STATUS_LABEL: Record<string, string> = {
  ANSWERED: '답변완료',
  WAITING: '답변대기',
};

export function qnaStatusLabel(status: string): string {
  return QNA_STATUS_LABEL[status] ?? status;
}
```

두 소비자는 각자의 `STATUS_LABEL` 상수와 그 위 주석을 지우고 이 함수를 부른다.
`STATUS_LABEL[item.status]` → `qnaStatusLabel(item.status)`. **표시 문구는 그대로다.**

### 3-2. `Pager` 이동 (경로만 바뀐다 — props 불변)

- `frontend/src/components/goods/Pager.tsx` → `frontend/src/components/ui/Pager.tsx`
- `frontend/src/components/goods/Pager.css` → `frontend/src/components/ui/Pager.css`
- `frontend/src/components/goods/Pager.test.tsx` → `frontend/src/components/ui/Pager.test.tsx`
- `git mv`로 옮긴다(이력 보존). 파일 안의 `import './Pager.css'`는 그대로 유효하다.
- import 경로를 고칠 곳: `frontend/src/pages/GoodsList.tsx`
  (`'../components/goods/Pager'` → `'../components/ui/Pager'`).

props는 이것 그대로다(변경 금지):

```ts
interface PagerProps {
  /** 1-based 현재 페이지 */
  page: number;
  totalPages: number;
  /** 클릭된 1-based 페이지 번호를 넘긴다 — URL 갱신·스크롤은 호출부 책임 */
  onPageChange: (page: number) => void;
}
```

### 3-3. AdminQna의 페이저 교체 (0-based ↔ 1-based 변환은 호출부 책임)

기존 `<div className="bb-admin-qna__pagination">…</div>` 블록(이전/다음 버튼 + "n / m" 표시)을
통째로 지우고 아래로 바꾼다. `page` 상태는 0-based 그대로 둔다(쿼리키·API가 그 값을 쓴다).

```tsx
<Pager
  page={page + 1}
  totalPages={totalPages}
  onPageChange={(next) => setPage(next - 1)}
/>
```

`AdminQna.css`의 `.bb-admin-qna__pagination*` 규칙은 쓰이지 않게 되므로 **함께 지운다**
(`.bb-admin-qna__action`은 답변 버튼이 계속 쓰므로 남긴다 — 지우기 전에 grep으로 확인할 것).

### 3-4. `QnaService.adminList` + `AdminQnaController` (size 파라미터)

```java
// QnaService
private static final int DEFAULT_PAGE_SIZE = 10;
/** 페이지 크기 상한 — GoodsController.MAX_PAGE_SIZE와 같은 값·같은 이유(응답 폭주 방지). */
private static final int MAX_PAGE_SIZE = 100;

@Transactional(readOnly = true)
public PageResponse<AdminQnaResponse> adminList(int page, int size) {
    int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    List<Qna> items = qnaRepository.findAllOrderByWaitingFirst(PageRequest.of(page, pageSize));
    long total = qnaRepository.count();
    List<AdminQnaResponse> responses = items.stream()
            .map(this::toAdminResponse)
            .toList();
    return PageResponse.of(responses, page, pageSize, total);
}
```

컨트롤러는 기본값으로 기존 동작을 보존한다:

```java
@RequestParam(defaultValue = "10") int size
```

(상세 Q&A 목록 `list(Long, Long, int)`는 시그니처·동작 그대로 두고 `DEFAULT_PAGE_SIZE`를 계속 쓴다.)

### 3-5. `GoodsService` 대표 옵션 비교자

```java
private static final java.util.Comparator<GoodsOption> 대표_옵션_순서 =
        java.util.Comparator.comparingInt(GoodsOption::getSortOrder)
                .thenComparing(GoodsOption::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
```

javadoc의 "NPE 함정" 문단은 아래 사실로 교체한다(문구는 그대로 옮겨 적을 것):

```
 * <p><b>미저장 옵션</b>: {@code id}가 null인 옵션(같은 트랜잭션에서 아직 flush되지 않은
 * {@code new GoodsOption(...)})이 컬렉션에 섞여도 NPE 없이 <b>맨 뒤로</b> 밀린다
 * ({@code nullsLast}). 저장된 옵션들끼리의 순서가 먼저 결정되므로 대표 옵션이 미저장분에
 * 가로채이지 않는다. 그래도 옵션을 추가한 뒤에는 flush/재조회로 id를 확정한 컬렉션을 넘겨라 —
 * 정렬이 안전해졌을 뿐, 미저장 엔티티를 흘려보내는 것이 옳은 코드는 아니다.
```

---

## 4. 웨이브·터미널 분할

| 웨이브 | 터미널 | 브랜치 | 범위 | 모델 |
|---|---|---|---|---|
| W1 (병렬 2) | T1 | `feature/hygiene-frontend` | Q&A 라벨 공용화 + 페이저 이동·admin 재사용 | sonnet |
| W1 | T2 | `feature/hygiene-backend` | 비교자 nullsLast + admin 문의 size 파라미터 | sonnet |
| W2 (직렬) | 오케스트레이터 | — | 머지 게이트 + 기준선 + `5268693` 회고 리뷰 + 문서 정리 | opus |

- 둘 다 sonnet: 판단이 갈리는 지점(§2·§3)을 계획서가 코드로 못 박았고, 돈·재고·동시성 영역이 아니다.
- T1은 프론트 전용, T2는 백엔드 전용 — 파일이 겹치지 않는다.

### 파일 소유권

| 터미널 | 소유 파일 |
|---|---|
| T1 | `frontend/src/features/qna/status.ts`(신규), `frontend/src/features/qna/status.test.ts`(신규), `frontend/src/components/goods/QnaList.tsx`, `frontend/src/pages/admin/AdminQna.tsx`, `frontend/src/pages/admin/AdminQna.css`, `frontend/src/components/ui/Pager.tsx`·`Pager.css`·`Pager.test.tsx`(goods/에서 `git mv`), `frontend/src/pages/GoodsList.tsx`(import 경로 한 줄), 위 화면들의 기존 테스트 파일(경로·import 수정만) |
| T2 | `backend/…/qna/QnaService.java`, `backend/…/qna/AdminQnaController.java`, `backend/…/qna/QnaServiceTest.java`(있으면 수정·없으면 신규), `backend/…/catalog/GoodsService.java`, `backend/…/catalog/GoodsServiceTest.java` |

- **T1은 `GoodsList.tsx`에서 import 경로 한 줄만 고친다.** 다른 로직을 건드리면 중단·보고.
- **T2는 `Qna.java`·`QnaRepository`를 열지 않는다** — 상태 문자열과 쿼리는 그대로다.

---

## 5. 태스크 상세

### T1-A: Q&A 상태 라벨 공용화

**Files:** `features/qna/status.ts`(신규), `features/qna/status.test.ts`(신규), `QnaList.tsx`, `AdminQna.tsx`
**Interfaces:** Produces `qnaStatusLabel(status: string): string` (§3-1 전량)

- [ ] **1. 실패 테스트** — `frontend/src/features/qna/status.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { qnaStatusLabel } from './status';

describe('qnaStatusLabel', () => {
  it('백엔드 상태 문자열을 한글 표시명으로 바꾼다', () => {
    expect(qnaStatusLabel('WAITING')).toBe('답변대기');
    expect(qnaStatusLabel('ANSWERED')).toBe('답변완료');
  });

  it('미지 코드는 코드를 그대로 돌려준다 — 칸이 비면 상태 없는 문의로 읽힌다', () => {
    expect(qnaStatusLabel('CLOSED')).toBe('CLOSED');
  });
});
```

- [ ] **2. RED 확인**: `npx vitest run src/features/qna/status.test.ts` — 모듈 부재로 실패
- [ ] **3. §3-1 그대로 구현 → GREEN**
- [ ] **4. 두 소비자 교체**: `QnaList.tsx`·`AdminQna.tsx`의 지역 `STATUS_LABEL` 상수와 그 주석을
  지우고 `qnaStatusLabel(...)` 호출로 바꾼다. **표시 문구는 그대로**이므로 두 화면의 기존
  테스트는 단언 수정 없이 통과해야 한다 — 깨지면 중단·보고
- [ ] **5. `npm test` + `npx tsc -p tsconfig.app.json --noEmit` 전체 녹색**
- [ ] **6. 커밋** `refactor(qna): 상태 라벨을 features/qna/status로 모으고 미지 코드는 코드 그대로 노출`

### T1-B: 페이저 공용화 + admin 문의 재사용

**Files:** `components/ui/Pager.*`(goods/에서 이동), `GoodsList.tsx`(import 한 줄), `AdminQna.tsx`, `AdminQna.css`

- [ ] **1. 이동**: `git mv frontend/src/components/goods/Pager.tsx frontend/src/components/ui/Pager.tsx`
  (`.css`·`.test.tsx`도 같은 방식). `GoodsList.tsx`의 import 경로를 고친다.
  `npm test`로 **여기서 한 번 녹색을 확인**한다 — 이동만으로 깨진 게 없어야 다음 단계의 실패가
  admin 작업 때문임이 분명해진다
- [ ] **2. 실패 테스트** — `frontend/src/pages/admin/AdminQna.test.tsx`(이미 있다). 그 파일의
  기존 렌더 헬퍼·MSW 관례를 그대로 따르고, 기존 케이스는 건드리지 않는다:

```tsx
it('문의 목록 아래에 번호 페이저를 렌더한다 — 이전/다음만으로는 오래된 문의에 닿지 못한다', async () => {
  renderAdminQna(); // 목이 여러 페이지 분량(예: totalPages 3)을 내려주는 상태
  const pager = await screen.findByRole('navigation', { name: '페이지 이동' });
  expect(within(pager).getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
  expect(within(pager).getByRole('button', { name: '3' })).toBeInTheDocument();
});

it('번호를 누르면 0-based page로 변환해 그 페이지를 조회한다', async () => {
  renderAdminQna();
  const pager = await screen.findByRole('navigation', { name: '페이지 이동' });
  await userEvent.click(within(pager).getByRole('button', { name: '2' }));
  await waitFor(() => {
    expect(capturedSearchParams?.get('page')).toBe('1');
  });
});
```

  (목이 페이지 정보를 충분히 내려주지 않으면 **핸들러를 임의로 바꾸지 말고**, 그 테스트 안에서
  `server.use(...)`로 그 케이스만 덮어쓴다 — 기존 관례다.)
- [ ] **3. RED → 구현**(§3-3): 인라인 페이저 블록 제거, `<Pager page={page + 1} … />` 배선,
  `AdminQna.css`에서 쓰이지 않게 된 `.bb-admin-qna__pagination*` 규칙 삭제
  (`grep -rn "bb-admin-qna__pagination" frontend/src`로 잔재 0건 확인)
- [ ] **4. GREEN + `npm test` + `npx tsc -p tsconfig.app.json --noEmit`**
- [ ] **5. 스크린샷**: `VITE_USE_MOCK=true npm run dev` → admin 문의 목록(`/admin/qna`)에
  번호 페이저가 보이는 상태 → 파일 경로 보고
- [ ] **6. 커밋** `refactor(admin): 문의 목록에 공용 번호 페이저 적용 — Pager를 components/ui로 이동`

### T2-A: 대표 옵션 비교자 방어

**Files:** `GoodsService.java`, `GoodsServiceTest.java`

- [ ] **1. 실패 테스트** — `GoodsServiceTest`에 추가(기존 옵션 픽스처 관례 사용):

```java
@Test
@DisplayName("id가 아직 없는 옵션이 섞여도 NPE 없이 저장된 옵션이 대표로 뽑힌다")
void 미저장_옵션_섞임() {
    // sortOrder가 더 작은(=앞서는) 미저장 옵션(id=null)을 컬렉션에 섞는다.
    // nullsLast는 2차 키에만 걸리므로, sortOrder 동률일 때 미저장분이 뒤로 밀리는 것을 본다.
    List<GoodsOption> options = new ArrayList<>(List.of(저장된_옵션(sortOrder = 1, id = 10L),
                                                        미저장_옵션(sortOrder = 1)));
    GoodsOption 대표 = options.stream().min(대표_옵션_순서_접근자).orElseThrow();
    assertThat(대표.getId()).isEqualTo(10L);
}
```

  비교자가 `private static final`이라 테스트에서 직접 못 부르면, **비교자를 public으로 열지 말고**
  이미 그것을 쓰는 공개 경로(`findOrderSnapshot`)로 같은 사실을 단언한다 — 미저장 옵션이 섞인
  상품에서 스냅샷이 예외 없이 저장된 옵션을 고르는지. 어느 쪽을 택했는지 보고서에 남긴다.
- [ ] **2. RED 확인**(현재 코드에서 NPE) → **3. §3-5 구현 + javadoc 교체 → GREEN**
- [ ] **4. `./gradlew test` 전체 녹색 → 커밋**
  `fix(catalog): 대표 옵션 비교자를 nullsLast로 방어 — 미저장 옵션 NPE 함정 제거`

### T2-B: admin 문의 페이지 크기 파라미터

**Files:** `QnaService.java`, `AdminQnaController.java`, `QnaServiceTest.java`

- [ ] **1. 실패 테스트** — `QnaServiceTest`(없으면 신규, 기존 qna 테스트 픽스처 관례 사용):

```java
@Test
@DisplayName("size를 주면 그 크기로 페이징한다")
void admin_목록_size_지정() {
    // 문의 25건 픽스처
    PageResponse<AdminQnaResponse> page = qnaService.adminList(0, 20);
    assertThat(page.content()).hasSize(20);
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.hasNext()).isTrue();
}

@Test
@DisplayName("size 상한은 100 — 그보다 크게 요청해도 100으로 깎는다")
void admin_목록_size_상한() {
    assertThat(qnaService.adminList(0, 1000).size()).isEqualTo(100);
}

@Test
@DisplayName("size가 0 이하면 1로 올린다 — PageRequest가 예외를 던지지 않게")
void admin_목록_size_하한() {
    assertThat(qnaService.adminList(0, 0).size()).isEqualTo(1);
}
```

- [ ] **2. RED 확인**(시그니처 불일치로 컴파일 실패) → **3. §3-4 구현 → GREEN.**
  기존 `adminList(int)` 호출부(컨트롤러·테스트)는 새 시그니처로 배선만 바꾼다
- [ ] **4. 기본값 보존 확인**: `size` 없이 호출하면 10건인지 — 기존 admin 테스트가 이를 이미
  단언하고 있으면 그대로 통과해야 한다(단언 수정 금지)
- [ ] **5. `./gradlew test` 전체 녹색 → 커밋**
  `feat(qna): admin 문의 목록에 size 파라미터(기본 10, 상한 100)`

---

## 6. W2 — 직렬 검증 웨이브 (오케스트레이터, 머지 후)

- [ ] 2개 브랜치 리뷰(단언 무수정 원칙 준수 확인) 후 main 머지
- [ ] 잔재 확인:
  - `grep -rn "STATUS_LABEL" frontend/src` → 0건
  - `grep -rn "components/goods/Pager" frontend/src` → 0건
  - `grep -rn "bb-admin-qna__pagination" frontend/src` → 0건
- [ ] `./gradlew test` + `integrationTest` + `npm test` + `tsc` + E2E
  ([[e2e-needs-e2e-profile-backend]] 절차 — compose 백엔드 내리고 e2e 프로필 bootRun,
  시드 계정 장바구니 비우기)
- [ ] admin 문의 페이저 스크린샷 열어 판정 + 실스택에서 `/admin/qna` 번호 이동 1회 확인
- [ ] **`5268693` 회고 리뷰**(§5 마지막 항목): 상세 페이지 담기 후 `['cart']` 무효화 커밋의
  diff를 읽고 ① 무효화 키가 Header/Cart와 같은 `['cart']`인지 ② 테스트가 그 동작을 실제로
  잡는지 확인한다. 문제가 없으면 "회고 리뷰 완료"로 §5에서 소거하고, 있으면 별도 항목으로 남긴다
- [ ] `docs/plans/2026-07-26-다음-작업.md` §5 정리: 이번 4건 + compose 항목(이미 완료) 소거,
  남는 것이 없으면 §5 자체를 "비었음"으로 마감 표기

---

## 7. 터미널 실행 프롬프트

> 사람은 프로젝트 루트에서 터미널을 열고 아래를 통째로 붙여넣는다. git 명령을 손으로 치지 않는다.

### T1 — 프론트 위생 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-위생프론트 -b feature/hygiene-frontend
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 코드 위생 마감 계획)과 같은지
  - docs/plans/2026-07-28-code-hygiene.md 와 DESIGN.md 의 pager 절이 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-code-hygiene.md 의 T1-A → T1-B를 순서대로 실행해라.
너는 이 계획의 T1 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 백엔드는 한 파일도 만지지 않는다.
가장 중요한 제약: **기존 테스트의 단언을 바꾸지 마라** — 이건 리팩토링이라 기존 테스트가
동작 보존의 증거다. import 경로 수정만 허용한다. GoodsList.tsx는 import 한 줄만 고친다.
Pager 이동은 git mv로 이력을 보존하고, 이동 직후 npm test 녹색을 먼저 확인해라.
스텝별 TDD와 스크린샷 DoD는 계획서 그대로.
```

### T2 — 백엔드 위생 (sonnet)

```
[1단계 — 작업 공간 만들기] 다른 무엇보다 먼저 이것부터 해라.
  git worktree add .claude/worktrees/뷰티보이-위생백엔드 -b feature/hygiene-backend
를 실행한 뒤 EnterWorktree 도구에 path로 그 경로를 넘겨 세션을 그 안으로 옮겨라.
진입 후 아래를 확인하고, 하나라도 어긋나면 중단하고 보고해라:
  - pwd가 해당 worktree인지
  - git log --oneline -1 이 루트에서 본 기점 커밋(docs(plan): 코드 위생 마감 계획)과 같은지
  - docs/plans/2026-07-28-code-hygiene.md 가 실제로 존재하는지
  - git status가 깨끗한지

[2단계 — 실행] docs/plans/2026-07-28-code-hygiene.md 의 T2-A → T2-B를 순서대로 실행해라.
너는 이 계획의 T2 실행 서브에이전트다(model: sonnet). CLAUDE.md 공통 규칙과 계획서의
Global Constraints·파일 소유권 표를 지켜라. 프론트는 한 파일도 만지지 않는다.
Qna.java·QnaRepository는 열지 않는다(상태 문자열·쿼리 불변). common 패키지도 열지 않는다.
기존 테스트 단언은 수정 금지 — 시그니처 변경에 따른 배선만 고친다.
스텝별 TDD는 계획서 그대로.
```
