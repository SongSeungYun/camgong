// 서버와 같은 텍스트 규칙(002·001 계약): 앞뒤 유니코드 공백 제거, 길이는 글자(code point) 수
const INVISIBLE = /[\p{Cc}\p{Cf}\u115f\u1160\u3164\uffa0\u2800]/u // 제어 문자(탭·개행 포함), 서식 문자(폭 없는 공백 등), 한글 채움 문자, 점자 빈칸

export const strip = (s: string) => s.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '')
export const chars = (s: string) => [...s].length
export const hasInvisible = (s: string) => INVISIBLE.test(s)
/** 공백·보이지 않는 글자뿐이면 true */
export const isBlank = (s: string) => /^[\p{White_Space}\p{Cf}\u115f\u1160\u3164\uffa0\u2800]*$/u.test(s)
