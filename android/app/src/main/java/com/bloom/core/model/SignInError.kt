package com.bloom.core.model

/** Google 로그인 실패 종류. 화면은 종류만 보고 안내 문구 선택(라이브러리 예외 타입을 몰라도 되게) */
sealed class SignInError : Exception() {
    /** 사용자가 계정 선택 창을 닫음. 안내 없이 그대로 둠 */
    data object Cancelled : SignInError()

    /** 기기에 Google 계정이 없음 */
    data object NoAccount : SignInError()

    /** 인터넷 연결 문제 */
    data object Network : SignInError()

    /** 그 밖의 실패 */
    data object Unknown : SignInError()
}
