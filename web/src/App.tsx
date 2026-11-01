import { CssBaseline, ThemeProvider } from "@mui/material"
import { createRouter, RouterProvider } from "@tanstack/react-router"
import { routeTree } from "@/routeTree.gen.ts"
import { theme } from "@/theme/theme.ts"
import { EventsProvider } from "@/hooks/EventsContext.tsx"

const router = createRouter({ routeTree })

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <EventsProvider>
        <RouterProvider router={router} />
      </EventsProvider>
    </ThemeProvider>
  )
}
