import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { Home } from './pages/Home';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Main } from './pages/Main';
import { GoodsList } from './pages/GoodsList';
import { Detail } from './pages/Detail';
import { Search } from './pages/Search';
import { Ranking } from './pages/Ranking';
import { Cart } from './pages/Cart';
import { Order } from './pages/Order';
import { RequireAuth } from './components/auth/RequireAuth';
import { Showcase } from './pages/dev/Showcase';

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
    ],
  },
  // 헤더/푸터 없이 토큰·프리미티브·카드 상태만 대조하는 dev 전용 화면. 상용 라우트가 아니다.
  { path: 'dev/components', element: <Showcase /> },
]);
