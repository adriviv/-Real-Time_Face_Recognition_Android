package com.atharvakale.facerecognition.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.atharvakale.facerecognition.SimilarityClassifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * Repository implementation using SharedPreferences
 * Handles storage and retrieval of Recognition objects using JSON serialization
 */
public class PreferencesRepository implements Repository<String, SimilarityClassifier.Recognition> {
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private String preferenceName;
    private String mapKey;
    private int outputSize;
    
    public PreferencesRepository(Context context, String preferenceName, String mapKey, int outputSize) {
        this.sharedPreferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.preferenceName = preferenceName;
        this.mapKey = mapKey;
        this.outputSize = outputSize;
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
            return editor.commit();
        } catch (Exception e) {
            e.printStackTrace();
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
            
            return retrievedMap;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
    
    @Override
    public boolean delete(String key) {
        Map<String, SimilarityClassifier.Recognition> currentMap = loadAll();
        if (currentMap.remove(key) != null) {
            return saveAll(currentMap);
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
            }
        }
        return changed && saveAll(currentMap);
    }
    
    @Override
    public boolean clear() {
        return saveAll(new HashMap<>());
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
     * Save similarity threshold setting
     */
    public void saveSimilarityThreshold(float threshold) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("distance", threshold);
        editor.apply();
    }
    
    /**
     * Load similarity threshold setting
     */
    public float loadSimilarityThreshold(float defaultValue) {
        return sharedPreferences.getFloat("distance", defaultValue);
    }
} 