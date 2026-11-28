package com.bloom.feature.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.bloom.core.designsystem.theme.BloomTheme

/**
 * 장소 상세(S-09)에서 ☆ 저장할 때 즐겨찾기 이름(별칭)을 입력받는다.
 * 지도 핀 · 주변 목록에서 연 장소는 사용자가 입력한 검색어가 없다. 별칭 · 검색어 칸에는 장소 이름(카카오 데이터)을
 * 넣지 않고 사용자가 친 글자만 넣기로 해서(NFR-11, 팀 합의 11/22) 저장 전에 이름을 직접 적게 한다.
 */
@Composable
fun SaveFavoriteDialog(
    onSave: (alias: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: 화면을 돌려도 입력 중인 글자가 남게
    var alias by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("즐겨찾기 이름") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("나중에 알아보기 쉬운 이름을 적어 주세요", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(MAX_ALIAS_LENGTH) },
                    singleLine = true,
                    placeholder = { Text("예: 학교, 알바") },
                    supportingText = { Text("${alias.length} / $MAX_ALIAS_LENGTH") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(alias.trim()) }, enabled = alias.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 별칭 최대 글자 수(잠정, 팀 확정 전). 즐겨찾기 별칭 수정(S-07-D1)도 같은 값으로 맞춘다. */
internal const val MAX_ALIAS_LENGTH = 20
