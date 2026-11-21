package com.bloom.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 위치 권한은 '정확한 위치'까지 필수(NFR-08). 첫 실행 시에는 요청하지 않고
 * [내 위치 보기] 또는 첫 경로 검색 때 요청한다.
 */
object LocationPermissions {
    val REQUIRED = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasPreciseLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

interface LocationTracker {
    fun hasPreciseLocationPermission(): Boolean

    /** 권한이 없거나 위치를 못 받으면 null. */
    suspend fun currentLocation(): LatLng?

    /**
     * 안내 중 위치 갱신. 지하철 차량 이동 구간은 간격을 30초로 늘린다(NFR-04).
     * 받은 위치를 거르지 않고 정확도·측정 시각과 함께 그대로 넘긴다(걸러 내기는 길안내 엔진에서).
     */
    fun locationUpdates(intervalMillis: Long): Flow<LocationFix>
}

@Singleton
class FusedLocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationTracker {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPreciseLocationPermission(): Boolean = LocationPermissions.hasPreciseLocation(context)

    @SuppressLint("MissingPermission") // 바로 위에서 권한 확인
    override suspend fun currentLocation(): LatLng? {
        if (!hasPreciseLocationPermission()) return null
        return runCatching {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        }.getOrNull()?.let { LatLng(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> {
        if (!hasPreciseLocationPermission()) return emptyFlow()
        return callbackFlow {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    // 여러 건이 묶여 올 수 있으니 측정 순서대로 모두 넘긴다.
                    result.locations.forEach { trySend(it.toLocationFix()) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(it) } // 등록 실패를 수집하는 쪽에 전달(정리는 awaitClose 에서)
            awaitClose { client.removeLocationUpdates(callback) }
        }
            // 기본 대기열(64건)이 차면 새 위치가 조용히 버려진다 → 제한 없이 모두 보관(위치는 몇 초에 1건이라 부담 적음).
            .buffer(Channel.UNLIMITED)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun bindLocationTracker(impl: FusedLocationTracker): LocationTracker
}

private fun Location.toLocationFix() = LocationFix(
    position = LatLng(latitude, longitude),
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    elapsedRealtimeMillis = elapsedRealtimeNanos / 1_000_000,
)
