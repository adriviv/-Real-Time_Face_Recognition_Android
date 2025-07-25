package com.atharvakale.facerecognition.features.audio;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.atharvakale.facerecognition.SimilarityClassifier;
import com.atharvakale.facerecognition.features.base.FeatureManager;
import com.atharvakale.facerecognition.ml.MLModelManager;
import com.atharvakale.facerecognition.ml.ModelConfig;
import com.atharvakale.facerecognition.ml.TFLiteProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Speaker Recognition Manager - Real-time speaker matching using FRILL model
 * Handles audio capture, processing, and speaker profile management
 */
public class AudioRecognitionManager implements FeatureManager {
    private static final String TAG = "AudioRecognitionManager";
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 200;
    
    private Activity context;
    private MLModelManager modelManager;
    private AudioProcessor audioProcessor;
    private SpeakerRepository repository;
    private Map<String, SimilarityClassifier.Recognition> registeredSpeakers;
    
    // Audio capture components
    private AudioRecord audioRecord;
    private HandlerThread audioThread;
    private Handler audioHandler;
    private volatile boolean isRecording = false;
    private boolean isInitialized = false;
    private boolean isActive = false;
    private final Object audioRecordLock = new Object();
    
    // Registration state
    private boolean isRegistrationMode = false;
    private List<float[]> registrationWindows;
    private long registrationStartTime;
    private String pendingRegistrationName;
    
    // Audio processing configuration
    private final int sampleRate = ModelConfig.SpeakerRecognition.SAMPLE_RATE;
    private final int windowSizeSamples = ModelConfig.SpeakerRecognition.WINDOW_SIZE_SAMPLES;
    private final int hopSizeSamples = ModelConfig.SpeakerRecognition.HOP_SIZE_SAMPLES;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    
    // Sliding window buffer
    private float[] audioBuffer;
    private int bufferPosition = 0;
    private final Object bufferLock = new Object();
    
    public interface SpeakerRecognitionCallback {
        void onSpeakerMatched(String name, float similarity);
        void onNoSpeakerDetected();
        void onSpeakerRegistered(String name, boolean success);
        void onRegistrationProgress(int windowsProcessed, int totalWindows);
        void onError(String error);
        void onPermissionRequired();
    }
    
    private SpeakerRecognitionCallback callback;
    
    public AudioRecognitionManager(Activity context) {
        this.context = context;
        this.modelManager = new MLModelManager(context);
        this.repository = new SpeakerRepository(context);
        this.registeredSpeakers = new HashMap<>();
        this.registrationWindows = new ArrayList<>();
    }
    
    @Override
    public boolean initialize() {
        Log.d(TAG, "AudioRecognitionManager initialization starting...");
        try {
            // Check audio permission
            if (!hasAudioPermission()) {
                Log.w(TAG, "Audio permission not granted, requesting permission");
                requestAudioPermission();
                return false;
            }
            
            Log.d(TAG, "Audio permission granted, proceeding with initialization");
            
            // Initialize audio processor
            audioProcessor = new AudioProcessor(modelManager);
            if (!audioProcessor.isInitialized()) {
                Log.e(TAG, "Failed to initialize AudioProcessor");
                Log.e(TAG, "Speaker recognition will be disabled");
                audioProcessor = null;
                // Continue initialization - app can work without speaker recognition
            }
            
            if (audioProcessor != null) {
                Log.d(TAG, "AudioProcessor initialized successfully");
                
                // Load similarity threshold - always use the updated default if none saved
                float threshold = repository.loadSimilarityThreshold(
                    ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD
                );
                audioProcessor.setSimilarityThreshold(threshold);
                Log.d(TAG, "Similarity threshold set to: " + threshold);
                
                // Force save the new default threshold if it's different
                if (threshold != ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD) {
                    Log.d(TAG, "Updating threshold to new default: " + ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD);
                    repository.saveSimilarityThreshold(ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD);
                    audioProcessor.setSimilarityThreshold(ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD);
                }
            }
            
            // Load registered speakers
            registeredSpeakers.putAll(repository.loadAll());
            Log.d(TAG, "Loaded " + registeredSpeakers.size() + " registered speakers");
            
            // Initialize audio buffer
            audioBuffer = new float[windowSizeSamples + hopSizeSamples];
            Log.d(TAG, "Audio buffer initialized with size: " + audioBuffer.length);
            
            // Initialize audio thread
            audioThread = new HandlerThread("AudioProcessingThread");
            audioThread.start();
            audioHandler = new Handler(audioThread.getLooper());
            Log.d(TAG, "Audio processing thread started");
            
        isInitialized = true;
            Log.d(TAG, "AudioRecognitionManager initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioRecognitionManager", e);
            return false;
        }
    }
    
