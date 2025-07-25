package com.atharvakale.facerecognition.features.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import com.atharvakale.facerecognition.SimilarityClassifier;
import com.atharvakale.facerecognition.ml.MLModelManager;
import com.atharvakale.facerecognition.ml.ModelConfig;
import com.atharvakale.facerecognition.ml.TFLiteProcessor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles face detection, preprocessing, and recognition
 * Encapsulates all face-specific ML operations
 */
public class FaceProcessor {
    private FaceDetector detector;
    private MLModelManager modelManager;
    private float similarityThreshold;
    private boolean developerMode;
    private long lastErrorTime = 0;
    private static final long ERROR_THROTTLE_MS = 1000; // Only show errors once per second
    
    public interface FaceProcessingCallback {
        void onFaceDetected(String name, float distance, boolean isRecognized);
        void onNoFaceDetected();
        void onFaceForPreview(Bitmap faceBitmap);
        void onError(String error);
    }
    
    public FaceProcessor(MLModelManager modelManager) {
        this.modelManager = modelManager;
        this.similarityThreshold = ModelConfig.FaceRecognition.DEFAULT_SIMILARITY_THRESHOLD;
        this.developerMode = false;
        
        // Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        this.detector = FaceDetection.getClient(highAccuracyOpts);
    }
    
