import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// docs/의 안내문 본문(두 구분선 --- 사이)을 화면 글자와 비교하기 위한 도우미.
// 이 파일 기준(__dirname)으로 읽어 작업 디렉터리와 무관하다. jsdom에선 import.meta.url이 file:이 아니고, ?raw는 frontend 밖이라 Vite가 막는다
export const docBody = (name: string) => readFileSync(resolve(__dirname, '../../../docs', name), 'utf8').split(/\n---\n/)[1]!

/** 마크다운 표기(**, `, 표 구분, 목록 기호)를 걷어낸 글자를 공백 없이 */
export const plain = (s: string) =>
  s
    .split('\n')
    .filter((l) => !/^\|[-\s|]+\|$/.test(l)) // 표 구분 줄
    .map((l) => l.replace(/^- /, '').replace(/\*\*|`|\|/g, ''))
    .join('')
    .replace(/\s+/g, '')
