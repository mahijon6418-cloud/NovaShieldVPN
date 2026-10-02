# NovaShield VPN
Standalone Android VPN client shell with a different name/UI, built without Avast proprietary code.

## Build

The repository is a standard Android Gradle project. Build a debug APK with Gradle
8.10.2, JDK 17, and the Android 35 SDK installed:

```bash
gradle assembleDebug --stacktrace
```

The generated APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Access code
The default code is embedded for the requested prototype and can also be changed from **ENTER ACCESS CODE**.

## Important
The current service establishes Android's TUN interface only. It is **not an implementation of Avast Mimic** and will not connect to Avast's infrastructure. To make it a working VPN, provide an authorized VPN backend/protocol specification (for example WireGuard/OpenVPN or your own server protocol) and the tunnel transport can be integrated here.
