document.addEventListener("DOMContentLoaded", () => {
    // Find the stats element
    const statsElement = document.getElementById('frame-stats') as HTMLDivElement;

    // Find the iframe element by its ID
    const iframeElement = document.getElementById('live-stream-iframe') as HTMLIFrameElement;

    let frameCount = 0;
    let startTime = Date.now();
    let fps = 0;

    const updateFrame = () => {
        // 1. Update the iframe source to force a re-fetch of the latest frame
        //    We add a timestamp to the URL to prevent the browser from caching.
        if (iframeElement) {
            iframeElement.src = `http://localhost:8080/frame.jpg?t=${Date.now()}`;
        }

        // 2. Calculate FPS (same logic)
        frameCount++;
        if (frameCount % 10 === 0) {
            const currentTime = Date.now();
            const elapsedTime = (currentTime - startTime) / 1000;
            fps = Math.round(frameCount / elapsedTime);
        }

        // 3. Update the stats text
        if (statsElement) {
            statsElement.innerText = `Frame: ${frameCount} | Resolution: 640x480 | FPS: ${fps} (Live)`;
        }
    };

    // Start the continuous refresh loop
    setInterval(updateFrame, 100); 
});