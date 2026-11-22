package com.bloom.core.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.LatLng
import com.bloom.core.model.TransitMode
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.LatLng as KakaoLatLng
import kotlin.math.roundToInt

/** 지도 위 핀 하나. [label] 은 번호 핀("1"~"10", S-06)일 때만 */
data class MapMarker(
    val id: String,
    val point: LatLng,
    val label: String? = null,
    val selected: Boolean = false,
)

/**
 * 경로선 한 구간. 경로는 구간(도보 · 버스 · 지하철 한 토막) 단위로 받고, 구간마다 이동 수단 색으로 그린다(팀 합의 11/22).
 * 예: 경로 상세(역할B)에서 candidate.segments.map { MapRouteSegment(it.mode, it.path) }
 */
data class MapRouteSegment(
    val mode: TransitMode,
    val points: List<LatLng>,
)

private const val MY_LOCATION_ID = "me"

/**
 * 이동 수단별 경로선 모양(UI 설계서 3장 색). 구간 막대(경로 후보 화면)와 같은 짝으로 맞췄다:
 * 도보 = 중간 파랑 · 가는 선, 버스 = 보조 하늘 + 네이비 테두리(밝은 지도에서도 보이게), 지하철 = 네이비 + 흰 테두리.
 * 지도 바탕은 다크 모드에서도 밝은 기본 지도라 테마와 관계없이 같은 색을 쓴다.
 * RouteLineStyle.from(선 굵기, 선 색, 테두리 굵기, 테두리 색) — 굵기 단위는 px.
 */
private fun routeLineStyle(mode: TransitMode): RouteLineStyle = when (mode) {
    TransitMode.WALK -> RouteLineStyle.from(8f, 0xFF6A93C5.toInt(), 2f, Color.WHITE)
    TransitMode.BUS -> RouteLineStyle.from(12f, 0xFFB7D6F2.toInt(), 2f, 0xFF2F4B6B.toInt())
    TransitMode.SUBWAY -> RouteLineStyle.from(12f, 0xFF2F4B6B.toInt(), 2f, Color.WHITE)
}

/**
 * 카카오 지도(안드로이드 View)를 Compose 화면 안에 넣는 부품. 홈(S-01)·주변(S-06)·경로 상세(S-03)가 같이 쓴다.
 * 카카오맵 SDK 는 Compose 전용 부품이 없어서 AndroidView 로 감싼다.
 * local.properties 에 KAKAO_NATIVE_APP_KEY 가 없으면(SDK 초기화 안 됨) 지도 대신 안내 상자를 보여 준다.
 */
@Composable
fun KakaoMapView(
    center: LatLng,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 16,
    markers: List<MapMarker> = emptyList(),
    routeSegments: List<MapRouteSegment> = emptyList(),
    myLocation: LatLng? = null,
    // 값이 바뀔 때마다 center 로 카메라를 다시 옮긴다. center 가 같아도([내 위치 보기]를 다시 누름) 돌아오게 하려고 따로 받는다
    recenterRequest: Int = 0,
    onMarkerClick: (String) -> Unit = {},
    onMapError: (Exception) -> Unit = {},
) {
    if (!KakaoMapSdk.isInitialized()) {
        Box(modifier.background(BloomTheme.colors.skeleton), contentAlignment = Alignment.Center) {
            Text("지도를 보려면 local.properties 에 카카오 키를 넣어 주세요", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnError by rememberUpdatedState(onMapError)

    // 지도 시작 + 화면이 가려지면 멈추고 다시 보이면 재개, 화면을 떠나면 정리
    DisposableEffect(lifecycleOwner) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit
                override fun onMapError(error: Exception) = currentOnError(error) // 키 해시·키 오류도 여기로 온다
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    map.setOnLabelClickListener { _, _, label ->
                        if (label.labelId != MY_LOCATION_ID) currentOnMarkerClick(label.labelId)
                        true // 클릭을 여기서 처리했다는 뜻
                    }
                }
                override fun getPosition(): KakaoLatLng = KakaoLatLng.from(center.latitude, center.longitude)
                override fun getZoomLevel(): Int = zoomLevel
            },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    // 중심 좌표가 바뀌거나 다시 옮겨 달라는 요청이 오면 카메라 이동
    LaunchedEffect(kakaoMap, center, recenterRequest) {
        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(KakaoLatLng.from(center.latitude, center.longitude)))
    }

    // 핀 다시 그리기.
    // MapPins 가 이미 화면 밀도(density)를 곱해 그림을 만들므로 SDK 의 dp 배율(setApplyDpScale, 기본 켜짐)은 끈다.
    // 켜 두면 밀도를 한 번 더 곱해 고해상도 폰일수록 핀이 커진다. 끄면 글자 크기도 px 로 받으므로 밀도를 직접 곱한다.
    LaunchedEffect(kakaoMap, markers, myLocation) {
        val layer = kakaoMap?.labelManager?.layer ?: return@LaunchedEffect
        val density = context.resources.displayMetrics.density
        layer.removeAll()
        markers.forEach { m ->
            val style = LabelStyle.from(MapPins.pin(context, m.selected))
                .setApplyDpScale(false)
                .setTextStyles(((if (m.selected) 16 else 13) * density).roundToInt(), Color.WHITE)
            val options = LabelOptions.from(m.id, KakaoLatLng.from(m.point.latitude, m.point.longitude))
                .setStyles(LabelStyles.from(style))
            m.label?.let { options.setTexts(LabelTextBuilder().setTexts(it)) }
            layer.addLabel(options)
        }
        myLocation?.let {
            layer.addLabel(
                LabelOptions.from(MY_LOCATION_ID, KakaoLatLng.from(it.latitude, it.longitude))
                    .setStyles(LabelStyles.from(LabelStyle.from(MapPins.myLocation(context)).setApplyDpScale(false))),
            )
        }
    }

    // 경로선 다시 그리기(S-03 경로 상세). 구간마다 이동 수단 색으로 한 토막씩 이어 붙여 선 하나로 올린다
    LaunchedEffect(kakaoMap, routeSegments) {
        val layer = kakaoMap?.routeLineManager?.layer ?: return@LaunchedEffect
        layer.removeAll()
        val lines = routeSegments
            .filter { it.points.size >= 2 } // 점이 2개 이상이어야 선이 된다
            .map { segment ->
                RouteLineSegment.from(
                    segment.points.map { KakaoLatLng.from(it.latitude, it.longitude) },
                    routeLineStyle(segment.mode),
                )
            }
        if (lines.isNotEmpty()) layer.addRouteLine(RouteLineOptions.from(lines))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
