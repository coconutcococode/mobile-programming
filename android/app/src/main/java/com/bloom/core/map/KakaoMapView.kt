package com.bloom.core.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.LatLng as KakaoLatLng

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

/** fitPoints 로 카메라를 맞출 때 지도 가장자리 여백(핀이 잘리지 않게) */
private val FIT_PADDING = 48.dp

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
    // 점이 2개 이상이면 center 대신 이 점들이 모두 보이게 카메라를 맞춘다(경로 상세에서 경로 전체 보기). 비우면 기존처럼 center
    fitPoints: List<LatLng> = emptyList(),
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
    // 지도 시작 실패(키 해시 미등록, 인증 오류 등) 여부. 실패 시 빈 지도 대신 안내 표시
    var mapFailed by remember { mutableStateOf(false) }

    // 지도 시작 + 화면이 가려지면 멈추고 다시 보이면 재개, 화면을 떠나면 정리
    DisposableEffect(lifecycleOwner) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit
                override fun onMapError(error: Exception) { // 키 해시·키 오류도 여기로 온다
                    mapFailed = true
                    currentOnError(error)
                }
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

    // 중심 좌표가 바뀌거나 다시 옮겨 달라는 요청이 오면 카메라 이동. fitPoints 가 있으면 그 점들이 다 보이게 맞춘다
    val fitPaddingPx = with(LocalDensity.current) { FIT_PADDING.roundToPx() }
    LaunchedEffect(kakaoMap, center, recenterRequest, fitPoints) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (fitPoints.size >= 2) {
            val points = fitPoints.map { KakaoLatLng.from(it.latitude, it.longitude) }.toTypedArray()
            map.moveCamera(CameraUpdateFactory.fitMapPoints(points, fitPaddingPx))
        } else {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(KakaoLatLng.from(center.latitude, center.longitude)))
        }
    }

    // 핀 다시 그리기.
    // MapPins 가 이미 화면 밀도(density)를 곱해 그림을 만들므로 SDK 의 dp 배율(setApplyDpScale, 기본 켜짐)은 끔.
    // 켜 두면 밀도를 한 번 더 곱해 고해상도 폰일수록 핀이 커짐. 번호는 SDK 글자 대신 핀 그림 안에 직접 그림(원 가운데 정렬)
    LaunchedEffect(kakaoMap, markers, myLocation) {
        val layer = kakaoMap?.labelManager?.layer ?: return@LaunchedEffect
        layer.removeAll()
        markers.forEach { m ->
            val style = LabelStyle.from(MapPins.pin(context, m.selected, m.label)).setApplyDpScale(false)
            layer.addLabel(
                LabelOptions.from(m.id, KakaoLatLng.from(m.point.latitude, m.point.longitude)).setStyles(LabelStyles.from(style)),
            )
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

    // 지도 칸만 안내로 대체. 시트, 목록 등 화면의 나머지는 그대로 사용 가능
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (mapFailed) {
            // 지도 칸 아래쪽은 시트가 덮으므로 글자는 위쪽 30% 지점에 배치
            Box(Modifier.fillMaxSize().background(BloomTheme.colors.skeleton), contentAlignment = BiasAlignment(0f, -0.4f)) {
                Text("지도를 불러오지 못했어요", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
