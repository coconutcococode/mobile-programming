package com.bloom.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 바텀 시트 높이 3단계(UI 설계서 2장): 접힘(지도 위주) · 중간 · 펼침(목록 위주) */
enum class SheetStep(val label: String) { Collapsed("접힘"), Half("중간"), Expanded("펼침") }

/**
 * 3단 시트의 현재 높이. Compose 의 AnchoredDraggableState(정해 둔 위치들 사이를 끌어서 멈추게 하는 상태)를 감싼다.
 * 탭을 옮겼다 돌아와도 높이가 유지되게 rememberThreeStepSheetState 로 만든다(UI 설계서 2.3).
 */
@Stable
class ThreeStepSheetState(initial: SheetStep) {
    internal val draggable = AnchoredDraggableState(initial)

    val currentStep: SheetStep get() = draggable.currentValue

    suspend fun animateTo(step: SheetStep) = draggable.animateTo(step)

    companion object {
        /** 화면 회전·탭 이동 때 높이를 저장했다 복원하는 규칙(단계 이름만 저장) */
        val Saver = Saver<ThreeStepSheetState, String>(
            save = { it.currentStep.name },
            restore = { ThreeStepSheetState(SheetStep.valueOf(it)) },
        )
    }
}

@Composable
fun rememberThreeStepSheetState(initial: SheetStep = SheetStep.Half): ThreeStepSheetState =
    rememberSaveable(saver = ThreeStepSheetState.Saver) { ThreeStepSheetState(initial) }

/**
 * 지도(content) 위에 3단 시트를 겹친다(S-01 홈, S-06 주변).
 * - 손잡이(맨 위 회색 막대 줄)를 끌면 높이가 바뀌고, 누르면 접힘 → 중간 → 펼침 → 접힘 순으로 바뀐다(TalkBack 사용자도 조작 가능).
 * - 시트 안 목록은 손잡이와 따로 스크롤된다. 목록 스크롤과 시트 끌기가 서로 헷갈리지 않게 끌기는 손잡이에서만 받는다.
 * - 펼침에서 뒤로가기를 누르면 중간으로 내린다(UI 설계서 2.3 뒤로가기 규칙).
 *
 * @param collapsedHeight 접힘일 때 보이는 시트 높이(손잡이 + 제목 한 줄 정도)
 * @param expandedTopMargin 펼침일 때 시트 위쪽에 남겨 둘 공간(검색창이 가리지 않게)
 * @param aboveSheet 시트 바로 위에 붙어 함께 움직이는 부품([내 위치 보기] 버튼 등). 펼침에서는 숨긴다.
 */
@Composable
fun ThreeStepSheetScaffold(
    state: ThreeStepSheetState,
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    collapsedHeight: Dp = 120.dp,
    expandedTopMargin: Dp = 88.dp,
    aboveSheet: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.currentStep == SheetStep.Expanded) {
        scope.launch { state.animateTo(SheetStep.Half) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullPx = constraints.maxHeight.toFloat()
        val expandedPx = with(density) { expandedTopMargin.toPx() }
        val collapsedPx = fullPx - with(density) { collapsedHeight.toPx() }
        // 시트 '윗변'이 멈출 세 위치(화면 위에서부터 px). 중간 = 화면 높이의 절반
        val anchors = DraggableAnchors {
            SheetStep.Expanded at expandedPx
            SheetStep.Half at fullPx / 2f
            SheetStep.Collapsed at collapsedPx
        }
        SideEffect { state.draggable.updateAnchors(anchors) }
        // 처음 그릴 때는 아직 위치가 계산되지 않았을 수 있어(NaN) 현재 단계의 위치를 쓴다
        fun sheetTop(): Float = state.draggable.offset.takeUnless { it.isNaN() }
            ?: anchors.positionOf(state.draggable.currentValue)

        content()

        if (state.currentStep != SheetStep.Expanded) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(0, -(fullPx - sheetTop()).roundToInt()) },
                content = aboveSheet,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight - expandedTopMargin)
                .offset { IntOffset(0, sheetTop().roundToInt()) },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 6.dp,
        ) {
            Column {
                SheetHandle(
                    state = state,
                    onClick = {
                        val next = when (state.currentStep) {
                            SheetStep.Collapsed -> SheetStep.Half
                            SheetStep.Half -> SheetStep.Expanded
                            SheetStep.Expanded -> SheetStep.Collapsed
                        }
                        scope.launch { state.animateTo(next) }
                    },
                )
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = sheetContent)
            }
        }
    }
}

/** 손잡이 줄: 높이 48dp(터치 영역 최소 기준), 가운데 회색 막대. 끌기 · 누르기 모두 여기서 받는다. */
@Composable
private fun SheetHandle(state: ThreeStepSheetState, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .anchoredDraggable(state.draggable, Orientation.Vertical)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "시트 높이 바꾸기"
                stateDescription = state.currentStep.label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
        )
    }
}
