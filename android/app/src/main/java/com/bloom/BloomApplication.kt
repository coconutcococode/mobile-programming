package com.bloom

import android.app.Application
import android.util.Log
import com.bloom.feature.guide.GuideNotification
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BloomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GuideNotification.createChannel(this)
        // 키가 없으면(local.properties 미설정) 지도만 안 뜨고 나머지는 동작한다.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
            // 개발 중에만: 카카오 콘솔(플랫폼 키 → Android 앱 정보)에 등록할 키 해시를 Logcat 에 출력(검색어 KeyHash)
            if (BuildConfig.DEBUG) Log.d("KeyHash", KakaoMapSdk.INSTANCE.hashKey)
        }
    }
}
