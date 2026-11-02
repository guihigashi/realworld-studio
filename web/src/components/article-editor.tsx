import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Alert, Button, Card, CardContent, Stack, Tooltip, Typography } from "@mui/material"
import { CheckCircle, Publish, Replay, Save } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { StatusChip } from "@/components/StatusChip.tsx"
import { ToneChip } from "@/components/ToneChip.tsx"
import { MarkdownEditor } from "@/components/MarkdownEditor.tsx"
import { FormTextField } from "./FormTextField.tsx"
import { FormAutocomplete } from "./FormAutocomplete.tsx"

const articleEditorSchema = z.object({
  title: z.string().trim().min(1, "Article title is required"),
  description: z.string().trim(),
  tagList: z.array(z.string()),
  body: z.string(),
})

type ArticleEditorValues = z.infer<typeof articleEditorSchema>

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

  const [actionError, setActionError] = useState<string | null>(null)

  const {
    control,
    handleSubmit,
    reset,
    formState: { isDirty, isSubmitting },
  } = useForm<ArticleEditorValues>({
    resolver: zodResolver(articleEditorSchema),
    defaultValues: {
      title: article.title || article.topic || "",
      description: article.description || "",
      tagList: article.tagList || [],
      body: article.body || "",
    },
    values: {
      title: article.title || article.topic || "",
      description: article.description || "",
      tagList: article.tagList || [],
      body: article.body || "",
    },
    resetOptions: { keepDirtyValues: true },
  })

  const actionLoading = updateLoading || approveLoading || republishLoading || isSubmitting

  const handleSaveDraft = handleSubmit(async (data) => {
    setActionError(null)
    try {
      const updated = await updateArticleMutation({
        id: article.id,
        cmd: {
          title: data.title,
          description: data.description,
          body: data.body,
          tagList: data.tagList,
        },
      }).unwrap()
      reset({
        title: updated.title || updated.topic || "",
        description: updated.description || "",
        body: updated.body || "",
        tagList: updated.tagList || [],
      })
      setToastMessage("Draft changes saved successfully!")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to save draft"))
    }
  })

  const handleApprove = handleSubmit(async (data) => {
    setActionError(null)
    try {
      await approveArticleMutation({
        id: article.id,
        cmd: {
          title: data.title || undefined,
          description: data.description || undefined,
          body: data.body || undefined,
          tagList: data.tagList.length > 0 ? data.tagList : undefined,
        },
      }).unwrap()
      setToastMessage("Article approved! Background publishing worker triggered.")
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to approve article"))
    }
  })

  const handleRepublish = async () => {
    setActionError(null)
    try {
      await republishArticleMutation(article.id).unwrap()
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
          <FormTextField
            name="title"
            control={control}
            label="Article Title"
            fullWidth
            size="small"
            placeholder="Enter article title..."
          />

          <FormTextField
            name="description"
            control={control}
            label="Lead Description (1-2 sentences)"
            fullWidth
            size="small"
            multiline
            rows={2}
            placeholder="Short description summary for search and feed cards..."
          />

          <FormAutocomplete
            name="tagList"
            control={control}
            label="Article Tags"
            options={article.tagList}
            placeholder="Tags..."
          />
        </Stack>

        {/* Split Pane Markdown Editor */}
        <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
          Markdown Article Content
        </Typography>
        <Controller
          name="body"
          control={control}
          render={({ field }) => (
            <MarkdownEditor
              value={field.value}
              onChange={field.onChange}
              disabled={article.status === "Generating"}
              minHeight={450}
            />
          )}
        />
      </CardContent>
    </Card>
  )
}
