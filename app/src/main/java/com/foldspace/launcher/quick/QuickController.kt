package com.foldspace.launcher.quick

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/**
 * Performs what [QuickActions] says is performable.
 *
 * Everything here either works or reports that it did not; nothing pretends.
 * The controls the platform closes off are not implemented at all — they are
 * absent from [QuickActions] or marked as opening a system surface.
 */
class QuickController(private val context: Context) {

    private val cameras: CameraManager? =
        runCatching { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }.getOrNull()

    private val audio: AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }.getOrNull()

    fun hasTorch(): Boolean = torchCameraId() != null

    fun canWriteSettings(): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun kindOf(action: QuickAction): QuickKind = QuickActions.kindOf(
        action = action,
        sdkInt = Build.VERSION.SDK_INT,
        hasTorch = hasTorch(),
        canWriteSettings = canWriteSettings(),
    )

    fun visibleActions(): List<QuickAction> = QuickActions.visible(
        sdkInt = Build.VERSION.SDK_INT,
        hasTorch = hasTorch(),
        canWriteSettings = canWriteSettings(),
    )

    /** Returns whether the torch is on afterwards, or null if it could not be set. */
    fun setTorch(on: Boolean): Boolean? {
        val id = torchCameraId() ?: return null
        return runCatching {
            cameras?.setTorchMode(id, on)
            on
        }.getOrNull()
    }

    fun volume(stream: Int): Pair<Int, Int>? {
        val manager = audio ?: return null
        return runCatching {
            manager.getStreamVolume(stream) to manager.getStreamMaxVolume(stream)
        }.getOrNull()
    }

    fun setVolume(stream: Int, value: Int) {
        val manager = audio ?: return
        // Without FLAG_SHOW_UI there is no feedback that anything happened,
        // and the system's own volume panel is better than anything drawn here.
        runCatching { manager.setStreamVolume(stream, value, AudioManager.FLAG_SHOW_UI) }
    }

    fun brightness(): Int? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull()

    /** Only meaningful once WRITE_SETTINGS is granted; a no-op otherwise. */
    fun setBrightness(value: Int) {
        if (!canWriteSettings()) return
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
            )
        }
    }

    fun autoRotate(): Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
        ) == 1
    }.getOrDefault(false)

    fun setAutoRotate(on: Boolean) {
        if (!canWriteSettings()) return
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (on) 1 else 0,
            )
        }
    }

    /** Opens the system surface for whatever cannot be switched here. */
    fun open(action: QuickAction) {
        val intent = when (action) {
            QuickAction.Internet ->
                if (Build.VERSION.SDK_INT >= QuickActions.INTERNET_PANEL_SDK) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                }
            QuickAction.Wifi -> Intent(Settings.Panel.ACTION_WIFI)
            QuickAction.Bluetooth -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            QuickAction.Brightness, QuickAction.Rotation -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            )
            QuickAction.Torch, QuickAction.MediaVolume, QuickAction.RingVolume ->
                Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * The first camera that has a flash.
     *
     * Not assumed to be camera 0: on a foldable the back cameras are not
     * always first, and a front camera with no flash would make the torch
     * silently do nothing.
     */
    private fun torchCameraId(): String? = runCatching {
        val manager = cameras ?: return null
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private companion object {
        const val MIN_BRIGHTNESS = 1
        const val MAX_BRIGHTNESS = 255
    }
}
