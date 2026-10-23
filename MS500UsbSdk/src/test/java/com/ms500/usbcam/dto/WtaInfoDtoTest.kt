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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WTA detection parsing.
 *
 * FRAME_JSON and SUMMARY_JSON are **verbatim messages captured from a real WTA device**
 * (firmware `usb_gaze_3.0.1_20261020_101458`, model `sony_wta` 416x320, `model_match: true`),
 * stored in `debug_data/samples/wta/`. The camera was pointed at a two-person scene.
 *
 * Using the real bytes is what surfaced things the protocol document does not state: WTA reports an
 * invalid age as `-1` rather than `0`, and which of body/face arrives as an all-zero placeholder
 * varies per message - the captured frame has a zeroed body with a real face, the captured summary
 * has the reverse.
 *
 * HEAD_ONLY_JSON remains synthetic: a head with neither body nor face did not occur during capture.
 * It is derived from the same placeholder shape the real messages use.
 */
class WtaInfoDtoTest {

    private companion object {
        /** Frame message with the WTA-only fields appended to each detection. */
        /** Real frame message: zeroed body placeholder, real face, head in UVC coordinates. */
        const val FRAME_JSON = """
        {
          "data_status": "frame",
          "timestamp_us": 159227592,
          "detection_count": 1,
          "detections": [
            {
              "upload_status": "normal",
              "track_id": 42,
              "body": {
                "bounding_box": {
                  "left": 0,
                  "top": 0,
                  "right": 0,
                  "bottom": 0
                },
                "bounding_box_score": 0
              },
              "face": {
                "bounding_box": {
                  "left": 844,
                  "top": 225,
                  "right": 960,
                  "bottom": 333
                },
                "bounding_box_score": 0.32,
                "attribute": {
                  "gender": {
                    "type": "Female",
                    "score": 0
                  },
                  "age": 30
                },
                "pose": {
                  "yaw": 0,
                  "pitch": 0
                },
                "is_back2camera": false
              },
              "behavior": {
                "is_gaze": false,
                "detail_gaze_info": {
                  "stay_time": 12421,
                  "detail_gaze_time": [
                    {
                      "start": 150099,
                      "end": 150099
                    },
                    {
                      "start": 150808,
                      "end": 150808
                    },
                    {
                      "start": 151796,
                      "end": 152112
                    },
                    {
                      "start": 152374,
                      "end": 152374
                    },
                    {
                      "start": 155370,
                      "end": 155370
                    },
                    {
                      "start": 155807,
                      "end": 155807
                    },
                    {
                      "start": 156799,
                      "end": 156799
                    },
                    {
                      "start": 158092,
                      "end": 158092
                    }
                  ],
                  "bbox_cords": {
                    "first": {
                      "left": 904,
                      "top": 207,
                      "right": 996,
                      "bottom": 319
                    },
                    "last": {
                      "left": 876,
                      "top": 207,
                      "right": 973,
                      "bottom": 310
                    },
                    "conf": {
                      "left": 876,
                      "top": 216,
                      "right": 969,
                      "bottom": 333
                    },
                    "conf_score": 0.68
                  }
                }
              },
              "head": {
                "bounding_box": {
                  "left": 876,
                  "top": 207,
                  "right": 973,
                  "bottom": 310
                },
                "bounding_box_score": 0.5
              },
              "enter_time": 146806,
              "exit_time": -1,
              "person_counting_time": 5117,
              "counted": false,
              "watching_region": 0,
              "counting_region": 0
            }
          ]
        }
        """

        /** Head-only target: WTA could not associate a body or face, so both are all-zero. */
        const val HEAD_ONLY_JSON = """
        {"data_status":"frame","timestamp_us":61216300,"detection_count":1,"detections":[
          {"upload_status":"normal","track_id":2,
           "head":{"bounding_box":{"left":400,"top":200,"right":480,"bottom":300},"bounding_box_score":0.85},
           "body":{"bounding_box":{"left":0,"top":0,"right":0,"bottom":0},"bounding_box_score":0},
           "face":{"bounding_box":{"left":0,"top":0,"right":0,"bottom":0},"bounding_box_score":0,
                   "attribute":{"gender":{"type":"Unknown","score":0},"age":0},
                   "pose":{"yaw":0,"pitch":0},"is_back2camera":false},
           "behavior":{"is_gaze":false},
           "enter_time":60000,"exit_time":-1,"person_counting_time":0,
           "counted":false,"watching_region":-1,"counting_region":-1}
        ]}
        """

        /** Summary message: keeps the counting fields, drops the region indices. */
        /** Real summary message: no region indices, real exit_time, age reported as -1. */
        const val SUMMARY_JSON = """
        {
          "data_status": "summary",
          "summary_count": 1,
          "detections": [
            {
              "track_id": 68,
              "frame_count": 6,
              "stay_time_ms": 3043,
              "first_seen_ms": 265696,
              "last_seen_ms": 268739,
              "gaze_ratio": 0,
              "body": {
                "bounding_box": {
                  "left": 798,
                  "top": 207,
                  "right": 1061,
                  "bottom": 917
                },
                "bounding_box_score": 0.51
              },
              "face": {
                "bounding_box": {
                  "left": 0,
                  "top": 0,
                  "right": 0,
                  "bottom": 0
                },
                "bounding_box_score": 0,
                "attribute": {
                  "gender": {
                    "type": "Male",
                    "score": 0.51
                  },
                  "age": -1
                },
                "pose": {
                  "yaw": 16,
                  "pitch": -15
                },
                "is_back2camera": false,
                "back_ratio": 0
              },
              "behavior": {
                "is_gaze": false,
                "gaze_count": 0,
                "detail_gaze_info": {
                  "stay_time": 3043,
                  "detail_gaze_time": [],
                  "bbox_cords": {
                    "first": {
                      "left": 895,
                      "top": 207,
                      "right": 996,
                      "bottom": 324
                    },
                    "last": {
                      "left": 918,
                      "top": 207,
                      "right": 1010,
                      "bottom": 324
                    },
                    "conf": {
                      "left": 895,
                      "top": 207,
                      "right": 996,
                      "bottom": 324
                    },
                    "conf_score": 0.5
                  }
                }
              },
              "head": {
                "bounding_box": {
                  "left": 918,
                  "top": 207,
                  "right": 1010,
                  "bottom": 324
                },
                "bounding_box_score": 0.5
              },
              "enter_time": 265691,
              "exit_time": 268739,
              "person_counting_time": 163,
              "counted": false,
              "upload_status": "track_id_timeout"
            }
          ]
        }
        """
    }

