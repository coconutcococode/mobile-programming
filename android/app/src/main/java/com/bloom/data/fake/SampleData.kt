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

    val all = listOf(HongdaeStreet, School, BrewingLab, ConvenienceStore, Restaurant, Culture)
}
