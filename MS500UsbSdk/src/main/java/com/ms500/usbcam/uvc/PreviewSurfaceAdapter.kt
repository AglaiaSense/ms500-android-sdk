package com.ms500.usbcam.uvc

import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.ms500.ausbc.widget.IAspectRatio

internal class PreviewSurfaceAdapter(
    /** 当前用于渲染的 Surface。 */
    private var surface: Surface,
    /** 预览目标宽度。 */
    private var width: Int,
    /** 预览目标高度。 */
    private var height: Int
) : IAspectRatio {
    /** 底层要求能把 UI 相关任务切回主线程执行。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 更新目标宽高比，供底层重建预览时读取。 */
    override fun setAspectRatio(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** 返回当前记录的 Surface 宽度。 */
    override fun getSurfaceWidth(): Int = width

    /** 返回当前记录的 Surface 高度。 */
    override fun getSurfaceHeight(): Int = height

    /** 仅在 Surface 仍有效时向底层暴露，避免打开相机时使用失效对象。 */
    override fun getSurface(): Surface? = if (surface.isValid) surface else null

    /** 将底层请求的 UI 任务切回主线程。 */
    override fun postUITask(task: () -> Unit) {
        mainHandler.post(task)
    }

    /** 在 Surface 重建或尺寸变化后同步更新适配器内容。 */
    fun update(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.width = width
        this.height = height
    }
}