    @Override
    public boolean start() {
        Log.d(TAG, "Starting speaker recognition...");
        
        if (!isInitialized) {
            Log.e(TAG, "AudioRecognitionManager not initialized");
            if (callback != null) {
                callback.onError("Speaker recognition not initialized");
            }
            return false;
        }
        
        if (!hasAudioPermission()) {
            Log.w(TAG, "Audio permission not available");
            if (callback != null) {
                callback.onPermissionRequired();
            }
            return false;
        }
        
        if (isActive && isRecording) {
            Log.d(TAG, "Speaker recognition already active");
            return true;
        }
        
        try {
            startAudioCapture();
        isActive = true;
            Log.d(TAG, "Speaker recognition started successfully");
        return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting speaker recognition", e);
            isActive = false;
            if (callback != null) {
                callback.onError("Failed to start speaker recognition: " + e.getMessage());
            }
            return false;
        }
    }
    
    @Override
    public void stop() {
        Log.d(TAG, "Stopping speaker recognition...");
        
        try {
            stopAudioCapture();
            isActive = false;
            Log.d(TAG, "Speaker recognition stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping speaker recognition", e);
        isActive = false;
        }
    }
    
    @Override
    public void pause() {
        if (isActive) {
            stopAudioCapture();
        isActive = false;
            Log.d(TAG, "Speaker recognition paused");
        }
    }
    
    @Override
    public void resume() {
        if (isInitialized && !isActive) {
            if (start()) {
                Log.d(TAG, "Speaker recognition resumed");
            }
        }
    }
    
    @Override
    public boolean isActive() {
        return isActive && isRecording;
    }
    
    @Override
    public String getFeatureName() {
        return "Speaker Recognition";
    }
    
    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
    
