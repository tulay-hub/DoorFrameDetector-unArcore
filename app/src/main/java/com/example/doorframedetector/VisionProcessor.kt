package com.example.doorframedetector

import android.content.Context
import android.graphics.Bitmap
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
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .enableMultipleObjects()
        .build()
    
    private val objectDetector = ObjectDetection.getClient(options)
    
    // Door frame characteristics
    private val doorFrameLabels = listOf("door", "portal", "entrance", "frame", "access")
    
    fun processImage(imageProxy: ImageProxy) {
        // We need to close the imageProxy eventually.
        // If we launch a coroutine, we must ensure close() is called.
        // Ideally, we should process synchronously in the analyzer callback (which runs on a background thread already)
        // or ensure we close it.
        // CameraX analyzer runs on a dedicated executor, so we can block it slightly, but better to be fast.
        // ML Kit process() is async (returns Task).
        
        // Since we are using an Executor in CameraController for analysis, we are already on a background thread.
        // We can just call ML Kit synchronously-ish or wait for it.
        // But ML Kit Task API is callback based or awaitable.
        
        val rotation = imageProxy.imageInfo.rotationDegrees
        val mediaImage = imageProxy.image
        
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val width = mediaImage.width
        val height = mediaImage.height
        
        objectDetector.process(inputImage)
            .addOnSuccessListener { objects ->
                detectDoorFrameFromObjects(objects, width, height, rotation)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("VisionProcessor", "Detection error: ${e.message}")
            }
            .addOnCompleteListener {
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
        
        if (bestScore > 0.4f && bestDoorFrame != null) { // Lowered threshold slightly
            val bounds = bestDoorFrame.boundingBox
            
            // Analyze aspect ratio for door frame validation
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val aspectRatio = w / h
            
            // Door frames are typically tall (ratio < 1.0)
            // But depending on distance/angle, it might vary.
            // Let's be lenient.
            val isDoorFrameLike = aspectRatio < 0.9f
            
            if (isDoorFrameLike) {
                // Pass the rect directly.
                // Note: ML Kit returns coordinates relative to the InputImage.
                // If rotation is present, the coordinates might need transformation if we display them on a view that has different orientation logic.
                // But usually InputImage handles the rotation so the coordinates are upright?
                // Actually: "The bounding box is relative to the image that was passed to process()."
                // If we passed a rotated image (via rotationDegrees), ML Kit "rotates" it internally for detection?
                // No, ML Kit detects in the image buffer coordinates. The rotationDegrees tells ML Kit how to interpret "up".
                // The bounding box returned is in the coordinate system of the unrotated image buffer.
                // We will pass the raw buffer dimensions and rotation to the UI to handle the mapping.
                
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
        val aspectRatio = bounds.width().toFloat() / bounds.height().toFloat()
        
        // Ideal door aspect ratio is around 0.5 (width:height = 1:2)
        val ratioScore = 1f - abs(0.5f - aspectRatio)
        score += ratioScore * 0.3f
        
        // Size heuristic: door frames are typically large relative to frame
        // But not too large (entire screen)
        // score += sizeScore * 0.2f
        
        return score
    }
    
    fun stop() {
        currentDetectionJob?.cancel()
        coroutineScope.cancel()
    }
}
