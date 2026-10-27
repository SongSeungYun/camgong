import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchCheckSheet, type CheckFilter } from './api'

export const useCheckSheet = (id: number, filter: CheckFilter) =>
  useQuery({ queryKey: ['check', id, filter], queryFn: () => fetchCheckSheet(id, filter), placeholderData: keepPreviousData, enabled: Number.isInteger(id) })
