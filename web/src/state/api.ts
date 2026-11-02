import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react"

export interface GetArticlesParams {
  status?: string
  authorId?: string
}

export interface UpdateArticleArgs {
  id: string
  cmd: UpdateArticleCommand
}

export interface ApproveArticleArgs {
  id: string
  cmd: ApproveArticleCommand
}

export interface RegenerateArticleArgs {
  id: string
  cmd?: RegenerateArticleCommand
}

export const api = createApi({
  reducerPath: "api",
  baseQuery: fetchBaseQuery({
    baseUrl: "/api",
  }),
  tagTypes: ["Tag", "User", "Article"],
  endpoints: (build) => ({
    // Tags
    getTags: build.query<Tag[], void>({
      query: () => "/tags",
      providesTags: ["Tag"],
    }),
    seedTags: build.mutation<void, Tag[]>({
      query: (tags) => ({
        url: "/tags/seed",
        method: "POST",
        body: tags,
      }),
      invalidatesTags: ["Tag"],
    }),

    // Users / Personas
    getUsers: build.query<User[], void>({
      query: () => "/users",
      providesTags: ["User"],
    }),
    getUser: build.query<User, string>({
      query: (username) => `/users/${encodeURIComponent(username)}`,
      providesTags: (_result, _error, username) => [{ type: "User", id: username }],
    }),
    createUser: build.mutation<User, CreateUserCommand>({
      query: (cmd) => ({
        url: "/users",
        method: "POST",
        body: cmd,
      }),
      invalidatesTags: ["User"],
    }),
    generateUserProfile: build.mutation<GeneratedUserProfile, GenerateUserCommand>({
      query: (cmd) => ({
        url: "/users/generate",
        method: "POST",
        body: cmd,
      }),
    }),

    // Articles
    getArticles: build.query<Article[], GetArticlesParams | void>({
      query: (params) => ({
        url: "/articles",
        params: params || undefined,
      }),
      providesTags: ["Article"],
    }),
    getArticle: build.query<Article, string>({
      query: (id) => `/articles/${encodeURIComponent(id)}`,
      providesTags: (_result, _error, id) => [{ type: "Article", id }],
    }),
    enqueueArticle: build.mutation<Article, EnqueueArticleCommand>({
      query: (cmd) => ({
        url: "/articles",
        method: "POST",
        body: cmd,
      }),
      invalidatesTags: ["Article"],
    }),
    updateArticle: build.mutation<Article, UpdateArticleArgs>({
      query: ({ id, cmd }) => ({
        url: `/articles/${encodeURIComponent(id)}`,
        method: "PUT",
        body: cmd,
      }),
      invalidatesTags: (_result, _error, { id }) => [{ type: "Article", id }, "Article"],
    }),
    approveArticle: build.mutation<Article, ApproveArticleArgs>({
      query: ({ id, cmd }) => ({
        url: `/articles/${encodeURIComponent(id)}/approve`,
        method: "POST",
        body: cmd,
      }),
      invalidatesTags: (_result, _error, { id }) => [{ type: "Article", id }, "Article"],
    }),
    regenerateArticle: build.mutation<Article, RegenerateArticleArgs>({
      query: ({ id, cmd }) => ({
        url: `/articles/${encodeURIComponent(id)}/regenerate`,
        method: "POST",
        body: cmd ?? {},
      }),
      invalidatesTags: (_result, _error, { id }) => [{ type: "Article", id }, "Article"],
    }),
  }),
})
