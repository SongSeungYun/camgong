package com.camgong.auth;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 기동 시 JDBC 연결 설정이 영향 행 판정(useAffectedRows=true)을 쓰는지 확인한다. 한도·잠금·토큰 단일 사용·중복 가드가
 * "ON DUPLICATE KEY UPDATE 가 값이 안 바뀌면 0행" 이라는 판정에 기대는데, DB_URL 환경변수로 URL 을 바꾸면서 이 옵션이 빠지면
 * 기존 행에도 1이 돌아와 한도 검사 없이 메일이 나가는 식으로 조용히 깨진다. 임시 테이블로 검사하므로 데이터에는 손대지 않는다(운영 DB 계정에 CREATE TEMPORARY TABLES 권한이 필요하다).
 */
@Component
class DatabaseSettingsCheck implements InitializingBean {

    private final DataSource dataSource;

    DatabaseSettingsCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE IF NOT EXISTS camgong_affected_rows_check (id INT PRIMARY KEY, v INT)");
            checkNoLegacySchema(st);
            try {
                st.executeUpdate("INSERT INTO camgong_affected_rows_check VALUES (1, 1)");
                int rows = st.executeUpdate(
                        "INSERT INTO camgong_affected_rows_check VALUES (1, 1) ON DUPLICATE KEY UPDATE v = v");
                if (rows != 0) {
                    throw new IllegalStateException("JDBC URL 에 useAffectedRows=true 가 필요하다(값이 안 바뀐 ON DUPLICATE KEY UPDATE 가 "
                            + rows + " 행으로 보고됨). DB_URL 을 바꿀 때 이 옵션을 함께 넣어야 한다.");
                }
            } finally {
                st.execute("DROP TEMPORARY TABLE IF EXISTS camgong_affected_rows_check");
            }
        }
    }

    /**
     * 변경 B2 이전 스키마(pending_signup 에 display_name·password_hash 가 NOT NULL 로 남은 DB)는 schema.sql 이
     * CREATE TABLE IF NOT EXISTS 라서 바뀌지 않고, 그대로 두면 신청마다 500 이 난다. 알려 주고 기동을 막는다.
     */
    static void checkNoLegacySchema(Statement st) throws SQLException {
        try (var rs = st.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'pending_signup' AND column_name IN ('display_name', 'password_hash')")) {
            if (rs.next()) {
                throw new IllegalStateException("pending_signup 이 변경 B2 이전 스키마(" + rs.getString(1)
                        + " 컬럼)다. 로컬 DB 를 새로 만들어야 한다(예: docker rm -f 후 새 MySQL 컨테이너).");
            }
        }
    }
}
