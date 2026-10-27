package com.camgong.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 요청(스레드) 단위로 Hibernate 가 실행한 SQL 개수를 센다. 테스트 프로파일에서만 켠다(application-test.yml).
 * MockMvc 는 호출 스레드에서 요청을 처리하므로 ThreadLocal 로 충분하다. JdbcClient 쿼리는 세지 않는다.
 */
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }
}
