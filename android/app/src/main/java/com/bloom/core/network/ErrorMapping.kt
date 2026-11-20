package com.bloom.core.network

import com.bloom.core.model.AppError
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException -> AppError.Offline
    is SocketTimeoutException -> AppError.Timeout
    is HttpException -> when (code()) {
        401, 403 -> AppError.Unauthorized
        404 -> AppError.NotFound
        else -> AppError.Server(code())
    }
    is IOException -> AppError.Offline
    else -> AppError.Unknown(this)
}
