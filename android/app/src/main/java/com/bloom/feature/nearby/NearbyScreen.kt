package com.bloom.feature.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.component.BloomChip
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.component.ScreenStub
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.NearbySort
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceCategory
import com.bloom.data.fake.SamplePlaces
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.feature.place.PlaceDetailSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun save(place: Place) {
        viewModelScope.launch { favoriteRepository.add(place, alias = place.name) }
    }
}

/**
 * S-06 주변(지도 + 추천 시트).
 * TODO(트랙 A): 지도 번호 핀(1~10) = 목록 번호, 시트 3단(접힘·중간·펼침), 반경 500m 최대 10개,
 *  추천순(로그인·동의 S-06-D1) / 거리순, 상태 S-06-1~5(공공 상가정보 대체 시 출처 표시).
 */
@Composable
fun NearbyScreen(
    onDirections: (Place) -> Unit,
    onLoginClick: () -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(PlaceCategory.CAFE) }
    var sort by rememberSaveable { mutableStateOf(NearbySort.DISTANCE) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenStub(
        screenId = "S-06",
        title = "주변 추천",
        description = "지도 위에 카테고리 4종 · 추천순/거리순 시트를 겹친다. 아래 목록은 가짜 데이터.",
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceCategory.entries.forEach { c -> BloomChip(c.label, selected = c == category, onClick = { category = c }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbySort.entries.forEach { s ->
                // 비로그인은 거리순만(S-06-B1)
                BloomChip(s.label, selected = s == sort, onClick = { if (s == NearbySort.DISTANCE || isLoggedIn) sort = s })
            }
        }
        if (!isLoggedIn) {
            NoticeBanner("로그인하면 맞춤 추천순을 볼 수 있어요", actionLabel = "로그인", onAction = onLoginClick)
        }
        SamplePlaces.all.filter { it.category == category }.forEachIndexed { index, place ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { selected = place.id }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${place.distanceMeters}m · 도보 ${place.walkMinutes}분", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
            }
        }
    }

    SamplePlaces.all.firstOrNull { it.id == selected }?.let { place ->
        PlaceDetailSheet(
            place = place,
            isLoggedIn = isLoggedIn,
            onDirections = { selected = null; onDirections(it) },
            onSave = { viewModel.save(it); selected = null },
            onLoginClick = { selected = null; onLoginClick() },
            onDismiss = { selected = null },
        )
    }
}
