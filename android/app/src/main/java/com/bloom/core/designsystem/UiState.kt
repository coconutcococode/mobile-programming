package com.bloom.core.designsystem

import com.bloom.core.model.AppError

/**
 * 화면 상태 5가지(UI 설계서 4장): 기본 · 로딩 · 비어 있음 · 오류 · 부분.
 * '부분' 상태는 Success 에 notice(배너 문구)를 붙여서 표현한다.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T, val notice: String? = null) : UiState<T>
    data class Error(val error: AppError) : UiState<Nothing>
}

/** 오류 화면에 보여 줄 '원인' 문구. 화면별로 더 구체적인 문구가 필요하면 화면에서 덮어쓴다. */
fun AppError.toUserMessage(): String = when (this) {
    AppError.Offline -> "인터넷 연결이 필요합니다"
    AppError.Timeout -> "응답이 늦어지고 있어요"
    AppError.Unauthorized -> "로그인이 필요해요"
    AppError.NotFound -> "정보를 찾을 수 없어요"
    is AppError.Server -> "서버에 문제가 생겼어요"
    is AppError.Unknown -> "알 수 없는 오류가 발생했어요"
}
