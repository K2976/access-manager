# Testing Guide

Validating a VPN-based firewall requires observing system-wide behaviors. Standard unit tests cannot accurately replicate the Android kernel's routing behaviors or Wi-Fi/Cellular handovers.

## 1. Functional Testing (Blocked vs Allowed Apps)
**Setup:** Install a web browser (e.g., Chrome) and a background-sync app (e.g., WhatsApp). 
**Blocked Apps:** 
- Set Chrome to `BLOCKED` in AccessManager.
- Open Chrome, attempt to navigate to `https://google.com`.
- **Expected Result:** The browser should spin and eventually show `ERR_CONNECTION_TIMED_OUT` or `ERR_NAME_NOT_RESOLVED`. No data should load.
**Allowed Apps:**
- Set Chrome to `ALLOWED`.
- Refresh the page.
- **Expected Result:** The page should load instantly with no visible latency compared to non-VPN usage.

## 2. VPN Lifecycle Testing
**Setup:** Stream a continuous 1080p video in an `ALLOWED` app.
- **Action:** Open AccessManager and toggle the VPN off.
- **Expected Result:** The video stream should transition seamlessly to the raw physical network. The VPN key icon should disappear from the status bar.
- **Action:** Revoke the VPN via Android Settings (`Settings -> Network -> VPN -> AccessManager -> Disconnect`).
- **Expected Result:** The AccessManager app should detect the revocation (`onRevoke`), tear down native sockets cleanly, and reset its UI state without crashing.

## 3. Network Switching Testing
**Setup:** Connect to Wi-Fi. Stream a video in an `ALLOWED` app.
- **Action:** Turn off Wi-Fi (forcing fallback to Cellular).
- **Expected Result:** The video stream may pause for 1-3 seconds as TCP Keepalives cull the dead Wi-Fi sockets and the app re-establishes connections over the Cellular interface. Playback must resume. No native crash should occur.

## 4. Stress Testing
**Setup:** Use an app like `Termux` or an intensive BitTorrent client to open hundreds of concurrent connections.
- **Expected Result:** AccessManager's `SessionManager` should successfully allocate sockets. `poll()` should handle the load. If file descriptor limits are reached (`EMFILE`), `SessionManager` should safely refuse new connections without crashing the daemon.

## 5. Battery and Memory Testing
**Setup:** Leave AccessManager running for 24 hours with typical device usage.
- **Expected Result (Battery):** AccessManager should not appear in the top 5 battery-consuming apps in Android Battery settings.
- **Expected Result (Memory):** Use Android Studio Profiler. The Native Memory heap should remain stable (fluctuating based on active buffers but returning to a baseline when idle). It should not continuously grow (which would indicate a `pbuf` or `BackendEvent` leak).

## 6. Leak & Crash Testing
**Setup:** Rapidly toggle the VPN ON/OFF 50 times using a UI automation script or fast manual tapping.
- **Expected Result:** No JNI global reference leaks. No `UnsatisfiedLinkError`. No native `SIGSEGV` or `SIGABRT` tombstone in `logcat`.
