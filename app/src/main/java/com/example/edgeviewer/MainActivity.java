package com.example.edgeviewer;

import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import com.example.edgeviewer.ProcessedFrameHolder;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.edgeviewer.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import android.opengl.GLSurfaceView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opencv.core.Mat;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import org.opencv.imgproc.Imgproc;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    // --- New CameraX variables ---
    private GLSurfaceView mGLView;
    private MyGLRenderer mRenderer;
    public native void processFrame(long matAddrRgba);
    private static final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private ExecutorService cameraExecutor;
    // ----------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the PreviewView from our XML layout
        // --- Set up the GLSurfaceView ---
        mRenderer = new MyGLRenderer();
        mGLView = findViewById(R.id.gl_surface_view);

// Use OpenGL ES 2.0 [cite: 10]
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);

// Only render when we have a new frame (saves battery)
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        // Make sure OpenCV is loaded
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!");
            finish(); // Close the app if OpenCV can't load
        } System.loadLibrary("edgeviewer");
        // Create a background thread for camera operations
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Check if we already have camera permission
        if (allPermissionsGranted()) {
            startCamera(); // If we do, start the camera
        } else {
            // If not, ask for permission
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // We are deleting the "Hello from C++" text logic
        // binding = ActivityMainBinding.inflate(getLayoutInflater());
        // setContentView(binding.getRoot());
        // TextView tv = binding.sampleText;
        // tv.setText(stringFromJNI());
    }

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
    private ImageAnalysis imageAnalysis;
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview

                // Select the back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // --- THIS IS THE NEW PART ---
                // Find this block in startCamera()
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        // .setTargetRotation(mGLView.getDisplay().getRotation()) // <-- DELETE THIS LINE
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        // This function is called for every single camera frame

                        // 1. Get the raw image data (Y, U, V planes)
                        ImageProxy.PlaneProxy[] planes = image.getPlanes();
                        ByteBuffer yBuffer = planes[0].getBuffer();
                        ByteBuffer uBuffer = planes[1].getBuffer(); // U plane
                        ByteBuffer vBuffer = planes[2].getBuffer(); // V plane

                        int ySize = yBuffer.remaining();
                        int uSize = uBuffer.remaining();
                        int vSize = vBuffer.remaining();

                        byte[] yuvBytes = new byte[ySize + uSize + vSize];

                        // Copy Y, U, and V in the correct order (YUV)
                        yBuffer.get(yuvBytes, 0, ySize);
                        uBuffer.get(yuvBytes, ySize, uSize); // U plane is second
                        vBuffer.get(yuvBytes, ySize + uSize, vSize); // V plane is last

                        // 2. Create OpenCV Mat objects
                        //    Use the .put() method for safety
                        Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), org.opencv.core.CvType.CV_8UC1);
                        yuvMat.put(0, 0, yuvBytes);
                        Mat rgbaMat = new Mat();

                        // 3. Convert YUV (I420 format) to RGBA
                        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_I420, 4);

                        // 4. Send the RGBA Mat's memory address to C++
                        //    C++ code modifies rgbaMat to contain Canny Edges
                        processFrame(rgbaMat.getNativeObjAddr());

                        // 5. Convert the Canny Edges (which are 1-channel Grayscale data)
                        //    into JPEG bytes for the web server.
                        //    We use the Imgcodecs.imencode method.
                        MatOfByte mob = new MatOfByte();

                        // IMPORTANT: Before encoding, we need to convert the 4-channel Mat
                        // back to a simpler, single-channel image for Imgcodecs to handle
                        // (Since the web server doesn't need 4 channels).
                        Mat singleChannel = new Mat();
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
                        singleChannel.release(); // <-- Release the new Mat
                        mob.release(); // <-- Release the MatOfByte object

                        // 9. Close the image
                        image.close();
                    }
                });
                // --- END OF NEW PART ---


                // Unbind any previous use cases
                cameraProvider.unbindAll();

                // Bind the camera and BOTH use cases
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis);// [cite: 24]

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
    }

    // We don't need this C++ function in the main activity anymore
    // public native String stringFromJNI();
}