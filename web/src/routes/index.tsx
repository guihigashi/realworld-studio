import { createFileRoute } from "@tanstack/react-router"
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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material"
import { AutoAwesome, LocalOffer, PersonAdd, Refresh } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { PaperBanner } from "@/components/PaperBanner.tsx"

export const Route = createFileRoute("/")({
  component: RouteComponent,
})

const DEFAULT_SEED_TAGS = [
  "scala",
  "zio",
  "functional-programming",
  "distributed-systems",
  "postgresql",
  "rust",
  "typescript",
  "ai-agents",
  "microservices",
  "concurrency",
]

function RouteComponent() {
  const { data: users = [], isLoading: usersLoading, error: usersError, refetch: fetchUsers } = api.useGetUsersQuery()
  const { data: tags = [], isLoading: tagsLoading, error: tagsError, refetch: fetchTags } = api.useGetTagsQuery()

  const [seedTagsMutation] = api.useSeedTagsMutation()
  const [generateUserProfileMutation, { isLoading: aiGenerating }] = api.useGenerateUserProfileMutation()
  const [createUserMutation] = api.useCreateUserMutation()

  const [actionError, setActionError] = useState<string | null>(null)

  // Generate with AI Modal State
  const [aiModalOpen, setAiModalOpen] = useState(false)
  const [aiSelectedTags, setAiSelectedTags] = useState<string[]>([])
  const [aiProfile, setAiProfile] = useState<GeneratedUserProfile | null>(null)
  const [aiError, setAiError] = useState<string | null>(null)
  const [aiSaving, setAiSaving] = useState(false)

  // Manual Create Modal State
  const [manualModalOpen, setManualModalOpen] = useState(false)
  const [manualForm, setManualForm] = useState({
    username: "",
    email: "",
    password: "",
    bio: "",
    avatarUrl: "",
    tags: [] as string[],
  })
  const [manualSaving, setManualSaving] = useState(false)
  const [manualError, setManualError] = useState<string | null>(null)

  const handleRefresh = () => {
    void fetchUsers()
    void fetchTags()
  }

  const handleSeedTags = async () => {
    try {
      await seedTagsMutation(DEFAULT_SEED_TAGS.map((value) => ({ value }))).unwrap()
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to seed tags"))
    }
  }

  const handleGenerateAi = async () => {
    setAiError(null)
    try {
      const profile = await generateUserProfileMutation({
        tags: aiSelectedTags.length > 0 ? aiSelectedTags : ["scala", "zio"],
      }).unwrap()
      setAiProfile(profile)
    } catch (err: unknown) {
      setAiError(getErrorMessage(err, "AI generation failed"))
    }
  }

  const handleSaveAiProfile = async () => {
    if (!aiProfile) return
    setAiSaving(true)
    setAiError(null)
    try {
      await createUserMutation({
        username: aiProfile.suggestedUsername,
        email: aiProfile.suggestedEmail,
        password: aiProfile.suggestedPassword || "password123",
        bio: aiProfile.suggestedBio,
        tags: aiProfile.tags.map((t) => t.value),
      }).unwrap()
      setAiModalOpen(false)
      setAiProfile(null)
      setAiSelectedTags([])
    } catch (err: unknown) {
      setAiError(getErrorMessage(err, "Failed to save persona"))
    } finally {
      setAiSaving(false)
    }
  }

  const handleManualSubmit = async () => {
    if (!manualForm.username || !manualForm.email || !manualForm.bio) {
      setManualError("Username, email, and bio are required")
      return
    }
    setManualSaving(true)
    setManualError(null)
    try {
      await createUserMutation({
        username: manualForm.username,
        email: manualForm.email,
        password: manualForm.password || "password123",
        bio: manualForm.bio,
        avatarUrl: manualForm.avatarUrl || undefined,
        tags: manualForm.tags,
      }).unwrap()
      setManualModalOpen(false)
      setManualForm({
        username: "",
        email: "",
        password: "",
        bio: "",
        avatarUrl: "",
        tags: [],
      })
    } catch (err: unknown) {
      setManualError(getErrorMessage(err, "Failed to create persona"))
    } finally {
      setManualSaving(false)
    }
  }

  const loading = usersLoading || tagsLoading
  const error =
    actionError ||
    (usersError ? getErrorMessage(usersError, "Failed to load personas") : null) ||
    (tagsError ? getErrorMessage(tagsError, "Failed to load tags") : null)

  return (
    <Box>
      {/* Header bar */}
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
            Expert Personas Hub
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage synthetic author identities with domain tags and writing styles.
          </Typography>
        </Box>

        <Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap" }}>
          {tags.length === 0 && (
            <Button variant="outlined" color="secondary" startIcon={<LocalOffer />} onClick={handleSeedTags}>
              Seed Default Tags
            </Button>
          )}
          <Button variant="outlined" startIcon={<Refresh />} onClick={handleRefresh} disabled={loading}>
            Refresh
          </Button>
          <Button variant="outlined" startIcon={<PersonAdd />} onClick={() => setManualModalOpen(true)}>
            Add Manually
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AutoAwesome />}
            onClick={() => {
              setAiProfile(null)
              setAiError(null)
              setAiModalOpen(true)
            }}
          >
            Generate with AI
          </Button>
        </Stack>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {error}
        </Alert>
      )}

      {/* Available tags banner */}
      {tags.length > 0 && <PaperBanner tags={tags} />}

      {/* Persona Cards Grid */}
      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
          <CircularProgress />
        </Box>
      ) : users.length === 0 ? (
        <Card sx={{ p: 5, textAlign: "center", bgcolor: "background.paper" }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No Personas Found
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Get started by generating author personas with AI or adding one manually.
          </Typography>
          <Button variant="contained" startIcon={<AutoAwesome />} onClick={() => setAiModalOpen(true)}>
            Generate Your First Persona
          </Button>
        </Card>
      ) : (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, 1fr)",
              lg: "repeat(3, 1fr)",
            },
            gap: 2.5,
          }}
        >
          {users.map((user) => (
            <Card key={user.id} sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
              <CardContent sx={{ flex: 1, p: 2.5 }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: "center", mb: 2 }}>
                  <Avatar
                    src={user.avatarUrl || undefined}
                    alt={user.username}
                    sx={{
                      width: 50,
                      height: 50,
                      bgcolor: "primary.dark",
                      fontSize: "1.2rem",
                      fontWeight: 700,
                    }}
                  >
                    {user.username.slice(0, 2).toUpperCase()}
                  </Avatar>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography variant="h6" noWrap sx={{ fontSize: "1.05rem", fontWeight: 600 }}>
                      @{user.username}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: "block" }}>
                      {user.email}
                    </Typography>
                  </Box>
                </Stack>

                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{
                    mb: 2.5,
                    minHeight: 40,
                    display: "-webkit-box",
                    WebkitLineClamp: 3,
                    WebkitBoxOrient: "vertical",
                    overflow: "hidden",
                    lineHeight: 1.5,
                  }}
                >
                  {user.bio}
                </Typography>

                <Box sx={{ mt: "auto" }}>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: "block", mb: 1, fontWeight: 600 }}
                  >
                    EXPERTISE TAGS ({user.tags.length})
                  </Typography>
                  <Box sx={{ display: "flex", gap: 0.7, flexWrap: "wrap" }}>
                    {user.tags.map((tag) => (
                      <Chip
                        key={tag.value}
                        label={`#${tag.value}`}
                        size="small"
                        sx={{
                          fontSize: "0.75rem",
                          bgcolor: "rgba(99, 102, 241, 0.12)",
                          color: "#a5b4fc",
                          border: "1px solid rgba(99, 102, 241, 0.25)",
                        }}
                      />
                    ))}
                  </Box>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      {/* AI Persona Generator Modal */}
      <Dialog
        open={aiModalOpen}
        onClose={() => !aiGenerating && !aiSaving && setAiModalOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <AutoAwesome color="primary" />
          Generate Persona with AI
        </DialogTitle>
        <DialogContent dividers>
          {aiError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setAiError(null)}>
              {aiError}
            </Alert>
          )}

          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            The backend LLM synthesizes an authentic engineering persona, including realistic bio, handle, and expertise
            based on selected domain tags.
          </Typography>

          <Autocomplete
            multiple
            freeSolo
            options={tags.map((t) => t.value)}
            value={aiSelectedTags}
            onChange={(_, newValue) => setAiSelectedTags(newValue)}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Domain Tags (Select or Type)"
                placeholder="e.g. scala, zio, rust..."
                helperText="Leave empty to use default domain tags"
              />
            )}
            sx={{ mb: 3 }}
          />

          {aiGenerating ? (
            <Box sx={{ textAlign: "center", py: 4 }}>
              <CircularProgress size={36} sx={{ mb: 2 }} />
              <Typography variant="body2" color="text.secondary">
                Synthesizing persona via llama.cpp / fallback generator...
              </Typography>
            </Box>
          ) : aiProfile ? (
            <Card sx={{ bgcolor: "rgba(255, 255, 255, 0.03)", p: 2 }}>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                Synthesized Profile:
              </Typography>
              <TextField
                label="Username"
                fullWidth
                size="small"
                value={aiProfile.suggestedUsername}
                onChange={(e) => setAiProfile({ ...aiProfile, suggestedUsername: e.target.value })}
                sx={{ mb: 1.5 }}
              />
              <TextField
                label="Email"
                fullWidth
                size="small"
                value={aiProfile.suggestedEmail}
                onChange={(e) => setAiProfile({ ...aiProfile, suggestedEmail: e.target.value })}
                sx={{ mb: 1.5 }}
              />
              <TextField
                label="Bio"
                fullWidth
                multiline
                rows={3}
                size="small"
                value={aiProfile.suggestedBio}
                onChange={(e) => setAiProfile({ ...aiProfile, suggestedBio: e.target.value })}
                sx={{ mb: 1.5 }}
              />
              <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 0.5 }}>
                Assigned Tags:
              </Typography>
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                {aiProfile.tags.map((t) => (
                  <Chip key={t.value} label={`#${t.value}`} size="small" />
                ))}
              </Box>
            </Card>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setAiModalOpen(false)} disabled={aiGenerating || aiSaving}>
            Cancel
          </Button>
          {!aiProfile ? (
            <Button
              variant="contained"
              onClick={handleGenerateAi}
              disabled={aiGenerating}
              startIcon={aiGenerating ? <CircularProgress size={16} /> : <AutoAwesome />}
            >
              {aiGenerating ? "Generating..." : "Generate Profile"}
            </Button>
          ) : (
            <>
              <Button onClick={handleGenerateAi} disabled={aiGenerating || aiSaving}>
                Regenerate
              </Button>
              <Button
                variant="contained"
                onClick={handleSaveAiProfile}
                disabled={aiSaving}
                startIcon={aiSaving ? <CircularProgress size={16} /> : undefined}
              >
                {aiSaving ? "Saving..." : "Accept & Save Persona"}
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>

      {/* Manual Create Persona Modal */}
      <Dialog open={manualModalOpen} onClose={() => !manualSaving && setManualModalOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Author Persona Manually</DialogTitle>
        <DialogContent dividers>
          {manualError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setManualError(null)}>
              {manualError}
            </Alert>
          )}

          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Username"
              fullWidth
              required
              value={manualForm.username}
              onChange={(e) => setManualForm({ ...manualForm, username: e.target.value })}
              placeholder="e.g. dev_sarah"
            />
            <TextField
              label="Email"
              fullWidth
              required
              type="email"
              value={manualForm.email}
              onChange={(e) => setManualForm({ ...manualForm, email: e.target.value })}
              placeholder="e.g. sarah@example.com"
            />
            <TextField
              label="Conduit Password"
              fullWidth
              type="password"
              value={manualForm.password}
              onChange={(e) => setManualForm({ ...manualForm, password: e.target.value })}
              helperText="Used to register & authenticate against Conduit API"
            />
            <TextField
              label="Avatar URL (Optional)"
              fullWidth
              value={manualForm.avatarUrl}
              onChange={(e) => setManualForm({ ...manualForm, avatarUrl: e.target.value })}
              placeholder="https://..."
            />
            <TextField
              label="Bio"
              fullWidth
              multiline
              rows={3}
              required
              value={manualForm.bio}
              onChange={(e) => setManualForm({ ...manualForm, bio: e.target.value })}
              placeholder="A short biography and technical background..."
            />
            <Autocomplete
              multiple
              freeSolo
              options={tags.map((t) => t.value)}
              value={manualForm.tags}
              onChange={(_, newValue) => setManualForm({ ...manualForm, tags: newValue })}
              renderInput={(params) => <TextField {...params} label="Expertise Tags" placeholder="Tags..." />}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setManualModalOpen(false)} disabled={manualSaving}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleManualSubmit}
            disabled={manualSaving}
            startIcon={manualSaving ? <CircularProgress size={16} /> : undefined}
          >
            {manualSaving ? "Saving..." : "Create Persona"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