    @Override
    public void cleanup() {
        Log.d(TAG, "Cleaning up AudioRecognitionManager...");
        
        try {
        stop();
            
            if (audioThread != null) {
                audioThread.quitSafely();
                try {
                    audioThread.join(1000); // Wait max 1 second
                } catch (InterruptedException e) {
                    Log.w(TAG, "Audio thread cleanup interrupted", e);
                    Thread.currentThread().interrupt();
                }
                audioThread = null;
                audioHandler = null;
            }
            
            if (audioProcessor != null) {
                audioProcessor.cleanup();
                audioProcessor = null;
            }
            
            if (modelManager != null) {
                modelManager.cleanup();
            }
            
            registeredSpeakers.clear();
            
            isInitialized = false;
            Log.d(TAG, "AudioRecognitionManager cleaned up successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        isInitialized = false;
        }
    }
    
    @Override
    public boolean isSupported() {
        // Check microphone availability
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }
    
    /**
     * Set callback for speaker recognition events
     */
    public void setSpeakerRecognitionCallback(SpeakerRecognitionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start speaker registration process
     */
    public void startSpeakerRegistration(String speakerName) {
        Log.d(TAG, "startSpeakerRegistration called for: " + speakerName);
        Log.d(TAG, "isInitialized: " + isInitialized + ", isActive: " + isActive + ", isRecording: " + isRecording);
        
        if (!isInitialized || speakerName == null || speakerName.trim().isEmpty()) {
            Log.e(TAG, "Cannot start registration - invalid state or name");
            if (callback != null) {
                callback.onError("Invalid speaker name or not initialized");
            }
            return;
        }
        
        synchronized (bufferLock) {
            isRegistrationMode = true;
            pendingRegistrationName = speakerName.trim();
            registrationWindows.clear();
            registrationStartTime = System.currentTimeMillis();
        }
        
        Log.d(TAG, "Speaker registration started for: " + pendingRegistrationName + 
                   ", Target duration: " + ModelConfig.SpeakerRecognition.REGISTRATION_DURATION_SEC + " seconds");
    }
    
    /**
     * Stop speaker registration and save profile
     */
    public void stopSpeakerRegistration() {
        Log.d(TAG, "stopSpeakerRegistration called");
        synchronized (bufferLock) {
            if (!isRegistrationMode) {
                Log.w(TAG, "Not in registration mode, ignoring stop request");
                return;
            }
            
            Log.d(TAG, "Stopping registration with " + registrationWindows.size() + " windows");
            isRegistrationMode = false;
            
            // Process registration windows
            processRegistrationAsync();
        }
    }
    
    /**
     * Delete a registered speaker
     */
    public boolean deleteSpeaker(String name) {
        registeredSpeakers.remove(name);
        boolean success = repository.delete(name);
        Log.d(TAG, "Deleted speaker: " + name + ", success: " + success);
        return success;
    }
    
    /**
     * Get all registered speaker names
     */
    public String[] getRegisteredSpeakerNames() {
        return registeredSpeakers.keySet().toArray(new String[0]);
    }
    
    /**
     * Set similarity threshold
     */
    public void setSimilarityThreshold(float threshold) {
        if (audioProcessor != null) {
            audioProcessor.setSimilarityThreshold(threshold);
        }
        repository.saveSimilarityThreshold(threshold);
        Log.d(TAG, "Similarity threshold set to: " + threshold);
    }
    
    /**
     * Get current similarity threshold
     */
    public float getSimilarityThreshold() {
        if (audioProcessor != null) {
            return audioProcessor.getSimilarityThreshold();
        }
        return ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD;
    }
    
    private void startAudioCapture() {
        Log.d(TAG, "Starting audio capture...");
        try {
            synchronized (audioRecordLock) {
                // Stop any existing recording first
                if (audioRecord != null) {
                    stopAudioCapture();
                }
                
                int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    throw new RuntimeException("Invalid buffer size for audio configuration");
                }
                bufferSize = Math.max(bufferSize, windowSizeSamples * 2);
                
                Log.d(TAG, "Audio configuration - Sample rate: " + sampleRate + 
                          ", Buffer size: " + bufferSize + 
                          ", Window size: " + windowSizeSamples);
                
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                );
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release();
                    throw new RuntimeException("AudioRecord not initialized properly. State: " + audioRecord.getState());
                }
                
                Log.d(TAG, "AudioRecord created successfully");
                
                audioRecord.startRecording();
                
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.release();
                    throw new RuntimeException("Failed to start recording. Recording state: " + audioRecord.getRecordingState());
                }
                
                isRecording = true;
                Log.d(TAG, "Audio recording started successfully");
            }
            
