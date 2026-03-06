package com.example.doorframedetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), VisionProcessor.DetectionListener {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var statusText: TextView
    private lateinit var doorframeStatusText: TextView
    private lateinit var instructionText: TextView
    
    private lateinit var cameraController: CameraController
    // private lateinit var visionProcessor: VisionProcessor
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        try {
            previewView = findViewById(R.id.view_finder)
            overlayView = findViewById(R.id.overlay_view)
            statusText = findViewById(R.id.status_text)
            doorframeStatusText = findViewById(R.id.doorframe_status)
            instructionText = findViewById(R.id.instruction_text)
            
            // Initialize components
            cameraController = CameraController(this)
            
            /*
            try {
                visionProcessor = VisionProcessor(this, this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize VisionProcessor", e)
                Toast.makeText(this, "ML Kit 初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
                statusText.text = "检测功能不可用"
                // Don't return, allow camera to start without detection if possible
            }
            */
            statusText.text = "检测功能已禁用 (安全模式)"
            
            // Check camera permission
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else {
                startCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "需要摄像头权限才能使用此应用",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun startCamera() {
        statusText.text = "正在启动相机..."
        
        previewView.post {
            try {
                cameraController.startCamera(
                    this,
                    previewView,
                    analysisExecutor
                ) { imageProxy ->
                    /*
                    if (::visionProcessor.isInitialized) {
                        visionProcessor.processImage(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                    */
                    imageProxy.close() // Just close the image in safe mode
                }
                statusText.text = "相机已启动 (安全模式)"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                statusText.text = "相机启动失败"
                Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraController.isInitialized) {
            cameraController.release()
        }
        /*
        if (::visionProcessor.isInitialized) {
            visionProcessor.stop()
        }
        */
        analysisExecutor.shutdown()
    }
    
    // VisionProcessor.DetectionListener implementation
    override fun onDoorFrameDetected(box: RectF, sourceWidth: Int, sourceHeight: Int, rotation: Int) {
        runOnUiThread {
            doorframeStatusText.text = "检测到门框"
            doorframeStatusText.setTextColor(ContextCompat.getColor(
                this,
                android.R.color.holo_orange_dark
            ))
            
            // Map coordinates from image to screen
            val mappedRect = mapRectToScreen(box, sourceWidth, sourceHeight, rotation)
            overlayView.setDetection(mappedRect, "门框")
        }
    }
    
    override fun onDetectionCleared() {
        runOnUiThread {
            doorframeStatusText.text = getString(R.string.no_doorframe)
            doorframeStatusText.setTextColor(ContextCompat.getColor(
                this,
                android.R.color.holo_red_dark
            ))
            overlayView.setDetection(null)
        }
    }
    
    private fun mapRectToScreen(box: RectF, imageWidth: Int, imageHeight: Int, rotation: Int): RectF {
        if (previewView.width == 0 || previewView.height == 0) return RectF()
        
        val matrix = Matrix()
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        
        // Calculate the aspect ratio of the input image
        // If rotation is 90 or 270, we swap width and height for the calculation
        val isRotated = rotation == 90 || rotation == 270
        val srcWidth = if (isRotated) imageHeight.toFloat() else imageWidth.toFloat()
        val srcHeight = if (isRotated) imageWidth.toFloat() else imageHeight.toFloat()
        
        // Calculate scale to fill the view (CenterCrop)
        val scaleX = viewWidth / srcWidth
        val scaleY = viewHeight / srcHeight
        val scale = kotlin.math.max(scaleX, scaleY)
        
        // Calculate offsets to center the image
        val scaledWidth = srcWidth * scale
        val scaledHeight = srcHeight * scale
        val dx = (viewWidth - scaledWidth) / 2
        val dy = (viewHeight - scaledHeight) / 2
        
        // Apply transformations
        // 1. Scale
        matrix.postScale(scale, scale)
        
        // 2. Translate
        matrix.postTranslate(dx, dy)
        
        val mappedBox = RectF(box)
        
        if (isRotated) {
            // Transform the box from buffer coordinates to visual upright coordinates
            // Map buffer coordinates to 0..1
            mappedBox.left /= imageWidth
            mappedBox.right /= imageWidth
            mappedBox.top /= imageHeight
            mappedBox.bottom /= imageHeight
            
            // Now we have normalized coordinates in the buffer space.
            // Apply rotation to get normalized coordinates in the visual space.
            if (rotation == 90) {
                // (x, y) -> (y, 1-x)
                val newLeft = mappedBox.top
                val newTop = 1f - mappedBox.right
                val newRight = mappedBox.bottom
                val newBottom = 1f - mappedBox.left
                mappedBox.set(newLeft, newTop, newRight, newBottom)
            } else if (rotation == 270) {
                // (x, y) -> (1-y, x)
                val newLeft = 1f - mappedBox.bottom
                val newTop = mappedBox.left
                val newRight = 1f - mappedBox.top
                val newBottom = mappedBox.right
                mappedBox.set(newLeft, newTop, newRight, newBottom)
            } else if (rotation == 180) {
                 // (x, y) -> (1-x, 1-y)
                val newLeft = 1f - mappedBox.right
                val newTop = 1f - mappedBox.bottom
                val newRight = 1f - mappedBox.left
                val newBottom = 1f - mappedBox.top
                mappedBox.set(newLeft, newTop, newRight, newBottom)
            }
            
            // Now mappedBox is normalized 0..1 in the visual space (srcWidth x srcHeight)
            // Scale up to visual size
            mappedBox.left *= srcWidth
            mappedBox.right *= srcWidth
            mappedBox.top *= srcHeight
            mappedBox.bottom *= srcHeight
            
            // Now apply the view scaling (CenterCrop)
            mappedBox.left *= scale
            mappedBox.right *= scale
            mappedBox.top *= scale
            mappedBox.bottom *= scale
            
            mappedBox.offset(dx, dy)
            
            return mappedBox
        } else {
            // No rotation, just scale and offset
             // Map buffer coordinates to 0..1
            mappedBox.left /= imageWidth
            mappedBox.right /= imageWidth
            mappedBox.top /= imageHeight
            mappedBox.bottom /= imageHeight
            
            // Scale up to visual size (which is same as image size)
            mappedBox.left *= srcWidth
            mappedBox.right *= srcWidth
            mappedBox.top *= srcHeight
            mappedBox.bottom *= srcHeight
             
            mappedBox.left *= scale
            mappedBox.right *= scale
            mappedBox.top *= scale
            mappedBox.bottom *= scale
            
            mappedBox.offset(dx, dy)
            return mappedBox
        }
    }
}
