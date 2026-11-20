package com.bloom.core.model

/** 화면이 구분해서 보여줘야 하는 오류 종류. 오류 화면에는 항상 원인 + 다음 행동을 함께 둔다(UI 설계서 4장). */
sealed interface AppError {
    data object Offline : AppError
    data object Timeout : AppError
    data object Unauthorized : AppError
    data object NotFound : AppError
    data class Server(val code: Int?) : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}
