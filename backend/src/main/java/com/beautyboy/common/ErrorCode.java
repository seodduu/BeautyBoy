package com.beautyboy.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    OK(HttpStatus.OK, "성공"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    MEMBER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "요청이 데이터 제약 조건과 충돌합니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    GOODS_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다"),
    GOODS_INVALID_SORT(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 조건입니다"),
    GOODS_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),

    // ---------------------------------------------------------------------
    // 아래는 Wave 2 상수 선점 구역.
    //
    // 이 파일은 common(동결) 소속이지만 "자기 도메인 접두사 상수 추가"는 허용돼 있다.
    // 문제는 Wave 2가 터미널 3개 병렬이라, 각 터미널이 enum 끝에 append하면
    // git이 같은 줄에서 3방향 충돌로 본다는 것이다. 그래서 착수 전에 세 세트를
    // 한 커밋으로 미리 넣어둔다 — 이후 각 터미널은 이 파일을 아예 열지 않는다.
    //
    // 여기 없는 코드가 필요해지면 임의로 추가하지 말고 중단하고 보고한다.
    // ---------------------------------------------------------------------

    // Wave 2 T1 — search
    SEARCH_QUERY_TOO_SHORT(HttpStatus.BAD_REQUEST, "검색어는 2자 이상이어야 합니다"),
    SEARCH_INVALID_SORT(HttpStatus.BAD_REQUEST, "지원하지 않는 검색 정렬 조건입니다"),

    // Wave 2 T1 — ranking
    RANKING_SNAPSHOT_NOT_READY(HttpStatus.NOT_FOUND, "아직 집계된 랭킹이 없습니다"),

    // Wave 2 T2 — cart
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 없는 상품입니다"),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다"),
    CART_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "수량이 올바르지 않습니다"),

    // Wave 2 T2 — order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"),
    ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 회원의 주문입니다"),
    ORDER_PRICE_MISMATCH(HttpStatus.CONFLICT, "상품 가격이 변경되었습니다. 장바구니를 다시 확인해주세요"),
    ORDER_OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족한 상품이 있습니다"),
    ORDER_INVALID_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다"),

    // Wave 2 T2 — payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다"),
    PAYMENT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 승인된 결제입니다"),
    // 금액 불일치는 위변조 시도일 수 있다 — 승인 취소까지 간 뒤 이 코드로 응답한다(설계 7장 결제 2단계).
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 금액이 주문 금액과 일치하지 않습니다"),
    PAYMENT_GATEWAY_FAILED(HttpStatus.BAD_GATEWAY, "결제 승인에 실패했습니다"),

    // Wave 2 T3 — review / qna / wishlist
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다"),
    REVIEW_NOT_PURCHASED(HttpStatus.FORBIDDEN, "구매한 상품에만 리뷰를 쓸 수 있습니다"),
    REVIEW_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 리뷰를 작성한 주문입니다"),
    REVIEW_HELPFUL_DUPLICATED(HttpStatus.CONFLICT, "이미 도움이 됐어요를 누른 리뷰입니다"),
    QNA_NOT_FOUND(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다"),
    QNA_SECRET_FORBIDDEN(HttpStatus.FORBIDDEN, "비밀글은 작성자만 볼 수 있습니다"),
    WISHLIST_ALREADY_ADDED(HttpStatus.CONFLICT, "이미 찜한 상품입니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
