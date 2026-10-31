package com.rayluxeo.aida.ai.gemini

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device has a network that actually reaches the internet right now.
 *
 * Behind an interface so Mira's availability rules can be tested off-device, and separate from
 * the client so that deciding whether to call Gemini never costs a network round trip.
 */
interface ConnectivityProbe {
    fun isOnline(): Boolean
}

/**
 * Asks ConnectivityManager about the active network. NET_CAPABILITY_VALIDATED is the part that
 * matters: a captive hotel portal reports INTERNET but would leave Mira waiting for a timeout.
 */
@Singleton
class AndroidConnectivityProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityProbe {

    override fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
