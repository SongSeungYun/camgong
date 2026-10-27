const won = new Intl.NumberFormat('ko-KR')

export const formatWon = (n: number): string => `${won.format(n)}원`

export const amount = (unitPrice: number, quantity: number): number => unitPrice * quantity
