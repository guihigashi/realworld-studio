import { createFileRoute } from "@tanstack/react-router"
import { useEffect, useMemo, useState } from "react"
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material"
import { CheckCircleOutlined, ErrorOutlined, History, Link, OpenInNew, Refresh, Replay } from "@mui/icons-material"
import { useEvents } from "@/hooks/useEvents.ts"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { StatusChip } from "@/components/StatusChip.tsx"
import { ToneChip } from "@/components/ToneChip.tsx"
import { ArticleStatuses } from "@/lib/constants.ts"

export const Route = createFileRoute("/audit")({
  component: RouteComponent,
})

function RouteComponent() {
  const {
    data: rawArticles = [],
    isLoading: articlesLoading,
    error: articlesError,
    refetch: loadArticles,
  } = api.useGetArticlesQuery()
  const { data: rawUsers = [], isLoading: usersLoading, error: usersError } = api.useGetUsersQuery()
  const [regenerateArticleMutation, { isLoading: retryLoading }] = api.useRegenerateArticleMutation()

  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [authorFilter, setAuthorFilter] = useState<string>("all")
  const [actionError, setActionError] = useState<string | null>(null)
  const { addListener } = useEvents()

  const users = useMemo(() => {
    return rawUsers.reduce<Record<string, User>>((acc, u) => {
      acc[u.id] = u
      return acc
    }, {})
  }, [rawUsers])

  const articles = useMemo(() => {
    return [...rawArticles].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  }, [rawArticles])

  useEffect(() => {
    const unsubscribe = addListener(() => {
      void loadArticles()
    })
    return unsubscribe
  }, [addListener, loadArticles])

  const filteredArticles = articles.filter((a) => {
    if (statusFilter !== "all" && a.status !== statusFilter) return false
    if (authorFilter !== "all" && a.authorId !== authorFilter) return false
    return true
  })

  const publishedCount = articles.filter((a) => a.status === "Published").length
  const failedCount = articles.filter((a) => a.status === "Failed").length

  const handleRetry = async (articleId: string) => {
    setActionError(null)
    try {
      await regenerateArticleMutation({ id: articleId }).unwrap()
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to retry article"))
    }
  }

  const loading = articlesLoading || usersLoading
  const error =
    actionError ||
    (articlesError ? getErrorMessage(articlesError, "Failed to load audit history") : null) ||
    (usersError ? getErrorMessage(usersError, "Failed to load users") : null)

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box>
      {/* Header */}
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{
          justifyContent: "space-between",
          alignItems: { xs: "flex-start", sm: "center" },
          mb: 3,
        }}
      >
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
            Publishing & Audit Log
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Live telemetry of published articles with external RealWorld links, timestamps, and error monitoring.
          </Typography>
        </Box>

        <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadArticles()} disabled={loading}>
          Refresh
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {error}
        </Alert>
      )}

      {/* Metric Cards Banner */}
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" },
          gap: 2,
          mb: 3,
        }}
      >
        <Card sx={{ p: 2 }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
            <History color="primary" />
            <Typography variant="subtitle2" color="text.secondary">
              Total Managed
            </Typography>
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 700, mt: 0.5 }}>
            {articles.length}
          </Typography>
        </Card>

        <Card sx={{ p: 2 }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
            <CheckCircleOutlined color="success" />
            <Typography variant="subtitle2" color="text.secondary">
              Published on Conduit
            </Typography>
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 700, mt: 0.5, color: "#10b981" }}>
            {publishedCount}
          </Typography>
        </Card>

        <Card sx={{ p: 2 }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
            <ErrorOutlined color="error" />
            <Typography variant="subtitle2" color="text.secondary">
              Failed / Retried
            </Typography>
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 700, mt: 0.5, color: "#ef4444" }}>
            {failedCount}
          </Typography>
        </Card>
      </Box>

      {/* Filter Controls */}
      <Card sx={{ p: 2, mb: 3 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            select
            size="small"
            label="Filter by Status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="all">All Statuses ({articles.length})</MenuItem>
            {ArticleStatuses.map((s) => (
              <MenuItem key={s} value={s}>
                {s} ({articles.filter((a) => a.status === s).length})
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size="small"
            label="Filter by Author"
            value={authorFilter}
            onChange={(e) => setAuthorFilter(e.target.value)}
            sx={{ minWidth: 220 }}
          >
            <MenuItem value="all">All Authors</MenuItem>
            {Object.values(users).map((u) => (
              <MenuItem key={u.id} value={u.id}>
                @{u.username}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </Card>

      {/* Articles Feed */}
      {filteredArticles.length === 0 ? (
        <Card sx={{ p: 5, textAlign: "center" }}>
          <Typography variant="h6" color="text.secondary">
            No articles match the selected filter.
          </Typography>
        </Card>
      ) : (
        <Stack spacing={2}>
          {filteredArticles.map((article) => {
            const author = users[article.authorId]
            return (
              <Card key={article.id} sx={{ p: 2.5 }}>
                <CardContent sx={{ p: "0 !important" }}>
                  <Stack
                    direction={{ xs: "column", md: "row" }}
                    spacing={1.5}
                    sx={{
                      justifyContent: "space-between",
                      alignItems: { xs: "flex-start", md: "center" },
                      mb: 1.5,
                    }}
                  >
                    <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                      <StatusChip status={article.status} />
                      <ToneChip tone={article.tone} />
                      {author && (
                        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                          @{author.username}
                        </Typography>
                      )}
                      <Typography variant="caption" color="text.secondary">
                        {new Date(article.updatedAt).toLocaleString()}
                      </Typography>
                    </Stack>

                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                      {article.conduitUrl && (
                        <Button
                          variant="outlined"
                          color="success"
                          size="small"
                          href={article.conduitUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          startIcon={<Link />}
                          endIcon={<OpenInNew sx={{ fontSize: 14 }} />}
                        >
                          View on Conduit
                        </Button>
                      )}

                      {article.status === "Failed" && (
                        <Button
                          variant="outlined"
                          color="error"
                          size="small"
                          startIcon={<Replay />}
                          onClick={() => void handleRetry(article.id)}
                          disabled={retryLoading}
                        >
                          Retry Job
                        </Button>
                      )}
                    </Stack>
                  </Stack>

                  <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5, fontSize: "1.1rem" }}>
                    {article.title || article.topic}
                  </Typography>

                  {article.description && (
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, lineHeight: 1.5 }}>
                      {article.description}
                    </Typography>
                  )}

                  {article.errorMessage && (
                    <Alert severity="error" sx={{ my: 1.5 }}>
                      {article.errorMessage}
                    </Alert>
                  )}

                  <Box sx={{ display: "flex", gap: 0.8, flexWrap: "wrap", mt: 1.5 }}>
                    {article.tagList.map((tag) => (
                      <Chip
                        key={tag}
                        label={`#${tag}`}
                        size="small"
                        sx={{ fontSize: "0.75rem", bgcolor: "rgba(255, 255, 255, 0.05)" }}
                      />
                    ))}
                    {article.targetWordCount && (
                      <Chip
                        label={`Target: ~${article.targetWordCount} words`}
                        size="small"
                        variant="outlined"
                        sx={{ fontSize: "0.75rem" }}
                      />
                    )}
                  </Box>
                </CardContent>
              </Card>
            )
          })}
        </Stack>
      )}
    </Box>
  )
}
