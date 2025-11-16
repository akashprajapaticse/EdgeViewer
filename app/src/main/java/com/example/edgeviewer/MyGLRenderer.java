package com.example.edgeviewer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "MyGLRenderer";

    // --- Shader Code (This runs on the GPU) ---

    // Vertex shader: Positions the vertices of our rectangle
    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec2 vTexCoord;" +
                    "varying vec2 fTexCoord;" +
                    "void main() {" +
                    "  gl_Position = vPosition;" +
                    "  fTexCoord = vTexCoord;" +
                    "}";

    // Fragment shader: "paints" the texture onto the rectangle
    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform sampler2D sTexture;" +
                    "varying vec2 fTexCoord;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(sTexture, fTexCoord);" +
                    "}";

    // --- OpenGL Program and Data Buffers ---

    private int programHandle;
    private int textureId;
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;

    // A simple square to draw on
    private static final float[] quadVertices = {
            -1.0f, -1.0f,  // Bottom Left
            1.0f, -1.0f,  // Bottom Right
            -1.0f,  1.0f,  // Top Left
            1.0f,  1.0f   // Top Right
    };

    private static final float[] texCoords = {
            1.0f, 1.0f,   // Bottom Left
            1.0f, 0.0f,   // Bottom Right
            0.0f, 1.0f,   // Top Left
            0.0f, 0.0f    // Top Right
    };

    // --- Frame Data (to pass from C++ to the renderer) ---

    private int frameWidth, frameHeight;
    private ByteBuffer frameBuffer = null;
    private boolean frameDirty = false;

    /**
     * This method is called from the ImageAnalysis thread to update the frame.
     * It copies the data from the Mat into a byte buffer.
     */
    public synchronized void updateFrame(Mat frame) {
        frameWidth = frame.width();
        frameHeight = frame.height();

        int dataSize = (int) (frame.total() * frame.channels());
        if (frameBuffer == null || frameBuffer.capacity() < dataSize) {
            // Allocate a new buffer if we don't have one or it's too small
            frameBuffer = ByteBuffer.allocateDirect(dataSize);
            frameBuffer.order(ByteOrder.nativeOrder());
        }

        byte[] frameData = new byte[dataSize];
        frame.get(0, 0, frameData); // Get all bytes from the Mat

        frameBuffer.rewind();
        frameBuffer.put(frameData); // Put the bytes into the buffer
        frameBuffer.rewind();

        frameDirty = true; // Mark the frame as "dirty" (needs redraw)
    }

    /**
     * Helper method to load and compile OpenGL shaders.
     */
    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // Check for compile errors
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + type + ":");
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * Called once when the surface is created.
     * This is where we set up our shaders and buffers.
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Set the clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // --- Compile Shaders ---
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        programHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(programHandle, vertexShader);
        GLES20.glAttachShader(programHandle, fragmentShader);
        GLES20.glLinkProgram(programHandle);

        // --- Create Buffers ---
        ByteBuffer vb = ByteBuffer.allocateDirect(quadVertices.length * 4);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(quadVertices);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tb.asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

        // --- Create Texture ---
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * Called when the surface changes size (e.g., screen rotation).
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    /**
     * Called on every frame redraw.
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        synchronized (this) {
            if (frameDirty) {
                // If we have a new frame, upload it to the texture
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        frameWidth, frameHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frameBuffer);
                frameDirty = false;
            }
        }

        // Use our shader program
        GLES20.glUseProgram(programHandle);

        // --- Pass in vertex data ---
        int posHandle = GLES20.glGetAttribLocation(programHandle, "vPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // --- Pass in texture coordinate data ---
        int texHandle = GLES20.glGetAttribLocation(programHandle, "vTexCoord");
        GLES20.glEnableVertexAttribArray(texHandle);
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // --- Set the active texture ---
        int samplerHandle = GLES20.glGetUniformLocation(programHandle, "sTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerHandle, 0);

        // Draw the rectangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Disable attributes
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(texHandle);
    }
}