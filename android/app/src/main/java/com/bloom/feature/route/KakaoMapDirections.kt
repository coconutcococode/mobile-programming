package com.bloom.feature.route

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bloom.core.model.LatLng
import java.net.URLEncoder

/**
 * S-02-4 '카카오맵 길찾기': 우리 앱에서 경로를 못 불러왔을 때 카카오맵에서 이어서 찾게 한다.
 * 좌표는 방금 받은 값을 넘기기만 하고 저장하지 않는다(NFR-11).
 *
 * @property appUri 카카오맵 앱 대중교통 길찾기. 출발(현재 위치)·도착 좌표가 둘 다 있을 때만 만든다.
 * @property webUrl 앱이 없거나 좌표가 없을 때 여는 웹 길찾기. 장소 ID만으로 열 수 있다.
 */
data class KakaoMapDirections(val appUri: String?, val webUrl: String)

/**
 * @param origin 기기에서 받은 현재 위치. 못 받아 기본 지역으로 검색했으면 null — 사용자가 있지 않은 곳을 출발지로 넘기지 않는다.
 * @param destination 실시간으로 조회한 목적지 좌표. 조회에 실패했으면 null.
 */
fun kakaoMapDirections(placeId: String, origin: LatLng?, destination: LatLng?): KakaoMapDirections {
    val appUri = if (origin != null && destination != null) {
        "kakaomap://route?sp=${origin.param()}&ep=${destination.param()}&by=PUBLICTRANSIT"
    } else {
        null
    }
    // 카카오 지도 URL 바로가기: /link/to/{장소ID}
    val webUrl = "https://map.kakao.com/link/to/${URLEncoder.encode(placeId, Charsets.UTF_8.name())}"
    return KakaoMapDirections(appUri, webUrl)
}

private fun LatLng.param() = "$latitude,$longitude"

/** 카카오맵 앱(Google Play 패키지 이름) */
const val KAKAO_MAP_PACKAGE = "net.daum.android.map"

/** 열어 볼 링크와 받을 앱. [packageName]이 null 이면 그 링크를 열 수 있는 아무 앱(브라우저). */
data class KakaoMapLaunch(val uri: String, val packageName: String?)

/**
 * 앱 → 웹 순서.
 * 앱 링크에는 현재 위치와 목적지가 들어 있으므로 카카오맵 앱으로만 보낸다.
 * 같은 `kakaomap://` 주소를 등록한 다른 앱이 위치를 받아 가지 못하게 하기 위해서다.
 * 웹 링크에는 장소 ID만 있어 아무 브라우저로나 연다.
 */
fun kakaoMapLaunches(directions: KakaoMapDirections): List<KakaoMapLaunch> = listOfNotNull(
    directions.appUri?.let { KakaoMapLaunch(it, KAKAO_MAP_PACKAGE) },
    KakaoMapLaunch(directions.webUrl, packageName = null),
)

/** [kakaoMapLaunches] 순서로 열어 본다. 둘 다 열 곳이 없으면 false. */
fun openKakaoMapDirections(context: Context, directions: KakaoMapDirections): Boolean {
    for (launch in kakaoMapLaunches(directions)) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(launch.uri))
            .setPackage(launch.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // 카카오맵 앱이 없으면 다음 후보(웹)
        }
    }
    return false
}
