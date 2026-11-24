package com.bloom.data.fake

import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceCategory

/** 개발용 가짜 데이터. 실제 상호·전화번호가 아님. */
object SamplePlaces {
    val HongdaeStreet = Place(
        id = "sample-hongdae-street",
        name = "홍대 걷고싶은거리",
        address = "서울 마포구 서교동",
        location = LatLng(37.5551, 126.9228),
    )
    val School = Place(
        id = "sample-school",
        name = "홍익대학교",
        address = "서울 마포구 와우산로 94",
        location = LatLng(37.5509, 126.9255),
    )
    val BrewingLab = Place(
        id = "sample-cafe-1",
        name = "브루잉 랩 연남",
        address = "서울 마포구 동교로 000",
        location = LatLng(37.5600, 126.9240),
        category = PlaceCategory.CAFE,
        phone = "02-000-0000",
        placeUrl = "https://place.map.kakao.com/",
        distanceMeters = 150,
        walkMinutes = 2,
        recommendReason = "자주 찾는 카테고리",
    )
    val ConvenienceStore = Place(
        id = "sample-cvs-1",
        name = "연남 편의점",
        address = "서울 마포구 성미산로 000",
        location = LatLng(37.5610, 126.9230),
        category = PlaceCategory.CONVENIENCE_STORE,
        distanceMeters = 80,
        walkMinutes = 1,
    )
    val Restaurant = Place(
        id = "sample-food-1",
        name = "연남 식당",
        address = "서울 마포구 연남로 000",
        location = LatLng(37.5620, 126.9250),
        category = PlaceCategory.RESTAURANT,
        distanceMeters = 230,
        walkMinutes = 3,
    )
    val Culture = Place(
        id = "sample-culture-1",
        name = "연남 소극장",
        address = "서울 마포구 동교로 000",
        location = LatLng(37.5590, 126.9260),
        category = PlaceCategory.CULTURE,
        distanceMeters = 410,
        walkMinutes = 6,
    )

    /** 주변(S-06) 번호 핀 확인용 추가 장소. 이름은 지어낸 값 */
    private val moreNearby = listOf(
        nearby("sample-cafe-2", "카페 모퉁이", LatLng(37.5593, 126.9228), PlaceCategory.CAFE, 220),
        nearby("sample-cafe-3", "느린 오후 커피", LatLng(37.5612, 126.9262), PlaceCategory.CAFE, 310),
        nearby("sample-cafe-4", "연남 로스터스", LatLng(37.5584, 126.9247), PlaceCategory.CAFE, 380),
        nearby("sample-cvs-2", "동교 편의점", LatLng(37.5580, 126.9236), PlaceCategory.CONVENIENCE_STORE, 260),
        nearby("sample-food-2", "골목 국수집", LatLng(37.5606, 126.9218), PlaceCategory.RESTAURANT, 170),
        nearby("sample-food-3", "연남 분식", LatLng(37.5589, 126.9255), PlaceCategory.RESTAURANT, 340),
    )

    val all = listOf(HongdaeStreet, School, BrewingLab, ConvenienceStore, Restaurant, Culture) + moreNearby

    /** 도보 1분 = 약 67m 기준 */
    private fun nearby(id: String, name: String, location: LatLng, category: PlaceCategory, meters: Int) = Place(
        id = id,
        name = name,
        address = "서울 마포구 연남동 000",
        location = location,
        category = category,
        distanceMeters = meters,
        walkMinutes = (meters + 66) / 67,
    )
}
