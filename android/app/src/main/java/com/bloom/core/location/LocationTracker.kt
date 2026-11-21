package com.bloom.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bloom.core.model.LatLng
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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

    /** 안내 중 위치 갱신. 지하철 차량 이동 구간은 간격을 30초로 늘린다(NFR-04). */
    fun locationUpdates(intervalMillis: Long): Flow<LatLng>
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
    override fun locationUpdates(intervalMillis: Long): Flow<LatLng> {
        if (!hasPreciseLocationPermission()) return emptyFlow()
        return callbackFlow {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(LatLng(it.latitude, it.longitude)) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close(it) } // 등록 실패를 수집하는 쪽에 전달(정리는 awaitClose 에서)
            awaitClose { client.removeLocationUpdates(callback) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun bindLocationTracker(impl: FusedLocationTracker): LocationTracker
}
