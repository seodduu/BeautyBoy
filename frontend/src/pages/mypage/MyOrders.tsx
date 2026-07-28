import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchOrderDetail, fetchOrders } from '../../api/order';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { Pager } from '../../components/ui/Pager';
import { Skeleton } from '../../components/ui/Skeleton';
import { formatWon } from '../../components/ui/Price';
import './MyOrders.css';

/** 한 페이지 건수 — 서버 기본값(size=10)과 맞춘다. */
const PAGE_SIZE = 10;

/** URL의 page는 1-based 표시값이다. 손으로 친 미지값·0 이하는 1로 접어 빈 화면을 막는다. */
function normalizePage(raw: string | null): number {
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1;
}

/** 목록·상세 공용 "대표상품 외 N건" 표기. itemCount가 1이면 대표상품명만 보여준다. */
function buildOrderLabel(representativeGoodsName: string, itemCount: number): string {
  if (itemCount <= 1) {
    return representativeGoodsName;
  }
  return `${representativeGoodsName} 외 ${itemCount - 1}건`;
}

/**
 * 마이페이지 주문내역 `/mypage/orders`(목록) · `/mypage/orders/:orderNo`(상세).
 * 하나의 컴포넌트가 라우트 파라미터 유무로 목록/상세를 가른다 — 두 화면이 같은 "주문 한 줄"
 * 개념을 공유하고, 상세는 목록 탭의 하위 화면이라는 성격(MyPageLayout 주석 참고)에 맞춘다.
 *
 * 상세는 주문 시점 스냅샷(OrderDetail)만 렌더한다 — 현재 회원 프로필/배송지를 조인하지 않는다
 * (project law: 주문 시점 데이터는 스냅샷). 금액도 서버가 계산한 payableAmount 그대로 보여준다.
 */
export function MyOrders() {
  const { orderNo } = useParams<{ orderNo?: string }>();

  if (orderNo) {
    return <OrderDetailView orderNo={orderNo} />;
  }
  return <OrderListView />;
}

function OrderListView() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = normalizePage(searchParams.get('page'));

  const ordersQuery = useQuery({
    queryKey: ['myOrders', page],
    // 페이지 전환 중 이전 목록을 유지한다 — 매번 스켈레톤으로 돌아가면 목록 높이가 무너진다.
    placeholderData: keepPreviousData,
    // URL은 1-based 표시값, API는 0-based다.
    queryFn: () => fetchOrders(page - 1, PAGE_SIZE),
  });

  const handlePageChange = (next: number) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      // 1페이지는 파라미터를 지운다 — `?page=1`과 무파라미터가 같은 화면의 두 주소가 되지 않게.
      if (next <= 1) params.delete('page');
      else params.set('page', String(next));
      return params;
    });
  };

  if (ordersQuery.isLoading) {
    return (
      <div className="bb-my-orders">
        <Skeleton ratio="16 / 5" />
      </div>
    );
  }

  if (ordersQuery.isError || !ordersQuery.data) {
    return (
      <div className="bb-my-orders">
        <ErrorState title="주문내역을 불러오지 못했어요" onRetry={() => ordersQuery.refetch()} />
      </div>
    );
  }

  const data = ordersQuery.data;

  if (data.content.length === 0) {
    return (
      <div className="bb-my-orders">
        <EmptyState
          title="아직 주문 내역이 없어요"
          description="마음에 드는 상품을 담아 첫 주문을 시작해 보세요."
          action={{ label: '상품 보러 가기', onClick: () => navigate('/goods') }}
        />
      </div>
    );
  }

  return (
    <div className="bb-my-orders">
      <ul className="bb-my-orders__list">
        {data.content.map((order) => (
          <li key={order.orderNo}>
            <button
              type="button"
              className="bb-my-orders__row"
              onClick={() => navigate(`/mypage/orders/${order.orderNo}`)}
            >
              <div className="bb-my-orders__row-info">
                <span className="bb-my-orders__row-date">
                  {order.orderedAt.slice(0, 10)}
                </span>
                <span className="bb-my-orders__row-name">
                  {buildOrderLabel(order.representativeGoodsName, order.itemCount)}
                </span>
                {/* 재주문 등으로 날짜·상품명·금액이 같은 건이 쌓여도 구분할 수 있도록 주문번호를 보조 정보로 노출 */}
                <span className="bb-my-orders__row-order-no">주문번호 {order.orderNo}</span>
              </div>
              <span className="bb-my-orders__row-amount">{formatWon(order.payableAmount)}</span>
            </button>
          </li>
        ))}
      </ul>
      <Pager page={page} totalPages={data.totalPages} onPageChange={handlePageChange} />
    </div>
  );
}

function OrderDetailView({ orderNo }: { orderNo: string }) {
  const detailQuery = useQuery({
    queryKey: ['myOrderDetail', orderNo],
    queryFn: () => fetchOrderDetail(orderNo),
  });

  if (detailQuery.isLoading) {
    return (
      <div className="bb-order-detail">
        <Skeleton ratio="16 / 9" />
      </div>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="bb-order-detail">
        <ErrorState title="주문 정보를 불러오지 못했어요" onRetry={() => detailQuery.refetch()} />
      </div>
    );
  }

  const detail = detailQuery.data;

  return (
    <div className="bb-order-detail">
      <h2 className="bb-order-detail__title">
        {buildOrderLabel(detail.items[0]?.goodsName ?? '', detail.items.length)}
      </h2>
      <p className="bb-order-detail__meta">주문번호 {detail.orderNo}</p>

      <section className="bb-order-detail__section">
        <h3 className="bb-order-detail__section-title">주문 상품</h3>
        <ul className="bb-order-detail__items">
          {detail.items.map((item, index) => (
            <li key={`${item.goodsName}-${index}`} className="bb-order-detail__item">
              <div className="bb-order-detail__item-info">
                <span className="bb-order-detail__item-name">{item.goodsName}</span>
                {item.optionName && (
                  <span className="bb-order-detail__item-option">{item.optionName}</span>
                )}
              </div>
              <div className="bb-order-detail__item-meta">
                <span>{item.quantity}개</span>
                <span>{formatWon(item.lineAmount)}</span>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {/* 주문 시점 배송지 스냅샷 — 현재 회원 배송지(members/me/addresses)를 참조하지 않는다. */}
      <section className="bb-order-detail__section">
        <h3 className="bb-order-detail__section-title">배송지</h3>
        <p className="bb-order-detail__receiver">{detail.receiverName}</p>
        <p className="bb-order-detail__phone">{detail.receiverPhone}</p>
        <p className="bb-order-detail__address">
          ({detail.zipcode}) {detail.address1} {detail.address2}
        </p>
      </section>

      <section className="bb-order-detail__section bb-order-detail__amount">
        <span>결제금액</span>
        <span className="bb-order-detail__amount-value">{formatWon(detail.payableAmount)}</span>
      </section>
    </div>
  );
}
