package com.ms500.usbcam.uvc

import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.ms500.ausbc.widget.IAspectRatio

/**
 * 预览 Surface 适配器。
 *
 * 包装 Android [Surface] 以适配底层 [IAspectRatio] 接口，
 * 负责维护宽高比、Surface 有效性校验，以及将 UI 任务切回主线程。
 *
 * Preview surface adapter.
 * Wraps an Android [Surface] to fit the underlying [IAspectRatio] interface,
 * maintaining aspect ratio, validating surface liveness, and posting UI tasks to the main thread.
 *
 * @property surface 当前用于渲染的 Surface / Current rendering surface.
 * @property width   预览目标宽度 / Target preview width.
 * @property height  预览目标高度 / Target preview height.
 */
internal class PreviewSurfaceAdapter(
    private var surface: Surface,
    private var width: Int,
    private var height: Int
) : IAspectRatio {
    /** 底层要求能把 UI 相关任务切回主线程执行。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 更新目标宽高比，供底层重建预览时读取。
     *
     * Updates the target aspect ratio for the underlying preview rebuild.
     *
     * @param width  新宽度 / New width.
     * @param height 新高度 / New height.
     */
    override fun setAspectRatio(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * 返回当前记录的 Surface 宽度。
     *
     * Returns the currently recorded surface width.
     *
     * @return 宽度值 / Width value.
     */
    override fun getSurfaceWidth(): Int = width

    /**
     * 返回当前记录的 Surface 高度。
     *
     * Returns the currently recorded surface height.
     *
     * @return 高度值 / Height value.
     */
    override fun getSurfaceHeight(): Int = height

    /**
     * 仅在 Surface 仍有效时向底层暴露，避免打开相机时使用失效对象。
     *
     * Exposes the surface only if it is still valid, preventing use of a stale object when opening the camera.
     *
     * @return 有效的 [Surface] 或 null / Valid [Surface] or null.
     */
    override fun getSurface(): Surface? = if (surface.isValid) surface else null

    /**
     * 将底层请求的 UI 任务切回主线程。
     *
     * Posts a UI task to the main thread as requested by the underlying layer.
     *
     * @param task 待执行的任务 / Task to execute.
     */
    override fun postUITask(task: () -> Unit) {
        mainHandler.post(task)
    }

    /**
     * 在 Surface 重建或尺寸变化后同步更新适配器内容。
     *
     * Synchronously updates the adapter after surface recreation or size change.
     *
     * @param surface 新的 [Surface] / New [Surface].
     * @param width   新宽度 / New width.
     * @param height  新高度 / New height.
     */
    fun update(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.width = width
        this.height = height
    }
}
