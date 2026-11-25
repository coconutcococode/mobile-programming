package com.bloom

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.bloom.core.model.SignInError
import com.bloom.data.remote.toSignInError
import com.bloom.feature.settings.signInMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Google 로그인 실패 종류 분류와 설정 화면 안내 문구 */
class SignInErrorTest {

    @Test
    fun `라이브러리 예외를 취소, 계정 없음, 그 밖의 실패로 나눈다`() {
        assertEquals(SignInError.Cancelled, GetCredentialCancellationException().toSignInError())
        assertEquals(SignInError.NoAccount, NoCredentialException().toSignInError())
        assertEquals(SignInError.Unknown, IllegalStateException().toSignInError())
        // FirebaseNetworkException 은 만들 때 안드로이드 함수를 불러 일반 단위 테스트에서 생성 불가(인터넷 분류는 에뮬레이터 확인)
    }

    @Test
    fun `사용자가 직접 닫으면 안내하지 않고 나머지는 안내 문구를 보여 준다`() {
        assertNull(signInMessage(SignInError.Cancelled))
        assertNotNull(signInMessage(SignInError.NoAccount))
        assertNotNull(signInMessage(SignInError.Network))
        assertNotNull(signInMessage(SignInError.Unknown))
    }
}
