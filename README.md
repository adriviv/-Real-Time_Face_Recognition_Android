# Real-Time Face Recognition Android App

## 🌟 Overview

This Android application demonstrates real-time face recognition using TensorFlow Lite and ML Kit. It's built with a modular architecture that supports multiple recognition features (face, audio, location).

## ✨ Features

- **Real-time Face Recognition** 🎭
  - Face detection using ML Kit
  - Face recognition using TensorFlow Lite
  - Support for multiple faces
  - Front/back camera switching

- **Modular Architecture** 🏗️
  - Clean separation of concerns
  - Reusable ML infrastructure
  - Easy to add new features
  - Robust error handling

- **Future Features** 🚀
  - Audio recognition support
  - Location-based features
  - Multi-modal recognition

## 🚀 Quick Start

1. **Clone the Repository**
   ```bash
   git clone https://github.com/yourusername/Real-Time_Face_Recognition_Android.git
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Build and Run**
   - Connect an Android device or use an emulator
   - Click "Run" (▶️) or press `Shift + F10`

## 📱 Usage

1. **Face Recognition**
   - Launch the app
   - Point camera at a face
   - Click "Add Face" to register
   - Enter name when prompted
   - Switch to recognition mode

2. **Camera Controls**
   - Switch between front/back cameras
   - Adjust recognition sensitivity
   - View registered faces
   - Import photos for recognition

## 🏗️ Architecture

The app follows a modular, layered architecture:

```
com.atharvakale.facerecognition/
├── ml/                 # ML Infrastructure
├── hardware/          # Hardware Abstraction
├── data/             # Data Management
└── features/         # Feature Modules
```

For detailed architecture information, see [ARCHITECTURE.md](ARCHITECTURE.md).

## 🔧 Technical Details

- **Minimum SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **ML Framework**: TensorFlow Lite
- **Face Detection**: ML Kit
- **Camera API**: CameraX
- **Architecture**: MVVM + Clean Architecture

## 📚 Dependencies

```gradle
dependencies {
    // ML Kit Face Detection
    implementation 'com.google.mlkit:face-detection:16.1.6'
    
    // TensorFlow Lite
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
    
    // CameraX
    implementation "androidx.camera:camera-core:1.3.1"
    implementation "androidx.camera:camera-camera2:1.3.1"
}
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [TensorFlow Lite](https://www.tensorflow.org/lite)
- [ML Kit](https://developers.google.com/ml-kit)
- [CameraX](https://developer.android.com/training/camerax)

## 📞 Contact

For questions or feedback, please open an issue or contact:
- Email: your.email@example.com
- Twitter: [@yourusername](https://twitter.com/yourusername)

