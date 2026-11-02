import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import {
  Alert,
  Autocomplete,
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
  TextField,
  Typography,
} from "@mui/material"
import { Send, Speed } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { ToneChip } from "@/components/ToneChip.tsx"
import { WritingTones } from "@/lib/constants.ts"

export const Route = createFileRoute("/studio")({
  component: RouteComponent,
})

function RouteComponent() {
  const navigate = useNavigate()
  const { data: users = [], isLoading: usersLoading, error: usersError } = api.useGetUsersQuery()
  const [enqueueArticleMutation, { isLoading: submitting }] = api.useEnqueueArticleMutation()

  const [formError, setFormError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  // Form State
  const [selectedAuthorId, setSelectedAuthorId] = useState<string>("")
  const [topic, setTopic] = useState("")
  const [customTags, setCustomTags] = useState<string[] | null>(null)
  const [tone, setTone] = useState<WritingTone>("TechnicalDeepDive")
  const [targetWordCount, setTargetWordCount] = useState<number>(200)

  const authorId = selectedAuthorId || users[0]?.id || ""
  const selectedUser = users.find((u) => u.id === authorId)
  const selectedTags = customTags ?? selectedUser?.tags.map((t) => t.value) ?? []

  const handleAuthorChange = (newAuthorId: string) => {
    setSelectedAuthorId(newAuthorId)
    const user = users.find((u) => u.id === newAuthorId)
    setCustomTags(user ? user.tags.map((t) => t.value) : [])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!authorId) {
      setFormError("Please select an author persona")
      return
    }
    if (!topic.trim()) {
      setFormError("Please enter a topic or prompt for the article")
      return
    }

    setFormError(null)
    setSuccessMsg(null)

    try {
      const article = await enqueueArticleMutation({
        authorId,
        topic: topic.trim(),
        tagList: selectedTags,
        tone,
        targetWordCount: targetWordCount > 0 ? targetWordCount : undefined,
      }).unwrap()

      setSuccessMsg(`Article "${article.topic}" queued successfully! Transitioning to generation...`)
      setTopic("")

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
        <Card component="form" onSubmit={handleSubmit} sx={{ p: { xs: 2.5, sm: 3.5 } }}>
          <CardContent sx={{ p: "0 !important" }}>
            <Stack spacing={3.5}>
              {/* Persona Selection */}
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  1. Select Author Persona
                </Typography>
                <TextField
                  select
                  fullWidth
                  value={authorId}
                  onChange={(e) => handleAuthorChange(e.target.value)}
                  helperText="The chosen persona determines the authoring voice and RealWorld publishing account."
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
                </TextField>

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
                <TextField
                  fullWidth
                  required
                  placeholder="e.g. Architecting Resilient Event-Driven Services with ZIO 2 and PostgreSQL LISTEN/NOTIFY"
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  helperText="The prompt guides the LLM to structure the article with technical depth, code examples, and conclusions."
                />
              </Box>

              {/* Tags Selection */}
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                  3. Target Tags & Keywords
                </Typography>
                <Autocomplete
                  multiple
                  freeSolo
                  options={selectedUser ? selectedUser.tags.map((t) => t.value) : []}
                  value={selectedTags}
                  onChange={(_, newValue) => setCustomTags(newValue)}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      placeholder="Add tags..."
                      helperText="These tags will be associated with the article on Conduit."
                    />
                  )}
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
                  <TextField select fullWidth value={tone} onChange={(e) => setTone(e.target.value as WritingTone)}>
                    {WritingTones.map((t) => (
                      <MenuItem key={t} value={t}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                          <ToneChip tone={t} size="small" />
                        </Stack>
                      </MenuItem>
                    ))}
                  </TextField>
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
                      label={`~${targetWordCount} words`}
                      size="small"
                      color="secondary"
                      variant="outlined"
                    />
                  </Stack>
                  <Slider
                    value={targetWordCount}
                    min={100}
                    max={500}
                    step={100}
                    valueLabelDisplay="auto"
                    onChange={(_, val) => setTargetWordCount(val as number)}
                    sx={{ color: "secondary.main", mt: 1 }}
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
                  disabled={submitting}
                  startIcon={submitting ? <CircularProgress size={20} color="inherit" /> : <Send />}
                  sx={{
                    px: 4,
                    py: 1.2,
                    fontSize: "1rem",
                    fontWeight: 600,
                  }}
                >
                  {submitting ? "Enqueuing..." : "Enqueue Article Generation"}
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}
    </Box>
  )
}
