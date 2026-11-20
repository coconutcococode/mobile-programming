package com.bloom

import android.app.Application
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
        }
    }
}
