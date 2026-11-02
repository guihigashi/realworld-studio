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
}

export function FormAutocomplete<T extends FieldValues>({
  name,
  control,
  label,
  placeholder,
  helperText,
  ...props
}: FormAutocompleteProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
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
            />
          )}
        />
      )}
    />
  )
}
