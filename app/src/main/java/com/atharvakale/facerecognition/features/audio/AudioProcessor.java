package com.atharvakale.facerecognition.features.audio;

import android.util.Log;
import com.atharvakale.facerecognition.SimilarityClassifier;
import com.atharvakale.facerecognition.ml.MLModelManager;
import com.atharvakale.facerecognition.ml.ModelConfig;
import com.atharvakale.facerecognition.ml.TFLiteProcessor;
import org.tensorflow.lite.Interpreter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * AudioProcessor handles FRILL model inference and speaker embedding generation
 * Processes audio windows to extract 192-dimensional speaker embeddings
 */
public class AudioProcessor {
    private static final String TAG = "AudioProcessor";
    
    private MLModelManager modelManager;
    private float similarityThreshold = ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD;
    private boolean isInitialized = false;
    private int lastInputSize = -1; // Cache to avoid unnecessary tensor resizing
    
    public interface AudioProcessingCallback {
        void onSpeakerDetected(String name, float similarity, boolean isRecognized);
        void onNoSpeakerDetected();
        void onEmbeddingGenerated(float[] embedding);
        void onError(String error);
    }
    
    public AudioProcessor(MLModelManager modelManager) {
        this.modelManager = modelManager;
        initialize();
    }
    
    private synchronized boolean initialize() {
        try {
            Log.d(TAG, "Starting AudioProcessor initialization...");
            
            // Reset cache on initialization
            lastInputSize = -1;
            
            // Load FRILL model
            Log.d(TAG, "Loading FRILL model: " + ModelConfig.SpeakerRecognition.MODEL_FILE);
            boolean modelLoaded = modelManager.loadModel(
                ModelConfig.SpeakerRecognition.MODEL_FILE,
                ModelConfig.SpeakerRecognition.MODEL_KEY
            );
            
            if (!modelLoaded) {
                Log.e(TAG, "Failed to load FRILL model from: " + ModelConfig.SpeakerRecognition.MODEL_FILE);
                Log.e(TAG, "Please ensure the frill.tflite file exists in app/src/main/assets/");
                return false;
            }
            
            Log.d(TAG, "FRILL model loaded successfully");
            
            // Verify model is actually loaded and inspect its input/output shape
            Interpreter interpreter = modelManager.getModel(ModelConfig.SpeakerRecognition.MODEL_KEY);
            if (interpreter == null) {
                Log.e(TAG, "Model loaded but not accessible");
                return false;
            }
            
            // Inspect model input and output tensors
            try {
                Log.d(TAG, "Inspecting FRILL model tensors...");
                Log.d(TAG, "Input tensor count: " + interpreter.getInputTensorCount());
                Log.d(TAG, "Output tensor count: " + interpreter.getOutputTensorCount());
                
                if (interpreter.getInputTensorCount() > 0) {
                    int[] inputShape = interpreter.getInputTensor(0).shape();
                    Log.d(TAG, "Input tensor 0 shape: " + java.util.Arrays.toString(inputShape));
                    Log.d(TAG, "Input tensor 0 dataType: " + interpreter.getInputTensor(0).dataType());
                    Log.d(TAG, "Input tensor 0 name: " + interpreter.getInputTensor(0).name());
                }
                
                if (interpreter.getOutputTensorCount() > 0) {
                    int[] outputShape = interpreter.getOutputTensor(0).shape();
                    Log.d(TAG, "Output tensor 0 shape: " + java.util.Arrays.toString(outputShape));
                    Log.d(TAG, "Output tensor 0 dataType: " + interpreter.getOutputTensor(0).dataType());
                    Log.d(TAG, "Output tensor 0 name: " + interpreter.getOutputTensor(0).name());
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Could not inspect model tensors", e);
            }
            
            isInitialized = true;
            Log.d(TAG, "AudioProcessor initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioProcessor", e);
            return false;
        }
    }
    
    /**
     * Generate speaker embedding from audio window
     * @param audioSamples Normalized audio samples for one window
     * @return 192-dimensional speaker embedding or null if error
     */
    public synchronized float[] generateEmbedding(float[] audioSamples) {
        if (!isInitialized) {
            Log.e(TAG, "AudioProcessor not initialized");
            return null;
        }
        
        try {
            Interpreter interpreter = modelManager.getModel(ModelConfig.SpeakerRecognition.MODEL_KEY);
            if (interpreter == null) {
                Log.e(TAG, "FRILL model not loaded");
                return null;
            }
            
            // Prepare input following FRILL documentation format
            // Input should be [1, audio_length] where audio_length is variable
            Log.v(TAG, "Preparing FRILL input for " + audioSamples.length + " audio samples");
            
            // Only resize input tensor if the size has changed (to avoid memory corruption)
            if (lastInputSize != audioSamples.length) {
                try {
                    interpreter.resizeInput(0, new int[]{1, audioSamples.length});
                    interpreter.allocateTensors();
                    lastInputSize = audioSamples.length;
                    Log.v(TAG, "Resized input tensor to [1, " + audioSamples.length + "]");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to resize input tensor", e);
                    return null;
                }
            }
            
            // Create input tensor as [1, audio_length] and copy data
            float[][] inputTensor = new float[1][audioSamples.length];
            System.arraycopy(audioSamples, 0, inputTensor[0], 0, audioSamples.length);
            
            // Prepare output tensor (should be [1, 2048] based on documentation)
            float[][] output = new float[1][ModelConfig.SpeakerRecognition.OUTPUT_SIZE];
            
            // Run inference using Java TensorFlow Lite API
            try {
                // Use MLModelManager for inference but with our custom input/output
                Object[] inputArray = {inputTensor};
                Map<Integer, Object> outputMap = new HashMap<>();
                outputMap.put(0, output);
                
                boolean success = modelManager.runInference(
                    ModelConfig.SpeakerRecognition.MODEL_KEY, 
                    inputArray, 
                    outputMap
                );
                
                if (success) {
                    Log.v(TAG, "FRILL inference successful, output shape: [1, " + output[0].length + "]");
                    return output[0]; // Return the 2048-dim embedding
                } else {
                    Log.e(TAG, "Failed to run FRILL inference");
                    return null;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to run FRILL inference", e);
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating embedding", e);
            return null;
        }
    }
    
    /**
     * Process audio window for speaker recognition
     * @param audioSamples Normalized audio samples
     * @param registeredSpeakers Map of registered speaker profiles
     * @param callback Callback for processing results
     */
    public void processAudioForRecognition(float[] audioSamples, 
                                          Map<String, SimilarityClassifier.Recognition> registeredSpeakers,
                                          AudioProcessingCallback callback) {
        
        Log.v(TAG, "=== SPEAKER RECOGNITION STARTED ===");
        Log.v(TAG, "Processing audio samples: " + audioSamples.length + " samples");
        Log.v(TAG, "Registered speakers count: " + registeredSpeakers.size());
        Log.v(TAG, "Similarity threshold: " + similarityThreshold);
        
        // Generate embedding for current audio window
        float[] currentEmbedding = generateEmbedding(audioSamples);
        if (currentEmbedding == null) {
            Log.e(TAG, "❌ Failed to generate embedding for recognition");
            callback.onError("Failed to generate embedding");
            return;
        }
        
        Log.v(TAG, "✅ Generated embedding for recognition: " + currentEmbedding.length + " dimensions");
        
        // If no registered speakers, indicate no speaker detected
        if (registeredSpeakers.isEmpty()) {
            Log.v(TAG, "❌ No registered speakers found - cannot perform recognition");
            callback.onNoSpeakerDetected();
            return;
        }
        
        // Find best match among registered speakers
        String bestMatch = null;
        float bestSimilarity = 0.0f;
        
        Log.v(TAG, "🔍 Comparing against " + registeredSpeakers.size() + " registered speakers:");
        
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registeredSpeakers.entrySet()) {
            String speakerName = entry.getKey();
            SimilarityClassifier.Recognition recognition = entry.getValue();
            
            Log.v(TAG, "  - Checking speaker: " + speakerName);
            
            // Get stored embedding
            float[][] storedEmbedding = (float[][]) recognition.getExtra();
            if (storedEmbedding == null || storedEmbedding.length == 0) {
                Log.w(TAG, "  ❌ Speaker " + speakerName + " has no stored embedding");
                continue;
            }
            
            Log.v(TAG, "  - Speaker " + speakerName + " has embedding: " + storedEmbedding[0].length + " dimensions");
            
            // Calculate cosine similarity
            float similarity = TFLiteProcessor.calculateCosineSimilarity(
                currentEmbedding, 
                storedEmbedding[0]
            );
            
            Log.v(TAG, "  - Similarity with " + speakerName + ": " + String.format("%.3f", similarity));
            
            // Update best match if this is better
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = speakerName;
                Log.v(TAG, "  ✅ New best match: " + speakerName + " (similarity: " + String.format("%.3f", similarity) + ")");
            }
        }
        
        // Check if best match exceeds threshold
        Log.v(TAG, "🎯 Best match: " + bestMatch + " with similarity: " + String.format("%.3f", bestSimilarity));
        Log.v(TAG, "🎯 Threshold: " + String.format("%.3f", similarityThreshold));
        
        if (bestMatch != null && bestSimilarity >= similarityThreshold) {
            Log.d(TAG, "🎉 SPEAKER RECOGNIZED: " + bestMatch + " (similarity: " + String.format("%.3f", bestSimilarity) + ")");
            callback.onSpeakerDetected(bestMatch, bestSimilarity, true);
        } else {
            Log.v(TAG, "❌ No speaker match above threshold");
            callback.onNoSpeakerDetected();
        }
        
        Log.v(TAG, "=== SPEAKER RECOGNITION FINISHED ===");
    }
    
    /**
     * Process multiple audio windows and average embeddings for registration
     * @param audioWindowsList List of audio windows for enrollment
     * @return Averaged speaker embedding or null if error
     */
    public float[] processForRegistration(float[][] audioWindowsList) {
        Log.d(TAG, "Processing registration with " + (audioWindowsList != null ? audioWindowsList.length : 0) + " windows");
        
        if (audioWindowsList == null || audioWindowsList.length < ModelConfig.SpeakerRecognition.MIN_REGISTRATION_WINDOWS) {
            Log.e(TAG, "Insufficient audio windows for registration. Required: " + ModelConfig.SpeakerRecognition.MIN_REGISTRATION_WINDOWS + 
                      ", Provided: " + (audioWindowsList != null ? audioWindowsList.length : 0));
            return null;
        }
        
        float[] averageEmbedding = new float[ModelConfig.SpeakerRecognition.OUTPUT_SIZE];
        int validWindows = 0;
        
        // Process each audio window and accumulate embeddings
        Log.d(TAG, "Processing " + audioWindowsList.length + " audio windows...");
        for (int i = 0; i < audioWindowsList.length; i++) {
            float[] audioWindow = audioWindowsList[i];
            Log.v(TAG, "Processing window " + (i+1) + "/" + audioWindowsList.length);
            
            float[] embedding = generateEmbedding(audioWindow);
            if (embedding != null) {
                for (int j = 0; j < embedding.length; j++) {
                    averageEmbedding[j] += embedding[j];
                }
                validWindows++;
                Log.v(TAG, "Valid embedding generated for window " + (i+1));
            } else {
                Log.w(TAG, "Failed to generate embedding for window " + (i+1));
            }
        }
        
        if (validWindows == 0) {
            Log.e(TAG, "No valid embeddings generated during registration");
            return null;
        }
        
        // Average the embeddings
        for (int i = 0; i < averageEmbedding.length; i++) {
            averageEmbedding[i] /= validWindows;
        }
        
        Log.d(TAG, "Registration processed " + validWindows + "/" + audioWindowsList.length + " valid windows");
        return averageEmbedding;
    }
    
    /**
     * Set similarity threshold for speaker recognition
     * @param threshold Threshold value (0.0 - 1.0)
     */
    public void setSimilarityThreshold(float threshold) {
        this.similarityThreshold = Math.max(0.0f, Math.min(1.0f, threshold));
        Log.d(TAG, "Similarity threshold set to: " + this.similarityThreshold);
    }
    
    /**
     * Get current similarity threshold
     * @return Current threshold value
     */
    public float getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    /**
     * Check if AudioProcessor is initialized
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        // Model cleanup is handled by MLModelManager
        isInitialized = false;
        Log.d(TAG, "AudioProcessor cleaned up");
    }
} 