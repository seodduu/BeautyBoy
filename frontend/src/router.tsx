import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Main } from './pages/Main';
import { GoodsList } from './pages/GoodsList';
import { Detail } from './pages/Detail';
import { Search } from './pages/Search';
import { Ranking } from './pages/Ranking';
import { Routine } from './pages/Routine';
import { Cart } from './pages/Cart';
import { Order } from './pages/Order';
import { OrderComplete } from './pages/OrderComplete';
import { OrderFail } from './pages/OrderFail';
import { RequireAuth } from './components/auth/RequireAuth';
import { RequireAdmin } from './components/auth/RequireAdmin';
import { Showcase } from './pages/dev/Showcase';
import { MyPageLayout } from './pages/mypage/MyPageLayout';
import { MyOrders } from './pages/mypage/MyOrders';
import { MyWishlist } from './pages/mypage/MyWishlist';
import { MyReviews } from './pages/mypage/MyReviews';
import { MyProfile } from './pages/mypage/MyProfile';
import { AdminLayout } from './pages/admin/AdminLayout';
import { AdminGoods } from './pages/admin/AdminGoods';
import { AdminRoutine } from './pages/admin/AdminRoutine';
import { AdminQna } from './pages/admin/AdminQna';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
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
      {
        path: 'order/fail',
        element: (
          <RequireAuth>
            <OrderFail />
          </RequireAuth>
        ),
      },
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
    ],
  },
  // 헤더/푸터 없이 토큰·프리미티브·카드 상태만 대조하는 dev 전용 화면. 상용 라우트가 아니다.
  { path: 'dev/components', element: <Showcase /> },
]);
