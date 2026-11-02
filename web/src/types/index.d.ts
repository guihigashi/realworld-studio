import { ArticleStatuses, WritingTones } from "@/lib/constants.ts"

declare global {
  type WritingTone = (typeof WritingTones)[number]
  type ArticleStatus = (typeof ArticleStatuses)[number]

  interface Tag {
    id: number
    value: string
  }

  interface User {
    id: string
    username: string
    email: string
    password?: string
    bio: string
    avatarUrl?: string | null
    tags: Tag[]
  }

  interface CreateUserCommand {
    username: string
    email: string
    password?: string
    bio: string
    avatarUrl?: string
    tags: string[]
  }

  interface UpdateUserCommand {
    username?: string
    email?: string
    password?: string
    bio?: string
    avatarUrl?: string | null
    tags?: string[]
  }

  interface GenerateUserCommand {
    tags: string[]
  }

  interface GeneratedUserProfile {
    suggestedUsername: string
    suggestedEmail: string
    suggestedPassword: string
    suggestedBio: string
    tags: Tag[]
  }

  interface Article {
    id: string
    authorId: string
    topic: string
    title: string | null
    description: string | null
    body: string | null
    tagList: string[]
    status: ArticleStatus
    tone: WritingTone
    targetWordCount?: number | null
    conduitUrl?: string | null
    errorMessage?: string | null
    createdAt: string
    updatedAt: string
  }

  interface EnqueueArticleCommand {
    authorId: string
    topic: string
    tagList: string[]
    tone: WritingTone
    targetWordCount?: number | null
  }

  interface UpdateArticleCommand {
    title?: string
    description?: string
    body?: string
    tagList?: string[]
  }

  interface ApproveArticleCommand {
    title?: string
    description?: string
    body?: string
    tagList?: string[]
  }

  interface RegenerateArticleCommand {
    topic?: string
    tagList?: string[]
    tone?: WritingTone
    targetWordCount?: number | null
  }

  interface ArticleEvent {
    id: string
    author_id: string
    status: ArticleStatus
    updated_at: string
  }
}
