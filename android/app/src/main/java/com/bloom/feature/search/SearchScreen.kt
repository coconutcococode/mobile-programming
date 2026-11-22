package com.bloom.feature.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.designsystem.UiState
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.ErrorState
import com.bloom.core.designsystem.component.SkeletonList
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.designsystem.toUserMessage
import com.bloom.core.location.LocationPermissions
import com.bloom.core.model.Place
import com.bloom.core.model.RecentDestination

/**
 * S-01 검색 상태(하단 탭 숨김). 들어오면 바로 입력창에 커서.
 * 검색어가 비면 최근 목적지, 입력하면 0.3초 뒤 자동완성(로딩 · 결과 없음 · 오류 상태).
 * 결과를 고르면 위치 권한 확인(S-01-D1) → 정확한 위치 허용이면 경로 후보(S-02), 아니면 위치 권한 안내(S-08-P1).
 * 뒤로가기: ① 키보드가 열려 있으면 키보드 닫기(안드로이드 기본) ② 검색 종료.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onDestinationChosen: (placeId: String, displayName: String, typedQuery: String) -> Unit,
    onLocationPermissionDenied: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    // 권한 창이 떠 있는 동안 고른 목적지를 들고 있다가, 정확한 위치를 허용하면 S-02 로 넘긴다
    var pending by remember { mutableStateOf<ChosenDestination?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val target = pending ?: return@rememberLauncherForActivityResult
        pending = null
        // 대략적 위치만 허용해도 경로 출발지로 쓸 수 없으므로(NFR-08) 정확한 위치 여부만 본다
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            onDestinationChosen(target.placeId, target.displayName, target.typedQuery)
        } else {
            onLocationPermissionDenied()
        }
    }

    // 검색 결과와 최근 목적지 모두 여기로: 최근 목적지 저장 → 위치 권한 확인(S-01-D1) → S-02
    val choose: (ChosenDestination) -> Unit = { target ->
        viewModel.rememberChoice(target.placeId, target.typedQuery)
        if (viewModel.hasLocationPermission()) {
            onDestinationChosen(target.placeId, target.displayName, target.typedQuery)
        } else {
            pending = target
            permissionLauncher.launch(LocationPermissions.REQUIRED)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(end = BloomDimens.ScreenHorizontal), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "검색 닫기") }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("장소·주소·역 검색") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "검색어 지우기") }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }

        when (val state = results) {
            // 최근 목적지는 장소 이름을 저장하지 않으므로 표시 이름도 검색어를 쓴다
            null -> RecentList(recents, onClick = { choose(ChosenDestination(it.placeId, it.query, it.query)) })
            UiState.Loading -> SkeletonList(Modifier.padding(BloomDimens.ScreenHorizontal), rows = 5, rowHeight = 56.dp)
            UiState.Empty -> EmptyState(title = "검색 결과가 없습니다", description = "다른 이름이나 주소로 검색해 보세요")
            is UiState.Error -> ErrorState(
                message = state.error.toUserMessage(),
                onAction = viewModel::retry,
                modifier = Modifier.padding(BloomDimens.ScreenHorizontal),
            )
            is UiState.Success -> LazyColumn {
                items(state.data, key = { it.id }) { place ->
                    // 검색어 자리에는 장소 이름이 아니라 사용자가 입력한 글자를 넘긴다(팀 합의 11/22)
                    PlaceRow(place, onClick = { choose(ChosenDestination(place.id, place.name, query.trim())) })
                }
            }
        }
    }
}

/** 고른 목적지. [typedQuery] 는 사용자가 입력한 글자(최근 목적지면 저장해 둔 검색어). */
private data class ChosenDestination(val placeId: String, val displayName: String, val typedQuery: String)

@Composable
private fun RecentList(recents: List<RecentDestination>, onClick: (RecentDestination) -> Unit) {
    if (recents.isEmpty()) {
        EmptyState(title = "최근 목적지가 없어요", description = "검색해서 길을 찾으면 여기에 쌓여요")
        return
    }
    LazyColumn {
        item {
            Text(
                "최근 목적지",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = BloomDimens.ScreenHorizontal, vertical = 8.dp),
            )
        }
        items(recents, key = { it.placeId }) { recent ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onClick(recent) }.padding(horizontal = BloomDimens.ScreenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = BloomTheme.colors.textSecondary)
                // 장소명은 저장할 수 없어서(카카오 정책) 사용자가 입력한 검색어를 보여 준다
                Text(recent.query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick)
            .padding(horizontal = BloomDimens.ScreenHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Text(place.address, style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
        }
    }
    HorizontalDivider()
}
