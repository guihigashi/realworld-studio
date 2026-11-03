import { useState } from "react"
import {
  Box,
  Chip,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material"
import { Edit, Visibility, ViewColumn } from "@mui/icons-material"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import rehypeHighlight from "rehype-highlight"
import "highlight.js/styles/github-dark.css"

interface MarkdownEditorProps {
  value: string
  onChange: (val: string) => void
  disabled?: boolean
  minHeight?: number
}

export function MarkdownEditor({
  value,
  onChange,
  disabled = false,
  minHeight = 420,
}: MarkdownEditorProps) {
  const [viewMode, setViewMode] = useState<"split" | "edit" | "preview">("split")

  const wordCount = value.trim() ? value.trim().split(/\s+/).length : 0
  const charCount = value.length

  return (
    <Box sx={{ width: "100%" }}>
      {/* Top bar with mode switcher and stats */}
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          px: 1.5,
          py: 0.5,
          borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
          bgcolor: "rgba(15, 23, 42, 0.6)",
          borderRadius: "8px 8px 0 0",
        }}
      >
        <Tabs
          value={viewMode}
          onChange={(_, val) => setViewMode(val)}
          sx={{
            minHeight: 36,
            "& .MuiTab-root": {
              minHeight: 36,
              py: 0,
              px: 1.5,
              fontSize: "0.85rem",
            },
          }}
        >
          <Tab icon={<ViewColumn sx={{ fontSize: 16 }} />} iconPosition="start" label="Split View" value="split" />
          <Tab icon={<Edit sx={{ fontSize: 16 }} />} iconPosition="start" label="Edit Only" value="edit" />
          <Tab icon={<Visibility sx={{ fontSize: 16 }} />} iconPosition="start" label="Preview Only" value="preview" />
        </Tabs>

        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <Chip label={`${wordCount} words`} size="small" variant="outlined" sx={{ fontSize: "0.75rem" }} />
          <Chip label={`${charCount} chars`} size="small" variant="outlined" sx={{ fontSize: "0.75rem" }} />
        </Stack>
      </Stack>

      {/* Editor & Preview Area */}
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns:
            viewMode === "split" ? { xs: "1fr", md: "1fr 1fr" } : "1fr",
          minHeight,
          border: "1px solid rgba(255, 255, 255, 0.08)",
          borderTop: "none",
          borderRadius: "0 0 8px 8px",
          overflow: "hidden",
          bgcolor: "#0d1117",
        }}
      >
        {/* Edit Pane */}
        {(viewMode === "split" || viewMode === "edit") && (
          <Box
            sx={{
              p: 1.5,
              borderRight: viewMode === "split" ? { md: "1px solid rgba(255, 255, 255, 0.08)" } : "none",
              display: "flex",
              flexDirection: "column",
            }}
          >
            <TextField
              multiline
              fullWidth
              value={value}
              onChange={(e) => onChange(e.target.value)}
              disabled={disabled}
              placeholder="Enter markdown body here..."
              variant="standard"
              slotProps={{
                input: {
                  disableUnderline: true,
                  sx: {
                    fontFamily: '"Fira Code", "JetBrains Mono", Consolas, monospace',
                    fontSize: "0.9rem",
                    lineHeight: 1.6,
                    color: "#e2e8f0",
                    height: "100%",
                    "& textarea": {
                      height: "100% !important",
                      minHeight: minHeight - 40,
                    },
                  },
                },
              }}
            />
          </Box>
        )}

        {/* Live Preview Pane */}
        {(viewMode === "split" || viewMode === "preview") && (
          <Paper
            elevation={0}
            sx={{
              p: 2.5,
              bgcolor: "#090d16",
              overflowY: "auto",
              maxHeight: minHeight + 100,
              fontSize: "0.95rem",
              lineHeight: 1.7,
              color: "#e2e8f0",
              "& h1, & h2, & h3, & h4, & h5, & h6": {
                color: "#f8fafc",
                fontWeight: 600,
                mt: 2,
                mb: 1,
              },
              "& h1": { fontSize: "1.6rem", borderBottom: "1px solid rgba(255, 255, 255, 0.1)", pb: 0.5 },
              "& h2": { fontSize: "1.35rem", borderBottom: "1px solid rgba(255, 255, 255, 0.08)", pb: 0.5 },
              "& h3": { fontSize: "1.15rem" },
              "& p": { mb: 1.5 },
              "& code": {
                fontFamily: '"Fira Code", "JetBrains Mono", Consolas, monospace',
                bgcolor: "rgba(99, 102, 241, 0.15)",
                color: "#a5b4fc",
                px: 0.7,
                py: 0.2,
                borderRadius: 1,
                fontSize: "0.85em",
              },
              "& pre": {
                bgcolor: "#161b22",
                p: 2,
                borderRadius: 2,
                overflowX: "auto",
                maxWidth: "100%",
                border: "1px solid rgba(255, 255, 255, 0.1)",
                my: 2,
                "& code": {
                  bgcolor: "transparent !important",
                  p: "0 !important",
                  color: "#f1f5f9",
                  fontSize: "0.88rem",
                  whiteSpace: "pre",
                  wordBreak: "normal",
                  overflowWrap: "normal",
                },
                "& code.hljs": {
                  bgcolor: "transparent !important",
                  p: "0 !important",
                },
              },
              "& blockquote": {
                borderLeft: "4px solid #6366f1",
                pl: 2,
                ml: 0,
                color: "#94a3b8",
                fontStyle: "italic",
              },
              "& ul, & ol": {
                pl: 3,
                mb: 1.5,
              },
              "& table": {
                width: "100%",
                borderCollapse: "collapse",
                mb: 2,
                "& th, & td": {
                  border: "1px solid rgba(255, 255, 255, 0.1)",
                  p: 1,
                  textAlign: "left",
                },
                "& th": {
                  bgcolor: "rgba(255, 255, 255, 0.05)",
                },
              },
            }}
          >
            {value.trim() ? (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeHighlight]}
              >
                {value}
              </ReactMarkdown>
            ) : (
              <Typography color="text.secondary" sx={{ fontStyle: "italic" }}>
                Preview will render here as you type...
              </Typography>
            )}
          </Paper>
        )}
      </Box>
    </Box>
  )
}
