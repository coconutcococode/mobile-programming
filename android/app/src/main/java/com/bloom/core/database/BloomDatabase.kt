package com.bloom.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * 계정별 즐겨찾기 캐시. 카카오 응답 중 저장 가능한 값은 placeId 하나(NFR-11),
 * 나머지는 사용자가 입력한 값(별칭·검색어)이다. 주소는 표시용 — 저장 허용 여부는 팀 확정 필요.
 */
@Entity(tableName = "favorites", indices = [Index("accountUid")])
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val accountUid: String,
    val placeId: String,
    val alias: String,
    val searchKeyword: String,
    val address: String,
    val updatedAtMillis: Long,
)

/** 오프라인에서 생긴 변경(추가·별칭 수정·삭제)을 서버로 보내기 전까지 쌓아 두는 계정별 대기열. */
@Entity(tableName = "pending_favorite_changes", indices = [Index("accountUid")])
data class PendingFavoriteChangeEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val accountUid: String,
    val favoriteId: String,
    val type: String, // ADD | RENAME | DELETE — 동기화 정책 확정 후 enum 으로
    val alias: String?,
    val createdAtMillis: Long,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE accountUid = :accountUid ORDER BY updatedAtMillis DESC")
    fun observe(accountUid: String): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM favorites WHERE accountUid = :accountUid")
    suspend fun clearAccount(accountUid: String)
}

@Dao
interface PendingFavoriteChangeDao {
    @Query("SELECT * FROM pending_favorite_changes WHERE accountUid = :accountUid ORDER BY seq")
    suspend fun getAll(accountUid: String): List<PendingFavoriteChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_favorite_changes WHERE accountUid = :accountUid")
    fun observeCount(accountUid: String): Flow<Int>

    @Upsert
    suspend fun upsert(entity: PendingFavoriteChangeEntity)

    @Query("DELETE FROM pending_favorite_changes WHERE seq = :seq")
    suspend fun delete(seq: Long)
}

@Database(
    entities = [FavoriteEntity::class, PendingFavoriteChangeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BloomDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pendingFavoriteChangeDao(): PendingFavoriteChangeDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BloomDatabase =
        Room.databaseBuilder(context, BloomDatabase::class.java, "bloom.db").build()

    @Provides
    fun provideFavoriteDao(db: BloomDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePendingFavoriteChangeDao(db: BloomDatabase): PendingFavoriteChangeDao = db.pendingFavoriteChangeDao()
}
