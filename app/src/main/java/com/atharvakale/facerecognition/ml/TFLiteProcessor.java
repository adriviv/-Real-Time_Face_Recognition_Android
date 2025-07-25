package com.atharvakale.facerecognition.ml;

import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for TensorFlow Lite preprocessing and postprocessing
 * Contains common operations used across different ML models
 */
public class TFLiteProcessor {
    
    /**
     * Convert bitmap to ByteBuffer for TensorFlow Lite input
     * @param bitmap Input bitmap
     * @param inputSize Required input size for the model
     * @param isQuantized Whether the model is quantized
     * @param imageMean Mean value for normalization
     * @param imageStd Standard deviation for normalization
     * @return ByteBuffer ready for model input
     */
    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap, int inputSize, 
                                              boolean isQuantized, float imageMean, float imageStd) {
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        imgData.rewind();
        
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - imageMean) / imageStd);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - imageMean) / imageStd);
                    imgData.putFloat(((pixelValue & 0xFF) - imageMean) / imageStd);
                }
            }
        }
        
        return imgData;
    }
    
    /**
     * Calculate Euclidean distance between two embedding vectors
     * @param emb1 First embedding vector
     * @param emb2 Second embedding vector
     * @return Euclidean distance
     */
    public static float calculateEuclideanDistance(float[] emb1, float[] emb2) {
        if (emb1.length != emb2.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same length");
        }
        
        float distance = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }
        return (float) Math.sqrt(distance);
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     * @param emb1 First embedding vector
     * @param emb2 Second embedding vector
     * @return Cosine similarity value (0-1, where 1 is perfect match)
     */
    public static float calculateCosineSimilarity(float[] emb1, float[] emb2) {
        if (emb1.length != emb2.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same length");
        }
        
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < emb1.length; i++) {
            dotProduct += emb1[i] * emb2[i];
            normA += emb1[i] * emb1[i];
            normB += emb2[i] * emb2[i];
        }
        
        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f;
        }
        
        return dotProduct / (float)(Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Convert audio samples to ByteBuffer for TensorFlow Lite input
     * @param audioSamples Input audio samples
     * @param inputSize Required input size for the model
     * @return ByteBuffer ready for model input
     */
    public static ByteBuffer audioToByteBuffer(float[] audioSamples, int inputSize) {
        ByteBuffer audioData = ByteBuffer.allocateDirect(inputSize * 4); // 4 bytes per float
        audioData.order(ByteOrder.nativeOrder());
        
        audioData.rewind();
        
        // Pad or truncate to exact input size
        for (int i = 0; i < inputSize; i++) {
            if (i < audioSamples.length) {
                audioData.putFloat(audioSamples[i]);
            } else {
                audioData.putFloat(0.0f); // Zero padding
            }
        }
        
        return audioData;
    }
    
    /**
     * Normalize audio samples to [-1, 1] range
     * @param audioSamples Input audio samples
     * @return Normalized audio samples
     */
    public static float[] normalizeAudio(float[] audioSamples) {
        if (audioSamples == null || audioSamples.length == 0) {
            return audioSamples;
        }
        
        // Find max absolute value
        float maxAbs = 0.0f;
        for (float sample : audioSamples) {
            maxAbs = Math.max(maxAbs, Math.abs(sample));
        }
        
        // Avoid division by zero
        if (maxAbs == 0.0f) {
            return audioSamples;
        }
        
        // Normalize to [-1, 1]
        float[] normalized = new float[audioSamples.length];
        for (int i = 0; i < audioSamples.length; i++) {
            normalized[i] = audioSamples[i] / maxAbs;
        }
        
        return normalized;
    }
    

    
    /**
     * Normalize an embedding vector
     * @param embedding Input embedding vector
     * @return Normalized embedding vector
     */
    public static float[] normalizeEmbedding(float[] embedding) {
        float norm = 0;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        
        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = embedding[i] / norm;
        }
        
        return normalized;
    }
} 