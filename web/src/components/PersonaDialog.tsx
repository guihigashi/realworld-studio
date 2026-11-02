import { useState } from "react"
import { useForm, type SubmitHandler } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import {
  Alert,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
} from "@mui/material"
import { Save } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { FormTextField } from "./FormTextField.tsx"
import { FormAutocomplete } from "./FormAutocomplete.tsx"

const personaFormSchema = z.object({
  username: z.string().trim().min(2, "Username must be at least 2 characters"),
  email: z.string().trim().email("Invalid email address"),
  password: z
    .string()
    .trim()
    .min(8, "Password must be at least 8 characters")
    .or(z.literal(""))
    .optional(),
  bio: z.string().trim().min(5, "Bio must be at least 5 characters"),
  avatarUrl: z.string().trim().url("Must be a valid URL").or(z.literal("")).optional(),
  tags: z.array(z.string()),
})

type PersonaFormValues = z.infer<typeof personaFormSchema>

const defaultPersonaValues: PersonaFormValues = {
  username: "",
  email: "",
  password: "",
  bio: "",
  avatarUrl: "",
  tags: [],
}

interface PersonaDialogProps {
  open: boolean
  onClose: () => void
  user?: User | null
  availableTags: string[]
}

export function PersonaDialog({ open, onClose, user, availableTags }: PersonaDialogProps) {
  const [createUserMutation] = api.useCreateUserMutation()
  const [updateUserMutation] = api.useUpdateUserMutation()
  const [apiError, setApiError] = useState<string | null>(null)

  const isEdit = Boolean(user)

  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting, isDirty },
  } = useForm<PersonaFormValues>({
    resolver: zodResolver(personaFormSchema),
    defaultValues: defaultPersonaValues,
    values: user
      ? {
          username: user.username,
          email: user.email,
          password: user.password || "",
          bio: user.bio,
          avatarUrl: user.avatarUrl || "",
          tags: user.tags.map((t) => t.value),
        }
      : defaultPersonaValues,
  })

  const handleClose = () => {
    setApiError(null)
    reset(defaultPersonaValues)
    onClose()
  }

  const onSubmit: SubmitHandler<PersonaFormValues> = async (data) => {
    setApiError(null)
    try {
      if (isEdit && user) {
        await updateUserMutation({
          id: user.id,
          cmd: {
            username: data.username,
            email: data.email,
            password: data.password ? data.password : undefined,
            bio: data.bio,
            avatarUrl: data.avatarUrl ? data.avatarUrl : undefined,
            tags: data.tags,
          },
        }).unwrap()
      } else {
        await createUserMutation({
          username: data.username,
          email: data.email,
          password: data.password || undefined,
          bio: data.bio,
          avatarUrl: data.avatarUrl || undefined,
          tags: data.tags,
        }).unwrap()
      }
      handleClose()
    } catch (err: unknown) {
      setApiError(getErrorMessage(err, isEdit ? "Failed to update persona" : "Failed to create persona"))
    }
  }

  return (
    <Dialog open={open} onClose={() => !isSubmitting && handleClose()} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle sx={{ fontWeight: 700 }}>
          {isEdit ? `Edit Persona (@${user?.username})` : "Add Persona Manually"}
        </DialogTitle>
        <DialogContent>
          {apiError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setApiError(null)}>
              {apiError}
            </Alert>
          )}

          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormTextField
              name="username"
              control={control}
              label="Username"
              fullWidth
              size="small"
              placeholder="e.g. dev_sarah"
              helperText="Author handle used on Conduit and Studio"
            />

            <FormTextField
              name="email"
              control={control}
              label="Email"
              fullWidth
              size="small"
              type="email"
              placeholder="e.g. sarah@example.com"
              helperText="Conduit login and notification email"
            />

            <FormTextField
              name="password"
              control={control}
              label="Conduit Password"
              fullWidth
              size="small"
              type="password"
              placeholder={isEdit ? "Leave blank to keep existing password" : "Min 8 chars, or leave blank for default"}
              helperText={
                isEdit
                  ? "Enter a new password (min 8 chars) or leave blank to keep unchanged."
                  : "Used to register & authenticate against Conduit API (min 8 chars)."
              }
            />

            <FormTextField
              name="bio"
              control={control}
              label="Bio"
              fullWidth
              multiline
              rows={3}
              placeholder="A short biography and technical background..."
              helperText="Author background, expertise, and style"
            />

            <FormTextField
              name="avatarUrl"
              control={control}
              label="Avatar URL (Optional)"
              fullWidth
              size="small"
              placeholder="https://..."
            />

            <FormAutocomplete
              name="tags"
              control={control}
              options={availableTags}
              label="Expertise Tags"
              placeholder="Select or type tags..."
              helperText="Topics used to steer article synthesis and author specialty"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={handleClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={isSubmitting || (isEdit && !isDirty)}
            startIcon={isSubmitting ? <CircularProgress size={16} /> : isEdit ? <Save /> : undefined}
          >
            {isSubmitting ? "Saving..." : isEdit ? "Save Changes" : "Create Persona"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
