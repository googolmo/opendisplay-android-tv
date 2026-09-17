# OpenDisplay Android TV (Receiver)

An open-source **Android TV / Google TV receiver** implementation for [OpenDisplay](https://github.com/peetzweg/opendisplay).

This application turns your Android TV, Chromecast with Google TV, or Android TV box into a high-performance, low-latency secondary wireless display for macOS.

---

## Features

- **Full OpenDisplay Protocol v3 Support**:
  - Implements the normative [OpenDisplay Wire Protocol (`pv=3`)](PROTOCOL.md).
  - Bonjour / mDNS Service Discovery advertising as `_opensidecar._tcp` (resolves natively on macOS).
  - Multiplexed single-TCP stream handling video (H.264 Annex B), telemetry, and control frames.
  - Heartbeat / Ping-Pong latency measurement (RTT & Clock offset calculation).
  - UDP side-channel support for high-frequency cursor position updates.
- **Hardware-Accelerated Decoding**:
  - Leverages Android's low-latency `MediaCodec` H.264 API with real-time priority hints.
  - Smooth rendering directly to native `SurfaceView`.
- **Customizable Resolution Modes**:
  - **Auto**: Automatically adopts native/maximum display bounds.
  - **1080p (1920 × 1080)**
  - **1440p (2560 × 1440)**
  - **4K UHD (3840 × 2160)**
- **Remote Mouse Pointer Rendering**:
  - Overlays macOS mouse pointer directly on the receiver with support for base64 PNG cursor sprite caches, hotspot offsets, and normalized coordinates.
- **Modern Android TV UI**:
  - Built with Jetpack Compose for TV (`androidx.tv.material3`).
  - Immersive full-screen playback with auto-hiding HUD dashboard showing real-time FPS, bitrate (Mbps), and RTT latency.
  - D-pad friendly controls and screen keep-awake handling.

---

## How It Works

```
┌───────────────────────────────┐                  ┌─────────────────────────────────┐
│     macOS (Sender)            │                  │    Android TV (Receiver)        │
│  https://github.com/peetzweg/ │                  │    OpenDisplay Android TV       │
│         opendisplay           │                  │                                 │
│                               │                  │  • Listens on TCP port 9000     │
│  1. Discovers receiver via    │  mDNS Discovery  │  • Advertises _opensidecar._tcp │
│     mDNS (_opensidecar._tcp)  ├─────────────────►│                                 │
│                               │                  │                                 │
│  2. Connects TCP socket       │  TCP :9000       │  • Performs handshake (pv=3)    │
│     (Handshake, Capabilities) ├─────────────────►│  • Sends device bounds & scale   │
│                               │                  │                                 │
│  3. Streams H.264 video &     │  H.264 & Control │  • Low-latency MediaCodec decode│
│     cursor events             ├─────────────────►│  • Renders to SurfaceView       │
│                               │                  │  • Displays cursor overlay      │
└───────────────────────────────┘                  └─────────────────────────────────┘
```

1. When launched, the receiver binds to **TCP port 9000** (with fallback to 9001) and registers mDNS service `_opensidecar._tcp` on your local network.
2. The [OpenDisplay macOS app](https://github.com/peetzweg/opendisplay) automatically detects your Android TV device.
3. Once connected, screen frames encoded in H.264 Annex B are decoded in real-time by the hardware decoder and displayed on screen.

---

## Requirements

### On Your TV / Receiver
- Android TV / Google TV device running **Android 10.0 (API 29)** or higher.
- Connected to the same local network (Wi-Fi or Ethernet) as your Mac.

### On Your Mac (Sender)
- [OpenDisplay for macOS](https://github.com/peetzweg/opendisplay).

---

## Getting Started

### Building from Source

Prerequisites:
- Android Studio Ladybug / Meerkat or later
- JDK 17+
- Android SDK with API 36

Clone the repository and compile debug APK:

```bash
git clone https://github.com/googolmo/opendisplay-android-tv.git
cd opendisplay-android-tv

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing to Android TV

Enable **Developer Options** and **ADB Debugging** on your Android TV:
1. Go to **Settings** > **Device Preferences** > **About**.
2. Click **Build** 7 times until developer mode is enabled.
3. Go to **Settings** > **Developer Options** > Enable **Network debugging** (or USB debugging).
4. Connect via ADB:
   ```bash
   adb connect <YOUR_TV_IP_ADDRESS>:5555
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Usage

1. Open **OpenDisplay** on your Android TV.
2. Ensure the status banner indicates **LISTENING ON PORT 9000**.
3. On your Mac, launch [OpenDisplay](https://github.com/peetzweg/opendisplay).
4. Click on your Android TV from the OpenDisplay menu bar icon / device list.
5. The TV will automatically switch into full-screen mirroring/extension mode.
6. **Remote Control Tips**:
   - Press **BACK** or tap to show/hide the HUD overlay (FPS, Bandwidth, Latency).
   - Press **Exit Full Screen** to return to the receiver dashboard and change resolution presets.

---

## Configuration & Resolution Presets

On the dashboard screen, you can configure the preferred resolution advertised to the sender:
- **Auto (Native/Max)**: Resolves the physical display window bounds automatically.
- **1080p (1920×1080)**: Recommended for standard Wi-Fi setups and older Android TV sticks.
- **1440p (2560×1440)**: Crisp display for 2K/4K TVs with stable 5GHz Wi-Fi.
- **4K UHD (3840×2160)**: Ultra high resolution (requires high-bandwidth Ethernet or Wi-Fi 6).

---

## Protocol Specification

For full details on packet framing, length headers, JSON control messages, demuxing heuristics, and handshake flows, refer to [PROTOCOL.md](PROTOCOL.md).

---

## Related Projects

- [peetzweg/opendisplay](https://github.com/peetzweg/opendisplay) - OpenDisplay macOS sender and official iOS client.

---

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
