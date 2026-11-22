package com.bloom.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인터넷 연결 여부를 실시간으로 알려 준다. 화면의 '부분(오프라인)' 상태 배너에 쓴다(UI 설계서 4장).
 * 안드로이드가 연결 변화를 알려 주는 콜백을 Flow 로 바꾼 것(callbackFlow).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        // INTERNET = 인터넷용 네트워크라는 뜻일 뿐이라, 와이파이에 붙었지만 바깥 인터넷이 안 되는 경우도 참이다.
        // VALIDATED = 안드로이드가 실제로 인터넷 접속을 확인했다는 뜻이라 둘 다 있어야 온라인으로 본다.
        fun NetworkCapabilities?.isOnline(): Boolean =
            this != null &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        fun current(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork).isOnline()

        val callback = object : ConnectivityManager.NetworkCallback() {
            // 연결 직후(onAvailable)에는 아직 확인 전일 수 있다. 확인 결과가 바뀔 때마다 이 함수가 불린다
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isOnline())
            }
            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
