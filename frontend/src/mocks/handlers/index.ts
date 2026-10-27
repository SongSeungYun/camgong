import { authHandlers } from './auth'
import { groupBuyHandlers } from './groupbuys'

export const handlers = [...authHandlers, ...groupBuyHandlers]
