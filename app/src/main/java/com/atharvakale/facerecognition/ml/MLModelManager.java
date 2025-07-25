package com.atharvakale.facerecognition.ml;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages TensorFlow Lite models for the application
 * Handles model loading, initialization, and inference execution
 */
public class MLModelManager {
    private Map<String, Interpreter> loadedModels;
    private Activity context;
    
    public MLModelManager(Activity context) {
        this.context = context;
        this.loadedModels = new HashMap<>();
    }
    
    /**
     * Load a TensorFlow Lite model from assets
     * @param modelName Name of the model file in assets
     * @param modelKey Unique key to identify this model
     * @return true if loaded successfully, false otherwise
     */
    public boolean loadModel(String modelName, String modelKey) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, modelName);
            Interpreter interpreter = new Interpreter(modelBuffer);
            loadedModels.put(modelKey, interpreter);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get a loaded model interpreter
     * @param modelKey The key of the model to retrieve
     * @return Interpreter instance or null if not found
     */
    public Interpreter getModel(String modelKey) {
        return loadedModels.get(modelKey);
    }
    
    /**
     * Run inference on a model
     * @param modelKey The key of the model to use
     * @param inputArray Input data for the model
     * @param outputMap Output map to store results
     * @return true if inference successful, false otherwise
     */
    public boolean runInference(String modelKey, Object[] inputArray, Map<Integer, Object> outputMap) {
        Interpreter interpreter = loadedModels.get(modelKey);
        if (interpreter != null) {
            try {
                interpreter.runForMultipleInputsOutputs(inputArray, outputMap);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    
    /**
     * Load model file from assets
     */
    private MappedByteBuffer loadModelFile(Activity activity, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Check if a model is loaded
     */
    public boolean isModelLoaded(String modelKey) {
        return loadedModels.containsKey(modelKey);
    }
    
    /**
     * Unload a specific model to free memory
     */
    public void unloadModel(String modelKey) {
        Interpreter interpreter = loadedModels.remove(modelKey);
        if (interpreter != null) {
            interpreter.close();
        }
    }
    
    /**
     * Unload all models and cleanup
     */
    public void cleanup() {
        for (Interpreter interpreter : loadedModels.values()) {
            if (interpreter != null) {
                interpreter.close();
            }
        }
        loadedModels.clear();
    }
} 