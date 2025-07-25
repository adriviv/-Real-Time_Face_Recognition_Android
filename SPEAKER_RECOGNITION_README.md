# Real-Time Speaker Recognition Feature

This document describes the implementation of the real-time speaker recognition feature using the FRILL model for the Android Face Recognition app.

## Overview

The speaker recognition system provides:

- **Real-time audio processing** with low-latency sliding windows
- **FRILL model inference** to extract 192-dimensional speaker embeddings
- **Speaker matching** via cosine similarity against registered profiles
- **Speaker enrollment** with 10-second audio recording and embedding averaging
- **Persistent storage** of speaker profiles with SharedPreferences

## Architecture

### Core Components

1. **AudioProcessor** (`features/audio/AudioProcessor.java`)
   - Handles FRILL model inference
   - Generates 192-dim speaker embeddings
   - Processes registration windows and averages embeddings
   - Performs speaker matching via cosine similarity

2. **AudioRecognitionManager** (`features/audio/AudioRecognitionManager.java`)
   - Main coordinator for speaker recognition
   - Handles real-time audio capture with sliding windows
   - Manages speaker profiles and registration flows
   - Provides callback interface for UI integration

3. **SpeakerRepository** (`features/audio/SpeakerRepository.java`)
   - Data persistence layer for speaker profiles
   - Uses SharedPreferences with JSON serialization
   - Stores similarity thresholds and system settings

4. **SpeakerRecognitionIntegration** (`features/audio/SpeakerRecognitionIntegration.java`)
   - High-level integration helper
   - Simplified API for common operations
   - UI-friendly callback interface

### Configuration

**ModelConfig.SpeakerRecognition** (`ml/ModelConfig.java`):
```java
MODEL_FILE = "frill.tflite"
SAMPLE_RATE = 16000
WINDOW_SIZE_MS = 975  // ~0.975 seconds
HOP_SIZE_MS = 240     // 240ms overlap
OUTPUT_SIZE = 192     // FRILL embedding dimension
DEFAULT_SIMILARITY_THRESHOLD = 0.75f
REGISTRATION_DURATION_SEC = 10
```

## Usage

### Basic Integration

```java
// Initialize speaker recognition
SpeakerRecognitionIntegration speakerRecognition = 
    new SpeakerRecognitionIntegration(this);

// Set up event listener
speakerRecognition.setEventListener(new SpeakerRecognitionIntegration.SpeakerEventListener() {
    @Override
    public void onSpeakerIdentified(String speakerName, float confidence) {
        Log.d("Speaker", "Identified: " + speakerName + " (" + confidence + ")");
    }
    
    @Override
    public void onSpeakerRegistrationComplete(String speakerName, boolean success) {
        Log.d("Speaker", "Registration " + (success ? "successful" : "failed") + ": " + speakerName);
    }
    
    @Override
    public void onSpeakerRegistrationProgress(int progress, int total) {
        Log.d("Speaker", "Registration progress: " + progress + "/" + total);
    }
    
    @Override
    public void onErrorOccurred(String error) {
        Log.e("Speaker", "Error: " + error);
    }
});

// Initialize and start
if (speakerRecognition.initialize()) {
    speakerRecognition.startListening();
}
```

### Speaker Registration

```java
// Start recording a speaker profile
speakerRecognition.registerSpeaker("John Doe");

// The system will automatically:
// 1. Record audio for 10 seconds
// 2. Extract embeddings from multiple windows
// 3. Average the embeddings into a single profile
// 4. Save the profile to persistent storage
// 5. Trigger onSpeakerRegistrationComplete callback
```

### Speaker Management

```java
// Get all registered speakers
String[] speakers = speakerRecognition.getRegisteredSpeakers();

// Delete a speaker
speakerRecognition.deleteSpeaker("John Doe");

// Adjust similarity threshold
speakerRecognition.setSimilarityThreshold(0.8f);

// Get system statistics
String stats = speakerRecognition.getSystemStats();
```

### Advanced Usage with AudioRecognitionManager

```java
// Direct access to the manager
AudioRecognitionManager manager = new AudioRecognitionManager(this);

// Set up detailed callback
manager.setSpeakerRecognitionCallback(new AudioRecognitionManager.SpeakerRecognitionCallback() {
    @Override
    public void onSpeakerMatched(String name, float similarity) {
        // Handle speaker match with detailed similarity score
    }
    
    @Override
    public void onNoSpeakerDetected() {
        // Handle when no speaker is detected
    }
    
    @Override
    public void onSpeakerRegistered(String name, boolean success) {
        // Handle registration completion
    }
    
    @Override
    public void onRegistrationProgress(int windowsProcessed, int totalWindows) {
        // Handle real-time registration progress
    }
    
    @Override
    public void onError(String error) {
        // Handle errors
    }
    
    @Override
    public void onPermissionRequired() {
        // Handle permission requests
    }
});

// Initialize and start
manager.initialize();
manager.start();
```

