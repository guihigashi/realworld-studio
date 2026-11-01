import { useContext } from "react"
import { EventsContext, type SseConnectionStatus } from "@/hooks/eventsContextDef.ts"

export type { SseConnectionStatus }

export function useEvents() {
  return useContext(EventsContext)
}