    @Test
    fun `frame message parses the WTA-only fields`() {
        val payload = WtaPayload.fromJson(JSONObject(FRAME_JSON))

        assertTrue(payload.isFrame)
        assertFalse(payload.isSummary)
        assertEquals(159227592L, payload.timestampUs)

        val detection = payload.detections.single()
        assertEquals(42, detection.trackId)

        // Head is the WTA tracking subject and boxes are in UVC coordinates, so values well above
        // the 512x384 model space are expected here.
        val head = requireNotNull(detection.head?.boundingBox)
        assertEquals(876, head.left)
        assertEquals(973, head.right)
        assertEquals(0.5, detection.head?.boundingBoxScore ?: 0.0, 1e-9)

        assertEquals(146806L, detection.enterTimeMs)
        assertEquals(WtaDetection.EXIT_TIME_STILL_IN_VIEW, detection.exitTimeMs)
        assertTrue("exit_time -1 means the track is still in view", detection.isStillInView)
        assertEquals(5117L, detection.personCountingTimeMs)
        assertFalse("300s is the default dwell threshold; 5s has not reached it", detection.counted)
        // Default regions cover the whole frame, so both indices are 0 rather than -1 here.
        assertEquals(0, detection.watchingRegion)
        assertEquals(0, detection.countingRegion)
    }

    @Test
    fun `head-only target is kept but its placeholder boxes are dropped from the snapshot`() {
        val payload = WtaPayload.fromJson(JSONObject(HEAD_ONLY_JSON))
        val detection = payload.detections.single()

        // The payload mirrors the wire, placeholders included.
        assertNotNull("payload keeps the all-zero body as sent", detection.body)
        assertNotNull("payload keeps the all-zero face as sent", detection.face)

        val snapshot = WtaInfoSnapshot.fromPayload(payload).single()

        assertEquals(2, snapshot.trackId)
        assertNotNull("a head-only target is a valid detection", snapshot.head)
        assertNull("an all-zero body is not a usable box", snapshot.body)
        assertNull("an all-zero face is not a usable box", snapshot.face)
        assertEquals(WtaInfoSnapshot.BOX_SPACE_UVC, snapshot.boundingBoxSpace)
    }

    @Test
    fun `region sentinel of -1 is preserved rather than normalised to zero`() {
        // Treating a missing region as region 0 would attribute a target to the first region it is
        // not in, so -1 has to survive parsing intact.
        val payload = WtaPayload.fromJson(JSONObject(HEAD_ONLY_JSON))

        val detection = payload.detections.single()
        assertEquals(WtaDetection.REGION_NONE, detection.watchingRegion)
        assertEquals(WtaDetection.REGION_NONE, detection.countingRegion)
    }

