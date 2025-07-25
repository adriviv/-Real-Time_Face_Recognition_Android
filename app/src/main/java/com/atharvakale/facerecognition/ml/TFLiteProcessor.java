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
     * @return Cosine similarity (0-1, where 1 is most similar)
     */
    public static float calculateCosineSimilarity(float[] emb1, float[] emb2) {
        if (emb1.length != emb2.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same length");
        }
        
        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;
        
        for (int i = 0; i < emb1.length; i++) {
            dotProduct += emb1[i] * emb2[i];
            norm1 += emb1[i] * emb1[i];
            norm2 += emb2[i] * emb2[i];
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
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