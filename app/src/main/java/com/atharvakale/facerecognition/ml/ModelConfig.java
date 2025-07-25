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
    
    // Future: Audio Recognition Model Configuration
    public static class AudioRecognition {
        public static final String MODEL_FILE = "audio_recognition.tflite";
        public static final String MODEL_KEY = "audio_recognition";
        // Additional audio-specific configurations will be added here
    }
    
    // Future: Other model configurations can be added here
    
    /**
     * Get model configuration by type
     */
    public static String getModelFile(String modelType) {
        switch (modelType) {
            case "face":
                return FaceRecognition.MODEL_FILE;
            case "audio":
                return AudioRecognition.MODEL_FILE;
            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }
    
    public static String getModelKey(String modelType) {
        switch (modelType) {
            case "face":
                return FaceRecognition.MODEL_KEY;
            case "audio":
                return AudioRecognition.MODEL_KEY;
            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }
} 