# 🚪 Door Frame Detector (Lite Version)

Android app for real-time door frame detection using CameraX and Machine Learning (ML Kit).
**Note:** This version has been optimized for compatibility and does NOT require ARCore support.

## 📱 Features
- **Real-time object detection** using Google ML Kit
- **Visual overlay** for detected door frames
- **CameraX integration** for stable camera preview
- **Broad device compatibility** (No ARCore required)

## 🛠️ Tech Stack
- **Camera Framework**: Android CameraX
- **Object Detection**: Google ML Kit Object Detection
- **UI**: Android View System (Custom Overlay)
- **Language**: Kotlin

## 🔧 Installation & Building

### Prerequisites
- Android Studio 2022+
- Android SDK 34+
- Java/Kotlin development environment

### Manual Build
```bash
# Clone the repository
git clone https://github.com/tulay-hub/DoorFrameDetector.git

# Build the APK
./gradlew assembleRelease

# APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

## 📁 Project Structure
```
DoorFrameDetector/
├── app/
│   ├── src/main/java/com/example/doorframedetector/
│   │   ├── MainActivity.kt          # Main UI & Logic
│   │   ├── VisionProcessor.kt       # ML Kit detection logic
│   │   ├── CameraController.kt      # CameraX management
│   │   └── OverlayView.kt           # Custom drawing view
│   ├── src/main/res/
│   │   ├── layout/activity_main.xml # UI Layout
│   ├── build.gradle                 # Dependencies
│   └── AndroidManifest.xml          # Permissions
```

## 📋 Requirements
- **Android 8.0+** (API 26+)
- **Camera permissions**
- **Internet connection** (for initial ML model download if needed)

## 🚀 How to Use
1. Install the APK on your device
2. Grant camera permissions when prompted
3. Point camera at a door frame
4. The app will highlight detected door frames with a green box

## 🔧 Troubleshooting
- **App crashes on startup**: Ensure your device has Google Play Services installed and updated.
- **Camera not starting**: Check permissions in system settings.
- **Detection slow**: Ensure good lighting conditions.

## 📄 License
This project is available under the MIT License.
