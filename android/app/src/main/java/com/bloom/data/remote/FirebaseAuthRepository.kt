package com.bloom.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.bloom.core.model.SignInError
import com.bloom.core.model.UserAccount
import com.bloom.data.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실제 Google 로그인(S-08): Credential Manager 로 Google 계정 선택 → 받은 Google ID 토큰으로 Firebase 로그인.
 * 서버에는 Firebase ID 토큰을 보냄([idToken], AuthInterceptor 가 Authorization 헤더에 붙임).
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser?.toAccount())
    override val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    init {
        // 앱을 다시 열어도 로그인 유지(Firebase 가 기기에 로그인 상태 보관), 로그인 · 로그아웃마다 갱신
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toAccount() }
    }

    override suspend fun signInWithGoogle(activityContext: Context): Result<UserAccount> = try {
        // '로그인' 버튼으로 시작하는 흐름이라 Google 공식 권장대로 GetSignInWithGoogleOption 사용
        val option = GetSignInWithGoogleOption.Builder(webClientId()).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SignInError.Unknown
        }
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await().user
            ?: throw SignInError.Unknown
        Result.success(user.toAccount())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Auth", "Google 로그인 실패", e)
        Result.failure(e.toSignInError())
    }

    override suspend fun signOut() {
        auth.signOut()
        // 다음 로그인 때 계정 선택 창을 다시 띄우도록 Credential Manager 에 남은 선택도 지움
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /**
     * 토큰을 못 받으면(인터넷 끊김, 계정 만료 등) null: 요청은 Authorization 없이 보내고 응답(401 등)은 서버가 판단.
     * Firebase 예외가 OkHttp 인터셉터 밖으로 새면 앱이 꺼질 수 있어서 여기서 막음
     */
    override suspend fun idToken(forceRefresh: Boolean): String? = try {
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Auth", "ID 토큰 받기 실패", e)
        null
    }

    /**
     * Google 로그인 요청에 쓰는 웹 클라이언트 ID. google-services 플러그인이 google-services.json 에서
     * default_web_client_id 문자열 리소스를 만들어 줌. 파일이 없는 빌드에는 리소스가 없어 이름으로 찾음
     */
    private fun webClientId(): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) throw SignInError.Unknown
        return context.getString(id)
    }
}

private fun FirebaseUser.toAccount() = UserAccount(uid = uid, email = email.orEmpty(), displayName = displayName)

/** 라이브러리 예외를 화면 안내용 종류로 변환 */
internal fun Throwable.toSignInError(): SignInError = when (this) {
    is SignInError -> this
    is GetCredentialCancellationException -> SignInError.Cancelled
    is NoCredentialException -> SignInError.NoAccount
    is FirebaseNetworkException -> SignInError.Network
    else -> SignInError.Unknown
}
