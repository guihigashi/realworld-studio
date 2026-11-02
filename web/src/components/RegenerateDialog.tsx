import { useState } from "react"
import { Controller, useForm, type SubmitHandler } from "react-hook-form"
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
  MenuItem,
  Stack,
  TextField,
} from "@mui/material"
import { Replay } from "@mui/icons-material"
import { api } from "@/state/api.ts"
import { getErrorMessage } from "@/state/helpers.ts"
import { WritingTones } from "@/lib/constants.ts"
import { ToneChip } from "@/components/ToneChip.tsx"
import { FormTextField } from "./FormTextField.tsx"

const regenerateFormSchema = z.object({
  topic: z.string().trim().min(3, "Topic prompt must be at least 3 characters"),
  tone: z.enum(WritingTones),
  targetWordCount: z.number().min(100, "Minimum 100 words").max(2000, "Maximum 2000 words"),
})

type RegenerateFormValues = z.infer<typeof regenerateFormSchema>

const defaultRegenerateValues: RegenerateFormValues = {
  topic: "",
  tone: "TechnicalDeepDive",
  targetWordCount: 800,
}

interface RegenerateDialogProps {
  open: boolean
  onClose: () => void
  article: Article | null
  onSuccess: () => void
}

export function RegenerateDialog({ open, onClose, article, onSuccess }: RegenerateDialogProps) {
  const [regenerateArticleMutation, { isLoading: regenLoading }] = api.useRegenerateArticleMutation()
  const [actionError, setActionError] = useState<string | null>(null)

  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<RegenerateFormValues>({
    resolver: zodResolver(regenerateFormSchema),
    defaultValues: defaultRegenerateValues,
    values: article
      ? {
          topic: article.topic,
          tone: article.tone || "TechnicalDeepDive",
          targetWordCount: article.targetWordCount || 800,
        }
      : defaultRegenerateValues,
  })

  const handleClose = () => {
    setActionError(null)
    reset(defaultRegenerateValues)
    onClose()
  }

  const onSubmit: SubmitHandler<RegenerateFormValues> = async (data) => {
    if (!article) return
    setActionError(null)
    try {
      await regenerateArticleMutation({
        id: article.id,
        cmd: {
          topic: data.topic.trim(),
          tone: data.tone,
          targetWordCount: data.targetWordCount,
        },
      }).unwrap()
      onSuccess()
      handleClose()
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, "Failed to trigger re-generation"))
    }
  }

  const isBusy = regenLoading || isSubmitting

  return (
    <Dialog open={open} onClose={() => !isBusy && handleClose()} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Replay color="primary" />
          Regenerate Article
        </DialogTitle>
        <DialogContent dividers>
          {actionError && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>
              {actionError}
            </Alert>
          )}

          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <FormTextField
              name="topic"
              control={control}
              label="Topic Prompt"
              fullWidth
              multiline
              rows={2}
            />

            <FormTextField select fullWidth name="tone" control={control} label="Writing Tone">
              {WritingTones.map((t) => (
                <MenuItem key={t} value={t}>
                  <ToneChip tone={t} size="small" />
                </MenuItem>
              ))}
            </FormTextField>

            <Controller
              name="targetWordCount"
              control={control}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
                  type="number"
                  label="Target Word Count"
                  fullWidth
                  inputRef={field.ref}
                  value={field.value ?? ""}
                  onChange={(e) => {
                    const val = e.target.value
                    field.onChange(val === "" ? 0 : Number(val))
                  }}
                  error={Boolean(fieldState.error)}
                  helperText={fieldState.error?.message}
                />
              )}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={handleClose} disabled={isBusy}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isBusy}
            startIcon={isBusy ? <CircularProgress size={16} /> : <Replay />}
          >
            Regenerate Draft
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
