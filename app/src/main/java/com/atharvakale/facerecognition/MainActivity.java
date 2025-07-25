package com.atharvakale.facerecognition;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.atharvakale.facerecognition.features.face.FaceRecognitionManager;
import com.atharvakale.facerecognition.hardware.CameraManager;

import java.io.FileDescriptor;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    
    // UI Components
    private PreviewView previewView;
    private ImageView face_preview;
    private TextView reco_name, preview_info, textAbove_preview;
    private Button recognize, camera_switch, actions;
    private ImageButton add_face;
    
    // Modular Components
    private FaceRecognitionManager faceRecognitionManager;
    private CameraManager cameraManager;
    
    // Constants
    private static final int SELECT_PICTURE = 1;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        initializeModularComponents();
        checkCameraPermission();
        setupEventListeners();
        startCamera();
    }
    
    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        face_preview = findViewById(R.id.imageView);
        reco_name = findViewById(R.id.textView);
        preview_info = findViewById(R.id.textView2);
        textAbove_preview = findViewById(R.id.textAbovePreview);
        add_face = findViewById(R.id.imageButton);
        recognize = findViewById(R.id.button3);
        camera_switch = findViewById(R.id.button5);
        actions = findViewById(R.id.button2);
        
        // Set initial UI state
        add_face.setVisibility(View.INVISIBLE);
        face_preview.setVisibility(View.INVISIBLE);
        textAbove_preview.setText("Recognized Face:");
    }
    
    private void initializeModularComponents() {
        try {
            // Initialize Face Recognition Manager
            faceRecognitionManager = new FaceRecognitionManager(this);
            
            // Initialize Camera Manager
            cameraManager = new CameraManager(this);
            cameraManager.initialize(previewView);
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize recognition system: " + e.getMessage(), 
                          Toast.LENGTH_LONG).show();
        }
    }
    
    private void checkCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
    }
    
    private void setupEventListeners() {
        // Actions button
        actions.setOnClickListener(v -> showActionsDialog());
        
        // Camera switch button
        camera_switch.setOnClickListener(v -> switchCamera());
        
        // Add face button
        add_face.setOnClickListener(v -> addFace());
        
        // Recognize/Add Face toggle button
        recognize.setOnClickListener(v -> toggleRecognitionMode());
    }
    
    private void startCamera() {
        if (cameraManager != null) {
            cameraManager.startCamera(this::processFrame);
        }
    }
    
    private void processFrame(@NonNull ImageProxy imageProxy) {
        if (faceRecognitionManager != null) {
            faceRecognitionManager.processFrame(imageProxy, cameraManager.shouldFlipX(), 
                new FaceRecognitionManager.FaceRecognitionCallback() {
                    @Override
                    public void onFaceRecognized(String name, float distance, boolean isKnown) {
                        runOnUiThread(() -> reco_name.setText(name));
                    }
                    
                    @Override
                    public void onNoFaceDetected() {
                        runOnUiThread(() -> {
                            if (faceRecognitionManager.hasRegisteredFaces()) {
                                reco_name.setText("No Face Detected!");
                            } else {
                                reco_name.setText("Add Face");
                            }
                        });
                    }
                    
                    @Override
                    public void onFaceReadyForRegistration(Bitmap faceBitmap) {
                        runOnUiThread(() -> face_preview.setImageBitmap(faceBitmap));
                    }
                    
                    @Override
                    public void onFaceRegistered(String name, boolean success) {
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(MainActivity.this, "Face registered: " + name, Toast.LENGTH_SHORT).show();
                                toggleRecognitionMode(); // Switch back to recognition mode
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to register face", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
        } else {
            // Close imageProxy if no face recognition manager
            imageProxy.close();
        }
    }
    
    private void showActionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Action:");
        
        String[] actions = {
            "View Recognition List", 
            "Update Recognition List", 
            "Save Recognitions", 
            "Load Recognitions", 
            "Clear All Recognitions", 
            "Import Photo (Beta)", 
            "Hyperparameters", 
            "Developer Mode"
        };
        
        builder.setItems(actions, (dialog, which) -> {
            switch (which) {
                case 0: displayRecognitionList(); break;
                case 1: updateRecognitionList(); break;
                case 2: saveRecognitions(); break;
                case 3: loadRecognitions(); break;
                case 4: clearAllRecognitions(); break;
                case 5: importPhoto(); break;
                case 6: adjustHyperparameters(); break;
                case 7: toggleDeveloperMode(); break;
            }
        });
        
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void switchCamera() {
        if (cameraManager != null) {
            cameraManager.switchCamera(this::processFrame);
        }
    }
    
    private void toggleRecognitionMode() {
        if (faceRecognitionManager != null) {
            boolean isRecognitionMode = faceRecognitionManager.isRecognitionMode();
            
            if (isRecognitionMode) {
                // Switch to Add Face mode
                faceRecognitionManager.setRecognitionMode(false);
                textAbove_preview.setText("Face Preview:");
                recognize.setText("Recognize");
                add_face.setVisibility(View.VISIBLE);
                reco_name.setVisibility(View.INVISIBLE);
                face_preview.setVisibility(View.VISIBLE);
                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");
            } else {
                // Switch to Recognition mode
                faceRecognitionManager.setRecognitionMode(true);
                textAbove_preview.setText("Recognized Face:");
                recognize.setText("Add Face");
                add_face.setVisibility(View.INVISIBLE);
                reco_name.setVisibility(View.VISIBLE);
                face_preview.setVisibility(View.INVISIBLE);
                preview_info.setText("");
            }
        }
    }
    
    private void addFace() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Name");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        
        builder.setPositiveButton("ADD", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty() && faceRecognitionManager != null) {
                faceRecognitionManager.registerFace(name, new FaceRecognitionManager.FaceRecognitionCallback() {
                    @Override
                    public void onFaceRecognized(String name, float distance, boolean isKnown) {}
                    
                    @Override
                    public void onNoFaceDetected() {}
                    
                    @Override
                    public void onFaceReadyForRegistration(Bitmap faceBitmap) {}
                    
                    @Override
                    public void onFaceRegistered(String registeredName, boolean success) {
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(MainActivity.this, "Face registered: " + registeredName, Toast.LENGTH_SHORT).show();
                                toggleRecognitionMode();
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to register face", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void displayRecognitionList() {
        if (faceRecognitionManager != null) {
            String[] names = faceRecognitionManager.getRegisteredFaceNames();
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            if (names.length == 0) {
                builder.setTitle("No Faces Added!!");
            } else {
                builder.setTitle("Recognitions:");
                builder.setItems(names, null);
            }
            builder.setPositiveButton("OK", null);
            builder.show();
        }
    }
    
    private void updateRecognitionList() {
        if (faceRecognitionManager != null) {
            String[] names = faceRecognitionManager.getRegisteredFaceNames();
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            if (names.length == 0) {
                builder.setTitle("No Faces Added!!");
                builder.setPositiveButton("OK", null);
            } else {
                builder.setTitle("Select Recognition to delete:");
                
                boolean[] checkedItems = new boolean[names.length];
                builder.setMultiChoiceItems(names, checkedItems, 
                    (dialog, which, isChecked) -> checkedItems[which] = isChecked);
                
                builder.setPositiveButton("OK", (dialog, which) -> {
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            faceRecognitionManager.deleteFace(names[i]);
                        }
                    }
                    Toast.makeText(this, "Recognitions Updated", Toast.LENGTH_SHORT).show();
                });
                builder.setNegativeButton("Cancel", null);
            }
            builder.show();
        }
    }
    
    private void saveRecognitions() {
        if (faceRecognitionManager != null) {
            boolean success = faceRecognitionManager.saveAllFaces();
            Toast.makeText(this, success ? "Recognitions Saved" : "Failed to save", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadRecognitions() {
        if (faceRecognitionManager != null) {
            faceRecognitionManager.reloadFaces();
            Toast.makeText(this, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void clearAllRecognitions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Do you want to delete all Recognitions?");
        builder.setPositiveButton("Delete All", (dialog, which) -> {
            if (faceRecognitionManager != null) {
                faceRecognitionManager.clearAllFaces();
                Toast.makeText(this, "Recognitions Cleared", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void importPhoto() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }
    
    private void adjustHyperparameters() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Euclidean Distance");
        builder.setMessage("0.00 -> Perfect Match\n0.65 -> Default (Very High Precision)\n0.80 -> High Precision\n1.00 -> Moderate Precision\nLower values = Higher precision\n\nCurrent Value:");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        
        if (faceRecognitionManager != null) {
            input.setText(String.valueOf(faceRecognitionManager.getSimilarityThreshold()));
        }
        
        builder.setView(input);
        
        builder.setPositiveButton("Update", (dialog, which) -> {
            try {
                float threshold = Float.parseFloat(input.getText().toString());
                if (faceRecognitionManager != null) {
                    faceRecognitionManager.setSimilarityThreshold(threshold);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void toggleDeveloperMode() {
        if (faceRecognitionManager != null) {
            boolean currentMode = faceRecognitionManager.isDeveloperMode();
            faceRecognitionManager.setDeveloperMode(!currentMode);
            Toast.makeText(this, "Developer Mode " + (!currentMode ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_LONG).show();
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap bitmap = getBitmapFromUri(selectedImageUri);
                face_preview.setImageBitmap(bitmap);
                
                // TODO: Process imported photo for face registration
                // This will be implemented when we enhance the face processor for photo import
                Toast.makeText(this, "Photo import functionality will be enhanced in next update", Toast.LENGTH_SHORT).show();
                
            } catch (IOException e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }
    
    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup modular components
        if (faceRecognitionManager != null) {
            faceRecognitionManager.cleanup();
        }
        
        if (cameraManager != null) {
            cameraManager.cleanup();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraManager != null) {
            cameraManager.stopCamera();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (cameraManager != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}

