import { createFileRoute } from "@tanstack/react-router"
import { useEffect, useMemo, useState } from "react"
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Snackbar,
  Stack,
  Typography,
} from "@mui/material"
import { RateReview, Refresh } from "@mui/icons-material"
import { PaperCard } from "@/components/PaperCard.tsx"
import { useEvents } from "@/hooks/useEvents.ts"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { ArticleEditor } from "@/components/article-editor.tsx"
import { RegenerateDialog } from "@/components/RegenerateDialog.tsx"

export const Route = createFileRoute("/approval")({
  component: RouteComponent,
})

function RouteComponent() {
  const {
    data: rawArticles = [],
    isLoading: articlesLoading,
    error: articlesError,
    refetch: fetchArticles,
  } = api.useGetArticlesQuery()
  const { data: rawUsers = [], isLoading: usersLoading, error: usersError } = api.useGetUsersQuery()

  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [regenOpen, setRegenOpen] = useState(false)

  const { addListener } = useEvents()

  const users = useMemo(() => {
    return rawUsers.reduce<Record<string, User>>((acc, u) => {
      acc[u.id] = u
      return acc
    }, {})
  }, [rawUsers])

  const articles = useMemo(() => {
    const order: Record<string, number> = {
      PendingApproval: 0,
      Generating: 1,
      QueuedGeneration: 2,
      Approved: 3,
      Publishing: 4,
      Published: 5,
      Failed: 6,
    }
    return [...rawArticles].sort((a, b) => (order[a.status] ?? 99) - (order[b.status] ?? 99))
  }, [rawArticles])

  // Subscribe to real-time events from SSE
  useEffect(() => {
    const remove = addListener((event) => {
      void fetchArticles()
      if (event.status === "PendingApproval") {
        setToastMessage("Article draft is ready for review!")
      } else if (event.status === "Published") {
        setToastMessage("Article successfully published to Conduit!")
      }
    })
    return remove
  }, [addListener, fetchArticles])

  const activeArticle =
    articles.find((a) => a.id === selectedArticleId) ||
    articles.find((a) => a.status === "PendingApproval") ||
    articles[0] ||
    null

  const loading = articlesLoading || usersLoading
  const error =
    actionError ||
    (articlesError ? getErrorMessage(articlesError, "Failed to fetch articles") : null) ||
    (usersError ? getErrorMessage(usersError, "Failed to fetch users") : null)

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  const pendingArticles = articles.filter(
    (a) => a.status === "PendingApproval" || a.status === "Generating" || a.status === "QueuedGeneration",
  )

  return (
    <Box>
      {/* Top Header */}
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
          <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              Human-in-the-Loop Approval Board
            </Typography>
            <Chip
              label={`${pendingArticles.length} In Review`}
              size="small"
              color={pendingArticles.length > 0 ? "primary" : "default"}
              sx={{ fontWeight: 600 }}
            />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Review, fine-tune markdown copy, and trigger publication to Conduit. Real-time sync via SSE.
          </Typography>
        </Box>

        <Button variant="outlined" startIcon={<Refresh />} onClick={() => void fetchArticles()} disabled={loading}>
          Refresh Queue
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {error}
        </Alert>
      )}

      {articles.length === 0 ? (
        <Card sx={{ p: 5, textAlign: "center" }}>
          <RateReview sx={{ fontSize: 48, color: "text.secondary", mb: 1.5 }} />
          <Typography variant="h6" gutterBottom>
            No Articles in Queue
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Enqueue an article from the Article Studio to start the generation pipeline.
          </Typography>
        </Card>
      ) : (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", lg: "340px 1fr" },
            gap: 3,
            alignItems: "start",
          }}
        >
          {/* Left Column: Article Queue List */}
          <Card sx={{ maxHeight: "calc(100vh - 180px)", display: "flex", flexDirection: "column" }}>
            <Box sx={{ p: 2, borderBottom: "1px solid rgba(255, 255, 255, 0.08)" }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                Article Queue ({articles.length})
              </Typography>
            </Box>
            <Box sx={{ overflowY: "auto", p: 1.5 }}>
              <Stack spacing={1.5}>
                {articles.map((item) => {
                  const isSelected = item.id === activeArticle?.id
                  const author = users[item.authorId]
                  return (
                    <PaperCard
                      key={item.id}
                      item={item}
                      author={author}
                      isSelected={isSelected}
                      onClick={() => setSelectedArticleId(item.id)}
                    />
                  )
                })}
              </Stack>
            </Box>
          </Card>

          {/* Right Column: Split-Pane Editor & Actions */}
          {activeArticle ? (
            <ArticleEditor
              key={`${activeArticle.id}-${activeArticle.status}-${activeArticle.updatedAt}`}
              article={activeArticle}
              users={users}
              onRegenerate={() => setRegenOpen(true)}
              onDelete={() => setSelectedArticleId(null)}
              setToastMessage={setToastMessage}
            />
          ) : null}
        </Box>
      )}

      {/* Regenerate Modal */}
      <RegenerateDialog
        open={regenOpen}
        onClose={() => setRegenOpen(false)}
        article={activeArticle}
        onSuccess={() => setToastMessage("Article sent for re-generation!")}
      />

      {/* Toast Notification */}
      <Snackbar
        open={Boolean(toastMessage)}
        autoHideDuration={4000}
        onClose={() => setToastMessage(null)}
        message={toastMessage}
      />
    </Box>
  )
}
