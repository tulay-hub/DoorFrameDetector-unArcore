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

class CameraController(private val context: Context) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    // private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    
    // 移除 initializeCamera，直接在 startCamera 中获取
    
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analysisExecutor: ExecutorService,
        frameProcessor: (ImageProxy) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Unbind existing use cases
                cameraProvider?.unbindAll()
                
                // Preview Use Case
                preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Image Analysis Use Case for real-time processing
                // Disabled for safe mode
                /*
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { image ->
                            frameProcessor(image)
                        }
                    }
                */
                
                // Camera Selector - use back camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                
                try {
                    // Bind use cases to camera
                    if (cameraProvider != null) {
                        // Only bind preview for now to ensure stability
                        camera = cameraProvider!!.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                            // imageAnalysis
                        )
                        Log.d("CameraController", "Camera started successfully (Preview Only)")
                    }
                } catch (exc: Exception) {
                    Log.e("CameraController", "Use case binding failed", exc)
                }
            } catch (e: Exception) {
                Log.e("CameraController", "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            // imageAnalysis = null
            preview = null
        } catch (e: Exception) {
            Log.e("CameraController", "Error stopping camera", e)
        }
    }
    
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
