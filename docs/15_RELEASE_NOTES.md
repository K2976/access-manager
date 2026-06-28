# Release Notes: AccessManager v1.0

We are thrilled to announce the v1.0 Release Candidate of AccessManager.

## Release Summary
AccessManager v1.0 delivers a fully functional, highly performant, per-application outbound firewall for Android (API 29+). Built on a robust C++ relay engine and modern Kotlin UI, it provides users with granular, root-less control over their device's internet connectivity.

## Core Features
*   **Per-App Firewalling:** Explicitly `ALLOW` or `BLOCK` network access for any installed application.
*   **Root-less Operation:** Utilizes standard Android `VpnService` APIs, requiring no device modifications.
*   **Transparent DNS:** Integrates seamlessly with the OS DNS resolver, bypassing the VPN for UDP/53 to guarantee compatibility and speed.
*   **High Performance:** Raw packet processing occurs entirely in C++ via a lock-free Actor threading model.
*   **Material 3 UI:** Clean, modern Jetpack Compose interface for managing app policies.

## Architecture Highlights
*   **lwIP Integration:** The battle-tested lightweight IP stack is embedded natively to handle TCP state machines and payload extraction safely.
*   **Strict Memory Safety:** JNI boundaries are strictly guarded; zero complex pointer sharing between threads.
*   **Robust Network Handovers:** Native implementation of `SO_KEEPALIVE` ensures rapid detection and recovery when switching between Wi-Fi and Cellular networks.

## Compatibility
*   Requires Android 10 (API 29) or higher.
*   Compiled for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` ABIs.

## Known Limitations
*   Requires exclusive access to the Android VPN slot. Cannot be run alongside other VPN services.
*   Only filters L3/L4 (IP/Port) traffic; no Layer 7 (HTTPS/URL) inspection.
*   Maximum throughput is constrained by per-packet JNI array copies (sufficient for standard use, but may bottleneck Gigabit speed tests).

## Breaking Changes
*(None. This is the initial stable release.)*
