package com.atharvakale.facerecognition.features.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.atharvakale.facerecognition.SimilarityClassifier;
import com.atharvakale.facerecognition.data.Repository;
import com.atharvakale.facerecognition.ml.ModelConfig;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * Repository implementation for speaker profiles using SharedPreferences
 * Handles storage and retrieval of speaker Recognition objects using JSON serialization
 */
public class SpeakerRepository implements Repository<String, SimilarityClassifier.Recognition> {
    private static final String TAG = "SpeakerRepository";
    
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private String preferenceName;
    private String mapKey;
    private int outputSize;
    
    public SpeakerRepository(Context context) {
        this.preferenceName = "speaker_profiles";
        this.mapKey = "speaker_map";
        this.outputSize = ModelConfig.SpeakerRecognition.OUTPUT_SIZE;
        this.sharedPreferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    @Override
    public boolean save(String key, SimilarityClassifier.Recognition value) {
        Map<String, SimilarityClassifier.Recognition> currentMap = loadAll();
        currentMap.put(key, value);
        return saveAll(currentMap);
    }
    
    @Override
    public boolean saveAll(Map<String, SimilarityClassifier.Recognition> items) {
        try {
            String jsonString = gson.toJson(items);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(mapKey, jsonString);
            boolean success = editor.commit();
            Log.d(TAG, "Saved " + items.size() + " speaker profiles");
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error saving speaker profiles", e);
            return false;
        }
    }
    
    @Override
    public SimilarityClassifier.Recognition load(String key) {
        Map<String, SimilarityClassifier.Recognition> allItems = loadAll();
        return allItems.get(key);
    }
    
    @Override
    public Map<String, SimilarityClassifier.Recognition> loadAll() {
        try {
            String defValue = gson.toJson(new HashMap<String, SimilarityClassifier.Recognition>());
            String json = sharedPreferences.getString(mapKey, defValue);
            
            Type type = new TypeToken<HashMap<String, SimilarityClassifier.Recognition>>(){}.getType();
            HashMap<String, SimilarityClassifier.Recognition> retrievedMap = gson.fromJson(json, type);
            
            // Fix format conversion issues (double to float) for embeddings
            for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet()) {
                float[][] output = new float[1][outputSize];
                ArrayList arrayList = (ArrayList) entry.getValue().getExtra();
                
                if (arrayList != null && !arrayList.isEmpty()) {
                    arrayList = (ArrayList) arrayList.get(0);
                    for (int counter = 0; counter < arrayList.size() && counter < outputSize; counter++) {
                        output[0][counter] = ((Double) arrayList.get(counter)).floatValue();
                    }
                    entry.getValue().setExtra(output);
                }
            }
            
            Log.d(TAG, "Loaded " + retrievedMap.size() + " speaker profiles");
            return retrievedMap;
        } catch (Exception e) {
            Log.e(TAG, "Error loading speaker profiles", e);
            return new HashMap<>();
        }
    }
    
    @Override
    public boolean delete(String key) {
        Map<String, SimilarityClassifier.Recognition> currentMap = loadAll();
        if (currentMap.remove(key) != null) {
            boolean success = saveAll(currentMap);
            Log.d(TAG, "Deleted speaker profile: " + key);
            return success;
        }
        return false;
    }
    
    @Override
    public boolean deleteAll(Iterable<String> keys) {
        Map<String, SimilarityClassifier.Recognition> currentMap = loadAll();
        boolean changed = false;
        for (String key : keys) {
            if (currentMap.remove(key) != null) {
                changed = true;
                Log.d(TAG, "Marked for deletion: " + key);
            }
        }
        if (changed) {
            boolean success = saveAll(currentMap);
            Log.d(TAG, "Batch delete completed");
            return success;
        }
        return true;
    }
    
    @Override
    public boolean clear() {
        boolean success = saveAll(new HashMap<>());
        Log.d(TAG, "Cleared all speaker profiles");
        return success;
    }
    
    @Override
    public boolean exists(String key) {
        return loadAll().containsKey(key);
    }
    
    @Override
    public int size() {
        return loadAll().size();
    }
    
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }
    
    /**
     * Save speaker recognition similarity threshold setting
     */
    public void saveSimilarityThreshold(float threshold) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("speaker_similarity_threshold", threshold);
        editor.apply();
        Log.d(TAG, "Saved similarity threshold: " + threshold);
    }
    
    /**
     * Load speaker recognition similarity threshold setting
     */
    public float loadSimilarityThreshold(float defaultValue) {
        float threshold = sharedPreferences.getFloat("speaker_similarity_threshold", defaultValue);
        Log.d(TAG, "Loaded similarity threshold: " + threshold);
        return threshold;
    }
    
    /**
     * Save speaker recognition enabled/disabled state
     */
    public void setSpeakerRecognitionEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("speaker_recognition_enabled", enabled);
        editor.apply();
        Log.d(TAG, "Speaker recognition enabled: " + enabled);
    }
    
    /**
     * Load speaker recognition enabled/disabled state
     */
    public boolean isSpeakerRecognitionEnabled() {
        boolean enabled = sharedPreferences.getBoolean("speaker_recognition_enabled", false);
        Log.d(TAG, "Speaker recognition enabled: " + enabled);
        return enabled;
    }
    
    /**
     * Get all registered speaker names
     */
    public String[] getRegisteredSpeakerNames() {
        Map<String, SimilarityClassifier.Recognition> allSpeakers = loadAll();
        return allSpeakers.keySet().toArray(new String[0]);
    }
    
    /**
     * Get statistics about stored speaker profiles
     */
    public String getStorageStats() {
        Map<String, SimilarityClassifier.Recognition> allSpeakers = loadAll();
        return "Speaker profiles: " + allSpeakers.size() + 
               ", Threshold: " + loadSimilarityThreshold(ModelConfig.SpeakerRecognition.DEFAULT_SIMILARITY_THRESHOLD) +
               ", Enabled: " + isSpeakerRecognitionEnabled();
    }
} 