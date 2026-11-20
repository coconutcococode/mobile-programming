package com.bloom.feature.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomSecondaryButton
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.Place

/**
 * S-09 장소 상세(시트). S-01 지도 핀 / S-06 지도 핀·목록 행에서 열린다.
 * TODO(트랙 A): 열 때마다 실시간 재조회(S-09-2 로딩 / S-09-4 오류), 카카오맵 '자세히 보기' 링크, 추천 근거 칩.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    place: Place,
    isLoggedIn: Boolean,
    onDirections: (Place) -> Unit,
    onSave: (Place) -> Unit,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showLoginDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = BloomDimens.ScreenHorizontal).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(place.name, style = MaterialTheme.typography.titleLarge)
            val meta = listOfNotNull(
                place.category?.label,
                place.distanceMeters?.let { "${it}m" },
                place.walkMinutes?.let { "도보 ${it}분" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
            Text("주소  ${place.address}", style = MaterialTheme.typography.bodyMedium)
            place.phone?.let { Text("전화  $it", style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // '길찾기'(212dp)를 '저장'(100dp)보다 크게 — 5.2 피츠의 법칙
                BloomPrimaryButton("길찾기", onClick = { onDirections(place) }, modifier = Modifier.width(212.dp))
                BloomSecondaryButton(
                    "☆ 저장",
                    onClick = { if (isLoggedIn) onSave(place) else showLoginDialog = true },
                    modifier = Modifier.width(100.dp),
                )
            }
        }
    }

    // S-07-D3 로그인 안내
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("로그인이 필요해요") },
            text = { Text("즐겨찾기는 Google 계정에 저장돼요.") },
            confirmButton = {
                TextButton(onClick = { showLoginDialog = false; onLoginClick() }) { Text("로그인하러 가기") }
            },
            dismissButton = { TextButton(onClick = { showLoginDialog = false }) { Text("취소") } },
        )
    }
}
