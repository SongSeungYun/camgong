package com.camgong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeysTest {

    @Test
    void trimsAndLowercases() {
        assertThat(Keys.normalize("  Student@School.AC.KR ")).isEqualTo("student@school.ac.kr");
    }

    @Test
    void leavesKoreanAlone() {
        assertThat(Keys.normalize(" 햇반왕 ")).isEqualTo("햇반왕");
    }

    @Test
    void caseDifferencesCollapse() {
        assertThat(Keys.normalize("Kim")).isEqualTo(Keys.normalize("kIM"));
    }
}
