import { createFileRoute } from "@tanstack/react-router"
import { useEffect, useMemo, useState } from "react"
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from "@mui/material"
import { RateReview, Refresh, Replay } from "@mui/icons-material"
import { PaperCard } from "@/components/PaperCard.tsx"
import { ToneChip } from "@/components/ToneChip.tsx"
import { useEvents } from "@/hooks/useEvents.ts"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { WritingTones } from "@/lib/constants.ts"
import { ArticleEditor } from "@/components/article-editor.tsx"

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
  const [regenerateArticleMutation, { isLoading: regenLoading }] = api.useRegenerateArticleMutation()

  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  // Regenerate Dialog
  const [regenOpen, setRegenOpen] = useState(false)
  const [regenTopic, setRegenTopic] = useState("")
  const [regenTone, setRegenTone] = useState<WritingTone>("TechnicalDeepDive")
  const [regenWordCount, setRegenWordCount] = useState<number>(800)

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

  const handleOpenRegenerate = () => {
    if (!activeArticle) return
    setRegenTopic(activeArticle.topic)
    setRegenTone(activeArticle.tone || "TechnicalDeepDive")
    setRegenWordCount(activeArticle.targetWordCount || 800)
    setRegenOpen(true)
  }

  const handleConfirmRegenerate = async () => {
    if (!activeArticle) return
    setActionError(null)
    try {
      await regenerateArticleMutation({
        id: activeArticle.id,
        cmd: {
          topic: regenTopic,
          tone: regenTone,
          targetWordCount: regenWordCount,
        },
      }).unwrap()
      setRegenOpen(false)
      setToastMessage("Article sent for re-generation!")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to trigger re-generation"))
    }
  }

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
              onRegenerate={handleOpenRegenerate}
              setToastMessage={setToastMessage}
            />
          ) : null}
        </Box>
      )}

      {/* Regenerate Modal */}
      <Dialog open={regenOpen} onClose={() => setRegenOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Replay color="primary" />
          Regenerate Article
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              label="Topic Prompt"
              fullWidth
              multiline
              rows={2}
              value={regenTopic}
              onChange={(e) => setRegenTopic(e.target.value)}
            />
            <TextField
              select
              fullWidth
              label="Writing Tone"
              value={regenTone}
              onChange={(e) => setRegenTone(e.target.value as WritingTone)}
            >
              {WritingTones.map((t) => (
                <MenuItem key={t} value={t}>
                  <ToneChip tone={t} size="small" />
                </MenuItem>
              ))}
            </TextField>
            <TextField
              type="number"
              label="Target Word Count"
              fullWidth
              value={regenWordCount}
              onChange={(e) => setRegenWordCount(parseInt(e.target.value, 10) || 800)}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setRegenOpen(false)} disabled={regenLoading}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleConfirmRegenerate}
            disabled={regenLoading}
            startIcon={regenLoading ? <CircularProgress size={16} /> : <Replay />}
          >
            Regenerate Draft
          </Button>
        </DialogActions>
      </Dialog>

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
