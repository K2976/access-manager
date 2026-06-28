# Limitations

AccessManager is designed to be highly reliable, but it operates under strict Android OS constraints. The following are genuine, real-world limitations of the current architecture.

## 1. JNI Bandwidth Cap
The interface between the Kotlin VpnService and the C++ Native Relay Engine uses a standard JNI method: `env->GetByteArrayElements` and `env->NewByteArray`. 
Every single downlink packet triggers a JNI callback into Kotlin. While this easily handles standard usage (Web browsing, 1080p video, background syncing), attempting to saturate a gigabit Wi-Fi connection (e.g., >500 Mbps speed tests) will hit a CPU bottleneck due to JNI context-switching and array copying overhead.

## 2. No Intra-Device Filtering
AccessManager captures outbound internet traffic via the `VpnService` `0.0.0.0/0` route. It does NOT capture `localhost` (`127.0.0.1`) traffic. If App A communicates with App B locally on the same device over localhost, AccessManager cannot block it.

## 3. Mutual Exclusion of VPNs
Android natively permits only **one** `VpnService` to be active at a time. If a user runs AccessManager, they cannot simultaneously run another VPN (e.g., NordVPN, ExpressVPN, or a corporate WireGuard tunnel). Starting another VPN will automatically revoke AccessManager's access.

## 4. No DNS Query Inspection
To maximize efficiency and compatibility, AccessManager does not intercept DNS (`UDP/53`). Android routes DNS queries over the underlying physical network because we do not specify a DNS server in `VpnBuilder`. Therefore, it is impossible to use AccessManager to block specific domains (e.g., blocking `facebook.com` while allowing the rest of a browser). AccessManager strictly blocks or allows the *entire* application.

## 5. IPv6-Only Networks Without NAT64
While extremely rare, if an ISP provides an IPv6-only network without offering NAT64/DNS64 translation, some application traffic may fail. AccessManager relies on the OS forcing traffic to IPv4 (because no IPv6 route is declared in the VPN). If IPv4 is genuinely unavailable at the physical layer, the OS will drop the traffic.
