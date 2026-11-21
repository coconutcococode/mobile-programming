# Bloom — 대중교통 길안내 · 주변 장소 추천 Android 앱

고급모바일프로그래밍 팀 프로젝트 (팀 Bloom)

## 폴더 구조

```
mobile-programming/
├─ android/     # Android 앱 (Kotlin · Jetpack Compose)
├─ server/      # 백엔드 (NestJS) — 백엔드 팀이 추가
└─ .github/workflows/android-ci.yml   # android/** 변경 시 빌드·단위 테스트
```

## Android 시작하기

1. Android Studio 에서 **`android/` 폴더**를 연다(저장소 루트가 아님).
2. `android/local.properties` 에 아래 값을 넣는다. 값은 노션 **환경변수** 페이지 참고. 이 파일은 커밋하지 않는다.
   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   KAKAO_NATIVE_APP_KEY=...
   # 선택: 기본값은 에뮬레이터에서 로컬 서버를 가리키는 http://10.0.2.2:3000/
   API_BASE_URL=http://10.0.2.2:3000/
   ```
3. Firebase 를 쓸 때는 `google-services.json` 을 `android/app/` 에 넣는다(커밋 금지). 파일이 없어도 빌드는 된다.
4. 빌드·테스트
   ```bash
   cd android && ./gradlew assembleDebug testDebugUnitTest
   ```

- 요구 환경: JDK 17 이상, Android SDK 36 (compileSdk 36 · minSdk 26)
- APK 에 넣는 키는 카카오 지도 네이티브 앱 키 하나뿐이다. REST API 키 등 서버 전용 키는 서버 환경 변수에만 둔다(NFR-09).

## Android 코드 구조

단일 `app` 모듈, 패키지로 나눈다.

```
com.bloom
├─ core/
│  ├─ designsystem/  theme(색·글자·여백 토큰) · component(버튼·칩·상태 뷰) · UiState
│  ├─ navigation/    라우트 정의 · NavHost(화면 연결은 여기서만) · 하단 탭
│  ├─ model/         Place · RouteCandidate · Segment · GuideSession 등 도메인 모델
│  ├─ network/       Retrofit · 인증 헤더 · 오류 변환
│  ├─ database/      Room — 계정별 즐겨찾기 캐시 · 오프라인 변경 대기열
│  ├─ datastore/     기기 설정(다크 모드 · 하차 진동)
│  └─ location/      위치 권한 · 위치 수신
├─ data/
│  ├─ repository/    저장소 인터페이스 (화면은 여기에만 의존)
│  ├─ fake/          가짜 구현 — 서버 API 확정 전까지 사용
│  ├─ session/       진행 중 안내 상태(메모리 전용)
│  └─ di/            구현 연결(DataModule) — Fake → 실제 구현 교체는 여기 한 곳
└─ feature/
   ├─ home/ route/ nearby/ place/ favorites/ settings/   # 지도·탐색·계정
   └─ guide/          # 안내 화면 · 전경 서비스 · 잠금화면 알림
```

- 화면 번호(S-01 …)는 UI 설계서 기준이다. 각 화면 파일 상단 주석에 남은 작업(TODO)을 적어 두었다.
- 화면 패턴의 기준 예시는 `feature/route/RouteCandidates*` (ViewModel → `UiState` → 화면).
- 아키텍처: MVVM + 단방향 데이터 흐름, Hilt(DI), Navigation Compose(타입 안전 라우트), Coroutines/Flow.

## 브랜치

`main`(제출) ← `develop` ← `feature/...` — 규칙은 노션 Git Convention 참고.
