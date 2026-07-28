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
