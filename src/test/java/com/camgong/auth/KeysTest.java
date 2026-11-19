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

    /** 010 T020: 아직 글자가 배정되지 않은 기본 무시 영역(Default_Ignorable 의 미할당 칸)은 화면에 아무것도 안 그린다. */
    @Test
    void unassignedDefaultIgnorableCodePointsAreInvisible() {
        for (int cp : new int[] {0x2065, 0xFFF0, 0xFFF8, 0xE0000, 0xE0002, 0xE0080, 0xE01F0, 0xE0FFF}) {
            assertThat(Character.getType(cp)).as(Integer.toHexString(cp)).isEqualTo(Character.UNASSIGNED);
            assertThat(Keys.hasInvisible("홍길동" + Character.toString(cp))).as(Integer.toHexString(cp)).isTrue();
        }
        // 다른 미할당 칸(JDK 보다 새 이모지 자리 등)은 상자나 글자로 보이므로 막지 않는다.
        assertThat(Character.getType(0x1FAE9)).isEqualTo(Character.UNASSIGNED);
        assertThat(Keys.hasInvisible("홍길동" + Character.toString(0x1FAE9))).isFalse();
    }
}
