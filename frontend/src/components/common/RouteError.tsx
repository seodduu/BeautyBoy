import { useNavigate, useRouteError } from 'react-router-dom';
import { Button } from '../ui/Button';
import { useTitle } from '../../hooks/useTitle';
import './RouteError.css';

const MESSAGES = {
  notFound: {
    title: '요청하신 페이지를 찾을 수 없어요',
  },
  error: {
    title: '화면을 불러오지 못했어요',
  },
} as const;

/**
 * 라우트 오류·없는 페이지 화면 (DESIGN.md `route-error`).
 *
 * **에러 객체가 없으면 404다.** 이 컴포넌트는 두 자리에서 쓰인다 — 예외가 올라오는
 * errorElement 자리와, 매칭 라우트가 없을 때 도는 catch-all(`path: '*'`) 자리. 후자는 예외가
 * 없으므로 useRouteError()가 undefined 또는 null이다(react-router v7 실측: null). 그것을
 * 그대로 404 신호로 읽는다.
 *
 * **"다시 시도"가 navigate가 아니라 reload인 이유**: 이 화면에 도달했다는 것은 라우터가 이미
 * 오류 상태를 들고 있다는 뜻이라, 같은 주소로 navigate하면 그 상태 그대로 다시 오류 화면이 뜬다.
 * 문서를 새로 받아야 복구된다. (404 경로에서는 오류 상태가 없지만, 문구가 "다시 시도"인 이상
 * 두 경로에서 같은 일을 해야 손님이 예측할 수 있다.)
 */
export function RouteError() {
  // 404와 라우트 오류가 같은 화면이므로 제목도 하나다(설계 §1.3) — 원인별로 나누지 않는다.
  useTitle('페이지를 찾을 수 없어요');
  const error = useRouteError();
  const navigate = useNavigate();
  // catch-all(path: '*') 자리에는 예외가 없다 — 라우터 구현에 따라 useRouteError()가
  // undefined 또는 null을 준다(react-router v7 실측: null). 둘 다 "에러 없음"으로 읽는다.
  const isNotFound = error === undefined || error === null;

  if (!isNotFound) {
    // 원인 문자열(예외 메시지·스택)은 화면에 내지 않는다 — 개발 편의는 콘솔로만 충족한다.
    console.error(error);
  }

  const { title } = isNotFound ? MESSAGES.notFound : MESSAGES.error;

  const handleRetry = () => {
    window.location.reload();
  };

  const handleGoHome = () => {
    navigate('/');
  };

  return (
    <div className="bb-route-error" role="alert">
      <h1 className="bb-route-error__title">{title}</h1>
      <div className="bb-route-error__actions">
        <Button variant="primary" onClick={handleRetry}>
          다시 시도
        </Button>
        <Button variant="ghost" onClick={handleGoHome}>
          홈으로
        </Button>
      </div>
    </div>
  );
}
