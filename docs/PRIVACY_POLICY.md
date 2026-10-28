# OpenDisplay TV Receiver Privacy Policy

**Effective date:** October 28, 2026

**App:** OpenDisplay TV Receiver (`im.amomo.opendisplay`)

**Developer:** MOMO

This policy describes the data handled by OpenDisplay TV Receiver (the “App”). Replace the developer and contact placeholders before publishing this policy.

## Information the App handles

The App is a receiver for a sender that you run on your Mac or another compatible device. When you start the receiver, it advertises its availability on your local network and listens for incoming connections.

While a sender is connected, the App receives and processes the video stream, cursor images and positions, and connection messages needed to display the stream and its status. The App also uses the connecting device's network address to identify the active connection in the interface and diagnostic logs. These items are handled on the receiver and are not uploaded by the App to a server operated by the developer.

To make the receiver discoverable and establish a connection, the App makes the following device information available to compatible devices on the same local network:

- A random installation ID, which is saved in the App's local preferences and advertised for device identification.
- The device model, local network addresses, supported protocol version, display dimensions, and an available cursor connection port.

The App saves the installation ID and your selected resolution mode in local app preferences. It does not create user accounts and does not request your contacts, precise location, microphone, or camera.

## How information is used

The App uses the information above only to discover the receiver, connect to a sender, decode and display the stream, render the cursor, and show connection status and performance information such as frame rate, bandwidth, and latency.

## Sharing and network connections

The App does not send the information it handles to the developer or to an analytics, advertising, or cloud service. The App includes no advertising or analytics SDKs.

The video and connection data travel directly between the sender and the receiver over your local network. The App also sends protocol messages and connection statistics, such as frame rate, bandwidth, and latency, back to the connected sender. The receiver announces its presence using local network discovery (mDNS/Bonjour). While the receiver is listening, another device that can reach it on that network may attempt to connect. The OpenDisplay protocol version used by this App does not provide authentication or encryption. Use the receiver only on a network you trust, and stop the receiver when you no longer need it.

## Storage, retention, and deletion

Video, cursor, and connection data are processed temporarily in memory while a session is active. The App does not record or persist the received stream. Session data is discarded when the connection ends or the receiver stops.

The installation ID and selected resolution remain in the App's local preferences until you clear the App's storage or uninstall it. Depending on your Android device and backup settings, Android may include app preferences in a device backup or restore them to a device. The developer does not operate that backup service. You can clear the App's local data in Android settings or uninstall the App to remove its local preferences.

The App may write connection status and technical diagnostic messages to the Android system log. The App does not upload these logs. Android controls system-log retention and access.

## Security

The App limits its network activity to the receiver functions described above. As noted in “Sharing and network connections,” the current protocol does not encrypt or authenticate sender connections. Local network security depends on the network and devices you use. Do not connect the receiver to an untrusted network when handling private screen content.

## Children

The App does not knowingly collect personal information from children. It does not include accounts, advertising, or behavioral analytics.

## Changes to this policy

If the App's data practices change, this policy will be updated. The effective date at the top of this page indicates when the current version took effect.
