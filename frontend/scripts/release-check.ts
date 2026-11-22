import { readFileSync } from 'node:fs'
import { findPlaceholders } from './check-placeholders.ts'

// npm run build:release가 빌드 전에 실행한다(deploy/web.Dockerfile). 자리표시가 하나라도 있으면 1로 끝난다.
const FILES = ['src/features/privacy/PrivacyText.tsx', 'src/features/auth/ConsentText.tsx']
const found = FILES.flatMap((f) => findPlaceholders(readFileSync(new URL(`../${f}`, import.meta.url), 'utf8')).map((p) => `${f}: ${p}`))
if (found.length) {
  console.error(`자리표시가 남아 있어 배포 빌드를 멈춘다:\n${found.join('\n')}`)
  process.exit(1)
}
