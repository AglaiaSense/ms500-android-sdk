package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.dto.GazePayload
import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 凝视（Gaze）信息 API 内部实现类。
 *
 * 监听 CDC 消息流，过滤 gaze 数据消息，解析为 `InferenceResult` 并向上层暴露为 Flow。
 *
 * Internal implementation of gaze info API.
 * Listens to CDC message events, filters gaze data messages, parses them into InferenceResult,
 * and exposes them as a Flow.
 */
internal class GazeInfoApiImpl(
    /** CDC 连接实现，用于订阅消息事件。 */
    private val connection: CdcConnectApiImpl
) : GazeInfoApi {

    /**
     * 凝视推理结果流。
     * Flow of gaze inference results.
     */
    override val results: Flow<InferenceResult> = callbackFlow {
        // 触发 sender 初始化，确保消息事件通路已建立
        connection.getSender()

        /**
         * 收集 CDC 消息事件协程。
         * Coroutine that collects CDC message events.
         */
        val job = launch {
            connection.messageEvents.collect { event ->
                // 过滤非 gaze 数据或错误消息
                if (!event.isGazeData || event.isError) {
                    return@collect
                }

                // 将原始 JSON 文本解析为 JSONObject
                val json = event.jsonText
                    ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                    ?: return@collect

                // 优先尝试解析为 GazePayload，提取 detections 中的结果
                val payload = runCatching { GazePayload.fromJson(json) }.getOrNull()
                val detections = payload?.detections.orEmpty()
                if (detections.isNotEmpty()) {
                    detections.forEach { detection ->
                        val result = detection.raw?.let { raw -> runCatching { InferenceResult.fromJson(raw) }.getOrNull() }
                        if (result != null) {
                            trySend(result)
                        }
                    }
                    return@collect
                }

                // 兜底：直接按 InferenceResult 解析
                runCatching { InferenceResult.fromJson(json) }
                    .onSuccess { trySend(it) }
            }
        }

        // 流关闭时取消收集协程
        awaitClose { job.cancel() }
    }
}
