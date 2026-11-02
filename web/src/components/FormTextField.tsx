import { Controller, type Control, type FieldValues, type Path } from "react-hook-form"
import { TextField, type TextFieldProps } from "@mui/material"

export type FormTextFieldProps<T extends FieldValues> = Omit<
  TextFieldProps,
  "name" | "value" | "error"
> & {
  name: Path<T>
  control: Control<T, any, any>
}

export function FormTextField<T extends FieldValues>({
  name,
  control,
  helperText,
  onChange,
  ...props
}: FormTextFieldProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          {...props}
          inputRef={field.ref}
          value={field.value ?? ""}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message || helperText}
          onChange={(e) => {
            field.onChange(e)
            onChange?.(e)
          }}
        />
      )}
    />
  )
}