    @Test
    fun `summary keeps counting state and omits region indices`() {
        val payload = WtaPayload.fromJson(JSONObject(SUMMARY_JSON))

        assertTrue(payload.isSummary)
        val detection = payload.detections.single()

        assertEquals(268739L, detection.exitTimeMs)
        assertFalse("a timed-out track is no longer in view", detection.isStillInView)
        assertEquals(163L, detection.personCountingTimeMs)
        assertFalse("the track left long before the 300s dwell threshold", detection.counted)
        assertNull("summary messages carry no watching_region", detection.watchingRegion)
        assertNull("summary messages carry no counting_region", detection.countingRegion)
        assertEquals(6, detection.frameCount)
        assertEquals(265696L, detection.firstSeenMs)
        assertEquals(268739L, detection.lastSeenMs)
        // enter_time and first_seen_ms are different fields: the head appeared 5ms before the
        // track was first confirmed.
        assertEquals(265691L, detection.enterTimeMs)
    }

    @Test
    fun `the payload preserves the age sentinel while the snapshot drops it`() {
        // Captured from the real summary message. The sentinel must survive **parsing** - -1 is
        // distinguishable from a real age of 0, so it may not be normalised away on the payload,
        // which mirrors the device JSON.
        //
        // The snapshot is the other half of that contract: it drops what WTA could not determine,
        // the same way it drops the fixed is_back2camera and all-zero boxes. Since firmware 3.0.4
        // there are two sentinels (-1 and 999) and 999 passes every `age < 0` check, so leaving raw
        // sentinels in the snapshot would make each caller learn both magic numbers.
        //
        // This test asserted `snapshot.age == -1` before 3.0.4. The change is deliberate; callers
        // needing the distinction read WtaDetection.isAgeUnrecognized on the payload.
        val payload = WtaPayload.fromJson(JSONObject(SUMMARY_JSON))

        val detection = payload.detections.single()
        assertEquals("the payload must not normalise the sentinel away", -1, detection.face?.attribute?.age)
        assertFalse("-1 is no-face, not unrecognized", detection.isAgeUnrecognized)

        val snapshot = WtaInfoSnapshot.fromPayload(payload).single()
        assertNull("the snapshot exposes a real age or nothing", snapshot.age)
    }

    @Test
    fun `which box is a placeholder varies between messages`() {
        // The captured frame has a zeroed body with a real face; the captured summary has a real
        // body with a zeroed face. Nothing may assume a fixed one is missing.
        val frame = WtaInfoSnapshot.fromPayload(
            WtaPayload.fromJson(JSONObject(FRAME_JSON))
        ).single()
        val summary = WtaInfoSnapshot.fromPayload(
            WtaPayload.fromJson(JSONObject(SUMMARY_JSON))
        ).single()

        assertNull("captured frame has an all-zero body", frame.body)
        assertNotNull("captured frame has a real face", frame.face)
        assertNotNull("captured summary has a real body", summary.body)
        assertNull("captured summary has an all-zero face", summary.face)
    }

    @Test
    fun `empty frame yields no snapshots`() {
        // An empty payload must not synthesise a placeholder snapshot: a consumer drawing that
        // would paint a detection that never existed.
        val payload = WtaPayload.fromJson(
            JSONObject("""{"data_status":"frame","timestamp_us":1,"detection_count":0,"detections":[]}""")
        )

        assertTrue(payload.detections.isEmpty())
        assertTrue(WtaInfoSnapshot.fromPayload(payload).isEmpty())
    }

    @Test
    fun `snapshot omits the fixed back-to-camera placeholder and gender score`() {
        // WTA always reports is_back2camera=false and reuses the gender score slot for a
        // person-class score, so neither may reach a business consumer as a finding.
        val payload = WtaPayload.fromJson(JSONObject(FRAME_JSON))
        val snapshot = WtaInfoSnapshot.fromPayload(payload).single()

        val snapshotFields = WtaInfoSnapshot::class.java.declaredFields.map { it.name }.toSet()
        // Exact names, not a substring match: "trackId" contains "ack".
        for (forbidden in listOf("isBackToCamera", "backRatio", "genderScore")) {
            assertFalse(
                "snapshot must not expose $forbidden - WTA cannot determine it",
                snapshotFields.contains(forbidden)
            )
        }
        // The gender label itself is still useful and is kept.
        assertEquals("Female", snapshot.gender)
        assertEquals(30, snapshot.age)
    }

    // ==================== 固件 3.0.4 / 协议 4.5 ====================

