package com.andrerinas.openheadunit.host

import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.AapProjectionActivity

/**
 * What a host app needs to switch to and from the Android Auto screen: whether a phone is
 * connected, whether the projection is in front, and the intent that brings it forward.
 */
object HostBridge {
    @JvmStatic
    fun isConnected(context: Context): Boolean =
        try { App.provide(context).commManager.isConnected } catch (e: Exception) { false }

    @JvmStatic
    fun isProjectionForeground(): Boolean = AapProjectionActivity.isForeground

    /** Brings the projection screen to the front (it lives in its own task). */
    @JvmStatic
    fun projectionIntent(context: Context): Intent =
        Intent(context, AapProjectionActivity::class.java)
            .putExtra(AapProjectionActivity.EXTRA_FOCUS, true)
}
