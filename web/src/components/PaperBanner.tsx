import { Card, Chip, Stack, Tooltip, Typography } from "@mui/material"

export function PaperBanner({ tags }: { tags: Tag[] }) {
  return (
    <Card sx={{ mb: 3, p: 2, bgcolor: "rgba(255, 255, 255, 0.02)" }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
        <Tooltip title="Registered tags in the database pool">
          <Typography variant="caption" sx={{ fontWeight: 600, color: "text.secondary", mr: 1 }}>
            AVAILABLE DOMAIN TAGS:
          </Typography>
        </Tooltip>
        {tags.map((tag) => (
          <Chip
            key={tag.name}
            label={tag.name}
            size="small"
            variant="outlined"
            sx={{ fontSize: "0.75rem", borderColor: "rgba(255, 255, 255, 0.12)" }}
          />
        ))}
      </Stack>
    </Card>
  )
}
