package com.example.doorframedetector

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
// import com.google.mlkit.vision.common.InputImage
// import com.google.mlkit.vision.objects.DetectedObject
// import com.google.mlkit.vision.objects.ObjectDetection
// import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.*
import kotlin.math.abs

class VisionProcessor(private val context: Context, private val listener: DetectionListener) {
    
    interface DetectionListener {
        fun onDoorFrameDetected(box: RectF, sourceWidth: Int, sourceHeight: Int, rotation: Int)
        fun onDetectionCleared()
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /*
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE) 
        .enableClassification()
        .enableMultipleObjects()
        .build()
    
    private val objectDetector = ObjectDetection.getClient(options)
    */
    
    fun processImage(imageProxy: ImageProxy) {
        // Just close image for now
        imageProxy.close()
    }
    
    fun stop() {
        coroutineScope.cancel()
    }
}