## Technical Details

### Audio Processing Pipeline

1. **Audio Capture**: 16kHz mono audio using AudioRecord
2. **Sliding Window**: 975ms windows with 240ms hop size
3. **Normalization**: Audio samples normalized to [-1, 1] range
4. **FRILL Inference**: Extract 192-dim embeddings per window
5. **Similarity Matching**: Cosine similarity against registered profiles

### Registration Process

1. **Data Collection**: Record audio for 10 seconds
2. **Window Extraction**: Extract overlapping 975ms windows
3. **Embedding Generation**: Generate embeddings for each window
4. **Quality Filtering**: Only use valid embeddings
5. **Averaging**: Average all embeddings into single profile
6. **Storage**: Persist to SharedPreferences as JSON

### Real-Time Recognition

1. **Continuous Processing**: Process audio in 240ms intervals
2. **Window Sliding**: Maintain circular buffer for efficiency
3. **Background Threading**: Audio processing on dedicated thread
4. **Threshold Comparison**: Match if similarity > threshold (default 0.75)
5. **Callback Delivery**: Notify UI thread of matches

## Permissions

The feature requires audio recording permission:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

The system automatically requests permissions when needed.

## Performance Considerations

### Memory Usage
- Audio buffer: ~62KB (975ms + 240ms at 16kHz)
- FRILL model: ~1-2MB (depending on quantization)
- Per speaker profile: ~768 bytes (192 floats)

### CPU Usage
- FRILL inference: ~10-20ms per window on modern devices
- Audio processing: <1ms per hop
- Background threading prevents UI blocking

### Battery Impact
- Continuous microphone access
- Regular ML inference
- Recommend user control for enabling/disabling

## Data Storage

Speaker profiles are stored in SharedPreferences:
- **Preference name**: "speaker_profiles"
- **Format**: JSON serialization of Recognition objects
- **Backup**: Automatically backed up with app data
- **Size**: ~1KB per speaker profile

## Troubleshooting

### Common Issues

1. **Permission Denied**
   - Ensure RECORD_AUDIO permission is granted
   - Check app settings if automatic request fails

2. **Model Loading Failed**
   - Verify `frill.tflite` exists in `assets/` folder
   - Check available memory for model loading

3. **Poor Recognition Accuracy**
   - Ensure quiet environment during registration
   - Increase similarity threshold for stricter matching
   - Re-register speakers with better audio quality

4. **Audio Capture Issues**
   - Check microphone availability
   - Verify no other apps are using microphone
   - Test with different audio sources

### Debug Logging

Enable debug logging to troubleshoot issues:
```java
adb shell setprop log.tag.AudioRecognitionManager VERBOSE
adb shell setprop log.tag.AudioProcessor VERBOSE
adb shell setprop log.tag.SpeakerRepository VERBOSE
```

## Integration Examples

### In MainActivity

```java
public class MainActivity extends AppCompatActivity {
    private SpeakerRecognitionIntegration speakerRecognition;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize speaker recognition
        speakerRecognition = new SpeakerRecognitionIntegration(this);
        speakerRecognition.setEventListener(new MySpeakerListener());
        
        if (speakerRecognition.initialize()) {
            speakerRecognition.startListening();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (speakerRecognition != null) {
            speakerRecognition.cleanup();
        }
        super.onDestroy();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (speakerRecognition != null) {
            speakerRecognition.onPermissionResult(requestCode, grantResults);
        }
    }
}
```

## Future Enhancements

1. **Multi-Speaker Support**: Detect multiple speakers simultaneously
2. **Speaker Verification**: Continuous verification during conversation
3. **Voice Activity Detection**: Only process when speech is detected
4. **Cloud Sync**: Synchronize speaker profiles across devices
5. **Speaker Adaptation**: Continuously improve profiles over time

## API Reference

### SpeakerRecognitionIntegration

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize the speaker recognition system |
| `startListening()` | Start listening for speakers |
| `stopListening()` | Stop listening for speakers |
| `registerSpeaker(name)` | Start speaker registration process |
| `deleteSpeaker(name)` | Delete a registered speaker |
| `getRegisteredSpeakers()` | Get array of registered speaker names |
| `setSimilarityThreshold(threshold)` | Set matching threshold (0.0-1.0) |
| `getSimilarityThreshold()` | Get current matching threshold |
| `isListening()` | Check if actively listening |
| `getSystemStats()` | Get system statistics |
| `cleanup()` | Clean up resources |

### AudioRecognitionManager

Provides lower-level access to speaker recognition functionality with more detailed callbacks and control options.

### SpeakerRepository

Handles data persistence with support for:
- Save/load speaker profiles
- Similarity threshold storage
- System settings management
- Bulk operations (delete all, etc.)

---

This implementation provides a complete, production-ready speaker recognition system that integrates seamlessly with the existing face recognition architecture. 