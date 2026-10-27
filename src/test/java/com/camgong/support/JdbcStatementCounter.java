package com.camgong.support;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * JDBC 수준에서 요청(스레드) 단위로 준비·생성된 SQL 문장 수를 센다. Hibernate 의 StatementInspector(QueryCountInspector)는
 * JdbcClient/JdbcTemplate 쿼리를 못 세므로, 데이터소스를 감싸 둘 다 센다. 의존성 추가 없이 java.lang.reflect.Proxy 로 한다.
 * 테스트 구성(IntegrationTestBase.TestClockConfig)에서만 등록한다. 트랜잭션 제어 문장(setAutoCommit 등)은 세지 않는다.
 */
public class JdbcStatementCounter implements BeanPostProcessor {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource ds) || bean instanceof ProxyMarker) {
            return bean;
        }
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { DataSource.class, ProxyMarker.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == ProxyMarker.class) {
                        return null;
                    }
                    Object result = invoke(ds, method, args);
                    return result instanceof Connection c && method.getName().equals("getConnection") ? wrap(c) : result;
                });
    }

    interface ProxyMarker {
    }

    private static Connection wrap(Connection c) {
        return (Connection) Proxy.newProxyInstance(JdbcStatementCounter.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("prepareStatement") || name.equals("prepareCall") || name.equals("createStatement")) {
                        COUNT.get()[0]++;
                    }
                    return invoke(c, method, args);
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
