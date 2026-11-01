import { createRootRoute, Outlet } from "@tanstack/react-router"
import { Layout } from "@/components/Layout.tsx"

export const Route = createRootRoute({
  component: RouteComponent,
})

function RouteComponent() {
  return (
    <Layout>
      <Outlet />
    </Layout>
  )
}
