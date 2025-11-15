#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgeviewer_MainActivity_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jlong matAddrRgba) {

    // 1. Get the C++ 'Mat' object from the memory address
    cv::Mat& frame = *(cv::Mat*)matAddrRgba;

    // 2. Create a Mat for the grayscale image
    cv::Mat gray;

    // 3. Convert the frame to grayscale
    cv::cvtColor(frame, gray, cv::COLOR_RGBA2GRAY);

    // 4. (For the assignment) Apply Canny edge detection
    cv::Mat edges;
    cv::Canny(gray, edges, 100, 200);

    // 5. For now, we'll put the grayscale image
    //    back into the original frame to prove it works.
    //    This converts the 1-channel gray image back to a
    //    4-channel RGBA image so it can be displayed.
    cv::cvtColor(edges, frame, cv::COLOR_GRAY2RGBA);
}