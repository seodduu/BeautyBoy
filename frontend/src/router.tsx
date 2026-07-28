import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Main } from './pages/Main';
import { Sets } from './pages/Sets';
import { GoodsList } from './pages/GoodsList';
import { Detail } from './pages/Detail';
import { Routine } from './pages/Routine';
import { Cart } from './pages/Cart';
import { RequireAuth } from './components/auth/RequireAuth';
import { RequireAdmin } from './components/auth/RequireAdmin';
import { RouteError } from './components/common/RouteError';

// 쪼개는 화면: 대부분의 손님이 안 가거나(관리자·마이페이지), 결제 SDK를 물고 있거나(주문 계열),
// 상용 라우트가 아니다(Showcase). 첫 진입·탐색 주 경로는 정적 임포트로 남긴다 — 거기까지
// 쪼개면 첫 클릭마다 네트워크 왕복이 하나 더 붙어 번들을 줄이려다 체감 속도를 깎는다.
const Search = lazy(() => import('./pages/Search').then((m) => ({ default: m.Search })));
const Ranking = lazy(() => import('./pages/Ranking').then((m) => ({ default: m.Ranking })));
const Order = lazy(() => import('./pages/Order').then((m) => ({ default: m.Order })));
const OrderComplete = lazy(() =>
  import('./pages/OrderComplete').then((m) => ({ default: m.OrderComplete })),
);
const OrderFail = lazy(() => import('./pages/OrderFail').then((m) => ({ default: m.OrderFail })));
const Showcase = lazy(() => import('./pages/dev/Showcase').then((m) => ({ default: m.Showcase })));
const MyPageLayout = lazy(() =>
  import('./pages/mypage/MyPageLayout').then((m) => ({ default: m.MyPageLayout })),
);
const MyOrders = lazy(() => import('./pages/mypage/MyOrders').then((m) => ({ default: m.MyOrders })));
const MyWishlist = lazy(() =>
  import('./pages/mypage/MyWishlist').then((m) => ({ default: m.MyWishlist })),
);
const MyReviews = lazy(() =>
  import('./pages/mypage/MyReviews').then((m) => ({ default: m.MyReviews })),
);
const MyProfile = lazy(() =>
  import('./pages/mypage/MyProfile').then((m) => ({ default: m.MyProfile })),
);
const AdminLayout = lazy(() =>
  import('./pages/admin/AdminLayout').then((m) => ({ default: m.AdminLayout })),
);
const AdminGoods = lazy(() =>
  import('./pages/admin/AdminGoods').then((m) => ({ default: m.AdminGoods })),
);
const AdminRoutine = lazy(() =>
  import('./pages/admin/AdminRoutine').then((m) => ({ default: m.AdminRoutine })),
);
const AdminQna = lazy(() => import('./pages/admin/AdminQna').then((m) => ({ default: m.AdminQna })));

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        // 경로 없는 경계. errorElement를 루트(<Layout/>)에 걸면 오류 시 헤더·푸터째 사라져
        // 손님이 다른 곳으로 갈 길을 잃는다. 한 겹 안쪽에 걸어야 <Outlet/> 자리에서만 대체된다.
        errorElement: <RouteError />,
        children: [
        { index: true, element: <Home /> },
        { path: 'login', element: <Login /> },
        { path: 'signup', element: <Signup /> },
        {
          path: 'main',
          element: (
            <RequireAuth>
              <Main />
            </RequireAuth>
          ),
        },
        {
          path: 'sets',
          element: (
            <RequireAuth>
              <Sets />
            </RequireAuth>
          ),
        },
        {
          path: 'goods',
          element: (
            <RequireAuth>
              <GoodsList />
            </RequireAuth>
          ),
        },
        {
          path: 'goods/:goodsNo',
          element: (
            <RequireAuth>
              <Detail />
            </RequireAuth>
          ),
        },
        {
          path: 'search',
          element: (
            <RequireAuth>
              <Search />
            </RequireAuth>
          ),
        },
        {
          path: 'ranking',
          element: (
            <RequireAuth>
              <Ranking />
            </RequireAuth>
          ),
        },
        // 로그인 불필요 — 설계 8장 "비회원=3문항 퀴즈"이고 SecurityConfig가 GET /routines·
        // POST /compat/check를 permitAll로 열어둔다. 전체 담기(POST /cart/items/bulk)만
        // 인증이 필요하며, 비회원이 누르면 401 → 토스트로 실패 안내한다(가드로 막지 않는다).
        { path: 'routine', element: <Routine /> },
        {
          path: 'cart',
          element: (
            <RequireAuth>
              <Cart />
            </RequireAuth>
          ),
        },
        {
          path: 'order',
          element: (
            <RequireAuth>
              <Order />
            </RequireAuth>
          ),
        },
        // 토스 리다이렉트 착지점. 전체 페이지 로드로 들어오므로 App의 /auth/refresh 세션 복원이
        // 끝날 때까지 RequireAuth가 판정을 미룬다 — 승인 요청에 액세스 토큰이 필요하다.
        {
          path: 'order/complete',
          element: (
            <RequireAuth>
              <OrderComplete />
            </RequireAuth>
          ),
        },
        // 토스 실패 리다이렉트 착지점. API 호출도 인증도 필요 없는 화면이라 RequireAuth로 감싸지
        // 않는다 — 감싸면 리프레시가 실패한 상태로 돌아온 사용자가 실패 사유 대신 /login으로 튕긴다.
        { path: 'order/fail', element: <OrderFail /> },
        {
          path: 'mypage',
          element: (
            <RequireAuth>
              <MyPageLayout />
            </RequireAuth>
          ),
          children: [
            { index: true, element: <Navigate to="orders" replace /> },
            { path: 'orders', element: <MyOrders /> },
            { path: 'orders/:orderNo', element: <MyOrders /> },
            { path: 'wishlist', element: <MyWishlist /> },
            { path: 'reviews', element: <MyReviews /> },
            { path: 'profile', element: <MyProfile /> },
          ],
        },
        {
          path: 'admin',
          element: (
            <RequireAdmin>
              <AdminLayout />
            </RequireAdmin>
          ),
          children: [
            { index: true, element: <Navigate to="goods" replace /> },
            { path: 'goods', element: <AdminGoods /> },
            { path: 'routine', element: <AdminRoutine /> },
            { path: 'qna', element: <AdminQna /> },
          ],
        },
          // 매칭되는 주소가 없을 때. 예외가 아니므로 errorElement로는 오지 않는다 —
          // 같은 화면을 여기서 직접 렌더한다(404와 오류는 손님에게 같은 사건이다).
          { path: '*', element: <RouteError /> },
        ],
      },
    ],
  },
  // 헤더/푸터 없이 토큰·프리미티브·카드 상태만 대조하는 dev 전용 화면. 상용 라우트가 아니다.
  // Layout 밖 라우트라 Layout의 Suspense 경계를 못 타므로 여기서 직접 감싼다.
  {
    path: 'dev/components',
    element: (
      <Suspense fallback={null}>
        <Showcase />
      </Suspense>
    ),
  },
]);
