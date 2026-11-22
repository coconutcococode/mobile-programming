package com.bloom.data.di

import com.bloom.core.network.AuthTokenProvider
import com.bloom.data.fake.FakeAuthRepository
import com.bloom.data.fake.FakeFavoriteRepository
import com.bloom.data.fake.FakePlaceRepository
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.data.fake.FakeTransitRepository
import com.bloom.data.local.RoomRecentDestinationRepository
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RecentDestinationRepository
import com.bloom.data.repository.RouteRepository
import com.bloom.data.repository.TransitRepository
import com.bloom.data.session.InMemoryGuideSessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 저장소 구현 연결. 서버 API 가 준비되면 Fake* 를 실제 구현(data/remote)으로 하나씩 바꾼다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindPlaceRepository(impl: FakePlaceRepository): PlaceRepository

    @Binds
    abstract fun bindRouteRepository(impl: FakeRouteRepository): RouteRepository

    @Binds
    abstract fun bindTransitRepository(impl: FakeTransitRepository): TransitRepository

    @Binds
    abstract fun bindFavoriteRepository(impl: FakeFavoriteRepository): FavoriteRepository

    @Binds
    abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    abstract fun bindRecentDestinationRepository(impl: RoomRecentDestinationRepository): RecentDestinationRepository

    @Binds
    abstract fun bindGuideSessionRepository(impl: InMemoryGuideSessionRepository): GuideSessionRepository

    companion object {
        @Provides
        fun provideAuthTokenProvider(authRepository: AuthRepository): AuthTokenProvider =
            AuthTokenProvider { authRepository.idToken() }
    }
}
