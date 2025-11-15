package com.example.edgeviewer;

import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import android.opengl.GLSurfaceView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;

import org.opencv.core.Mat;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import org.opencv.imgproc.Imgproc;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    // --- OpenGL/JNI Variables ---
    private GLSurfaceView mGLView;
    private MyGLRenderer mRenderer;
    public native void processFrame(long matAddrRgba);

    // --- BONUS: TOGGLE FLAG & JNI Toggler (Must match native-lib.cpp signatures) ---
    public native void setCannyState(boolean enabled);
    private boolean isCannyEnabled = true;
    // ----------------------------------------

    private static final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysis;

    // BONUS: Web Server Reference and FPS Tracking
    private ImageWebServer webServer;
    private long lastTime = System.currentTimeMillis();
    private int frameCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Set up the GLSurfaceView ---
        mRenderer = new MyGLRenderer();
        mGLView = findViewById(R.id.gl_surface_view);

        // Use OpenGL ES 2.0
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);

        // Only render when we have a new frame (saves battery)
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // --- JNI/OpenCV Initialization ---
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!");
            finish();
        }
        System.loadLibrary("edgeviewer");
        // ---------------------------------

        // --- BONUS: START THE WEB SERVER ---
        try {
            webServer = new ImageWebServer();
        } catch (IOException e) {
            Log.e("Server", "Could not start web server!", e);
        }
        // --- END START WEB SERVER ---

        // Create a background thread for camera operations
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Check if we already have camera permission
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            // If not, ask for permission
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Initial state update for the button and C++ flag
        setCannyState(isCannyEnabled);
        Button toggleButton = findViewById(R.id.toggle_button);
        if (toggleButton != null) {
            toggleButton.setText(isCannyEnabled ? "Toggle RAW" : "Toggle Canny");
        }
    }

    // --- BONUS: BUTTON CLICK HANDLER (Called from XML: android:onClick="onToggleClicked") ---
    public void onToggleClicked(View view) {
        Button button = (Button) view;

        // 1. Toggle the state locally
        isCannyEnabled = !isCannyEnabled;

        // 2. Send the new state to C++ via JNI
        setCannyState(isCannyEnabled);

        // 3. Update button text and trigger a redraw
        button.setText(isCannyEnabled ? "Toggle RAW" : "Toggle Canny");
        if (mGLView != null) mGLView.requestRender();
    }
    // ------------------------------------------------------------------------

    /**
     * Checks if all required permissions are granted.
     */
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * This function is called after the user responds to the permission request.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Permission was granted, start the camera
                startCamera();
            } else {
                // Permission was denied
                Toast.makeText(this, "Camera permission is required to use this app.", Toast.LENGTH_SHORT).show();
                finish(); // Close the app if permission is denied
            }
        }
    }

    /**
     * Initializes and starts the camera feed.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Select the back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Set up the ImageAnalysis use case
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy image) {

                        // --- BONUS: FPS LOGGING ---
                        frameCount++;
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTime >= 1000) { // Check every 1 second
                            double fps = frameCount / ((currentTime - lastTime) / 1000.0);
                            Log.d("FPS_LOG", "Live FPS: " + String.format("%.2f", fps));
                            frameCount = 0;
                            lastTime = currentTime;
                        }
                        // --- END FPS LOGGING ---

                        // 1. Get the raw image data (Y, U, V planes)
                        ImageProxy.PlaneProxy[] planes = image.getPlanes();
                        ByteBuffer yBuffer = planes[0].getBuffer();
                        ByteBuffer uBuffer = planes[1].getBuffer();
                        ByteBuffer vBuffer = planes[2].getBuffer();

                        int ySize = yBuffer.remaining();
                        int uSize = uBuffer.remaining();
                        int vSize = vBuffer.remaining();

                        byte[] yuvBytes = new byte[ySize + uSize + vSize];

                        // Copy Y, U, and V in the correct order (YUV)
                        yBuffer.get(yuvBytes, 0, ySize);
                        uBuffer.get(yuvBytes, ySize, uSize);
                        vBuffer.get(yuvBytes, ySize + uSize, vSize);

                        // 2. Create OpenCV Mat objects
                        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), org.opencv.core.CvType.CV_8UC1);
                        yuvMat.put(0, 0, yuvBytes);
                        Mat rgbaMat = new Mat();

                        // 3. Convert YUV (I420 format) to RGBA
                        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_I420, 4);

                        // 4. Send the RGBA Mat's memory address to C++
                        processFrame(rgbaMat.getNativeObjAddr());

                        // 5. Convert the Canny Edges to bytes for the web server (BONUS)
                        MatOfByte mob = new MatOfByte();
                        Mat singleChannel = new Mat();
                        // Convert the processed RGBA back to a single channel image for encoding
                        Imgproc.cvtColor(rgbaMat, singleChannel, Imgproc.COLOR_RGBA2GRAY);

                        Imgcodecs.imencode(".jpg", singleChannel, mob);
                        byte[] processedBytes = mob.toArray();

                        // 6. Save the bytes to the static holder for the server
                        ProcessedFrameHolder.updateFrame(processedBytes, rgbaMat.cols(), rgbaMat.rows());

                        // 7. Tell the GLSurfaceView to re-draw itself
                        mRenderer.updateFrame(rgbaMat);
                        mGLView.requestRender();

                        // 8. Release the Mat objects
                        yuvMat.release();
                        rgbaMat.release();
                        singleChannel.release();
                        mob.release();

                        // 9. Close the image
                        image.close();
                    }
                });


                // Unbind any previous use cases
                cameraProvider.unbindAll();

                // Bind the camera and ImageAnalysis use case
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis);

            } catch (Exception e) {
                Log.e("CameraX", "Failed to start camera.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down the background thread when the app is closed
        cameraExecutor.shutdown();

        // --- BONUS: STOP THE WEB SERVER ---
        if (webServer != null) {
            webServer.stop();
        }
        // --- END STOP WEB SERVER ---
    }
}