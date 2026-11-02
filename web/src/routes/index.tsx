import { createFileRoute } from "@tanstack/react-router"
import { useMemo, useState } from "react"
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
import { AutoAwesome, Delete, Edit, LocalOffer, PersonAdd, Refresh } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { PaperBanner } from "@/components/PaperBanner.tsx"
import { PersonaDialog } from "@/components/PersonaDialog.tsx"

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
  const [deleteUserMutation, { isLoading: deleting }] = api.useDeleteUserMutation()

  const [actionError, setActionError] = useState<string | null>(null)

  // Persona Modal State (Add / Edit)
  const [personaDialogOpen, setPersonaDialogOpen] = useState(false)
  const [selectedUserForEdit, setSelectedUserForEdit] = useState<User | null>(null)

  // Delete Confirmation Modal State
  const [deleteConfirmUser, setDeleteConfirmUser] = useState<User | null>(null)

  // Generate with AI Modal State
  const [aiModalOpen, setAiModalOpen] = useState(false)
  const [aiSelectedTags, setAiSelectedTags] = useState<string[]>([])
  const [aiProfile, setAiProfile] = useState<GeneratedUserProfile | null>(null)
  const [aiError, setAiError] = useState<string | null>(null)
  const [aiSaving, setAiSaving] = useState(false)

  const availableTagValues = useMemo(() => tags.map((t) => t.value), [tags])

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

  const handleOpenAdd = () => {
    setSelectedUserForEdit(null)
    setPersonaDialogOpen(true)
  }

  const handleOpenEdit = (user: User) => {
    setSelectedUserForEdit(user)
    setPersonaDialogOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (!deleteConfirmUser) return
    setActionError(null)
    try {
      await deleteUserMutation(deleteConfirmUser.id).unwrap()
      setDeleteConfirmUser(null)
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to delete persona"))
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
          <Button variant="outlined" startIcon={<PersonAdd />} onClick={handleOpenAdd}>
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
              <CardContent sx={{ flex: 1, p: 2.5, display: "flex", flexDirection: "column" }}>
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

                <Box sx={{ mt: "auto", mb: 2 }}>
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

                {/* Card Actions: Edit and Delete */}
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{
                    pt: 1.5,
                    borderTop: "1px solid rgba(255, 255, 255, 0.08)",
                    justifyContent: "flex-end",
                  }}
                >
                  <Button
                    size="small"
                    variant="outlined"
                    color="inherit"
                    startIcon={<Edit />}
                    onClick={() => handleOpenEdit(user)}
                  >
                    Edit
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    color="error"
                    startIcon={<Delete />}
                    onClick={() => setDeleteConfirmUser(user)}
                  >
                    Delete
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      {/* RHF Controlled Persona Modal (Add / Edit) */}
      <PersonaDialog
        open={personaDialogOpen}
        onClose={() => setPersonaDialogOpen(false)}
        user={selectedUserForEdit}
        availableTags={availableTagValues}
      />

      {/* Delete Confirmation Modal */}
      <Dialog
        open={Boolean(deleteConfirmUser)}
        onClose={() => !deleting && setDeleteConfirmUser(null)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle sx={{ fontWeight: 700 }}>Delete Persona & Articles?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Are you sure you want to delete <strong>@{deleteConfirmUser?.username}</strong>?
          </Typography>
          <Alert severity="warning">
            This action cannot be undone. All articles authored by this persona will also be deleted from the database.
          </Alert>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDeleteConfirmUser(null)} disabled={deleting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => void handleConfirmDelete()}
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={16} /> : <Delete />}
          >
            {deleting ? "Deleting..." : "Delete Persona & Articles"}
          </Button>
        </DialogActions>
      </Dialog>

      {/* AI Persona Generator Modal */}
      <Dialog
        open={aiModalOpen}
        onClose={() => !aiGenerating && !aiSaving && setAiModalOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ fontWeight: 700 }}>Generate Persona with AI</DialogTitle>
        <DialogContent>
          {aiError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setAiError(null)}>
              {aiError}
            </Alert>
          )}

          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Select key focus topics or technologies to guide the identity, domain style, and biography of the generated persona.
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
                label="Target Domains"
                placeholder={aiSelectedTags.length === 0 ? "e.g. scala, devops..." : ""}
                helperText="Leave empty to use general defaults (scala, zio)"
              />
            )}
            sx={{ mb: 3 }}
          />

          {aiProfile && (
            <Card variant="outlined" sx={{ p: 2, bgcolor: "rgba(255, 255, 255, 0.02)" }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1, color: "primary.light" }}>
                AI Suggested Persona:
              </Typography>
              <Typography variant="body2">
                <strong>Username:</strong> @{aiProfile.suggestedUsername}
              </Typography>
              <Typography variant="body2">
                <strong>Email:</strong> {aiProfile.suggestedEmail}
              </Typography>
              <Typography variant="body2" sx={{ mt: 1, mb: 1.5, color: "text.secondary" }}>
                <strong>Bio:</strong> {aiProfile.suggestedBio}
              </Typography>
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                {aiProfile.tags.map((t) => (
                  <Chip key={t.value} label={`#${t.value}`} size="small" />
                ))}
              </Box>
            </Card>
          )}
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
              {aiGenerating ? "Generating..." : "Generate Identity"}
            </Button>
          ) : (
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" onClick={handleGenerateAi} disabled={aiGenerating || aiSaving}>
                Re-roll
              </Button>
              <Button
                variant="contained"
                onClick={handleSaveAiProfile}
                disabled={aiSaving}
                startIcon={aiSaving ? <CircularProgress size={16} /> : undefined}
              >
                {aiSaving ? "Saving..." : "Accept Persona"}
              </Button>
            </Stack>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  )
}
