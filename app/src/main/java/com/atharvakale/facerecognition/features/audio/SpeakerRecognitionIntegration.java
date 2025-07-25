package com.atharvakale.facerecognition.features.audio;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

/**
 * Integration helper class demonstrating how to use SpeakerRecognitionManager
 * in the main application. This provides a simple interface for common operations.
 */
public class SpeakerRecognitionIntegration {
    private static final String TAG = "SpeakerIntegration";
    
    private AudioRecognitionManager speakerManager;
    private Activity context;
    private boolean isListening = false;
    
    public interface SpeakerEventListener {
        void onSpeakerIdentified(String speakerName, float confidence);
        void onSpeakerRegistrationComplete(String speakerName, boolean success);
        void onSpeakerRegistrationProgress(int progress, int total);
        void onErrorOccurred(String error);
    }
    
    private SpeakerEventListener eventListener;
    
    public SpeakerRecognitionIntegration(Activity context) {
        this.context = context;
        this.speakerManager = new AudioRecognitionManager(context);
        setupCallbacks();
    }
    
    /**
     * Initialize the speaker recognition system
     * @return true if initialization successful
     */
    public boolean initialize() {
        boolean success = speakerManager.initialize();
        if (success) {
            Log.d(TAG, "Speaker recognition system initialized");
        } else {
            Log.e(TAG, "Failed to initialize speaker recognition system");
        }
        return success;
    }
    
    /**
     * Start listening for speakers
     * @return true if started successfully
     */
    public boolean startListening() {
        if (speakerManager.start()) {
            isListening = true;
            Log.d(TAG, "Started listening for speakers");
            return true;
        } else {
            Log.e(TAG, "Failed to start speaker listening");
            return false;
        }
    }
    
    /**
     * Stop listening for speakers
     */
    public void stopListening() {
        speakerManager.stop();
        isListening = false;
        Log.d(TAG, "Stopped listening for speakers");
    }
    
    /**
     * Register a new speaker
     * @param speakerName Name of the speaker to register
     */
    public void registerSpeaker(String speakerName) {
        if (speakerName == null || speakerName.trim().isEmpty()) {
            showError("Please enter a valid speaker name");
            return;
        }
        
        speakerManager.startSpeakerRegistration(speakerName);
        showMessage("Recording speaker profile for: " + speakerName + ". Please speak for 10 seconds.");
        Log.d(TAG, "Started registration for speaker: " + speakerName);
    }
    
    /**
     * Cancel ongoing speaker registration
     */
    public void cancelRegistration() {
        speakerManager.stopSpeakerRegistration();
        Log.d(TAG, "Speaker registration cancelled");
    }
    
    /**
     * Delete a registered speaker
     * @param speakerName Name of speaker to delete
     * @return true if deletion successful
     */
    public boolean deleteSpeaker(String speakerName) {
        boolean success = speakerManager.deleteSpeaker(speakerName);
        if (success) {
            showMessage("Speaker '" + speakerName + "' deleted successfully");
            Log.d(TAG, "Deleted speaker: " + speakerName);
        } else {
            showError("Failed to delete speaker: " + speakerName);
        }
        return success;
    }
    
    /**
     * Get list of all registered speakers
     * @return Array of registered speaker names
     */
    public String[] getRegisteredSpeakers() {
        return speakerManager.getRegisteredSpeakerNames();
    }
    
    /**
     * Set the similarity threshold for speaker matching
     * @param threshold Threshold value (0.0 - 1.0, default 0.75)
     */
    public void setSimilarityThreshold(float threshold) {
        speakerManager.setSimilarityThreshold(threshold);
        Log.d(TAG, "Similarity threshold set to: " + threshold);
    }
    
    /**
     * Get current similarity threshold
     * @return Current threshold value
     */
    public float getSimilarityThreshold() {
        return speakerManager.getSimilarityThreshold();
    }
    
