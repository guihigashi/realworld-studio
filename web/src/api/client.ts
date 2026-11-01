const BASE_URL = ""

async function request<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
  })

  if (!res.ok) {
    const errorText = await res.text()
    let errorDetail = errorText
    try {
      const parsed = JSON.parse(errorText)
      if (parsed.message) errorDetail = parsed.message
      else if (typeof parsed === "string") errorDetail = parsed
    } catch {
      // ignore
    }
    throw new Error(errorDetail || `HTTP error ${res.status}: ${res.statusText}`)
  }

  // Handle empty responses (like 204 or void return)
  const contentType = res.headers.get("content-type")
  if (contentType && contentType.includes("application/json")) {
    return (await res.json()) as T
  }
  return undefined as T
}

export const api = {
  // Tags
  getTags: (): Promise<Tag[]> => request<Tag[]>("/api/tags"),
  seedTags: (tags: Tag[]): Promise<void> =>
    request<void>("/api/tags/seed", {
      method: "POST",
      body: JSON.stringify(tags),
    }),

  // Users / Personas
  getUsers: (): Promise<User[]> => request<User[]>("/api/users"),
  getUser: (username: string): Promise<User> => request<User>(`/api/users/${encodeURIComponent(username)}`),
  createUser: (cmd: CreateUserCommand): Promise<User> =>
    request<User>("/api/users", {
      method: "POST",
      body: JSON.stringify(cmd),
    }),
  generateUserProfile: (cmd: GenerateUserCommand): Promise<GeneratedUserProfile> =>
    request<GeneratedUserProfile>("/api/users/generate", {
      method: "POST",
      body: JSON.stringify(cmd),
    }),

  // Articles
  getArticles: (params?: { status?: string; authorId?: string }): Promise<Article[]> => {
    const searchParams = new URLSearchParams()
    if (params?.status) searchParams.set("status", params.status)
    if (params?.authorId) searchParams.set("authorId", params.authorId)
    const qs = searchParams.toString()
    return request<Article[]>(`/api/articles${qs ? `?${qs}` : ""}`)
  },
  getArticle: (id: string): Promise<Article> => request<Article>(`/api/articles/${encodeURIComponent(id)}`),
  enqueueArticle: (cmd: EnqueueArticleCommand): Promise<Article> =>
    request<Article>("/api/articles", {
      method: "POST",
      body: JSON.stringify(cmd),
    }),
  updateArticle: (id: string, cmd: UpdateArticleCommand): Promise<Article> =>
    request<Article>(`/api/articles/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(cmd),
    }),
  approveArticle: (id: string, cmd: ApproveArticleCommand): Promise<Article> =>
    request<Article>(`/api/articles/${encodeURIComponent(id)}/approve`, {
      method: "POST",
      body: JSON.stringify(cmd),
    }),
  regenerateArticle: (id: string, cmd: RegenerateArticleCommand): Promise<Article> =>
    request<Article>(`/api/articles/${encodeURIComponent(id)}/regenerate`, {
      method: "POST",
      body: JSON.stringify(cmd),
    }),
}
