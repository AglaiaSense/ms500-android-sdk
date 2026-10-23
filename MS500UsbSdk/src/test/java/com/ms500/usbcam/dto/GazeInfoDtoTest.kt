/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.dto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GAZE detection parsing, against responses captured from a real device.
 *
 * Fixtures are verbatim messages from firmware `usb_gaze_2.0.6_20261016_143157` with the camera
 * pointed at a two-person scene - stored in `debug_data/samples/gaze/`. Using the real bytes rather
 * than hand-written JSON is what makes these tests evidence: they would have caught, for example,
 * that `gaze_ratio` arrives as the integer `1` rather than `1.0`.
 */
class GazeInfoDtoTest {

    private companion object {
        /** First detection of a real two-target frame message. */
        const val FRAME_JSON = """
        {"data_status":"frame","timestamp_us":2586419424,"detection_count":2,"detections":[
          {"upload_status":"normal","track_id":10,
           "body":{"bounding_box":{"left":252,"top":101,"right":302,"bottom":263},"bounding_box_score":0.82},
           "face":{"bounding_box":{"left":236,"top":120,"right":251,"bottom":137},"bounding_box_score":0.81,
                   "attribute":{"gender":{"type":"Male","score":0.63},"age":45},
                   "pose":{"yaw":11.24,"pitch":15.32},"is_back2camera":false},
           "behavior":{"is_gaze":true,"detail_gaze_info":{"stay_time":387601,
                       "detail_gaze_time":[{"start":2198818,"end":2586419}],
                       "bbox_cords":{"first":{"left":251,"top":102,"right":302,"bottom":264},
                                     "last":{"left":252,"top":101,"right":302,"bottom":263},
                                     "conf":{"left":252,"top":101,"right":302,"bottom":263},
                                     "conf_score":0.84}}}},
          {"upload_status":"normal","track_id":11,
           "body":{"bounding_box":{"left":211,"top":109,"right":277,"bottom":297},"bounding_box_score":0.71}}
        ]}
        """

        /** A real track-timeout summary. Note there is no top-level track_id. */
        const val SUMMARY_JSON = """
        {"data_status":"summary","summary_count":1,"detections":[
          {"track_id":10,"frame_count":7236,"stay_time_ms":518375,
           "first_seen_ms":2198818,"last_seen_ms":2717193,"gaze_ratio":1,
           "body":{"bounding_box":{"left":252,"top":101,"right":302,"bottom":263},"bounding_box_score":0.8},
           "face":{"bounding_box":{"left":236,"top":118,"right":252,"bottom":135},"bounding_box_score":0.8,
                   "attribute":{"gender":{"type":"Male","score":0.55},"age":45},
                   "pose":{"yaw":8,"pitch":7},"is_back2camera":false,"back_ratio":0},
           "behavior":{"is_gaze":true,"gaze_count":7236,
                       "detail_gaze_info":{"stay_time":518375,
                       "detail_gaze_time":[{"start":2198818,"end":2717193}],
                       "bbox_cords":{"first":{"left":251,"top":102,"right":302,"bottom":264},
                                     "last":{"left":252,"top":101,"right":302,"bottom":263},
                                     "conf":{"left":252,"top":101,"right":302,"bottom":263},
                                     "conf_score":0.84}}},
           "upload_status":"track_id_timeout"}
        ]}
        """
    }

    @Test
    fun `frame message parses both targets with model-space boxes`() {
        val payload = GazePayload.fromJson(JSONObject(FRAME_JSON))

        assertEquals(GazePayload.DATA_STATUS_FRAME, payload.dataStatus)
        assertEquals(2586419424L, payload.timestampUs)
        assertEquals(2, payload.detectionCount)
        assertEquals(2, payload.detections.size)
        assertNull("frame messages carry no summary_count", payload.summaryCount)

        val first = payload.detections[0]
        assertEquals(10, first.trackId)
        assertEquals(GazePayload.UPLOAD_STATUS_NORMAL, first.uploadStatus)

        // Boxes stay in the 512x384 model tensor space; nothing here rescales them.
        val body = requireNotNull(first.body?.boundingBox)
        assertEquals(252, body.left)
        assertEquals(101, body.top)
        assertEquals(302, body.right)
        assertEquals(263, body.bottom)
        assertEquals(0.82, first.body?.boundingBoxScore ?: 0.0, 1e-9)

        assertEquals("Male", first.face?.attribute?.gender)
        assertEquals(45, first.face?.attribute?.age)
        assertEquals(0.63, first.face?.attribute?.genderScore ?: 0.0, 1e-9)
        assertEquals(11.24, first.face?.pose?.yaw ?: 0.0, 1e-9)
        assertEquals(15.32, first.face?.pose?.pitch ?: 0.0, 1e-9)
        assertFalse(first.face?.isBackToCamera ?: true)

        assertTrue(first.behavior?.isGaze ?: false)
        assertEquals(387601L, first.behavior?.detailGazeInfo?.stayTime)
        assertEquals(0.84, first.behavior?.detailGazeInfo?.bboxCords?.confScore ?: 0.0, 1e-9)

        // A target the device reported with body only must survive, not be dropped.
        val second = payload.detections[1]
        assertEquals(11, second.trackId)
        assertNotNull(second.body)
        assertNull(second.face)
    }

