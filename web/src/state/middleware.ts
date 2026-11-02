import { createListenerMiddleware } from "@reduxjs/toolkit"

export const listenerMiddleware = createListenerMiddleware()

export const startAppListening = listenerMiddleware.startListening.withTypes<RootState, AppDispatch>()