    @Test
    fun `age 999 is a sentinel, not an age`() {
        // 3.0.4 新增：有脸但角度超范围时报 999。只检查 age < 0 的调用方会把 999 当年龄显示。
        val detection = WtaDetection.fromJson(
            JSONObject(
                """{"track_id":1,"face":{"bounding_box":{"left":10,"top":10,"right":50,"bottom":60},
                   "bounding_box_score":0.8,"attribute":{"age":999,"gender":{"type":"Male","score":0.5}}}}"""
            )
        )
        assertEquals(999, detection.face?.attribute?.age)
        assertTrue(detection.isAgeUnrecognized)
        assertNull("999 必须不作为年龄暴露", detection.estimatedAge)
    }

    @Test
    fun `age -1 still means no age, and is not reported as unrecognized`() {
        val detection = WtaDetection.fromJson(
            JSONObject("""{"track_id":1,"face":{"attribute":{"age":-1}}}""")
        )
        assertNull(detection.estimatedAge)
        assertFalse("-1 是没有脸，不是识别不出", detection.isAgeUnrecognized)
    }

    @Test
    fun `a real age passes through`() {
        val detection = WtaDetection.fromJson(
            JSONObject("""{"track_id":1,"face":{"attribute":{"age":30}}}""")
        )
        assertEquals(30, detection.estimatedAge)
        assertFalse(detection.isAgeUnrecognized)
    }

    @Test
    fun `the two age sentinels are distinct values`() {
        // 如果哪天它们相等，estimatedAge 就区分不出来了。
        assertNotEquals(WtaDetection.AGE_NO_FACE, WtaDetection.AGE_UNRECOGNIZED)
        assertEquals(-1, WtaDetection.AGE_NO_FACE)
        assertEquals(999, WtaDetection.AGE_UNRECOGNIZED)
    }

    @Test
    fun `counting_times is dense and counted_regions is sparse`() {
        // 两者形状不同是最容易写错的地方：前者下标即区域序号，后者是区域序号的列表。
        val detection = WtaDetection.fromJson(
            JSONObject(
                """{"track_id":7,"person_counting_time":31468,"counted":true,
                   "counting_times":[31468,0,5200],"counted_regions":[0,2]}"""
            )
        )
        assertEquals(listOf(31468L, 0L, 5200L), detection.countingTimesMs)
        assertEquals(listOf(0, 2), detection.countedRegions)

        assertEquals(31468L, detection.countingTimeMsFor(0))
        assertEquals(0L, detection.countingTimeMsFor(1))
        assertEquals(5200L, detection.countingTimeMsFor(2))
        assertNull("越界要给 null，不能抛", detection.countingTimeMsFor(3))

        assertTrue(detection.isCountedIn(0))
        assertFalse("区域 1 没计数，尽管它在 counting_times 里有条目", detection.isCountedIn(1))
        assertTrue(detection.isCountedIn(2))
    }

    @Test
    fun `older firmware without the new arrays parses to empty lists`() {
        // 3.0.1 不报这两个字段。缺字段是正常状态，不是错误。
        val detection = WtaDetection.fromJson(
            JSONObject("""{"track_id":7,"person_counting_time":31468,"counted":false}""")
        )
        assertTrue(detection.countingTimesMs.isEmpty())
        assertTrue(detection.countedRegions.isEmpty())
        assertNull(detection.countingTimeMsFor(0))
        assertFalse(detection.isCountedIn(0))
    }

    @Test
    fun `counted with an empty counted_regions is accepted`() {
        // 设备侧修复前出现过「改区域瞬间 counted=true 但 counted_regions 为空」。
        // 解析不能因此报错，上层自己决定怎么显示。
        val detection = WtaDetection.fromJson(
            JSONObject("""{"track_id":7,"counted":true,"counting_times":[],"counted_regions":[]}""")
        )
        assertTrue(detection.counted)
        assertTrue(detection.countedRegions.isEmpty())
    }

    @Test
    fun `snapshot carries the new fields and hides the age sentinels`() {
        val payload = WtaPayload.fromJson(
            JSONObject(
                """{"data_status":"frame","detection_count":1,"detections":[
                   {"track_id":9,"face":{"attribute":{"age":999}},
                    "counting_times":[1000,2000],"counted_regions":[1]}]}"""
            )
        )
        val snapshot = WtaInfoSnapshot.fromPayload(payload).single()
        assertNull("snapshot 只暴露真实年龄", snapshot.age)
        assertEquals(listOf(1000L, 2000L), snapshot.countingTimesMs)
        assertEquals(listOf(1), snapshot.countedRegions)
    }
}
