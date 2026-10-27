package com.camgong.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.camgong.member.MemberRepository;

/** 테스트 프로파일에서 Hibernate 쿼리가 실제로 세어지는지(스파이크 가정 확인). */
class QueryCountInspectorIT extends IntegrationTestBase {

    @Autowired
    MemberRepository members;

    @Test
    void countsHibernateStatementsPerThread() {
        QueryCountInspector.reset();
        members.findById(1L);
        members.findById(2L);

        assertThat(QueryCountInspector.count()).isEqualTo(2);
    }
}
