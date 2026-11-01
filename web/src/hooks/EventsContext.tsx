import { type ReactNode, useEffect, useRef, useState } from "react"
import { EventsContext, type SseConnectionStatus } from "@/hooks/eventsContextDef.ts"

export function EventsProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<SseConnectionStatus>("connecting")
  const [lastEvent, setLastEvent] = useState<ArticleEvent | null>(null)
  const listenersRef = useRef<Set<(event: ArticleEvent) => void>>(new Set())

  useEffect(() => {
    let es: EventSource | null = null
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
    let isDisposed = false

    const connect = () => {
      if (isDisposed) return
      setStatus("connecting")

      try {
        es = new EventSource("/api/events")

        es.onopen = () => {
          if (!isDisposed) {
            setStatus("connected")
          }
        }

        const handleRawData = (dataStr: string) => {
          try {
            const event: ArticleEvent = JSON.parse(dataStr)
            setLastEvent(event)
            listenersRef.current.forEach((listener) => {
              try {
                listener(event)
              } catch (err) {
                console.error("Error in event listener:", err)
              }
            })
          } catch (e) {
            console.warn("Failed to parse SSE event data:", dataStr, e)
          }
        }

        es.addEventListener("article_event", (msg: MessageEvent) => {
          handleRawData(msg.data)
        })

        es.onmessage = (msg: MessageEvent) => {
          handleRawData(msg.data)
        }

        es.onerror = () => {
          if (!isDisposed) {
            setStatus("disconnected")
            if (es) {
              es.close()
              es = null
            }
            reconnectTimeout = setTimeout(connect, 3000)
          }
        }
      } catch (err) {
        console.error("Failed to initialize EventSource:", err)
        setStatus("disconnected")
        reconnectTimeout = setTimeout(connect, 3000)
      }
    }

    connect()

    return () => {
      isDisposed = true
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      if (es) es.close()
    }
  }, [])

  const addListener = (listener: (event: ArticleEvent) => void) => {
    listenersRef.current.add(listener)
    return () => {
      listenersRef.current.delete(listener)
    }
  }

  return <EventsContext.Provider value={{ status, lastEvent, addListener }}>{children}</EventsContext.Provider>
}
