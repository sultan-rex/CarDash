package com.andrerinas.openheadunit.host

import android.content.Context
import com.andrerinas.openheadunit.utils.Settings

/**
 * Lets a host app choose the phones whose Bluetooth connection starts Android Auto. The choice is
 * written to the two lists the head unit uses: the one its Bluetooth receiver reads when a device
 * connects, and the one its wireless wake-up reaches out to.
 */
object AutoStart {
    /** The chosen phones' addresses; empty when auto-start is off. */
    @JvmStatic
    fun getMacs(context: Context): Set<String> = Settings(context).autoStartBluetoothDeviceMacs

    @JvmStatic
    fun isAnyPhone(context: Context): Boolean = Settings(context).autoStartAnyPhone

    /** On: any phone that connects over Bluetooth starts Android Auto. */
    @JvmStatic
    fun setAnyPhone(context: Context, on: Boolean) {
        Settings(context).autoStartAnyPhone = on
        Settings.syncAutoStartAnyPhoneToDeviceStorage(context, on)
    }

    /** Pass an empty set to turn auto-start off. [firstName] is the first phone's plain name. */
    @JvmStatic
    fun setDevices(context: Context, macs: Set<String>, firstName: String) {
        val settings = Settings(context)
        settings.autoStartBluetoothDeviceMacs = macs
        settings.autoStartBluetoothDeviceName = if (macs.isEmpty()) "" else firstName
        // With phones chosen, only they are woken; with none chosen the head unit's default
        // (wake every paired phone) applies again.
        settings.nativePokeBtMacs = macs
        settings.nativePokeAllPairedDevices = macs.isEmpty()
        // The receiver reads this copy before the device is unlocked (Android 7+).
        Settings.syncAutoStartBtMacsToDeviceStorage(context, macs)
    }
}
