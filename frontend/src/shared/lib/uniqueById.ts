/** 커서 쪽 사이에 데이터가 바뀌어 같은 항목이 두 번 오더라도 한 번만 보이게 한다(먼저 온 것을 남김). */
export const uniqueById = <T extends { id: number }>(items: T[]): T[] => {
  const seen = new Set<number>()
  return items.filter((x) => !seen.has(x.id) && !!seen.add(x.id))
}
