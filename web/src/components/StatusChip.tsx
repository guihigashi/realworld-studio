import { Chip } from "@mui/material"
import {
  AutoAwesome,
  CheckCircleOutlined,
  DoneAll,
  ErrorOutlined,
  HourglassEmpty,
  Publish,
  RateReview,
} from "@mui/icons-material"

interface StatusChipProps {
  status: ArticleStatus
  size?: "small" | "medium"
}

export function StatusChip({ status, size = "small" }: StatusChipProps) {
  switch (status) {
    case "QueuedGeneration":
      return (
        <Chip
          icon={<HourglassEmpty sx={{ fontSize: "14px !important" }} />}
          label="Queued"
          size={size}
          sx={{ bgcolor: "rgba(148, 163, 184, 0.15)", color: "#94a3b8", border: "1px solid #475569" }}
        />
      )
    case "Generating":
      return (
        <Chip
          icon={<AutoAwesome sx={{ fontSize: "14px !important", animation: "spin 2s linear infinite" }} />}
          label="Generating..."
          size={size}
          color="warning"
          variant="filled"
          sx={{
            fontWeight: 600,
            "@keyframes spin": {
              "0%": { transform: "rotate(0deg)" },
              "100%": { transform: "rotate(360deg)" },
            },
          }}
        />
      )
    case "PendingApproval":
      return (
        <Chip
          icon={<RateReview sx={{ fontSize: "14px !important" }} />}
          label="Pending Review"
          size={size}
          color="info"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      )
    case "Approved":
      return (
        <Chip
          icon={<CheckCircleOutlined sx={{ fontSize: "14px !important" }} />}
          label="Approved"
          size={size}
          color="secondary"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      )
    case "Publishing":
      return (
        <Chip
          icon={<Publish sx={{ fontSize: "14px !important" }} />}
          label="Publishing..."
          size={size}
          color="warning"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      )
    case "Published":
      return (
        <Chip
          icon={<DoneAll sx={{ fontSize: "14px !important" }} />}
          label="Published"
          size={size}
          color="success"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      )
    case "Failed":
      return (
        <Chip
          icon={<ErrorOutlined sx={{ fontSize: "14px !important" }} />}
          label="Failed"
          size={size}
          color="error"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      )
    default:
      return <Chip label={status} size={size} />
  }
}
