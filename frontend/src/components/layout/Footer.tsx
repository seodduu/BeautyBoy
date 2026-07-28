import { useLocation } from 'react-router-dom';
import './Footer.css';

/**
 * 전자상거래 표기 푸터 (DESIGN.md footer-beautyboy).
 * 랜딩(/)은 풀블리드 히어로 한 장이 화면 전부이므로 렌더하지 않는다.
 * 표기 값은 실존 정보가 아니다 — 데모 고지를 표기 블록 첫 줄에 명시하고,
 * 등록번호류는 자릿수만 맞춘 자리표시 값을 쓴다.
 * 이용약관·개인정보처리방침·고객센터는 대상 화면이 생기기 전까지 링크로 위장하지 않는다.
 */
export function Footer() {
  const { pathname } = useLocation();

  if (pathname === '/') {
    return null;
  }

  return (
    <footer className="bb-footer">
      <ul className="bb-footer__guides">
        <li>이용약관</li>
        <li>개인정보처리방침</li>
        <li>고객센터</li>
      </ul>
      <div className="bb-footer__legal">
        <p className="bb-footer__notice">
          본 사이트는 취업 포트폴리오용 데모입니다. 아래 표기는 형식 예시입니다.
        </p>
        <p>상호 뷰티보이(BeautyBoy) · 대표 홍길동 · 사업자등록번호 000-00-00000</p>
        <p>
          통신판매업 신고번호 제0000-서울00-00000호 · 주소 서울특별시 00구 00로 0 ·
          이메일 demo@beautyboy.example
        </p>
      </div>
      <div className="bb-footer__strip">
        <span className="bb-footer__wordmark">BEAUTY BOY</span>
        <span className="bb-footer__copyright">© 2026 BeautyBoy — Portfolio Demo</span>
      </div>
    </footer>
  );
}
