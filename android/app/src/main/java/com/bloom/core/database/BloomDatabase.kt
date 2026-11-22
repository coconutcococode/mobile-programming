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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** 최근 목적지(최대 20개). 장소 ID + 사용자가 입력한 검색어만 저장(카카오 데이터 저장 금지, NFR-11). */
@Entity(tableName = "recent_destinations")
data class RecentDestinationEntity(
    @PrimaryKey val placeId: String,
    val query: String,
    val savedAtMillis: Long,
)

@Dao
interface RecentDestinationDao {
    @Query("SELECT * FROM recent_destinations ORDER BY savedAtMillis DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<RecentDestinationEntity>>

    /** 같은 placeId 면 검색어·시각만 새로 덮어써서 맨 위로 올린다. */
    @Upsert
    suspend fun upsert(entity: RecentDestinationEntity)

    /** 최신 [keep]개만 남기고 지운다. */
    @Query("DELETE FROM recent_destinations WHERE placeId NOT IN (SELECT placeId FROM recent_destinations ORDER BY savedAtMillis DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Database(
    entities = [FavoriteEntity::class, PendingFavoriteChangeEntity::class, RecentDestinationEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BloomDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pendingFavoriteChangeDao(): PendingFavoriteChangeDao
    abstract fun recentDestinationDao(): RecentDestinationDao
}

/**
 * 버전 1 → 2: 최근 목적지 표 추가. 이미 앱을 설치한 폰의 즐겨찾기 표를 지우지 않으려고
 * '처음부터 다시 만들기' 대신 새 표만 만드는 이전 규칙(Migration)을 둔다.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recent_destinations` (`placeId` TEXT NOT NULL, `query` TEXT NOT NULL, " +
                "`savedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`placeId`))",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BloomDatabase =
        Room.databaseBuilder(context, BloomDatabase::class.java, "bloom.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideFavoriteDao(db: BloomDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePendingFavoriteChangeDao(db: BloomDatabase): PendingFavoriteChangeDao = db.pendingFavoriteChangeDao()

    @Provides
    fun provideRecentDestinationDao(db: BloomDatabase): RecentDestinationDao = db.recentDestinationDao()
}
