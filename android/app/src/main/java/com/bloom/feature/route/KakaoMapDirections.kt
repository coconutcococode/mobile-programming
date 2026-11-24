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

/** 앱 → 웹 순서로 열어 본다. 둘 다 열 곳이 없으면 false. */
fun openKakaoMapDirections(context: Context, directions: KakaoMapDirections): Boolean {
    val uris = listOfNotNull(directions.appUri, directions.webUrl)
    for (uri in uris) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // 카카오맵 앱이 없으면 다음 후보(웹)
        }
    }
    return false
}