    @Test
    fun `summary message has no top-level track id and keeps seen timestamps`() {
        val payload = GazePayload.fromJson(JSONObject(SUMMARY_JSON))

        assertEquals(GazePayload.DATA_STATUS_SUMMARY, payload.dataStatus)
        assertEquals(1, payload.summaryCount)
        assertNull(
            "protocol 3.5 removed the redundant top-level track_id",
            payload.trackId
        )
        assertNull("summary messages carry no timestamp_us", payload.timestampUs)

        val detection = payload.detections.single()
        assertEquals(10, detection.trackId)
        assertEquals(GazePayload.UPLOAD_STATUS_TRACK_ID_TIMEOUT, detection.uploadStatus)
        assertEquals(7236, detection.frameCount)
        assertEquals(518375L, detection.stayTimeMs)
        assertEquals(2198818L, detection.firstSeenMs)
        assertEquals(2717193L, detection.lastSeenMs)
        assertEquals(7236, detection.behavior?.gazeCount)
        assertEquals(0.0, detection.face?.backRatio ?: -1.0, 1e-9)
    }

    @Test
    fun `gaze_ratio arrives as an integer and still parses as a double`() {
        // The device sends 1, not 1.0, when every frame was a gaze frame. Parsing must not assume
        // a JSON float here.
        val payload = GazePayload.fromJson(JSONObject(SUMMARY_JSON))

        assertEquals(1.0, payload.detections.single().gazeRatio ?: 0.0, 1e-9)
    }

    @Test
    fun `empty frame parses to zero detections`() {
        // Verbatim from debug_data/samples/gaze/detections_empty.json - captured with nothing in
        // front of the camera and inference_result_filter_enable set false, which is the only
        // condition under which the device emits these at all. It means "clear the overlay", so
        // it must parse rather than be rejected.
        val payload = GazePayload.fromJson(
            JSONObject(
                """{"data_status": "frame", "timestamp_us": 3735695728, "detection_count": 0, "detections": []}"""
            )
        )

        assertEquals(GazePayload.DATA_STATUS_FRAME, payload.dataStatus)
        assertEquals(0, payload.detectionCount)
        assertTrue(payload.detections.isEmpty())
    }

    @Test
    fun `empty frame yields one snapshot carrying the empty state`() {
        // GAZE differs from WTA here by design: WtaInfoSnapshot.fromPayload returns an empty list
        // for an empty payload, while GazeInfoSnapshot.fromPayloadDetections emits one snapshot
        // with null detection fields. The GAZE behaviour predates this work and existing consumers
        // rely on receiving something per payload, so it is preserved rather than aligned.
        val payload = GazePayload.fromJson(
            JSONObject(
                """{"data_status": "frame", "timestamp_us": 3735695728, "detection_count": 0, "detections": []}"""
            )
        )

        val snapshots = GazeInfoSnapshot.fromPayloadDetections(payload)

        assertEquals(1, snapshots.size)
        assertEquals(GazePayload.DATA_STATUS_FRAME, snapshots.single().dataStatus)
        assertNull("an empty frame has no track", snapshots.single().trackId)
        assertNull("an empty frame has no body box", snapshots.single().body)
    }

    @Test
    fun `snapshots are produced one per detection`() {
        val payload = GazePayload.fromJson(JSONObject(FRAME_JSON))

        val snapshots = GazeInfoSnapshot.fromPayloadDetections(payload)

        assertEquals(2, snapshots.size)
        assertEquals("10", snapshots[0].trackId)
        assertEquals("11", snapshots[1].trackId)
        assertEquals(GazePayload.DATA_STATUS_FRAME, snapshots[0].dataStatus)
    }
}
