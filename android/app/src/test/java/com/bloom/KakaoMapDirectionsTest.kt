package com.bloom

import com.bloom.core.model.LatLng
import com.bloom.feature.route.KAKAO_MAP_PACKAGE
import com.bloom.feature.route.KakaoMapLaunch
import com.bloom.feature.route.kakaoMapDirections
import com.bloom.feature.route.kakaoMapLaunches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-02-4 '카카오맵 길찾기' 링크: 앱은 현재 위치·목적지 좌표가 둘 다 있을 때만, 웹은 장소 ID만으로. */
class KakaoMapDirectionsTest {

    private val here = LatLng(37.5563, 126.9236)
    private val there = LatLng(37.5551, 126.9228)

    @Test
    fun `좌표가 둘 다 있으면 앱 대중교통 길찾기, 출발은 현재 위치`() {
        val directions = kakaoMapDirections("18577297", here, there)
        assertEquals("kakaomap://route?sp=37.5563,126.9236&ep=37.5551,126.9228&by=PUBLICTRANSIT", directions.appUri)
        assertEquals("https://map.kakao.com/link/to/18577297", directions.webUrl)
    }

    @Test
    fun `현재 위치를 못 받았으면 기본 지역을 출발지로 넘기지 않고 웹으로만 연다`() {
        val directions = kakaoMapDirections("18577297", origin = null, destination = there)
        assertNull(directions.appUri)
        assertEquals("https://map.kakao.com/link/to/18577297", directions.webUrl)
    }

    @Test
    fun `목적지 조회에 실패해도 장소 ID로 웹 길찾기를 연다`() {
        val directions = kakaoMapDirections("18577297", origin = here, destination = null)
        assertNull(directions.appUri)
        assertEquals("https://map.kakao.com/link/to/18577297", directions.webUrl)
    }

    @Test
    fun `장소 ID에 경로 구분 문자가 있어도 링크가 깨지지 않게 인코딩한다`() {
        assertEquals("https://map.kakao.com/link/to/a%2Fb%3Fc", kakaoMapDirections("a/b?c", null, null).webUrl)
    }

    @Test
    fun `위치가 든 앱 링크는 카카오맵 앱으로만 보내고, 웹 링크는 아무 브라우저로 연다`() {
        val directions = kakaoMapDirections("18577297", here, there)
        assertEquals(
            listOf(
                KakaoMapLaunch(directions.appUri!!, KAKAO_MAP_PACKAGE),
                KakaoMapLaunch("https://map.kakao.com/link/to/18577297", packageName = null),
            ),
            kakaoMapLaunches(directions),
        )
        assertEquals(
            listOf(KakaoMapLaunch("https://map.kakao.com/link/to/18577297", packageName = null)),
            kakaoMapLaunches(kakaoMapDirections("18577297", null, there)),
        )
    }
}
