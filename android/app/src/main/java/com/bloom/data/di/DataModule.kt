package com.bloom.data.di

import android.content.Context
import com.bloom.core.network.AuthTokenProvider
import com.bloom.data.fake.FakeAuthRepository
import com.bloom.data.fake.FakeFavoriteRepository
import com.bloom.data.fake.FakePlaceRepository
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.data.fake.FakeTransitRepository
import com.bloom.data.local.RoomRecentDestinationRepository
import com.bloom.data.remote.FirebaseAuthRepository
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RecentDestinationRepository
import com.bloom.data.repository.RouteRepository
import com.bloom.data.repository.TransitRepository
import com.bloom.data.session.InMemoryGuideSessionRepository
import com.google.firebase.FirebaseApp
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

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
    abstract fun bindRecentDestinationRepository(impl: RoomRecentDestinationRepository): RecentDestinationRepository

    @Binds
    abstract fun bindGuideSessionRepository(impl: InMemoryGuideSessionRepository): GuideSessionRepository

    companion object {
        /**
         * google-services.json 이 있으면 Firebase 가 앱 시작 때 자동 초기화되어 실제 Google 로그인,
         * 없으면(CI, 설정 파일을 아직 받지 않은 컴퓨터) 가짜 로그인. Provider: 고른 쪽 하나만 생성
         */
        @Provides
        @Singleton
        fun provideAuthRepository(
            @ApplicationContext context: Context,
            firebase: Provider<FirebaseAuthRepository>,
            fake: Provider<FakeAuthRepository>,
        ): AuthRepository = if (FirebaseApp.getApps(context).isNotEmpty()) firebase.get() else fake.get()

        @Provides
        fun provideAuthTokenProvider(authRepository: AuthRepository): AuthTokenProvider =
            AuthTokenProvider { authRepository.idToken() }
    }
}
