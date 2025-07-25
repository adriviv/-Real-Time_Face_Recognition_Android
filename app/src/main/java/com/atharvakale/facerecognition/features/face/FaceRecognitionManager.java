package com.atharvakale.facerecognition.features.face;

import android.app.Activity;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import com.atharvakale.facerecognition.SimilarityClassifier;
import com.atharvakale.facerecognition.data.PreferencesRepository;
import com.atharvakale.facerecognition.ml.MLModelManager;
import com.atharvakale.facerecognition.ml.ModelConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * Main coordinator for face recognition functionality
 * Manages face data, processing, and provides clean interface for UI
 */
public class FaceRecognitionManager {
    private MLModelManager modelManager;
    private FaceProcessor faceProcessor;
    private PreferencesRepository repository;
    private Map<String, SimilarityClassifier.Recognition> registeredFaces;
    
    private boolean isRecognitionMode = true;
    private float[][] currentEmbeddings;
    
    public interface FaceRecognitionCallback {
        void onFaceRecognized(String name, float distance, boolean isKnown);
        void onNoFaceDetected();
        void onFaceReadyForRegistration(Bitmap faceBitmap);
        void onFaceRegistered(String name, boolean success);
        void onError(String error);
    }
    
    public FaceRecognitionManager(Activity context) {
        // Initialize ML infrastructure
        this.modelManager = new MLModelManager(context);
        
        // Load face recognition model
        boolean modelLoaded = modelManager.loadModel(
            ModelConfig.FaceRecognition.MODEL_FILE,
            ModelConfig.FaceRecognition.MODEL_KEY
        );
        
        if (!modelLoaded) {
            throw new RuntimeException("Failed to load face recognition model");
        }
        
        // Initialize face processor
        this.faceProcessor = new FaceProcessor(modelManager);
        
        // Initialize data repository
        this.repository = new PreferencesRepository(
            context, 
            "HashMap", 
            "map", 
            ModelConfig.FaceRecognition.OUTPUT_SIZE
        );
        
        // Load registered faces
        this.registeredFaces = new HashMap<>(repository.loadAll());
        
        // Load similarity threshold
        float threshold = repository.loadSimilarityThreshold(
            ModelConfig.FaceRecognition.DEFAULT_SIMILARITY_THRESHOLD
        );
        faceProcessor.setSimilarityThreshold(threshold);
    }
    
    /**
     * Process camera frame for face recognition or registration
     */
    public void processFrame(@NonNull ImageProxy imageProxy, boolean flipX, FaceRecognitionCallback callback) {
        
        FaceProcessor.FaceProcessingCallback processingCallback = new FaceProcessor.FaceProcessingCallback() {
            @Override
            public void onFaceDetected(String name, float distance, boolean isRecognized) {
                callback.onFaceRecognized(name, distance, isRecognized);
            }
            
            @Override
            public void onNoFaceDetected() {
                if (registeredFaces.isEmpty()) {
                    callback.onFaceRecognized("Add Face", Float.MAX_VALUE, false);
                } else {
                    callback.onNoFaceDetected();
                }
            }
            
            @Override
            public void onFaceForPreview(Bitmap faceBitmap) {
                // Generate embeddings for potential registration
                currentEmbeddings = faceProcessor.generateEmbeddings(faceBitmap);
                callback.onFaceReadyForRegistration(faceBitmap);
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        };
        
        if (isRecognitionMode) {
            faceProcessor.processImageForRecognition(imageProxy, registeredFaces, flipX, processingCallback);
        } else {
            faceProcessor.processImageForPreview(imageProxy, flipX, processingCallback);
        }
    }
    
    /**
     * Register a new face with the given name
     */
    public void registerFace(String name, FaceRecognitionCallback callback) {
        if (currentEmbeddings == null) {
            callback.onFaceRegistered(name, false);
            return;
        }
        
        // Create recognition object
        SimilarityClassifier.Recognition recognition = new SimilarityClassifier.Recognition("0", "", -1f);
        recognition.setExtra(currentEmbeddings);
        
        // Save to registered faces
        registeredFaces.put(name, recognition);
        
        // Persist to storage
        boolean success = repository.save(name, recognition);
        callback.onFaceRegistered(name, success);
        
        // Clear current embeddings
        currentEmbeddings = null;
    }
    
    /**
     * Delete a registered face
     */
    public boolean deleteFace(String name) {
        registeredFaces.remove(name);
        return repository.delete(name);
    }
    
    /**
     * Delete multiple registered faces
     */
    public boolean deleteFaces(String[] names) {
        for (String name : names) {
            registeredFaces.remove(name);
        }
        return repository.deleteAll(java.util.Arrays.asList(names));
    }
    
    /**
     * Clear all registered faces
     */
    public boolean clearAllFaces() {
        registeredFaces.clear();
        return repository.clear();
    }
    
    /**
     * Save all current faces to persistent storage
     */
    public boolean saveAllFaces() {
        return repository.saveAll(registeredFaces);
    }
    
    /**
     * Reload faces from persistent storage
     */
    public void reloadFaces() {
        registeredFaces.putAll(repository.loadAll());
    }
    
    /**
     * Get all registered face names
     */
    public String[] getRegisteredFaceNames() {
        return registeredFaces.keySet().toArray(new String[0]);
    }
    
    /**
     * Get count of registered faces
     */
    public int getRegisteredFaceCount() {
        return registeredFaces.size();
    }
    
    /**
     * Check if any faces are registered
     */
    public boolean hasRegisteredFaces() {
        return !registeredFaces.isEmpty();
    }
    
    /**
     * Set recognition mode (true) or registration mode (false)
     */
    public void setRecognitionMode(boolean recognitionMode) {
        this.isRecognitionMode = recognitionMode;
    }
    
    /**
     * Check if in recognition mode
     */
    public boolean isRecognitionMode() {
        return isRecognitionMode;
    }
    
    /**
     * Set similarity threshold for recognition
     */
    public void setSimilarityThreshold(float threshold) {
        faceProcessor.setSimilarityThreshold(threshold);
        repository.saveSimilarityThreshold(threshold);
    }
    
    /**
     * Get current similarity threshold
     */
    public float getSimilarityThreshold() {
        return faceProcessor.getSimilarityThreshold();
    }
    
    /**
     * Enable/disable developer mode
     */
    public void setDeveloperMode(boolean enabled) {
        faceProcessor.setDeveloperMode(enabled);
    }
    
    /**
     * Check if developer mode is enabled
     */
    public boolean isDeveloperMode() {
        return faceProcessor.isDeveloperMode();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (modelManager != null) {
            modelManager.cleanup();
        }
    }
} 