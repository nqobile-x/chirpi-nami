package com.nqobile.chirpynami.core.call

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.nqobile.chirpynami.ai.video.GhostBandLandmarks
import com.nqobile.chirpynami.ai.video.GhostBandMeshCodec
import com.nqobile.chirpynami.ai.video.GhostBandTopology
import com.nqobile.chirpynami.ai.video.Landmark3D
import java.util.concurrent.Executors

/**
 * GhostBand sender: front camera -> on-device 468-point face mesh -> 139-point GhostBand frame.
 *
 * No pixels ever leave the phone; only face geometry does. Frames are throttled to ~12 fps
 * and the newest camera frame always wins, so a slow phone drops frames instead of lagging.
 */
class ChirpyFaceCapture(
    private val context: Context,
    private val onFrame: (local: GhostBandLandmarks, packet: ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "ChirpyFace"
        private const val MIN_FRAME_INTERVAL_MS = 80L
    }

    private val detector = FaceMeshDetection.getClient(
        FaceMeshDetectorOptions.Builder().setUseCase(FaceMeshDetectorOptions.FACE_MESH).build()
    )
    private val analysisExecutor = Executors.newSingleThreadExecutor { Thread(it, "chirpy-face").apply { isDaemon = true } }
    private var provider: ProcessCameraProvider? = null
    private var lastFrameMs = 0L
    private var frameIndex = 0L
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    @Volatile private var wanted = false

    @Volatile var faceVisible = false
        private set

    fun start(owner: LifecycleOwner) {
        wanted = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!wanted) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { analyze(it) }
                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, selector, analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Camera unavailable", e)
            }
        }, mainExecutor)
    }

    /** Safe from any thread: calls can end on a socket thread, but CameraX must unbind on main. */
    fun stop() {
        wanted = false
        faceVisible = false
        mainExecutor.execute { if (!wanted) provider?.unbindAll() }
    }

    fun close() {
        stop()
        detector.close()
        analysisExecutor.shutdown()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val image = proxy.image
        if (image == null || now - lastFrameMs < MIN_FRAME_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastFrameMs = now
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit reports points in the upright (rotated) image space.
        val width = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
        val height = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()

        detector.process(InputImage.fromMediaImage(image, rotation))
            .addOnSuccessListener { meshes ->
                val mesh = meshes.firstOrNull()
                faceVisible = mesh != null
                if (mesh == null) return@addOnSuccessListener
                val byIndex = arrayOfNulls<Landmark3D>(468)
                for (point in mesh.allPoints) {
                    if (point.index in byIndex.indices) {
                        byIndex[point.index] = Landmark3D(point.position.x / width, point.position.y / height, 0f)
                    }
                }
                val subset = GhostBandTopology.SUBSET.map { byIndex[it] ?: return@addOnSuccessListener }
                val packet = GhostBandMeshCodec.encode(frameIndex++, subset)
                GhostBandMeshCodec.decode(packet)?.let { onFrame(it, packet) }
            }
            .addOnFailureListener { Log.w(TAG, "Face mesh failed", it) }
            .addOnCompleteListener { proxy.close() }
    }
}
