package com.example.doorframedetector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraController(private val context: Context) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    
    suspend fun initializeCamera(): Boolean {
        return try {
            cameraProvider = getCameraProvider()
            true
        } catch (e: Exception) {
            Log.e("CameraController", "Failed to initialize camera", e)
            false
        }
    }
    
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analysisExecutor: ExecutorService,
        frameProcessor: (ImageProxy) -> Unit
    ) {
        val cameraProvider = cameraProvider ?: return
        
        // Unbind existing use cases
        cameraProvider.unbindAll()
        
        // Preview Use Case
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image Analysis Use Case for real-time processing
        // Using default YUV_420_888 format which is optimal for ML Kit
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // .setTargetResolution(Size(1280, 720)) // Optional: set resolution
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { image ->
                    frameProcessor(image)
                }
            }
        
        // Camera Selector - use back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        
        try {
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("CameraController", "Use case binding failed", exc)
        }
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
        imageAnalysis = null
        preview = null
    }
    
    private suspend fun getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                continuation.resume(cameraProviderFuture.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
