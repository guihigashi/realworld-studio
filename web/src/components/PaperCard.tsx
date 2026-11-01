import { StatusChip } from "@/components/StatusChip.tsx"
import { Box, Chip, Stack, Typography } from "@mui/material"

export function PaperCard({
  item,
  author,
  isSelected,
  onClick,
}: {
  item: Article
  author?: User
  isSelected: boolean
  onClick: () => void
}) {
  return (
    <Box
      onClick={onClick}
      sx={{
        p: 2,
        borderRadius: 2,
        cursor: "pointer",
        border: "1px solid",
        borderColor: isSelected ? "primary.main" : "rgba(255, 255, 255, 0.08)",
        bgcolor: isSelected ? "rgba(99, 102, 241, 0.08)" : "rgba(255, 255, 255, 0.02)",
        transition: "all 0.15s ease-in-out",
        "&:hover": {
          borderColor: isSelected ? "primary.main" : "rgba(99, 102, 241, 0.4)",
          bgcolor: isSelected ? "rgba(99, 102, 241, 0.12)" : "rgba(255, 255, 255, 0.04)",
        },
      }}
    >
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          mb: 1,
        }}
      >
        <StatusChip status={item.status} size="small" />
        <Typography variant="caption" color="text.secondary">
          {new Date(item.updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
        </Typography>
      </Stack>

      <Typography
        variant="subtitle2"
        sx={{
          fontWeight: 600,
          lineHeight: 1.3,
          mb: 0.5,
          display: "-webkit-box",
          WebkitLineClamp: 2,
          WebkitBoxOrient: "vertical",
          overflow: "hidden",
        }}
      >
        {item.title || item.topic}
      </Typography>

      {author && (
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", mb: 1 }}>
          @{author.username}
        </Typography>
      )}

      <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
        {item.tagList.slice(0, 3).map((tag) => (
          <Chip key={tag} label={`#${tag}`} size="small" sx={{ fontSize: "0.7rem", height: 20 }} />
        ))}
      </Box>
    </Box>
  )
}
