import { useState, type ReactNode } from "react"
import {
  AppBar,
  Box,
  Chip,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material"
import {
  Api,
  AutoAwesome,
  FiberManualRecord,
  HistoryEdu,
  Menu,
  OpenInNew,
  PeopleAlt,
  RateReview,
} from "@mui/icons-material"
import { Link, useRouterState } from "@tanstack/react-router"
import { useEvents } from "@/hooks/useEvents.ts"

const DRAWER_WIDTH = 250

const NAV_ITEMS = [
  { label: "Personas Hub", path: "/", icon: <PeopleAlt /> },
  { label: "Article Studio", path: "/studio", icon: <AutoAwesome /> },
  { label: "Approval Board", path: "/approval", icon: <RateReview /> },
  { label: "Publishing Audit", path: "/audit", icon: <HistoryEdu /> },
]

export function Layout({ children }: { children: ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down("md"))
  const { status } = useEvents()
  const routerState = useRouterState()
  const currentPath = routerState.location.pathname

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  const sseStatusBadge = (() => {
    switch (status) {
      case "connected":
        return (
          <Tooltip title="Real-time SSE push connected to ZIO Hub">
            <Chip
              icon={<FiberManualRecord sx={{ fontSize: "10px !important", color: "#10b981 !important" }} />}
              label="Live Sync"
              size="small"
              sx={{
                bgcolor: "rgba(16, 185, 129, 0.12)",
                color: "#10b981",
                border: "1px solid rgba(16, 185, 129, 0.3)",
                fontWeight: 600,
                fontSize: "0.75rem",
              }}
            />
          </Tooltip>
        )
      case "connecting":
        return (
          <Tooltip title="Connecting to /api/events...">
            <Chip
              icon={<FiberManualRecord sx={{ fontSize: "10px !important", color: "#f59e0b !important" }} />}
              label="Connecting..."
              size="small"
              sx={{
                bgcolor: "rgba(245, 158, 11, 0.12)",
                color: "#f59e0b",
                border: "1px solid rgba(245, 158, 11, 0.3)",
                fontSize: "0.75rem",
              }}
            />
          </Tooltip>
        )
      default:
        return (
          <Tooltip title="Disconnected from SSE stream. Reconnecting in 3s...">
            <Chip
              icon={<FiberManualRecord sx={{ fontSize: "10px !important", color: "#ef4444 !important" }} />}
              label="Offline"
              size="small"
              sx={{
                bgcolor: "rgba(239, 68, 68, 0.12)",
                color: "#ef4444",
                border: "1px solid rgba(239, 68, 68, 0.3)",
                fontSize: "0.75rem",
              }}
            />
          </Tooltip>
        )
    }
  })()

  const drawerContent = (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      {/* Drawer Brand Header */}
      <Box sx={{ p: 2.5, display: "flex", alignItems: "center", gap: 1.5 }}>
        <Box
          sx={{
            width: 38,
            height: 38,
            borderRadius: "10px",
            bgcolor: "primary.main",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontWeight: 800,
            fontSize: "1.2rem",
            boxShadow: "0 4px 12px rgba(99, 102, 241, 0.4)",
          }}
        >
          RW
        </Box>
        <Box>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
            Conduit Studio
          </Typography>
          <Typography variant="caption" color="text.secondary">
            AI Seeder & Pipeline
          </Typography>
        </Box>
      </Box>

      {/* Nav links */}
      <List sx={{ px: 1.5, py: 1, flex: 1 }}>
        {NAV_ITEMS.map((item) => {
          const isSelected =
            item.path === "/"
              ? currentPath === "/"
              : currentPath.startsWith(item.path)

          return (
            <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link}
                to={item.path}
                onClick={() => {
                  if (isMobile) setMobileOpen(false)
                }}
                sx={{
                  borderRadius: 2,
                  bgcolor: isSelected ? "rgba(99, 102, 241, 0.15)" : "transparent",
                  color: isSelected ? "primary.light" : "text.primary",
                  border: isSelected ? "1px solid rgba(99, 102, 241, 0.3)" : "1px solid transparent",
                  "&:hover": {
                    bgcolor: isSelected ? "rgba(99, 102, 241, 0.22)" : "rgba(255, 255, 255, 0.04)",
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isSelected ? "primary.light" : "text.secondary",
                    minWidth: 40,
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Typography
                      sx={{
                        fontWeight: isSelected ? 600 : 500,
                        fontSize: "0.92rem",
                      }}
                    >
                      {item.label}
                    </Typography>
                  }
                />
              </ListItemButton>
            </ListItem>
          )
        })}
      </List>

      {/* Drawer Footer with Quick Swagger Link */}
      <Box sx={{ p: 2, borderTop: "1px solid rgba(255, 255, 255, 0.08)" }}>
        <ListItemButton
          component="a"
          href="/docs/openapi"
          target="_blank"
          rel="noopener noreferrer"
          sx={{
            borderRadius: 2,
            border: "1px solid rgba(255, 255, 255, 0.08)",
            bgcolor: "rgba(255, 255, 255, 0.02)",
            "&:hover": { bgcolor: "rgba(255, 255, 255, 0.06)" },
          }}
        >
          <ListItemIcon sx={{ minWidth: 36, color: "secondary.light" }}>
            <Api fontSize="small" />
          </ListItemIcon>
          <ListItemText
            primary={
              <Typography sx={{ fontSize: "0.85rem", fontWeight: 600 }}>
                Swagger Docs
              </Typography>
            }
            secondary={
              <Typography sx={{ fontSize: "0.72rem", color: "text.secondary" }}>
                OpenAPI 3.0
              </Typography>
            }
          />
          <OpenInNew sx={{ fontSize: 16, color: "text.secondary" }} />
        </ListItemButton>
      </Box>
    </Box>
  )

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      {/* Top App Bar */}
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
        }}
      >
        <Toolbar sx={{ justifyContent: "space-between" }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
            {isMobile && (
              <IconButton
                color="inherit"
                aria-label="open drawer"
                edge="start"
                onClick={handleDrawerToggle}
              >
                <Menu />
              </IconButton>
            )}
            <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 600, fontSize: "1.05rem" }}>
              RealWorld Conduit AI Studio
            </Typography>
          </Stack>

          <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
            {sseStatusBadge}
            <Tooltip title="View Interactive OpenAPI & Swagger UI">
              <Chip
                component="a"
                href="/docs/openapi"
                target="_blank"
                rel="noopener noreferrer"
                clickable
                icon={<Api sx={{ fontSize: "14px !important" }} />}
                label="API Docs"
                size="small"
                variant="outlined"
                sx={{
                  display: { xs: "none", sm: "inline-flex" },
                  borderColor: "rgba(255, 255, 255, 0.15)",
                  "&:hover": { borderColor: "primary.light" },
                }}
              />
            </Tooltip>
          </Stack>
        </Toolbar>
      </AppBar>

      {/* Responsive Navigation Drawer */}
      <Box
        component="nav"
        sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}
        aria-label="mailbox folders"
      >
        {/* Mobile temporary drawer */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", md: "none" },
            "& .MuiDrawer-paper": { boxSizing: "border-box", width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>

        {/* Desktop permanent drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: "none", md: "block" },
            "& .MuiDrawer-paper": { boxSizing: "border-box", width: DRAWER_WIDTH },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3, md: 4 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: "64px",
          minHeight: "calc(100vh - 64px)",
        }}
      >
        {children}
      </Box>
    </Box>
  )
}
