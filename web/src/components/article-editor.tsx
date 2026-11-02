import { api } from "@/state/api.ts"
import { useState } from "react"
import { getErrorMessage } from "@/state/helpers.ts"
import { Alert, Autocomplete, Button, Card, CardContent, Stack, TextField, Tooltip, Typography } from "@mui/material"
import { StatusChip } from "@/components/StatusChip.tsx"
import { ToneChip } from "@/components/ToneChip.tsx"
import { CheckCircle, Publish, Replay, Save } from "@mui/icons-material"
import { MarkdownEditor } from "@/components/MarkdownEditor.tsx"

interface ArticleEditorProps {
  article: Article
  users: Record<string, User>
  onRegenerate: () => void
  setToastMessage: (msg: string) => void
}

export function ArticleEditor({ article, users, onRegenerate, setToastMessage }: ArticleEditorProps) {
  const [updateArticleMutation, { isLoading: updateLoading }] = api.useUpdateArticleMutation()
  const [approveArticleMutation, { isLoading: approveLoading }] = api.useApproveArticleMutation()
  const [republishArticleMutation, { isLoading: republishLoading }] = api.useRepublishArticleMutation()

  const [draftTitle, setDraftTitle] = useState(article.title || article.topic || "")
  const [draftDescription, setDraftDescription] = useState(article.description || "")
  const [draftBody, setDraftBody] = useState(article.body || "")
  const [draftTags, setDraftTags] = useState<string[]>(article.tagList || [])
  const [isDirty, setIsDirty] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const actionLoading = updateLoading || approveLoading || republishLoading

  const handleSaveDraft = async () => {
    setActionError(null)
    try {
      await updateArticleMutation({
        id: article.id,
        cmd: {
          title: draftTitle,
          description: draftDescription,
          body: draftBody,
          tagList: draftTags,
        },
      }).unwrap()
      setIsDirty(false)
      setToastMessage("Draft changes saved successfully!")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to save draft"))
    }
  }

  const handleApprove = async () => {
    setActionError(null)
    try {
      await approveArticleMutation({
        id: article.id,
        cmd: {
          title: draftTitle || undefined,
          description: draftDescription || undefined,
          body: draftBody || undefined,
          tagList: draftTags.length > 0 ? draftTags : undefined,
        },
      }).unwrap()
      setIsDirty(false)
      setToastMessage("Article approved! Background publishing worker triggered.")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to approve article"))
    }
  }

  const handleRepublish = async () => {
    setActionError(null)
    try {
      await republishArticleMutation(article.id).unwrap()
      setIsDirty(false)
      setToastMessage("Article queued for republishing! Background publishing worker triggered.")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to republish article"))
    }
  }

  return (
    <Card sx={{ p: { xs: 2, sm: 3 } }}>
      <CardContent sx={{ p: "0 !important" }}>
        {actionError && (
          <Alert severity="error" sx={{ mb: 2.5 }} onClose={() => setActionError(null)}>
            {actionError}
          </Alert>
        )}

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
            <StatusChip status={article.status} />
            <ToneChip tone={article.tone} />
            {users[article.authorId] && (
              <Typography variant="caption" sx={{ color: "text.secondary", fontWeight: 600 }}>
                by @{users[article.authorId].username}
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
                onClick={onRegenerate}
                disabled={actionLoading || article.status === "Generating"}
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

            {article.status === "PendingApproval" && (
              <Button
                variant="contained"
                color="primary"
                size="small"
                startIcon={<CheckCircle />}
                onClick={handleApprove}
                disabled={actionLoading}
              >
                Approve & Publish
              </Button>
            )}

            {(article.status === "Published" || article.status === "Failed") && (
              <Button
                variant="contained"
                color="warning"
                size="small"
                startIcon={<Publish />}
                onClick={handleRepublish}
                disabled={actionLoading}
              >
                Republish
              </Button>
            )}
          </Stack>
        </Stack>

        {/* Status-specific warning or notice */}
        {article.status === "Generating" && (
          <Alert severity="warning" sx={{ mb: 2.5 }}>
            This article is currently being generated by the LLM background worker. Content will update
            automatically once completed.
          </Alert>
        )}
        {article.status === "Published" && (
          <Alert severity="success" sx={{ mb: 2.5 }}>
            Published to RealWorld Conduit! Slug / External URL:{" "}
            {article.conduitUrl || "Available in Conduit blog"}
          </Alert>
        )}
        {article.status === "Failed" && (
          <Alert severity="error" sx={{ mb: 2.5 }}>
            Generation / publishing failed:{" "}
            {article.errorMessage || "Unknown error. Click Regenerate to retry."}
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
            options={article.tagList}
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
          disabled={article.status === "Generating"}
          minHeight={450}
        />
      </CardContent>
    </Card>
  )
}
