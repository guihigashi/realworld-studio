import { createContext } from 'react'

export type SseConnectionStatus = 'connecting' | 'connected' | 'disconnected'

export interface EventsContextValue {
  status: SseConnectionStatus
  lastEvent: ArticleEvent | null
  addListener: (listener: (event: ArticleEvent) => void) => () => void
}

export const EventsContext = createContext<EventsContextValue>({
  status: 'disconnected',
  lastEvent: null,
  addListener: () => () => {},
})
