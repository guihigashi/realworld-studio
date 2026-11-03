import { Card, Chip, Stack, Tooltip, Typography } from "@mui/material"

const MAX_TAGS_TO_SHOW = 10

export function PaperBanner({ tags }: { tags: Tag[] }) {
  const isTruncated = tags.length > MAX_TAGS_TO_SHOW
  return (
    <Card sx={{ mb: 3, p: 2, bgcolor: "rgba(255, 255, 255, 0.02)" }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap", rowGap: 1 }}>
        <Tooltip title="Registered tags in the database pool">
          <Typography variant="caption" sx={{ fontWeight: 600, color: "text.secondary", mr: 1 }}>
            AVAILABLE DOMAIN TAGS:
          </Typography>
        </Tooltip>
        {tags.slice(0, MAX_TAGS_TO_SHOW).map((tag) => (
          <Chip
            key={tag.value}
            label={tag.value}
            size="small"
            variant="outlined"
            sx={{ fontSize: "0.75rem", borderColor: "rgba(255, 255, 255, 0.12)" }}
          />
        ))}
        {isTruncated && (
          <Tooltip title="More tags available">
            <span>&hellip;</span>
          </Tooltip>
        )}
      </Stack>
    </Card>
  )
}
