import { configureStore } from "@reduxjs/toolkit"
import { api } from "@/state/api.ts"
import { listenerMiddleware } from "@/state/middleware.ts"

export const store = configureStore({
  reducer: {
    [api.reducerPath]: api.reducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(api.middleware, listenerMiddleware.middleware),
})