            // Start audio processing on background thread
            if (audioHandler != null) {
                audioHandler.post(this::processAudioLoop);
                Log.d(TAG, "Audio processing posted to background thread");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            isRecording = false;
            if (callback != null) {
                callback.onError("Failed to start audio recording: " + e.getMessage());
            }
        }
    }
    
    private void stopAudioCapture() {
        isRecording = false;
        
        synchronized (audioRecordLock) {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                    audioRecord = null;
                    Log.d(TAG, "AudioRecord stopped and released");
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping audio capture", e);
                }
            }
        }
    }
    
    private void processAudioLoop() {
        Log.d(TAG, "Audio processing loop started");
        short[] buffer = new short[hopSizeSamples];
        long lastLogTime = 0;
        
        while (isRecording) {
            AudioRecord currentRecord = null;
            
            // Get current audioRecord reference safely
            synchronized (audioRecordLock) {
                if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    currentRecord = audioRecord;
                } else {
                    Log.w(TAG, "AudioRecord is null or not recording, stopping loop");
                    break;
                }
            }
            
            if (currentRecord == null) {
                break;
            }
            
            try {
                int bytesRead = currentRecord.read(buffer, 0, buffer.length);
                
                // Log audio activity periodically
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLogTime > 2000) { // Log every 2 seconds
                    Log.v(TAG, "Audio processing active, bytes read: " + bytesRead + 
                              ", registration mode: " + isRegistrationMode);
                    lastLogTime = currentTime;
                }
                
                if (bytesRead > 0) {
                    // Convert to float and normalize
                    float[] floatBuffer = new float[bytesRead];
                    for (int i = 0; i < bytesRead; i++) {
                        floatBuffer[i] = buffer[i] / 32768.0f; // Normalize to [-1, 1]
                    }
                    
                    processAudioChunk(floatBuffer);
                } else if (bytesRead < 0) {
                    Log.w(TAG, "Audio read returned error code: " + bytesRead);
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord in invalid state, stopping loop");
                        break;
                    }
                }
                
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioRecord in illegal state", e);
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in audio processing loop", e);
                break;
            }
            
            // Add small delay to prevent busy waiting
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Log.d(TAG, "Audio processing thread interrupted");
                break;
            }
        }
        
        Log.d(TAG, "Audio processing loop ended");
        isRecording = false;
    }
    
    private void processAudioChunk(float[] chunk) {
        synchronized (bufferLock) {
            // Add chunk to sliding window buffer
            for (float sample : chunk) {
                audioBuffer[bufferPosition] = sample;
                bufferPosition = (bufferPosition + 1) % audioBuffer.length;
                
                // Check if we have a complete window
                if (bufferPosition % hopSizeSamples == 0) {
                    processWindow();
                }
            }
        }
    }
    
    private void processWindow() {
        // Extract current window from circular buffer
        float[] window = new float[windowSizeSamples];
        int startPos = (bufferPosition - windowSizeSamples + audioBuffer.length) % audioBuffer.length;
        
        for (int i = 0; i < windowSizeSamples; i++) {
            window[i] = audioBuffer[(startPos + i) % audioBuffer.length];
        }
        
        // Normalize the window
        window = TFLiteProcessor.normalizeAudio(window);
        
        Log.v(TAG, "🔄 processWindow: window extracted and normalized (" + window.length + " samples)");
        Log.v(TAG, "🔄 processWindow: isRegistrationMode = " + isRegistrationMode);
        
        if (isRegistrationMode) {
            Log.v(TAG, "📝 Calling processRegistrationWindow");
            processRegistrationWindow(window);
        } else {
            Log.v(TAG, "🎯 Calling processRecognitionWindow");
            processRecognitionWindow(window);
        }
    }
    
    private void processRegistrationWindow(float[] window) {
        registrationWindows.add(window.clone());
        
        long elapsed = System.currentTimeMillis() - registrationStartTime;
        int targetWindows = (ModelConfig.SpeakerRecognition.REGISTRATION_DURATION_SEC * 1000) / ModelConfig.SpeakerRecognition.HOP_SIZE_MS;
        
        Log.v(TAG, "Registration window " + registrationWindows.size() + "/" + targetWindows + 
                   ", elapsed: " + elapsed + "ms");
        
        if (callback != null) {
            callback.onRegistrationProgress(registrationWindows.size(), targetWindows);
        }
        
        // Stop registration automatically after target duration
        if (elapsed >= ModelConfig.SpeakerRecognition.REGISTRATION_DURATION_SEC * 1000) {
            Log.d(TAG, "Registration time elapsed, stopping registration with " + registrationWindows.size() + " windows");
            stopSpeakerRegistration();
        }
    }
    
    private void processRecognitionWindow(float[] window) {
        Log.v(TAG, "🎤 processRecognitionWindow called - window size: " + window.length);
        
        if (audioProcessor == null) {
            Log.w(TAG, "❌ AudioProcessor not available - speaker recognition disabled");
            return;
        }
        
        Log.v(TAG, "✅ AudioProcessor available, creating callback and starting recognition");
        Log.v(TAG, "📊 Current registered speakers: " + registeredSpeakers.size());
        for (String name : registeredSpeakers.keySet()) {
            Log.v(TAG, "  - " + name);
        }
        
        AudioProcessor.AudioProcessingCallback processingCallback = new AudioProcessor.AudioProcessingCallback() {
            @Override
            public void onSpeakerDetected(String name, float similarity, boolean isRecognized) {
                Log.d(TAG, "🎉 Speaker detected callback: " + name + " (similarity: " + String.format("%.3f", similarity) + ")");
                if (callback != null) {
                    callback.onSpeakerMatched(name, similarity);
                }
            }
            
            @Override
            public void onNoSpeakerDetected() {
                Log.v(TAG, "❌ No speaker detected callback triggered");
                if (callback != null) {
                    callback.onNoSpeakerDetected();
                }
            }
            
            @Override
            public void onEmbeddingGenerated(float[] embedding) {
                // Not used in recognition mode
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Recognition error callback: " + error);
                if (callback != null) {
                    callback.onError(error);
                }
            }
        };
        
        Log.v(TAG, "🚀 Calling audioProcessor.processAudioForRecognition...");
        audioProcessor.processAudioForRecognition(window, registeredSpeakers, processingCallback);
        Log.v(TAG, "✅ audioProcessor.processAudioForRecognition completed");
    }
    
    private void processRegistrationAsync() {
        audioHandler.post(() -> {
            try {
                if (registrationWindows.size() < ModelConfig.SpeakerRecognition.MIN_REGISTRATION_WINDOWS) {
                    if (callback != null) {
                        callback.onSpeakerRegistered(pendingRegistrationName, false);
                    }
                    return;
                }
                
                // Convert list to array
                float[][] windowsArray = registrationWindows.toArray(new float[0][]);
                
                // Process registration
                float[] averageEmbedding = audioProcessor.processForRegistration(windowsArray);
                
                if (averageEmbedding != null) {
                    // Create recognition object
                    SimilarityClassifier.Recognition recognition = new SimilarityClassifier.Recognition(
                        "0", pendingRegistrationName, -1f
                    );
                    float[][] embeddingArray = {averageEmbedding};
                    recognition.setExtra(embeddingArray);
                    
                    // Save to memory and storage
                    registeredSpeakers.put(pendingRegistrationName, recognition);
                    boolean success = repository.save(pendingRegistrationName, recognition);
                    
                    if (callback != null) {
                        callback.onSpeakerRegistered(pendingRegistrationName, success);
                    }
                    
                    Log.d(TAG, "Speaker registered: " + pendingRegistrationName + ", success: " + success);
                } else {
                    if (callback != null) {
                        callback.onSpeakerRegistered(pendingRegistrationName, false);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing registration", e);
                if (callback != null) {
                    callback.onSpeakerRegistered(pendingRegistrationName, false);
                }
            } finally {
                registrationWindows.clear();
                pendingRegistrationName = null;
            }
        });
    }
    
    private boolean hasAudioPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(
            context,
            new String[]{Manifest.permission.RECORD_AUDIO},
            AUDIO_PERMISSION_REQUEST_CODE
        );
    }
    
    /**
     * Handle permission request result
     */
    public void onPermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Audio permission granted");
                if (!isInitialized) {
                    initialize();
                }
            } else {
                Log.w(TAG, "Audio permission denied");
                if (callback != null) {
                    callback.onError("Audio permission required for speaker recognition");
                }
            }
        }
    }
    
    /**
     * Get statistics about registered speakers
     */
    public String getStats() {
        return repository.getStorageStats();
    }
} 