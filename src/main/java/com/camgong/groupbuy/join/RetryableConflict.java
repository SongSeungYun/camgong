package com.camgong.groupbuy.join;

/**
 * 같은 요청을 새 트랜잭션에서 다시 처리하면 풀릴 수 있는 충돌(낙관적 버전 충돌, 같은 회원의 동시 이중 요청,
 * 읽은 뒤 바뀐 참여 행). 던지면 트랜잭션이 롤백되어 게이트가 예약한 수량도 함께 되돌아간다.
 */
class RetryableConflict extends RuntimeException {

    RetryableConflict(String message, Throwable cause) {
        super(message, cause);
    }
}
