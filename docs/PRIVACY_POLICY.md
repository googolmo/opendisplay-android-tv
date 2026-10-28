# OpenDisplay TV Receiver Privacy Policy

**Effective date:** October 28, 2026

**App:** OpenDisplay TV Receiver (`im.amomo.opendisplay`)

**Developer:** [Enter the developer's legal name or organization name]

**Privacy contact:** [Enter a monitored privacy contact email address]

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

## Contact

For privacy questions, contact **[Enter a monitored privacy contact email address]**. The developer responsible for the App is **[Enter the developer's legal name or organization name]**.

---

# OpenDisplay TV Receiver 隐私政策

**生效日期：** 2026 年 10 月 28 日

**应用：** OpenDisplay TV Receiver（`im.amomo.opendisplay`）

**开发者：** [填写开发者法定姓名或组织名称]

**隐私联系邮箱：** [填写可接收隐私咨询的邮箱]

本政策说明 OpenDisplay TV Receiver（以下简称“本应用”）如何处理数据。发布前请填写开发者名称和联系邮箱。

## 本应用处理的信息

本应用是接收端，需要由你在 Mac 或其他兼容设备上运行发送端。你启动接收服务后，本应用会在本地网络中广播服务并等待连接。

发送端连接后，本应用会接收并处理视频流、光标图像和位置，以及显示画面和连接状态所需的控制消息。这些数据在接收设备上处理，本应用不会将其上传到开发者运营的服务器。本应用也会在界面和诊断日志中显示连接设备的网络地址。

为便于同一局域网内的兼容设备发现并连接接收端，本应用会向这些设备提供以下信息：

- 随机生成的安装 ID。该 ID 保存在本应用的本地偏好设置中，并通过局域网服务发现广播，用于识别接收设备。
- 设备型号、本地网络地址、支持的协议版本、显示尺寸和可用的光标连接端口。

本应用会在本地保存安装 ID 和你选择的分辨率模式。本应用不创建用户账号，也不请求通讯录、精确位置、麦克风或摄像头权限。

## 信息用途

本应用仅使用上述信息来发现接收端、连接发送端、解码并显示画面、绘制光标，以及显示连接状态和帧率、带宽、延迟等性能信息。

## 信息共享与网络连接

本应用不会把处理的信息发送给开发者或任何分析、广告或云服务。本应用不包含广告或分析 SDK。

视频和连接数据通过局域网在发送端与接收端之间直接传输。本应用也会向已连接的发送端发送协议消息和连接统计信息，例如帧率、带宽和延迟。本应用使用局域网服务发现（mDNS/Bonjour）广播接收端。接收服务运行期间，能够访问该设备的同一网络中的其他设备可能尝试连接。本应用所用的 OpenDisplay 协议版本不提供身份验证或加密。请仅在可信网络中使用本应用；不需要时请停止接收服务。

## 存储、保留和删除

视频、光标和连接数据只在会话期间临时保存在内存中。本应用不会录制或持久化保存收到的画面。连接结束或接收服务停止后，会话数据即被丢弃。

安装 ID 和所选分辨率会保留在本应用的本地偏好设置中，直到你清除本应用存储空间或卸载应用。根据 Android 设备和备份设置，Android 可能会将应用偏好设置纳入设备备份，或在设备上恢复这些设置。该备份服务不由开发者运营。你可以在 Android 设置中清除本应用的数据，或卸载本应用，以删除本地偏好设置。

本应用可能会向 Android 系统日志写入连接状态和技术诊断消息。本应用不会上传这些日志。系统日志的保留和访问由 Android 管理。

## 安全

本应用的网络活动仅用于上述接收功能。如“信息共享与网络连接”所述，当前协议不对发送端连接进行加密或身份验证。本地网络安全取决于你使用的网络和设备。处理私人屏幕内容时，请勿在不可信网络中运行接收服务。

## 儿童

本应用不会有意收集儿童的个人信息。本应用不包含账号、广告或行为分析功能。

## 政策变更

如果本应用的数据处理方式发生变化，我们会更新本政策。本页面顶部的生效日期表示当前版本开始生效的日期。

## 联系方式

如有隐私问题，请联系 **[填写可接收隐私咨询的邮箱]**。本应用的责任开发者为 **[填写开发者法定姓名或组织名称]**。
