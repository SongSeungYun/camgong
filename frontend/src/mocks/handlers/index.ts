import { authHandlers } from './auth'
import { groupBuyHandlers } from './groupbuys'
import { participationHandlers } from './participation'

export const handlers = [...authHandlers, ...groupBuyHandlers, ...participationHandlers]
