import { authHandlers } from './auth'
import { groupBuyHandlers } from './groupbuys'
import { meHandlers } from './me'
import { participationHandlers } from './participation'

export const handlers = [...authHandlers, ...groupBuyHandlers, ...participationHandlers, ...meHandlers]
