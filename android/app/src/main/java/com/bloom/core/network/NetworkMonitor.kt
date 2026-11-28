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
            // 기본 네트워크 콜백이라 onLost = 지금 쓰던 네트워크가 끊김. 이 시점에는 activeNetwork 가 아직 끊긴 네트워크를
            // 가리켜 current() 가 true 를 돌려줄 수 있으므로 바로 false. 다른 네트워크로 넘어가면 onCapabilitiesChanged 가 다시 알려 준다
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(current())
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
