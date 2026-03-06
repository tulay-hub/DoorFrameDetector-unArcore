package com.example.doorframedetector

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.*
import kotlin.math.abs

class VisionProcessor(private val context: Context, private val listener: DetectionListener) {
    
    interface DetectionListener {
        fun onDoorFrameDetected(box: RectF, sourceWidth: Int, sourceHeight: Int, rotation: Int)
        fun onDetectionCleared()
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentDetectionJob: Job? = null
    
    // Initialize ML Kit Object Detector
    // Changed to SINGLE_IMAGE_MODE to be safer on low-end devices and reduce crash risk
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE) 
        .enableClassification()
        .enableMultipleObjects()
        .build()
    
    private val objectDetector = ObjectDetection.getClient(options)
    
    // Door frame characteristics
    private val doorFrameLabels = listOf("door", "portal", "entrance", "frame", "access")
    
    fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            val width = mediaImage.width
            val height = mediaImage.height
            
            objectDetector.process(inputImage)
                .addOnSuccessListener { objects ->
                    try {
                        detectDoorFrameFromObjects(objects, width, height, rotation)
                    } catch (e: Exception) {
                        android.util.Log.e("VisionProcessor", "Error processing results: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VisionProcessor", "Detection error: ${e.message}")
                }
                .addOnCompleteListener {
                    // Always close the image proxy when done
                    imageProxy.close()
                }
        } catch (e: Exception) {
            android.util.Log.e("VisionProcessor", "Critical error in processImage: ${e.message}")
            imageProxy.close()
        }
    }
    
    private fun detectDoorFrameFromObjects(objects: List<DetectedObject>, width: Int, height: Int, rotation: Int) {
        var bestDoorFrame: DetectedObject? = null
        var bestScore = 0f
        
        for (obj in objects) {
            val score = calculateDoorFrameScore(obj)
            if (score > bestScore) {
                bestScore = score
                bestDoorFrame = obj
            }
        }
        
        if (bestScore > 0.4f && bestDoorFrame != null) { 
            val bounds = bestDoorFrame.boundingBox
            
            // Analyze aspect ratio for door frame validation
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            
            // Avoid division by zero
            if (h <= 0) return
            
            val aspectRatio = w / h
            
            // Door frames are typically tall (ratio < 1.0)
            val isDoorFrameLike = aspectRatio < 0.9f
            
            if (isDoorFrameLike) {
                val rect = RectF(bounds)
                
                // Switch to Main thread for listener
                coroutineScope.launch(Dispatchers.Main) {
                    listener.onDoorFrameDetected(rect, width, height, rotation)
                }
            } else {
                 coroutineScope.launch(Dispatchers.Main) {
                    listener.onDetectionCleared()
                }
            }
        } else {
            coroutineScope.launch(Dispatchers.Main) {
                listener.onDetectionCleared()
            }
        }
    }
    
    private fun calculateDoorFrameScore(obj: DetectedObject): Float {
        var score = 0f
        
        // Check classification labels
        for (label in obj.labels) {
            if (doorFrameLabels.any { label.text.contains(it, ignoreCase = true) }) {
                score += label.confidence * 1.5f
            } else {
                score += label.confidence * 0.5f
            }
        }
        
        // If no labels, rely on shape (door frames are often detected as generic objects first)
        if (obj.labels.isEmpty()) {
             score += 0.5f // Base score for any object
        }
        
        // Aspect ratio heuristic: door frames are tall rectangles
        val bounds = obj.boundingBox
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        
        if (height > 0) {
            val aspectRatio = width / height
            
            // Ideal door aspect ratio is around 0.5 (width:height = 1:2)
            val ratioScore = 1f - abs(0.5f - aspectRatio)
            score += ratioScore * 0.3f
        }
        
        return score
    }
    
    fun stop() {
        currentDetectionJob?.cancel()
        coroutineScope.cancel()
    }
}
