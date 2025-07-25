package com.atharvakale.facerecognition.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository interface for data operations
 * Provides a clean contract for data access across different storage mechanisms
 */
public interface Repository<K, V> {
    
    /**
     * Save a single item
     * @param key The key to store the item under
     * @param value The value to store
     * @return true if successful, false otherwise
     */
    boolean save(K key, V value);
    
    /**
     * Save multiple items
     * @param items Map of items to save
     * @return true if successful, false otherwise
     */
    boolean saveAll(Map<K, V> items);
    
    /**
     * Load a single item
     * @param key The key of the item to load
     * @return The loaded item or null if not found
     */
    V load(K key);
    
    /**
     * Load all items
     * @return Map of all stored items
     */
    Map<K, V> loadAll();
    
    /**
     * Delete a single item
     * @param key The key of the item to delete
     * @return true if successful, false otherwise
     */
    boolean delete(K key);
    
    /**
     * Delete multiple items
     * @param keys The keys of items to delete
     * @return true if successful, false otherwise
     */
    boolean deleteAll(Iterable<K> keys);
    
    /**
     * Clear all items
     * @return true if successful, false otherwise
     */
    boolean clear();
    
    /**
     * Check if an item exists
     * @param key The key to check
     * @return true if exists, false otherwise
     */
    boolean exists(K key);
    
    /**
     * Get the number of stored items
     * @return Count of items
     */
    int size();
    
    /**
     * Check if repository is empty
     * @return true if empty, false otherwise
     */
    boolean isEmpty();
} 