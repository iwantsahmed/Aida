package com.rayluxeo.aida.core.common

/**
 * Machine-readable failure reasons. Human-facing wording never lives in the domain layer —
 * the presentation layer renders these per active language.
 */
enum class MiraErrorCode {
    VALIDATION,
    NOT_FOUND,
    STORAGE,
    NOT_UNDERSTOOD,
    ENGINE_UNAVAILABLE,
    PERMISSION_REQUIRED,
    AUTHENTICATION_FAILED,
    UNKNOWN,
}

data class MiraError(
    val code: MiraErrorCode,
    /** Developer-facing detail for logs. Never spoken to the user verbatim. */
    val detail: String? = null,
    val cause: Throwable? = null,
)

/** Deliberately explicit result type so failures are impossible to ignore at call sites. */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: MiraError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (MiraError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun failure(code: MiraErrorCode, detail: String? = null, cause: Throwable? = null): AppResult.Failure =
    AppResult.Failure(MiraError(code, detail, cause))
