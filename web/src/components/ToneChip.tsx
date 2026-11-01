import { Chip } from "@mui/material"
import {
  Chat,
  MenuBook,
  Psychology,
  School,
  SentimentVerySatisfied,
  Whatshot,
} from "@mui/icons-material"

interface ToneChipProps {
  tone: WritingTone
  size?: "small" | "medium"
}

export function ToneChip({ tone, size = "small" }: ToneChipProps) {
  switch (tone) {
    case "TechnicalDeepDive":
      return (
        <Chip
          icon={<Psychology sx={{ fontSize: "14px !important" }} />}
          label="Technical Deep Dive"
          size={size}
          sx={{ bgcolor: "rgba(99, 102, 241, 0.15)", color: "#818cf8", border: "1px solid rgba(99, 102, 241, 0.3)" }}
        />
      )
    case "Conversational":
      return (
        <Chip
          icon={<Chat sx={{ fontSize: "14px !important" }} />}
          label="Conversational"
          size={size}
          sx={{ bgcolor: "rgba(6, 182, 212, 0.15)", color: "#22d3ee", border: "1px solid rgba(6, 182, 212, 0.3)" }}
        />
      )
    case "Academic":
      return (
        <Chip
          icon={<School sx={{ fontSize: "14px !important" }} />}
          label="Academic"
          size={size}
          sx={{ bgcolor: "rgba(168, 85, 247, 0.15)", color: "#c084fc", border: "1px solid rgba(168, 85, 247, 0.3)" }}
        />
      )
    case "Provocative":
      return (
        <Chip
          icon={<Whatshot sx={{ fontSize: "14px !important" }} />}
          label="Provocative"
          size={size}
          sx={{ bgcolor: "rgba(239, 68, 68, 0.15)", color: "#f87171", border: "1px solid rgba(239, 68, 68, 0.3)" }}
        />
      )
    case "Storyteller":
      return (
        <Chip
          icon={<MenuBook sx={{ fontSize: "14px !important" }} />}
          label="Storyteller"
          size={size}
          sx={{ bgcolor: "rgba(245, 158, 11, 0.15)", color: "#fbbf24", border: "1px solid rgba(245, 158, 11, 0.3)" }}
        />
      )
    case "Humorous":
      return (
        <Chip
          icon={<SentimentVerySatisfied sx={{ fontSize: "14px !important" }} />}
          label="Humorous"
          size={size}
          sx={{ bgcolor: "rgba(236, 72, 153, 0.15)", color: "#f472b6", border: "1px solid rgba(236, 72, 153, 0.3)" }}
        />
      )
    default:
      return <Chip label={tone} size={size} />
  }
}
