package com.bloom.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// 화면 번호는 UI 설계서 1.2 전체 화면 목록 기준.

// ── 하단 탭 루트(깊이 0) ──
@Serializable data object HomeRoute          // S-01 홈(지도)
@Serializable data object NearbyRoute        // S-06 주변(지도 + 추천 시트)
@Serializable data object FavoritesRoute     // S-07 즐겨찾기
@Serializable data object SettingsRoute      // S-08 설정

// ── 스택 화면 ──
/** S-01 검색 상태(하단 탭 숨김). 결과를 고르면 S-02 로 */
@Serializable data object SearchRoute

/**
 * S-02 경로 후보 목록. 목적지는 장소 ID 로 넘기고 화면에서 다시 조회한다.
 * @param searchQuery 사용자가 직접 입력한 검색어(검색 · 최근 목적지 · 즐겨찾기에서 올 때). 지도 핀 · 주변에서 오면 null.
 *  즐겨찾기 '검색어' 칸에는 장소 이름이 아니라 이 값만 저장한다(NFR-11, 팀 합의 11/22) → 역할B 저장 때 사용.
 */
@Serializable data class RouteCandidatesRoute(
    val destinationPlaceId: String,
    val destinationName: String,
    val searchQuery: String? = null,
)

/** S-03 경로 상세. 후보는 메모리(RouteRepository)에 있으므로 ID 만 넘긴다. */
@Serializable data class RouteDetailRoute(val candidateId: String)

@Serializable data object GuideRoute              // S-04 안내 진행(앱 내) — 하단 탭 숨김
@Serializable data object LocationPermissionRoute // S-08-P1 위치 권한 안내
@Serializable data object DataSourceRoute         // S-08-I1 데이터 출처

enum class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    NEARBY(NearbyRoute, "주변", Icons.Filled.Place, Icons.Outlined.Place),
    FAVORITES(FavoritesRoute, "즐겨찾기", Icons.Filled.Star, Icons.Outlined.StarOutline),
    SETTINGS(SettingsRoute, "설정", Icons.Filled.Settings, Icons.Outlined.Settings),
}
