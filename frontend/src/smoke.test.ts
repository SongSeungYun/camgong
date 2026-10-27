import { expect, test } from 'vitest'

test('테스트 환경이 jsdom으로 뜬다', () => {
  expect(document.body).toBeDefined()
})
