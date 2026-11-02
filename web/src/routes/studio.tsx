import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useMemo, useState } from "react"
import { Controller, useForm, useWatch, type SubmitHandler } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  MenuItem,
  Slider,
  Stack,
  Typography,
} from "@mui/material"
import { Send, Speed } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { ToneChip } from "@/components/ToneChip.tsx"
import { WritingTones } from "@/lib/constants.ts"
import { FormTextField } from "@/components/FormTextField.tsx"
import { FormAutocomplete } from "@/components/FormAutocomplete.tsx"

const studioFormSchema = z.object({
  authorId: z.string().min(1, "Please select an author persona"),
  topic: z.string().trim().min(3, "Topic must be at least 3 characters"),
  tagList: z.array(z.string()),
  tone: z.enum(WritingTones),
  targetWordCount: z.number().min(100).max(500),
})

type StudioFormValues = z.infer<typeof studioFormSchema>

const defaultStudioValues: StudioFormValues = {
  authorId: "",
  topic: "",
  tagList: [],
  tone: "TechnicalDeepDive",
  targetWordCount: 200,
}

export const Route = createFileRoute("/studio")({
  component: RouteComponent,
})

function RouteComponent() {
  const navigate = useNavigate()
  const { data: users = [], isLoading: usersLoading, error: usersError } = api.useGetUsersQuery()
  const [enqueueArticleMutation, { isLoading: submitting }] = api.useEnqueueArticleMutation()

  const [formError, setFormError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const firstUser = users[0]
  const initialValues = useMemo<StudioFormValues>(() => {
    if (!firstUser) return defaultStudioValues
    return {
      authorId: firstUser.id,
      topic: "",
      tagList: firstUser.tags.map((t) => t.value),
      tone: "TechnicalDeepDive",
      targetWordCount: 200,
    }
  }, [firstUser])

  const {
    control,
    handleSubmit,
    setValue,
    reset,
    formState: { isSubmitting },
  } = useForm<StudioFormValues>({
    resolver: zodResolver(studioFormSchema),
    defaultValues: defaultStudioValues,
    values: initialValues,
    resetOptions: { keepDirtyValues: true },
  })

  const watchedAuthorId = useWatch({ control, name: "authorId" })
  const watchedWordCount = useWatch({ control, name: "targetWordCount" })

  const selectedUser = users.find((u) => u.id === watchedAuthorId)

  const onSubmit: SubmitHandler<StudioFormValues> = async (data) => {
    setFormError(null)
    setSuccessMsg(null)

    try {
      const article = await enqueueArticleMutation({
        authorId: data.authorId,
        topic: data.topic.trim(),
        tagList: data.tagList,
        tone: data.tone,
        targetWordCount: data.targetWordCount > 0 ? data.targetWordCount : undefined,
      }).unwrap()

      setSuccessMsg(`Article "${article.topic}" queued successfully! Transitioning to generation...`)
      reset({ ...data, topic: "" })

      // Auto redirect to approval board after 1.5s
      setTimeout(() => {
        navigate({ to: "/approval" })
      }, 1500)
    } catch (err: unknown) {
      setFormError(getErrorMessage(err, "Failed to enqueue article generation"))
    }
  }

  const loading = usersLoading
  const error = formError || (usersError ? getErrorMessage(usersError, "Failed to load personas") : null)

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box sx={{ maxWidth: 900, mx: "auto" }}>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700, mb: 0.5 }}>
          Article Generation Studio
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Configure an AI author persona, topic, writing style, and constraints to trigger deterministic markdown
          synthesis.
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setFormError(null)}>
          {error}
        </Alert>
      )}

      {successMsg && (
        <Alert severity="success" sx={{ mb: 3 }}>
          {successMsg}
        </Alert>
      )}

      {users.length === 0 ? (
        <Card sx={{ p: 4, textAlign: "center" }}>
          <Typography variant="h6" gutterBottom>
            No Author Personas Available
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            You need to create at least one author persona before queueing articles.
          </Typography>
          <Button variant="contained" onClick={() => navigate({ to: "/" })}>
            Go to Personas Hub
          </Button>
        </Card>
      ) : (
        <Card component="form" onSubmit={handleSubmit(onSubmit)} sx={{ p: { xs: 2.5, sm: 3.5 } }}>
          <CardContent sx={{ p: "0 !important" }}>
            <Stack spacing={3.5}>
              {/* Persona Selection */}
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  1. Select Author Persona
                </Typography>
                <FormTextField
                  select
                  fullWidth
                  name="authorId"
                  control={control}
                  helperText="The chosen persona determines the authoring voice and RealWorld publishing account."
                  onChange={(e) => {
                    const newAuthorId = e.target.value
                    const user = users.find((u) => u.id === newAuthorId)
                    setValue("tagList", user ? user.tags.map((t) => t.value) : [], {
                      shouldDirty: true,
                      shouldValidate: true,
                    })
                  }}
                >
                  {users.map((u) => (
                    <MenuItem key={u.id} value={u.id}>
                      <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
                        <Avatar
                          src={u.avatarUrl || undefined}
                          sx={{ width: 28, height: 28, fontSize: "0.8rem", bgcolor: "primary.dark" }}
                        >
                          {u.username.slice(0, 2).toUpperCase()}
                        </Avatar>
                        <Typography sx={{ fontWeight: 600 }}>@{u.username}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          ({u.email})
                        </Typography>
                      </Stack>
                    </MenuItem>
                  ))}
                </FormTextField>

                {selectedUser && (
                  <Box
                    sx={{
                      mt: 1.5,
                      p: 1.5,
                      borderRadius: 2,
                      bgcolor: "rgba(255, 255, 255, 0.02)",
                      border: "1px solid rgba(255, 255, 255, 0.06)",
                    }}
                  >
                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 0.5 }}>
                      AUTHOR BIO:
                    </Typography>
                    <Typography variant="body2" sx={{ fontStyle: "italic", color: "text.secondary", mb: 1 }}>
                      &ldquo;{selectedUser.bio}&rdquo;
                    </Typography>
                  </Box>
                )}
              </Box>

              {/* Topic & Headline */}
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  2. Article Topic & Prompt
                </Typography>
                <FormTextField
                  name="topic"
                  control={control}
                  fullWidth
                  required
                  placeholder="e.g. Architecting Resilient Event-Driven Services with ZIO 2 and PostgreSQL LISTEN/NOTIFY"
                  helperText="The prompt guides the LLM to structure the article with technical depth, code examples, and conclusions."
                />
              </Box>

              {/* Tags Selection */}
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  3. Target Tags & Keywords
                </Typography>
                <FormAutocomplete
                  name="tagList"
                  control={control}
                  options={selectedUser ? selectedUser.tags.map((t) => t.value) : []}
                  placeholder="Add tags..."
                  helperText="These tags will be associated with the article on Conduit."
                />
              </Box>

              {/* Tone Selection & Target Word Count */}
              <Box
                sx={{
                  display: "grid",
                  gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
                  gap: 3,
                }}
              >
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                    4. Writing Tone
                  </Typography>
                  <FormTextField select fullWidth name="tone" control={control}>
                    {WritingTones.map((t) => (
                      <MenuItem key={t} value={t}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                          <ToneChip tone={t} size="small" />
                        </Stack>
                      </MenuItem>
                    ))}
                  </FormTextField>
                </Box>

                <Box>
                  <Stack
                    direction="row"
                    sx={{
                      justifyContent: "space-between",
                      alignItems: "center",
                      mb: 1,
                    }}
                  >
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      5. Target Word Count
                    </Typography>
                    <Chip
                      icon={<Speed sx={{ fontSize: "14px !important" }} />}
                      label={`~${watchedWordCount ?? 200} words`}
                      size="small"
                      color="secondary"
                      variant="outlined"
                    />
                  </Stack>
                  <Controller
                    name="targetWordCount"
                    control={control}
                    render={({ field }) => (
                      <Slider
                        value={field.value ?? 200}
                        min={100}
                        max={500}
                        step={100}
                        valueLabelDisplay="auto"
                        onChange={(_, val) => field.onChange(val as number)}
                        sx={{ color: "secondary.main", mt: 1 }}
                      />
                    )}
                  />
                  <Typography variant="caption" color="text.secondary">
                    Guides LLM generation context window and section depth.
                  </Typography>
                </Box>
              </Box>

              {/* Submit Button */}
              <Box sx={{ pt: 2, display: "flex", justifyContent: "flex-end", gap: 2 }}>
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={isSubmitting || submitting}
                  startIcon={isSubmitting || submitting ? <CircularProgress size={20} color="inherit" /> : <Send />}
                  sx={{
                    px: 4,
                    py: 1.2,
                    fontSize: "1rem",
                    fontWeight: 600,
                  }}
                >
                  {isSubmitting || submitting ? "Enqueuing..." : "Enqueue Article Generation"}
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}
    </Box>
  )
}
