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
import android.util.Log;
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
import com.atharvakale.facerecognition.features.audio.SpeakerRecognitionIntegration;
import com.atharvakale.facerecognition.hardware.CameraManager;
import com.google.mlkit.common.MlKit;

import java.io.FileDescriptor;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    
    // UI Components
    private PreviewView previewView;
    private ImageView face_preview;
    private TextView reco_name, preview_info, textAbove_preview, speaker_status;
    private Button recognize, camera_switch, actions, add_speaker;
    private ImageButton add_face;
    
    // Modular Components
    private FaceRecognitionManager faceRecognitionManager;
    private CameraManager cameraManager;
    private SpeakerRecognitionIntegration speakerRecognition;
    
    // Constants
    private static final int SELECT_PICTURE = 1;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 200;
    
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize MlKit first to fix face detection
        try {
            MlKit.initialize(this);
        } catch (Exception e) {
            // Handle initialization error gracefully
            e.printStackTrace();
        }
        
        initializeViews();
        initializeModularComponents();
        checkPermissions();
        setupEventListeners();
        startCamera();
    }
    
    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        face_preview = findViewById(R.id.imageView);
        reco_name = findViewById(R.id.textView);
        preview_info = findViewById(R.id.textView2);
        textAbove_preview = findViewById(R.id.textAbovePreview);
        speaker_status = findViewById(R.id.speaker_status);
        add_face = findViewById(R.id.imageButton);
        recognize = findViewById(R.id.button3);
        camera_switch = findViewById(R.id.button5);
        actions = findViewById(R.id.button2);
        add_speaker = findViewById(R.id.add_speaker);
        
        // Set initial UI state
        add_face.setVisibility(View.INVISIBLE);
        face_preview.setVisibility(View.INVISIBLE);
        textAbove_preview.setText("Recognized Face:");
        speaker_status.setText("Speaker: Not listening");
    }
    
    private void initializeModularComponents() {
        try {
            // Initialize Face Recognition Manager
            faceRecognitionManager = new FaceRecognitionManager(this);
            
            // Initialize Camera Manager
            cameraManager = new CameraManager(this);
            cameraManager.initialize(previewView);
            
            // Initialize Speaker Recognition
            speakerRecognition = new SpeakerRecognitionIntegration(this);
            speakerRecognition.setEventListener(new SpeakerRecognitionIntegration.SpeakerEventListener() {
                @Override
                public void onSpeakerIdentified(String speakerName, float confidence) {
                    Log.d("MainActivity", "🎉 SPEAKER IDENTIFIED UI CALLBACK: " + speakerName + " (confidence: " + String.format("%.3f", confidence) + ")");
                    runOnUiThread(() -> {
                        String displayText = "Speaker: " + speakerName + " (" + String.format("%.1f%%", confidence * 100) + ")";
                        speaker_status.setText(displayText);
                        Log.d("MainActivity", "✅ UI updated: " + displayText);
                    });
                }
                
                @Override
                public void onSpeakerRegistrationComplete(String speakerName, boolean success) {
                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(MainActivity.this, "Speaker '" + speakerName + "' registered successfully!", Toast.LENGTH_LONG).show();
                            speaker_status.setText("Speaker: Registration complete");
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to register speaker: " + speakerName, Toast.LENGTH_LONG).show();
                            speaker_status.setText("Speaker: Registration failed");
                        }
                        
                        // Restart listening after registration
                        new android.os.Handler().postDelayed(() -> {
                            startSpeakerListening();
                        }, 1000); // Wait 1 second before restarting
                    });
                }
                
                @Override
                public void onSpeakerRegistrationProgress(int progress, int total) {
                    runOnUiThread(() -> {
                        int percent = (progress * 100) / total;
                        speaker_status.setText("Recording: " + percent + "%");
                    });
                }
                
                @Override
                public void onErrorOccurred(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Speaker error: " + error, Toast.LENGTH_LONG).show();
                        speaker_status.setText("Speaker: Error - " + error);
                        Log.e("MainActivity", "Speaker recognition error: " + error);
                        
                        // Try to restart speaker recognition after a longer delay to prevent spam
                        new android.os.Handler().postDelayed(() -> {
                            if (speakerRecognition != null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                Log.d("MainActivity", "Attempting to restart speaker recognition after error");
                                startSpeakerListening();
                            }
                        }, 30000); // Wait 30 seconds before retry to prevent spam
                    });
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize recognition system: " + e.getMessage(), 
                          Toast.LENGTH_LONG).show();
        }
    }
    
    private void checkPermissions() {
        checkCameraPermission();
        checkAudioPermission();
    }
    
    private void checkCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
    }
    
    private void checkAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST_CODE);
        } else {
            // Initialize and start speaker recognition if permission already granted
            if (speakerRecognition != null) {
                if (speakerRecognition.initialize()) {
                    startSpeakerListening();
                }
            }
        }
    }
    
    private void startSpeakerListening() {
        if (speakerRecognition != null) {
            if (speakerRecognition.startListening()) {
                speaker_status.setText("Speaker: Listening...");
                Log.d("MainActivity", "Speaker listening started successfully");
            } else {
                speaker_status.setText("Speaker: Failed to start");
                Log.e("MainActivity", "Failed to start speaker listening");
            }
        } else {
            speaker_status.setText("Speaker: Not available");
            Log.w("MainActivity", "Speaker recognition not initialized");
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
        
        // Add speaker button
        add_speaker.setOnClickListener(v -> showAddSpeakerDialog());
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
            "--- Speaker Actions ---",
            "View Speaker List",
            "Clear All Speakers",
            "Speaker Settings",
            "--- General ---",
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
                case 5: /* separator */ break;
                case 6: displaySpeakerList(); break;
                case 7: clearAllSpeakers(); break;
                case 8: showSpeakerSettings(); break;
                case 9: /* separator */ break;
                case 10: importPhoto(); break;
                case 11: adjustHyperparameters(); break;
                case 12: toggleDeveloperMode(); break;
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
    
    private void displaySpeakerList() {
        if (speakerRecognition != null) {
            String[] speakers = speakerRecognition.getRegisteredSpeakers();
            
            if (speakers.length == 0) {
                Toast.makeText(this, "No speakers registered", Toast.LENGTH_SHORT).show();
                return;
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Registered Speakers (" + speakers.length + ")");
            
            StringBuilder speakerList = new StringBuilder();
            for (int i = 0; i < speakers.length; i++) {
                speakerList.append((i + 1)).append(". ").append(speakers[i]).append("\n");
            }
            
            builder.setMessage(speakerList.toString());
            builder.setPositiveButton("OK", null);
            
            builder.setNegativeButton("Delete Speaker", (dialog, which) -> {
                showDeleteSpeakerDialog(speakers);
            });
            
            builder.show();
        }
    }
    
    private void showDeleteSpeakerDialog(String[] speakers) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Speaker to Delete:");
        
        builder.setItems(speakers, (dialog, which) -> {
            String speakerToDelete = speakers[which];
            
            new AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Delete speaker '" + speakerToDelete + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (speakerRecognition.deleteSpeaker(speakerToDelete)) {
                        Toast.makeText(this, "Speaker deleted: " + speakerToDelete, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to delete speaker", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void clearAllSpeakers() {
        if (speakerRecognition != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Clear All Speakers");
            builder.setMessage("This will delete all registered speakers. Are you sure?");
            
            builder.setPositiveButton("Yes, Clear All", (dialog, which) -> {
                speakerRecognition.clearAllSpeakers();
                Toast.makeText(this, "All speakers cleared", Toast.LENGTH_SHORT).show();
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
    }
    
    private void showSpeakerSettings() {
        if (speakerRecognition != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Speaker Settings");
            
            String[] settings = {
                "Adjust Similarity Threshold",
                "View System Statistics",
                "Reset Settings"
            };
            
            builder.setItems(settings, (dialog, which) -> {
                switch (which) {
                    case 0: adjustSpeakerThreshold(); break;
                    case 1: showSpeakerStats(); break;
                    case 2: resetSpeakerSettings(); break;
                }
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
    }
    
    private void adjustSpeakerThreshold() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adjust Speaker Similarity Threshold");
        builder.setMessage("Current threshold: " + String.format("%.2f", speakerRecognition.getSimilarityThreshold()) + 
                          "\nHigher values = stricter matching");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format("%.2f", speakerRecognition.getSimilarityThreshold()));
        builder.setView(input);
        
        builder.setPositiveButton("Set", (dialog, which) -> {
            try {
                float threshold = Float.parseFloat(input.getText().toString());
                if (threshold >= 0.0f && threshold <= 1.0f) {
                    speakerRecognition.setSimilarityThreshold(threshold);
                    Toast.makeText(this, "Threshold set to: " + threshold, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Threshold must be between 0.0 and 1.0", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showSpeakerStats() {
        if (speakerRecognition != null) {
            String stats = speakerRecognition.getSystemStats();
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Speaker System Statistics");
            builder.setMessage(stats);
            builder.setPositiveButton("OK", null);
            builder.show();
        }
    }
    
    private void resetSpeakerSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Speaker Settings");
        builder.setMessage("This will reset the similarity threshold to default (0.75). Continue?");
        
        builder.setPositiveButton("Reset", (dialog, which) -> {
            speakerRecognition.setSimilarityThreshold(0.75f);
            Toast.makeText(this, "Settings reset to default", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAddSpeakerDialog() {
        if (speakerRecognition == null) {
            Toast.makeText(this, "Speaker recognition not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show();
            checkAudioPermission();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Speaker");
        builder.setMessage("Enter speaker name:");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        
        builder.setPositiveButton("Start Recording", (dialog, which) -> {
            String speakerName = input.getText().toString().trim();
            if (!speakerName.isEmpty()) {
                // No need to stop listening - registration can happen while listening
                
                // Start registration
                speakerRecognition.registerSpeaker(speakerName);
                Toast.makeText(this, "Recording started for " + speakerName + ". Please speak clearly for 10 seconds.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Please enter a valid speaker name", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_LONG).show();
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_LONG).show();
                if (speakerRecognition != null) {
                    if (speakerRecognition.initialize()) {
                        startSpeakerListening();
                    }
                }
            } else {
                Toast.makeText(this, "Audio permission denied", Toast.LENGTH_LONG).show();
            }
        }
        
        // Forward permission results to speaker recognition
        if (speakerRecognition != null) {
            speakerRecognition.onPermissionResult(requestCode, grantResults);
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
        if (speakerRecognition != null) {
            speakerRecognition.cleanup();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraManager != null) {
            cameraManager.stopCamera();
        }
        if (speakerRecognition != null) {
            speakerRecognition.stopListening();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (cameraManager != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        if (speakerRecognition != null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (speakerRecognition.initialize()) {
                startSpeakerListening();
            }
        }
    }
}

