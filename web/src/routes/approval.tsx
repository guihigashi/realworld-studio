import { createFileRoute } from "@tanstack/react-router"
import { useCallback, useEffect, useState, useTransition } from "react"
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
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
  Tooltip,
  Typography,
} from "@mui/material"
import { CheckCircle, RateReview, Refresh, Replay, Save } from "@mui/icons-material"
import { PaperCard } from "@/components/PaperCard.tsx"
import { StatusChip } from "@/components/StatusChip.tsx"
import { ToneChip } from "@/components/ToneChip.tsx"
import { useEvents } from "@/hooks/useEvents.ts"
import { api } from "@/api/client.ts"
import { MarkdownEditor } from "@/components/MarkdownEditor.tsx"
import { WritingTones } from "@/lib/constants.ts"

export const Route = createFileRoute("/approval")({
  component: RouteComponent,
})

function RouteComponent() {
  const [articles, setArticles] = useState<Article[]>([])
  const [users, setUsers] = useState<Record<string, User>>({})
  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [, startTransition] = useTransition()

  // Active Draft Form
  const [draftTitle, setDraftTitle] = useState("")
  const [draftDescription, setDraftDescription] = useState("")
  const [draftBody, setDraftBody] = useState("")
  const [draftTags, setDraftTags] = useState<string[]>([])
  const [isDirty, setIsDirty] = useState(false)

  // Regenerate Dialog
  const [regenOpen, setRegenOpen] = useState(false)
  const [regenTopic, setRegenTopic] = useState("")
  const [regenTone, setRegenTone] = useState<WritingTone>("TechnicalDeepDive")
  const [regenWordCount, setRegenWordCount] = useState<number>(800)

  const { addListener } = useEvents()

  const fetchArticles = useCallback(async () => {
    try {
      const [fetchedArticles, fetchedUsers] = await Promise.all([api.getArticles(), api.getUsers()])

      const userMap = fetchedUsers.reduce<Record<string, User>>((acc, u) => {
        acc[u.id] = u
        return acc
      }, {})

      const sorted = [...fetchedArticles].sort((a, b) => {
        const order: Record<string, number> = {
          PendingApproval: 0,
          Generating: 1,
          QueuedGeneration: 2,
          Approved: 3,
          Publishing: 4,
          Published: 5,
          Failed: 6,
        }
        return (order[a.status] ?? 99) - (order[b.status] ?? 99)
      })

      startTransition(() => {
        setArticles(sorted)
        setUsers(userMap)
      })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to fetch articles")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let ignore = false
    void (async () => {
      try {
        const [fetchedArticles, fetchedUsers] = await Promise.all([api.getArticles(), api.getUsers()])
        if (!ignore) {
          const userMap = fetchedUsers.reduce<Record<string, User>>((acc, u) => {
            acc[u.id] = u
            return acc
          }, {})

          const sorted = [...fetchedArticles].sort((a, b) => {
            const order: Record<string, number> = {
              PendingApproval: 0,
              Generating: 1,
              QueuedGeneration: 2,
              Approved: 3,
              Publishing: 4,
              Published: 5,
              Failed: 6,
            }
            return (order[a.status] ?? 99) - (order[b.status] ?? 99)
          })

          startTransition(() => {
            setArticles(sorted)
            setUsers(userMap)
            setLoading(false)
            const first = sorted.find((a) => a.status === "PendingApproval") || sorted[0]
            if (first) {
              setSelectedArticleId(first.id)
              setDraftTitle(first.title || first.topic || "")
              setDraftDescription(first.description || "")
              setDraftBody(first.body || "")
              setDraftTags(first.tagList || [])
            }
          })
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError(err instanceof Error ? err.message : "Failed to fetch articles")
          setLoading(false)
        }
      }
    })()
    return () => {
      ignore = true
    }
  }, [])

  // Subscribe to real-time events from SSE
  useEffect(() => {
    const remove = addListener((event) => {
      fetchArticles()
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

  const selectArticle = (a: Article) => {
    setSelectedArticleId(a.id)
    setDraftTitle(a.title || a.topic || "")
    setDraftDescription(a.description || "")
    setDraftBody(a.body || "")
    setDraftTags(a.tagList || [])
    setIsDirty(false)
  }

  const handleSaveDraft = async () => {
    if (!activeArticle) return
    setActionLoading(true)
    setError(null)
    try {
      const updated = await api.updateArticle(activeArticle.id, {
        title: draftTitle,
        description: draftDescription,
        body: draftBody,
        tagList: draftTags,
      })
      setIsDirty(false)
      setToastMessage("Draft changes saved successfully!")
      setArticles((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save draft")
    } finally {
      setActionLoading(false)
    }
  }

  const handleApprove = async () => {
    if (!activeArticle) return
    setActionLoading(true)
    setError(null)
    try {
      const updated = await api.approveArticle(activeArticle.id, {
        title: draftTitle || undefined,
        description: draftDescription || undefined,
        body: draftBody || undefined,
        tagList: draftTags.length > 0 ? draftTags : undefined,
      })
      setIsDirty(false)
      setToastMessage("Article approved! Background publishing worker triggered.")
      setArticles((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to approve article")
    } finally {
      setActionLoading(false)
    }
  }

  const handleOpenRegenerate = () => {
    if (!activeArticle) return
    setRegenTopic(activeArticle.topic)
    setRegenTone(activeArticle.tone || "TechnicalDeepDive")
    setRegenWordCount(activeArticle.targetWordCount || 800)
    setRegenOpen(true)
  }

  const handleConfirmRegenerate = async () => {
    if (!activeArticle) return
    setActionLoading(true)
    setError(null)
    try {
      const updated = await api.regenerateArticle(activeArticle.id, {
        topic: regenTopic,
        tone: regenTone,
        targetWordCount: regenWordCount,
      })
      setRegenOpen(false)
      setToastMessage("Article sent for re-generation!")
      setArticles((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to trigger re-generation")
    } finally {
      setActionLoading(false)
    }
  }

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

        <Button variant="outlined" startIcon={<Refresh />} onClick={fetchArticles} disabled={loading}>
          Refresh Queue
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
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
                      onClick={() => selectArticle(item)}
                    />
                  )
                })}
              </Stack>
            </Box>
          </Card>

          {/* Right Column: Split-Pane Editor & Actions */}
          {activeArticle ? (
            <Card sx={{ p: { xs: 2, sm: 3 } }}>
              <CardContent sx={{ p: "0 !important" }}>
                {/* Article Header & Action Buttons */}
                <Stack
                  direction={{ xs: "column", md: "row" }}
                  spacing={2}
                  sx={{
                    justifyContent: "space-between",
                    alignItems: { xs: "flex-start", md: "center" },
                    mb: 2.5,
                  }}
                >
                  <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                    <StatusChip status={activeArticle.status} />
                    <ToneChip tone={activeArticle.tone} />
                    {users[activeArticle.authorId] && (
                      <Typography variant="caption" sx={{ color: "text.secondary", fontWeight: 600 }}>
                        by @{users[activeArticle.authorId].username}
                      </Typography>
                    )}
                  </Stack>

                  <Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap" }}>
                    <Tooltip title="Regenerate with revised topic, tone, or word count">
                      <Button
                        variant="outlined"
                        color="inherit"
                        size="small"
                        startIcon={<Replay />}
                        onClick={handleOpenRegenerate}
                        disabled={actionLoading || activeArticle.status === "Generating"}
                      >
                        Regenerate
                      </Button>
                    </Tooltip>

                    <Button
                      variant="outlined"
                      size="small"
                      startIcon={<Save />}
                      onClick={handleSaveDraft}
                      disabled={actionLoading || !isDirty}
                    >
                      Save Draft
                    </Button>

                    <Button
                      variant="contained"
                      color="primary"
                      size="small"
                      startIcon={<CheckCircle />}
                      onClick={handleApprove}
                      disabled={actionLoading || activeArticle.status !== "PendingApproval"}
                    >
                      Approve & Publish
                    </Button>
                  </Stack>
                </Stack>

                {/* Status-specific warning or notice */}
                {activeArticle.status === "Generating" && (
                  <Alert severity="warning" sx={{ mb: 2.5 }}>
                    This article is currently being generated by the LLM background worker. Content will update
                    automatically once completed.
                  </Alert>
                )}
                {activeArticle.status === "Published" && (
                  <Alert severity="success" sx={{ mb: 2.5 }}>
                    Published to RealWorld Conduit! Slug / External URL:{" "}
                    {activeArticle.conduitUrl || "Available in Conduit blog"}
                  </Alert>
                )}
                {activeArticle.status === "Failed" && (
                  <Alert severity="error" sx={{ mb: 2.5 }}>
                    Generation / publishing failed:{" "}
                    {activeArticle.errorMessage || "Unknown error. Click Regenerate to retry."}
                  </Alert>
                )}

                {/* Editable Metadata Fields */}
                <Stack spacing={2} sx={{ mb: 2.5 }}>
                  <TextField
                    label="Article Title"
                    fullWidth
                    size="small"
                    value={draftTitle}
                    onChange={(e) => {
                      setDraftTitle(e.target.value)
                      setIsDirty(true)
                    }}
                    placeholder="Enter article title..."
                  />

                  <TextField
                    label="Lead Description (1-2 sentences)"
                    fullWidth
                    size="small"
                    multiline
                    rows={2}
                    value={draftDescription}
                    onChange={(e) => {
                      setDraftDescription(e.target.value)
                      setIsDirty(true)
                    }}
                    placeholder="Short description summary for search and feed cards..."
                  />

                  <Autocomplete
                    multiple
                    freeSolo
                    options={activeArticle.tagList}
                    value={draftTags}
                    onChange={(_, newVal) => {
                      setDraftTags(newVal)
                      setIsDirty(true)
                    }}
                    renderInput={(params) => (
                      <TextField {...params} size="small" label="Article Tags" placeholder="Tags..." />
                    )}
                  />
                </Stack>

                {/* Split Pane Markdown Editor */}
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  Markdown Article Content
                </Typography>
                <MarkdownEditor
                  value={draftBody}
                  onChange={(val) => {
                    setDraftBody(val)
                    setIsDirty(true)
                  }}
                  disabled={activeArticle.status === "Generating"}
                  minHeight={450}
                />
              </CardContent>
            </Card>
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
          <Button onClick={() => setRegenOpen(false)} disabled={actionLoading}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleConfirmRegenerate}
            disabled={actionLoading}
            startIcon={actionLoading ? <CircularProgress size={16} /> : <Replay />}
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
