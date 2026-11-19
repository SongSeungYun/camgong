package com.camgong.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 요청(스레드) 단위로 Hibernate 가 실행한 SQL 개수를 센다. 테스트 프로파일에서만 켠다(application-test.yml).
 * MockMvc 는 호출 스레드에서 요청을 처리하므로 ThreadLocal 로 충분하다. JdbcClient 쿼리는 세지 않는다.
 */
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<java.util.List<String>> SQL = ThreadLocal.withInitial(java.util.ArrayList::new);

    public static void reset() {
        COUNT.get()[0] = 0;
        SQL.get().clear();
    }

    /** reset 뒤에 이 스레드에서 실행된 Hibernate SQL(실행 계획이 바뀌는 쿼리 모양을 확인할 때 쓴다). */
    public static java.util.List<String> statements() {
        return java.util.List.copyOf(SQL.get());
    }

    public static int count() {
        return COUNT.get()[0];
    }

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        SQL.get().add(sql);
        return sql;
    }
}
