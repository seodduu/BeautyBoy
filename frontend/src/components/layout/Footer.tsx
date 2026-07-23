import './Footer.css';

/**
 * 절제된 다크 톤 푸터. 실제 정책/고객센터 페이지는 이 태스크 범위 밖이라
 * 링크는 텍스트 자리만 채운다.
 */
export function Footer() {
  return (
    <footer className="bb-footer">
      <div className="bb-footer__tick-rule" role="presentation" aria-hidden="true" />

      <div className="bb-footer__inner">
        <div>
          <p className="bb-footer__brand">
            BEAUTY BOY<span className="bb-footer__brand-dot">.</span>
          </p>
          <p className="bb-footer__desc">
            남자의 피부도 데이터로 관리합니다. 성분부터 루틴까지, 근거 있는 그루밍을
            제안하는 남성 화장품 커머스.
          </p>
        </div>

        <div>
          <p className="bb-footer__heading">고객센터</p>
          <ul className="bb-footer__links">
            <li>자주 묻는 질문</li>
            <li>1:1 문의</li>
            <li>배송 조회</li>
          </ul>
        </div>

        <div>
          <p className="bb-footer__heading">회사</p>
          <ul className="bb-footer__links">
            <li>이용약관</li>
            <li>개인정보처리방침</li>
            <li>사업자 정보</li>
          </ul>
        </div>
      </div>

      <div className="bb-footer__bottom">
        <span>© 2026 BEAUTY BOY</span>
        <span>MADE FOR MEN&apos;S SKIN</span>
      </div>
    </footer>
  );
}
