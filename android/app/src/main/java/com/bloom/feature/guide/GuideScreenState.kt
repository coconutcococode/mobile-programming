package com.bloom.feature.guide

import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.ArrivalState
import com.bloom.core.model.ArrivalStatus
import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import java.time.format.DateTimeFormatter

/*
 * 안내 진행 화면(S-04)에서 무엇을 보여 줄지 정하는 규칙. 화면 그리기와 분리해 단위 테스트로 확인한다.
 */

/** S-04 상단 배너. 여러 개면 급한 것부터 이 순서로 위에서 쌓는다. */
enum class GuideBanner(val message: String, val actionLabel: String?) {
    /** S-04-B3 경로 이탈(FR-06) */
    OFF_ROUTE("경로를 벗어났습니다", "재탐색"),

    /** S-04-B2 위치 부정확(UC-01 A5): 자동 전환이 멈추므로 손으로 넘기게 한다 */
    LOCATION_INACCURATE("위치가 정확하지 않아요 · 단계를 직접 넘겨 주세요", "다음 단계"),

    /** S-04-5 연결 끊김(NFR-05): 메모리의 경로와 GPS로 안내는 계속된다 */
    OFFLINE("연결 끊김 · 위치와 경과 시간으로 안내 중", null),

    /** S-04-B1 알림 권한 거절(UC-01 A1): 앱 화면으로는 계속 안내하지만 잠금화면에는 안 보인다 */
    NOTIFICATIONS_OFF("알림이 꺼져 있어 잠금화면에 안내가 보이지 않아요", "설정 열기"),
}

fun guideBanners(session: GuideSession, notificationsEnabled: Boolean, online: Boolean = true): List<GuideBanner> = buildList {
    if (session.isOffRoute) add(GuideBanner.OFF_ROUTE)
    if (session.isLocationInaccurate) add(GuideBanner.LOCATION_INACCURATE)
    if (!online) add(GuideBanner.OFFLINE)
    if (!notificationsEnabled) add(GuideBanner.NOTIFICATIONS_OFF)
}

/** 하단 큰 버튼 하나가 할 일. 경로 이탈이면 주 버튼을 재탐색으로 바꾼다(UI 설계 3.2). */
enum class GuidePrimaryAction(val label: String) {
    REROUTE("새 경로로 재탐색"),
    BOARD("승차"),
    ALIGHT("하차 완료"),
    NEXT("다음 단계"),
    END("안내 종료"),
}

fun primaryAction(session: GuideSession): GuidePrimaryAction = when {
    session.isLastStep -> GuidePrimaryAction.END
    session.isOffRoute -> GuidePrimaryAction.REROUTE
    session.currentStep.type == GuideStepType.WAIT_BOARDING -> GuidePrimaryAction.BOARD
    session.currentStep.type == GuideStepType.WAIT_ALIGHTING -> GuidePrimaryAction.ALIGHT
    else -> GuidePrimaryAction.NEXT
}

/** 주 버튼 위 한 줄 설명. */
fun primaryHint(session: GuideSession): String? = when (primaryAction(session)) {
    GuidePrimaryAction.REROUTE -> "지금 위치에서 경로를 다시 찾아요"
    GuidePrimaryAction.BOARD -> when (session.currentStep.segment?.mode) {
        TransitMode.BUS -> "버스에 탔다면 눌러 주세요"
        else -> "열차에 탔다면 눌러 주세요"
    }
    GuidePrimaryAction.ALIGHT -> "내렸다면 눌러 주세요"
    else -> null
}

/** 단계 카드 위 작은 글씨("현재 단계 · 승차 대기"). */
fun stepTypeLabel(type: GuideStepType): String = when (type) {
    GuideStepType.WALK -> "도보 이동"
    GuideStepType.WAIT_BOARDING -> "승차 대기"
    GuideStepType.RIDING -> "차량 이동"
    GuideStepType.WAIT_ALIGHTING -> "하차 대기"
    GuideStepType.ALIGHTED -> "하차 완료"
    GuideStepType.ARRIVED -> "도착"
}

/**
 * '현재 역 고치기'(UC-01 A9)를 보여 줄지. 지하철은 지하에서 위치를 못 받아 시간으로 현재 역을 추정하므로,
 * 차량 이동·하차 대기 단계에서만 사용자가 실제 역으로 바로잡을 수 있게 한다.
 */
fun canCorrectStation(session: GuideSession): Boolean {
    val segment = session.currentStep.segment ?: return false
    // 소요 시간을 모르면 시간 추정 자체를 하지 않으므로(다음 단계 버튼으로 진행) 고칠 것도 없다
    return segment.mode == TransitMode.SUBWAY && segment.durationMinutes > 0 &&
        session.currentStep.type in setOf(GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING)
}

/**
 * 현재 역 고치기에서 고를 수 있는 역. 목록 순번 = 승차역에서 지나온 역 수(0 = 승차역, 마지막 = 하차역).
 * 경로 데이터에 중간 역 이름이 없어 중간 역은 순번으로 보여 준다.
 */
