package com.atharvakale.facerecognition.features.base;

/**
 * Base interface for all feature managers
 * Provides common contract for different recognition features
 */
public interface FeatureManager {
    
    /**
     * Initialize the feature manager
     * @return true if initialization successful, false otherwise
     */
    boolean initialize();
    
    /**
     * Start the feature processing
     * @return true if started successfully, false otherwise
     */
    boolean start();
    
    /**
     * Stop the feature processing
     */
    void stop();
    
    /**
     * Pause the feature processing
     */
    void pause();
    
    /**
     * Resume the feature processing
     */
    void resume();
    
    /**
     * Check if the feature is currently active
     * @return true if active, false otherwise
     */
    boolean isActive();
    
    /**
     * Get the feature name
     * @return Feature name identifier
     */
    String getFeatureName();
    
    /**
     * Get the feature version
     * @return Feature version string
     */
    String getFeatureVersion();
    
    /**
     * Cleanup resources and shutdown
     */
    void cleanup();
    
    /**
     * Check if the feature is supported on current device
     * @return true if supported, false otherwise
     */
    boolean isSupported();
} 