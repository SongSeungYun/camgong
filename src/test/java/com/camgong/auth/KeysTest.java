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

    @Test
    void stripsUnicodeSpacesThatTrimLeaves() {
        assertThat(Keys.normalize("\u3000\u00A0\u2003Kim\u3000\t")).isEqualTo("kim");
        assertThat(Keys.strip("홍 길동\u3000")).isEqualTo("홍 길동");   // 가운데 공백은 그대로
    }

    @Test
    void detectsInvisibleFormatCharacters() {
        assertThat(Keys.hasInvisible("홍\u200B길동")).isTrue();
        assertThat(Keys.hasInvisible("홍길동\uFEFF")).isTrue();
        assertThat(Keys.hasInvisible("홍길동" + Character.toString(0xE0020))).isTrue();   // BMP 밖 태그 문자
        for (int filler : new int[] {0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800}) {             // 한글 채움 문자·점자 빈칸
            assertThat(Keys.hasInvisible("홍길동" + Character.toString(filler))).as(Integer.toHexString(filler)).isTrue();
        }
        assertThat(Keys.hasInvisible("홍 길동")).isFalse();
        assertThat(Keys.hasInvisible("홍\u0001길동")).isTrue();                               // 제어 문자
    }
}
