package com.ms500.usbcam.uvc

import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.ms500.ausbc.widget.IAspectRatio

/**
 * Preview Surface adapter.
 *
 * Wraps Android [Surface] to adapt to underlying [IAspectRatio] interface,
 * Responsible for maintaining aspect ratio、Surface Validity check，And converts UI Switches tasks back to main thread。
 *
 * Preview surface adapter.
 * Wraps an Android [Surface] to fit the underlying [IAspectRatio] interface,
 * maintaining aspect ratio, validating surface liveness, and posting UI tasks to the main thread.
 *
 * Current rendering surface.
 * Target preview width.
 * Target preview height.
 */
internal class PreviewSurfaceAdapter(
    private var surface: Surface,
    private var width: Int,
    private var height: Int
) : IAspectRatio {
    /** Underlying layer requires UI Switches related tasks back to main thread execution。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Updates target aspect ratio，For underlying to read when rebuilding preview。
     *
     * Updates the target aspect ratio for the underlying preview rebuild.
     *
     * New width.
     * New height.
     */
    override fun setAspectRatio(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * Returns currently recorded Surface Width。
     *
     * Returns the currently recorded surface width.
     *
     * Width value.
     */
    override fun getSurfaceWidth(): Int = width

    /**
     * Returns currently recorded Surface Height。
     *
     * Returns the currently recorded surface height.
     *
     * Height value.
     */
    override fun getSurfaceHeight(): Int = height

    /**
     * Only when Surface Exposes to underlying when still valid，Avoids using invalid objects when opening camera。
     *
     * Exposes the surface only if it is still valid, preventing use of a stale object when opening the camera.
     *
     * Valid [Surface] or null.
     */
    override fun getSurface(): Surface? = if (surface.isValid) surface else null

    /**
     * Converts underlying requested UI Switches tasks back to main thread。
     *
     * Posts a UI task to the main thread as requested by the underlying layer.
     *
     * Task to execute.
     */
    override fun postUITask(task: () -> Unit) {
        mainHandler.post(task)
    }

    /**
     * In Surface Synchronously updates adapter content after rebuild or size change。
     *
     * Synchronously updates the adapter after surface recreation or size change.
     *
     * New [Surface].
     * New width.
     * New height.
     */
    fun update(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.width = width
        this.height = height
    }
}
