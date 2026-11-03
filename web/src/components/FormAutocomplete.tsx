import { Controller, type Control, type FieldValues, type Path } from "react-hook-form"
import { Autocomplete, TextField, type AutocompleteProps } from "@mui/material"

export interface FormAutocompleteProps<T extends FieldValues>
  extends Omit<
    AutocompleteProps<string, true, false, true>,
    "renderInput" | "value" | "onChange"
  > {
  name: Path<T>
  control: Control<T, any, any>
  label?: string
  placeholder?: string
  helperText?: string
  allowPasteDelimiter?: boolean
}

export function FormAutocomplete<T extends FieldValues>({
  name,
  control,
  label,
  placeholder,
  helperText,
  allowPasteDelimiter = true,
  ...props
}: FormAutocompleteProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => {
        const handlePaste = (e: React.ClipboardEvent<HTMLDivElement>) => {
          if (!allowPasteDelimiter) return

          const pasted = e.clipboardData.getData("text")
          if (pasted.includes(",") || pasted.includes("\n")) {
            e.preventDefault()
            const newTokens = pasted
              .split(/[\n,]+/)
              .map((t) => t.trim().replace(/^[\s[\]"']+|[\s[\]"']+$/g, ""))
              .filter((t) => t.length > 0)

            const current = Array.isArray(field.value) ? field.value : []
            const merged = Array.from(new Set([...current, ...newTokens]))
            field.onChange(merged)
          }
        }

        return (
          <Autocomplete
            {...props}
            multiple
            freeSolo
            value={field.value || []}
            onChange={(_, newValue) => field.onChange(newValue)}
            renderInput={(params) => (
              <TextField
                {...params}
                size="small"
                label={label}
                placeholder={placeholder}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message || helperText}
                onPaste={handlePaste}
              />
            )}
          />
        )
      }}
    />
  )
}