    /**
     * Process image for face recognition
     */
    public void processImageForRecognition(@NonNull ImageProxy imageProxy, 
                                         Map<String, SimilarityClassifier.Recognition> registeredFaces,
                                         boolean flipX, 
                                         FaceProcessingCallback callback) {
        
        // Extract image data before async operations
        Bitmap frameBitmap = toBitmap(imageProxy.getImage());
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        
        InputImage image = InputImage.fromMediaImage(
            imageProxy.getImage(), 
            rotation
        );
        
        detector.process(image)
            .addOnSuccessListener(faces -> {
                try {
                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        processFaceFromBitmap(frameBitmap, rotation, face, registeredFaces, flipX, callback, true);
                    } else {
                        callback.onNoFaceDetected();
                    }
                } finally {
                    imageProxy.close();
                }
            })
            .addOnFailureListener(e -> {
                try {
                    // Throttle error messages to prevent spam
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastErrorTime > ERROR_THROTTLE_MS) {
                        callback.onError("Face detection failed: " + e.getMessage());
                        lastErrorTime = currentTime;
                    }
                } finally {
                    imageProxy.close();
                }
            });
    }
    
    /**
     * Process image for face preview (adding new face)
     */
    public void processImageForPreview(@NonNull ImageProxy imageProxy, 
                                     boolean flipX, 
                                     FaceProcessingCallback callback) {
        
        // Extract image data before async operations
        Bitmap frameBitmap = toBitmap(imageProxy.getImage());
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        
        InputImage image = InputImage.fromMediaImage(
            imageProxy.getImage(), 
            rotation
        );
        
        detector.process(image)
            .addOnSuccessListener(faces -> {
                try {
                    if (!faces.isEmpty()) {
                        Face face = faces.get(0);
                        processFaceFromBitmap(frameBitmap, rotation, face, null, flipX, callback, false);
                    } else {
                        callback.onNoFaceDetected();
                    }
                } finally {
                    imageProxy.close();
                }
            })
            .addOnFailureListener(e -> {
                try {
                    // Throttle error messages to prevent spam
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastErrorTime > ERROR_THROTTLE_MS) {
                        callback.onError("Face detection failed: " + e.getMessage());
                        lastErrorTime = currentTime;
                    }
                } finally {
                    imageProxy.close();
                }
            });
    }
    
    /**
     * Process detected face using pre-extracted bitmap
     */
    private void processFaceFromBitmap(Bitmap frameBitmap, int rotation, Face face, 
                                     Map<String, SimilarityClassifier.Recognition> registeredFaces,
                                     boolean flipX, FaceProcessingCallback callback, boolean isRecognition) {
        
        // Adjust orientation
        Bitmap rotatedBitmap = rotateBitmap(frameBitmap, rotation, false, false);
        
        // Get face bounding box and crop
        RectF boundingBox = new RectF(face.getBoundingBox());
        Bitmap croppedFace = getCropBitmapByCPU(rotatedBitmap, boundingBox);
        
        if (flipX) {
            croppedFace = rotateBitmap(croppedFace, 0, flipX, false);
        }
        
        // Scale to model input size
        Bitmap scaledFace = getResizedBitmap(croppedFace, 
            ModelConfig.FaceRecognition.INPUT_SIZE, 
            ModelConfig.FaceRecognition.INPUT_SIZE);
        
        if (isRecognition && registeredFaces != null) {
            recognizeFace(scaledFace, registeredFaces, callback);
        } else {
            callback.onFaceForPreview(scaledFace);
        }
    }
    
    /**
     * Process detected face (legacy method - kept for compatibility)
     */
    private void processFace(@NonNull ImageProxy imageProxy, Face face, 
                           Map<String, SimilarityClassifier.Recognition> registeredFaces,
                           boolean flipX, FaceProcessingCallback callback, boolean isRecognition) {
        
        // Convert MediaImage to Bitmap
        Bitmap frameBitmap = toBitmap(imageProxy.getImage());
        
        // Adjust orientation
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        
        processFaceFromBitmap(frameBitmap, rotation, face, registeredFaces, flipX, callback, isRecognition);
    }
    
    /**
     * Recognize face and find matches
     */
    private void recognizeFace(Bitmap faceBitmap, 
                             Map<String, SimilarityClassifier.Recognition> registeredFaces,
                             FaceProcessingCallback callback) {
        
        // Generate embeddings
        float[][] embeddings = generateEmbeddings(faceBitmap);
        if (embeddings == null) {
            callback.onError("Failed to generate face embeddings");
            return;
        }
        
        if (registeredFaces.isEmpty()) {
            callback.onFaceDetected("Add Face", Float.MAX_VALUE, false);
            return;
        }
        
        // Find nearest matches
        List<Pair<String, Float>> nearest = findNearest(embeddings[0], registeredFaces);
        
        if (!nearest.isEmpty()) {
            String name = nearest.get(0).first;
            float distance = nearest.get(0).second;
            boolean isRecognized = distance < similarityThreshold;
            
            if (developerMode && nearest.size() > 1) {
                String debugInfo = String.format("Nearest: %s\nDist: %.3f\n2nd Nearest: %s\nDist: %.3f", 
                    name, distance, nearest.get(1).first, nearest.get(1).second);
                callback.onFaceDetected(isRecognized ? debugInfo : "Unknown\n" + debugInfo, distance, isRecognized);
            } else {
                callback.onFaceDetected(isRecognized ? name : "Unknown", distance, isRecognized);
            }
        }
    }
    
    /**
     * Generate face embeddings for a given face bitmap
     */
    public float[][] generateEmbeddings(Bitmap faceBitmap) {
        try {
            ByteBuffer imgData = TFLiteProcessor.bitmapToByteBuffer(
                faceBitmap,
                ModelConfig.FaceRecognition.INPUT_SIZE,
                ModelConfig.FaceRecognition.IS_QUANTIZED,
                ModelConfig.FaceRecognition.IMAGE_MEAN,
                ModelConfig.FaceRecognition.IMAGE_STD
            );
            
            Object[] inputArray = {imgData};
            Map<Integer, Object> outputMap = new HashMap<>();
            float[][] embeddings = new float[1][ModelConfig.FaceRecognition.OUTPUT_SIZE];
            outputMap.put(0, embeddings);
            
            boolean success = modelManager.runInference(
                ModelConfig.FaceRecognition.MODEL_KEY, 
                inputArray, 
                outputMap
            );
            
            return success ? embeddings : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Find nearest matching faces
     */
    private List<Pair<String, Float>> findNearest(float[] embedding, 
                                                 Map<String, SimilarityClassifier.Recognition> registeredFaces) {
        
        List<Pair<String, Float>> neighbours = new ArrayList<>();
        Pair<String, Float> closest = null;
        Pair<String, Float> secondClosest = null;
        
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registeredFaces.entrySet()) {
            String name = entry.getKey();
            float[] knownEmbedding = ((float[][]) entry.getValue().getExtra())[0];
            
            float distance = TFLiteProcessor.calculateEuclideanDistance(embedding, knownEmbedding);
            
            if (closest == null || distance < closest.second) {
                secondClosest = closest;
                closest = new Pair<>(name, distance);
            } else if (secondClosest == null || distance < secondClosest.second) {
                secondClosest = new Pair<>(name, distance);
            }
        }
        
        if (closest != null) neighbours.add(closest);
        if (secondClosest != null) neighbours.add(secondClosest);
        
        return neighbours;
    }
    
    // Image processing utility methods
    private Bitmap toBitmap(Image image) {
        byte[] nv21 = YUV_420_888toNV21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);
        
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    
    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        
        byte[] nv21 = new byte[ySize + uvSize * 2];
        
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
        
        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);
        
        int pos = 0;
        
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else {
            long yBufferPos = -rowStride;
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }
        
        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();
        
        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());
        
        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());
                    return nv21;
                }
            } catch (ReadOnlyBufferException ex) {
                // Handle exception
            }
            vBuffer.put(1, savePixel);
        }
        
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }
        
        return nv21;
    }
    
    private Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRect(new RectF(0, 0, cropRectF.width(), cropRectF.height()), paint);
        
        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);
        
        canvas.drawBitmap(source, matrix, paint);
        
        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
        
        return resultBitmap;
    }
    
    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }
    
    // Getters and setters
    public void setSimilarityThreshold(float threshold) {
        this.similarityThreshold = threshold;
    }
    
    public float getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    public void setDeveloperMode(boolean enabled) {
        this.developerMode = enabled;
    }
    
    public boolean isDeveloperMode() {
        return developerMode;
    }
} 