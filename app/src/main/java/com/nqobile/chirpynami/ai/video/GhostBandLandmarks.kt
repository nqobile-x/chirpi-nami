package com.nqobile.chirpynami.ai.video

import java.io.Serializable

data class Landmark3D(
    val x: Float,
    val y: Float,
    val z: Float
) : Serializable

/**
 * 3D Facial Mesh and expression telemetry for GhostBand Video Calling.
 *
 * Tracks 68 salient facial points, head rotation Euler angles, and blendshapes.
 */
data class GhostBandLandmarks(
    val frameIndex: Long,
    val timestampMs: Long,
    val pitchDeg: Float,
    val yawDeg: Float,
    val rollDeg: Float,
    val eyeBlinkL: Float,
    val eyeBlinkR: Float,
    val mouthOpen: Float,
    val smile: Float,
    val landmarks: List<Landmark3D>
) : Serializable
