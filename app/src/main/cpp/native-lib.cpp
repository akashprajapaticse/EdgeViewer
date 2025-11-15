#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

// Global flag to control Canny processing state
static bool g_canny_enabled = true;

// --- JNI Toggler Implementation ---
extern "C" JNIEXPORT void JNICALL
Java_com_example_edgeviewer_MainActivity_setCannyState(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled) {
    // Updates the global flag based on the Java button state
    g_canny_enabled = enabled;
}
// ----------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgeviewer_MainActivity_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jlong matAddrRgba) {

    // 1. Get the C++ 'Mat' object from the memory address
    //    We work directly on this reference, modifying the Java Mat in place.
    cv::Mat& frame = *(cv::Mat*)matAddrRgba;

    // --- Conditional Processing for Toggle Button ---
    if (g_canny_enabled) {
        // Only run Canny detection when the flag is true

        // 2. Create Mats for grayscale and edges
        cv::Mat gray;
        cv::Mat edges;

        // 3. Convert the frame to grayscale
        cv::cvtColor(frame, gray, cv::COLOR_RGBA2GRAY);

        // 4. Apply Canny edge detection
        cv::Canny(gray, edges, 100, 200);

        // 5. Convert the 1-channel edges Mat back to a 4-channel RGBA Mat
        //    This overwrites the original 'frame' (the Java Mat) with the processed image.
        cv::cvtColor(edges, frame, cv::COLOR_GRAY2RGBA);
    }
    // ELSE: If g_canny_enabled is false, the 'frame' Mat remains the original RGBA color frame (Raw Feed).
}