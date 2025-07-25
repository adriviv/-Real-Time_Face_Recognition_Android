package com.atharvakale.facerecognition.ml;

/**
 * Configuration class for ML models
 * Contains model-specific parameters and settings
 */
public class ModelConfig {
    
    // Face Recognition Model Configuration
    public static class FaceRecognition {
        public static final String MODEL_FILE = "mobile_face_net.tflite";
        public static final String MODEL_KEY = "face_recognition";
        public static final int INPUT_SIZE = 112;
        public static final int OUTPUT_SIZE = 192;
        public static final float IMAGE_MEAN = 128.0f;
        public static final float IMAGE_STD = 128.0f;
        public static final boolean IS_QUANTIZED = false;
        public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.65f;
    }
    
    // FRILL Speaker Recognition Model Configuration
    public static class SpeakerRecognition {
        public static final String MODEL_FILE = "frill.tflite";
        public static final String MODEL_KEY = "speaker_recognition";
        public static final int SAMPLE_RATE = 16000;
        public static final int WINDOW_SIZE_MS = 2000; // 2 seconds (32000 samples at 16kHz)
        public static final int WINDOW_SIZE_SAMPLES = (SAMPLE_RATE * WINDOW_SIZE_MS) / 1000; // 32000 samples
        public static final int HOP_SIZE_MS = 500; // 500ms overlap for smoother processing
        public static final int HOP_SIZE_SAMPLES = (SAMPLE_RATE * HOP_SIZE_MS) / 1000; // 8000 samples
        public static final int OUTPUT_SIZE = 2048; // FRILL embedding dimension (from documentation)
                    public static final float DEFAULT_SIMILARITY_THRESHOLD = 0.55f;
        public static final int REGISTRATION_DURATION_SEC = 10; // 10 seconds for enrollment
        public static final int MIN_REGISTRATION_WINDOWS = 3; // Minimum windows for enrollment (fewer needed with 2s windows)
    }
    
    // Future: Other model configurations can be added here
    
    /**
     * Get model configuration by type
     */
    public static String getModelFile(String modelType) {
        switch (modelType) {
            case "face":
                return FaceRecognition.MODEL_FILE;
            case "speaker":
                return SpeakerRecognition.MODEL_FILE;
            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }
    
    public static String getModelKey(String modelType) {
        switch (modelType) {
            case "face":
                return FaceRecognition.MODEL_KEY;
            case "speaker":
                return SpeakerRecognition.MODEL_KEY;
            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }
} 