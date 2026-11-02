import type { FetchBaseQueryError } from "@reduxjs/toolkit/query"

/**
 * Type predicate to narrow an unknown error to `FetchBaseQueryError`
 */
export function isFetchBaseQueryError(error: unknown): error is FetchBaseQueryError {
  return typeof error === "object" && error != null && "status" in error
}

/**
 * Type predicate to narrow an unknown error to an object with a string 'message' property
 */
export function isErrorWithMessage(error: unknown): error is { message: string } {
  return typeof error === "object" && error != null && "message" in error && typeof (error as any).message === "string"
}

/**
 * Extract a human-readable error message from an unknown error,
 * including FetchBaseQueryError, Error instances, or plain objects with a message.
 */
export function getErrorMessage(error: unknown, fallback = "An unexpected error occurred"): string {
  if (isFetchBaseQueryError(error)) {
    if (typeof error.data === "string") return error.data
    if (typeof error.data === "object" && error.data != null && "message" in error.data) {
      return String((error.data as { message: unknown }).message)
    }
    return `HTTP ${error.status} error`
  }
  if (isErrorWithMessage(error)) return error.message
  if (error instanceof Error) return error.message
  return fallback
}
