import { authHandlers } from './auth'
import { checkHandlers } from './check'
import { groupBuyHandlers } from './groupbuys'
import { meHandlers } from './me'
import { notificationHandlers } from './notifications'
import { participationHandlers } from './participation'

export const handlers = [...authHandlers, ...groupBuyHandlers, ...participationHandlers, ...meHandlers, ...checkHandlers, ...notificationHandlers]
