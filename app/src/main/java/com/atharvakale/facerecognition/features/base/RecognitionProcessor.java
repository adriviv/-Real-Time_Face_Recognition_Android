package com.atharvakale.facerecognition.features.base;

/**
 * Base interface for recognition processors
 * Defines common contract for different types of recognition (Face, Audio, etc.)
 */
public interface RecognitionProcessor<InputType, OutputType> {
    
    /**
     * Process input data for recognition
     * @param input The input data to process
     * @param callback Callback to handle results
     */
    void processForRecognition(InputType input, ProcessingCallback<OutputType> callback);
    
    /**
     * Process input data for registration/training
     * @param input The input data to process
     * @param callback Callback to handle results
     */
    void processForRegistration(InputType input, ProcessingCallback<OutputType> callback);
    
    /**
     * Set the similarity threshold for recognition
     * @param threshold Similarity threshold (0.0 to 1.0)
     */
    void setSimilarityThreshold(float threshold);
    
    /**
     * Get the current similarity threshold
     * @return Current similarity threshold
     */
    float getSimilarityThreshold();
    
    /**
     * Enable or disable developer mode for debugging
     * @param enabled true to enable developer mode
     */
    void setDeveloperMode(boolean enabled);
    
    /**
     * Check if developer mode is enabled
     * @return true if developer mode is enabled
     */
    boolean isDeveloperMode();
    
    /**
     * Callback interface for processing results
     */
    interface ProcessingCallback<T> {
        void onSuccess(T result);
        void onError(String error);
        void onNoDataDetected();
    }
} 