    /**
     * Check if currently listening for speakers
     * @return true if listening
     */
    public boolean isListening() {
        return isListening && speakerManager.isActive();
    }
    
    /**
     * Get statistics about the speaker recognition system
     * @return Statistics string
     */
    public String getSystemStats() {
        String stats = speakerManager.getStats();
        String[] speakers = getRegisteredSpeakers();
        return stats + ", Active: " + isListening() + ", Speakers: [" + String.join(", ", speakers) + "]";
    }
    
    /**
     * Set event listener for speaker recognition events
     * @param listener Event listener implementation
     */
    public void setEventListener(SpeakerEventListener listener) {
        this.eventListener = listener;
    }
    
    /**
     * Handle permission request results
     * @param requestCode Request code from permission request
     * @param grantResults Results array from permission request
     */
    public void onPermissionResult(int requestCode, int[] grantResults) {
        speakerManager.onPermissionResult(requestCode, grantResults);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stopListening();
        speakerManager.cleanup();
        Log.d(TAG, "Speaker recognition integration cleaned up");
    }
    
    private void setupCallbacks() {
        speakerManager.setSpeakerRecognitionCallback(new AudioRecognitionManager.SpeakerRecognitionCallback() {
            @Override
            public void onSpeakerMatched(String name, float similarity) {
                Log.d(TAG, "Speaker matched: " + name + " (confidence: " + similarity + ")");
                if (eventListener != null) {
                    eventListener.onSpeakerIdentified(name, similarity);
                }
            }
            
            @Override
            public void onNoSpeakerDetected() {
                // Optionally handle this case - usually too frequent for UI updates
            }
            
            @Override
            public void onSpeakerRegistered(String name, boolean success) {
                if (success) {
                    showMessage("Speaker '" + name + "' registered successfully!");
                    Log.d(TAG, "Speaker registration successful: " + name);
                } else {
                    showError("Failed to register speaker: " + name);
                    Log.e(TAG, "Speaker registration failed: " + name);
                }
                
                if (eventListener != null) {
                    eventListener.onSpeakerRegistrationComplete(name, success);
                }
            }
            
            @Override
            public void onRegistrationProgress(int windowsProcessed, int totalWindows) {
                int progressPercent = (windowsProcessed * 100) / totalWindows;
                Log.d(TAG, "Registration progress: " + progressPercent + "%");
                
                if (eventListener != null) {
                    eventListener.onSpeakerRegistrationProgress(windowsProcessed, totalWindows);
                }
            }
            
            @Override
            public void onError(String error) {
                showError("Speaker recognition error: " + error);
                Log.e(TAG, "Speaker recognition error: " + error);
                
                if (eventListener != null) {
                    eventListener.onErrorOccurred(error);
                }
            }
            
            @Override
            public void onPermissionRequired() {
                showError("Audio permission required for speaker recognition");
                Log.w(TAG, "Audio permission required");
            }
        });
    }
    
    private void showMessage(String message) {
        context.runOnUiThread(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    private void showError(String error) {
        context.runOnUiThread(() -> {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show();
        });
    }
    
    // Example usage methods for UI integration
    
    /**
     * Example: Toggle speaker listening on/off
     */
    public void toggleListening() {
        if (isListening()) {
            stopListening();
        } else {
            startListening();
        }
    }
    
    /**
     * Example: Quick speaker registration with input dialog
     * This would typically be called from a UI button click
     */
    public void showSpeakerRegistrationDialog() {
        // In a real implementation, this would show an input dialog
        // For now, just demonstrate the registration flow
        String exampleName = "Speaker_" + System.currentTimeMillis();
        registerSpeaker(exampleName);
    }
    
    /**
     * Example: Clear all registered speakers (with confirmation)
     */
    public void clearAllSpeakers() {
        String[] speakers = getRegisteredSpeakers();
        for (String speaker : speakers) {
            deleteSpeaker(speaker);
        }
        showMessage("All speakers cleared");
    }
} 