fun stationChoices(segment: Segment): List<String> {
    val stops = segment.stopCount.coerceAtLeast(1)
    return (0..stops).map { passed ->
        when (passed) {
            0 -> "${segment.startName} (승차역)"
            stops -> "${segment.endName} (하차역)"
            else -> "승차역에서 ${passed}번째 역"
        }
    }
}

/** 지금 추정하고 있는 역(지나온 역 수). 목록에서 '지금 표시 중'으로 강조한다. 추정할 수 없으면 null. */
fun estimatedPassedStops(session: GuideSession): Int? {
    val segment = session.currentStep.segment ?: return null
    val stops = segment.stopCount.coerceAtLeast(1)
    if (session.isAtAlightingStation) return stops
    val remaining = session.remainingStops ?: return null
    return (stops - remaining).coerceIn(0, stops)
}

/**
 * 실시간 도착 카드(S-04-1~5)에 들어갈 글자. 화면은 이 값을 그대로 그린다.
 * @param baseTime 제목 오른쪽 작은 글씨("14:02 기준", "연결 끊김 · 14:02 기준")
 * @param main 큰 글씨("2분 후"). 없으면 [body] 만
 * @param body 큰 글씨 아래 줄들
 * @param dimmed 연결이 끊겨 마지막 값을 흐리게 보여 줄 때
 * @param showSource 서울시 공공데이터 값을 보여 줄 때 "출처: 서울특별시"(NFR-12)
 */
data class ArrivalCardText(
    val title: String,
    val baseTime: String? = null,
    val main: String? = null,
    val body: List<String> = emptyList(),
    val loading: Boolean = false,
    val dimmed: Boolean = false,
    val showRetry: Boolean = false,
    val showSource: Boolean = false,
)

private val BASE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private fun ArrivalInfo.baseTimeText() = baseTime.format(BASE_TIME_FORMAT)

private fun minutesText(minutes: Int?): String = when {
    minutes == null -> "도착 정보 없음"
    minutes <= 0 -> "곧 도착"
    else -> "${minutes}분 후"
}

private fun nextVehicle(mode: TransitMode?) = if (mode == TransitMode.BUS) "다음 버스" else "다음 열차"

fun arrivalCardText(state: ArrivalState, mode: TransitMode?): ArrivalCardText = when (state) {
    ArrivalState.Loading -> ArrivalCardText(title = "실시간 도착 정보 확인 중…", loading = true)
    is ArrivalState.Live -> ArrivalCardText(
        title = "실시간 도착 정보",
        baseTime = "${state.info.baseTimeText()} 기준",
        main = minutesText(state.info.firstMinutes),
        body = listOfNotNull(state.info.secondMinutes?.let { "${nextVehicle(mode)} ${minutesText(it)}" }),
        showSource = true,
    )
    is ArrivalState.Unavailable -> if (state.status == ArrivalStatus.UNSUPPORTED_AREA) {
        ArrivalCardText(
            title = "실시간 정보 미제공 지역",
            body = listOf("서울 밖 정류장은 실시간 도착 정보를 제공하지 않아요. 안내는 그대로 계속돼요."),
        )
    } else {
        ArrivalCardText(
            title = "실시간 정보 미제공",
            body = listOf("이 정류장의 실시간 도착 정보를 확인할 수 없어요. 안내는 그대로 계속돼요."),
        )
    }
    is ArrivalState.Failed -> ArrivalCardText(
        title = "실시간 정보를 불러올 수 없습니다",
        body = listOf("원인: 도착 정보 조회 ${state.failures}회 연속 실패", "30초마다 다시 시도해요"),
        showRetry = true,
    )
    is ArrivalState.Offline -> ArrivalCardText(
        title = "실시간 도착 정보",
        baseTime = state.last?.let { "연결 끊김 · ${it.baseTimeText()} 기준" } ?: "연결 끊김",
        main = state.last?.let { minutesText(it.firstMinutes) },
        body = listOf("연결되면 5초 안에 다시 조회해요"),
        dimmed = true,
        showSource = state.last != null,
    )
}

/** 잠금화면 알림(S-05)의 도착 정보 한 줄. FR-03 형식: "150번 3분 후 · 다음 11분 후 (14:02 기준)" */
fun arrivalNotificationLine(state: ArrivalState): String = when (state) {
    ArrivalState.Loading -> "도착 정보 확인 중…"
    is ArrivalState.Live -> buildString {
        append("${state.info.routeLabel} ${minutesText(state.info.firstMinutes)}")
        state.info.secondMinutes?.let { append(" · 다음 ${minutesText(it)}") }
        append(" (${state.info.baseTimeText()} 기준)")
    }
    is ArrivalState.Unavailable -> if (state.status == ArrivalStatus.UNSUPPORTED_AREA) "실시간 미제공 지역" else "실시간 정보 미제공"
    is ArrivalState.Failed -> "실시간 도착 정보를 불러올 수 없어요"
    is ArrivalState.Offline -> state.last?.let { "연결 끊김 · ${it.baseTimeText()} 기준" } ?: "연결 끊김"
}
