# 📄 Software Engineering Intern (R&D) Assessment: Real-Time Edge Detection Viewer

### I. Project Summary

This project implements a high-performance, real-time computer vision pipeline on Android by integrating **JNI (NDK)**, **OpenCV (C++)**, and **OpenGL ES 2.0+**. The solution achieves smooth, real-time rendering performance.

* **Final Output:** A real-time Canny Edge Detection Viewer.
* **Web Viewer:** A live, dynamic TypeScript debug tool (Bonus).

---

### II. Evaluation Checklist (Features Implemented)

| Area | Requirement | Status | Weight |
| :--- | :--- | :--- | :--- |
| **Native-C++ Integration** | JNI Integration (Frame Address Passing) | ✅ Done | 25% |
| **OpenCV Usage** | Apply Canny Edge Detection or Grayscale Filter (C++) | ✅ Done | 20% |
| **OpenGL Rendering** | Render Processed Image as a Texture (OpenGL ES 2.0+) | ✅ Done | 20% |
| **TypeScript Web Viewer** | Minimal Web Page & DOM Updates | ✅ Done | 20% |
| **Project Structure & History** | Modular Structure & Reflective Commit History | ✅ Done | 15% |
| **Bonus** | HTTP Endpoint (Live Server via NanoHTTPD) | 🌟 **Completed** | (Optional) |

---

### III. Architecture Explanation

#### A. Data Pipeline (JNI and OpenGL)

1.  **Frame Acquisition $\to$ JNI (25% Weight):** The Java layer uses the CameraX `ImageAnalysis` use case to capture frames. The `analyze()` method extracts the frame data, creates an OpenCV `Mat`, and passes its **memory address** to the native `processFrame()` function using **JNI**.
2.  **C++ Processing (20% Weight):** The native `processFrame()` function receives the frame as a `cv::Mat&` reference. [cite_start]It uses OpenCV to apply **Canny Edge Detection**, modifying the image data **in place**[cite: 27, 48].
3.  [cite_start]**Rendering (20% Weight):** The processed data is sent to the `MyGLRenderer`, uploaded as a texture using `GLES20.glTexImage2D`, and displayed on the `GLSurfaceView`[cite: 30, 31].

#### B. TypeScript Connection (Bonus)

* **Logic:** The Web Viewer, built with **TypeScript**, demonstrates comfort with project setup and DOM updates. It implements the optional HTTP Endpoint by hosting a server on the Android app, which serves the latest processed frame as a JPEG. [cite_start]The TypeScript client fetches this URL continually, creating a live stream illusion.

---

### IV. Screenshots

| Component | Description | Screenshot |
| :--- | :--- | :--- |
| **Android App** | Real-time Canny Edge Detection displayed in the correct orientation via OpenGL ES. |  |
| **Web Viewer** | Live status updates confirming C++ server activity via the forwarded port. |  |

---

### V. Setup Instructions

1.  **NDK/Dependencies:** Ensure Android API 36, NDK, and CMake are installed.
2.  **Web Viewer Setup:** Navigate to the `web/` folder and run `npm install` and `npx tsc`.
3.  **Network Setup (Crucial):** Run `adb reverse tcp:8080 tcp:8080` to establish the network bridge to the emulator.

---

### VI. Submission Criteria (Commit History)

**Mandatory Requirement (15% Weight):** The commit history for this repository reflects a **modular development process** and follows the sequential steps of **Setup, Core Integration, Rendering, and Bonus implementation**.