package com.camgong.auth;

import java.util.Locale;

/** 이메일 키·표시 이름 키 공용 정규화: 앞뒤 공백 제거 + 소문자(로캘 무관). 한글은 그대로다. */
public final class Keys {

    private Keys() {
    }

    public static String normalize(String value) {
        return strip(value).toLowerCase(Locale.ROOT);
    }

    /** 앞뒤 공백 제거. trim()·strip() 이 남기는 전각 공백(U+3000)·줄바꿈 없는 공백(U+00A0) 같은 유니코드 공백도 지운다. */
    public static String strip(String value) {
        int from = 0;
        int to = value.length();
        while (from < to && isSpace(value.charAt(from))) {
            from++;
        }
        while (to > from && isSpace(value.charAt(to - 1))) {
            to--;
        }
        return value.substring(from, to);
    }

    /**
     * 보이지 않는 글자가 있는지: 서식 문자(폭 없는 공백 U+200B, 태그 문자 U+E0020 등)와 빈칸처럼 보이는 글자(한글 채움 문자·점자 빈칸).
     * 같아 보이는 다른 이름(사칭)을 막는 데 쓴다. BMP 밖 글자도 보도록 코드 포인트 단위로 본다.
     */
    public static boolean hasInvisible(String value) {
        return value.codePoints().anyMatch(c -> Character.getType(c) == Character.FORMAT
                || c == 0x115F || c == 0x1160 || c == 0x3164 || c == 0xFFA0 || c == 0x2800);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
