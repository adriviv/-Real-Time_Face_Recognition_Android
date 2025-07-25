package com.atharvakale.facerecognition.hardware;

import android.app.Activity;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages camera operations including preview and image analysis
 * Provides a clean interface for camera functionality across features
 */
public class CameraManager {
    private Activity context;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private CameraSelector cameraSelector;
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private boolean flipX = false;
    
    public interface ImageAnalysisCallback {
        void onImageAvailable(@NonNull ImageProxy imageProxy);
    }
    
    public CameraManager(Activity context) {
        this.context = context;
    }
    
    /**
     * Initialize camera with preview view
     */
    public void initialize(PreviewView previewView) {
        this.previewView = previewView;
        this.cameraProviderFuture = ProcessCameraProvider.getInstance(context);
    }
    
    /**
     * Start camera preview and analysis
     */
    public void startCamera(ImageAnalysisCallback callback) {
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(callback);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }
    
    /**
     * Bind camera preview and image analysis
     */
    private void bindPreview(ImageAnalysisCallback callback) {
        // Unbind all use cases first to prevent conflicts
        cameraProvider.unbindAll();
        
        preview = new Preview.Builder().build();
        
        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();
        
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        
        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, callback::onImageAvailable);
        
        try {
            cameraProvider.bindToLifecycle(
                (LifecycleOwner) context, 
                cameraSelector, 
                preview,
                imageAnalysis
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Switch between front and back camera
     */
    public void switchCamera(ImageAnalysisCallback callback) {
        if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
            cameraFacing = CameraSelector.LENS_FACING_FRONT;
            flipX = true;
        } else {
            cameraFacing = CameraSelector.LENS_FACING_BACK;
            flipX = false;
        }
        
        if (cameraProvider != null) {
            bindPreview(callback);
        }
    }
    
    /**
     * Stop camera and unbind all use cases
     */
    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
    
    /**
     * Get current camera facing direction
     */
    public int getCameraFacing() {
        return cameraFacing;
    }
    
    /**
     * Check if image should be flipped horizontally (for front camera)
     */
    public boolean shouldFlipX() {
        return flipX;
    }
    
    /**
     * Check if camera is initialized
     */
    public boolean isInitialized() {
        return cameraProvider != null;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        stopCamera();
        cameraProvider = null;
    }
} 