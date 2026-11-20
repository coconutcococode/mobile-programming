package com.bloom.core.network

/**
 * Bloom 서버(NestJS) API.
 *
 * 엔드포인트는 백엔드와 API 명세를 확정한 뒤 추가한다. 그 전까지 화면은 data/fake 의 Fake 저장소로 개발한다.
 * 요청·응답 DTO 는 이 패키지(core/network/dto)에 두고, data 계층에서 core/model 로 변환한다.
 *
 * 예:
 * ```
 * @GET("v1/places/search")
 * suspend fun searchPlaces(@Query("query") query: String): PlaceSearchResponse
 * ```
 */
interface BloomApi
