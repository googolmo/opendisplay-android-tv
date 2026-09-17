package im.amomo.opendisplay.server

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import im.amomo.opendisplay.decoder.H264Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

enum class ResolutionMode(val displayName: String, val width: Int?, val height: Int?) {
    AUTO("Auto (Native/Max)", null, null),
    FHD("1080p (1920x1080)", 1920, 1080),
    QHD("1440p (2560x1440)", 2560, 1440),
    UHD_4K("4K UHD (3840x2160)", 3840, 2160)
}

data class ClientState(
    val clientAddress: String,
    val connectedTimeMs: Long = System.currentTimeMillis(),
    val senderPv: Int? = null,
    val senderMinPv: Int? = null,
    val rttMs: Long? = null,
    val clockOffsetMs: Long? = null,
    val videoFramesReceived: Long = 0,
    val controlMessagesReceived: Long = 0,
    val bytesReceived: Long = 0,
    val fps: Float = 0f,
    val mbps: Float = 0f,
    val lastCursorPos: Pair<Float, Float>? = null,
    val cursorVisible: Boolean = false,
    val cursorBitmap: Bitmap? = null,
    val cursorNormWidth: Float? = null,
    val cursorNormHeight: Float? = null,
    val cursorHotspotX: Float = 0f,
    val cursorHotspotY: Float = 0f,
    val udpCursorActive: Boolean = false,
    val lastMessageType: String? = null
)

data class ServerState(
    val isListening: Boolean = false,
    val port: Int = 9000,
    val cursorPort: Int? = null,
    val localIpAddresses: List<String> = emptyList(),
    val deviceId: String = "",
    val deviceName: String = "",
    val mDnsRegistered: Boolean = false,
    val resolutionMode: ResolutionMode = ResolutionMode.AUTO,
    val currentResolution: Pair<Int, Int> = Pair(1920, 1080),
    val connectedClient: ClientState? = null,
    val lastError: String? = null
)

class OpenDisplayServer(private val context: Context) {

    companion object {
        private const val TAG = "OpenDisplayServer"
        private const val DEFAULT_PORT = 9000
        private const val FALLBACK_PORT = 9001
        private const val SERVICE_TYPE = "_opensidecar._tcp."
        private const val PREFS_NAME = "opendisplay_prefs"
        private const val PREF_DEVICE_ID = "device_id"
        private const val PREF_RESOLUTION_MODE = "resolution_mode"
    }

    @Volatile
    private var isStarting = false

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val deviceId: String = getOrCreateDeviceId()
    val deviceName: String = "OpenDisplay-${Build.MODEL}"

    private val _serverState: MutableStateFlow<ServerState>
    val serverState: StateFlow<ServerState>

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeOutputStream: DataOutputStream? = null

    private var udpSocket: DatagramSocket? = null
    private var udpJob: Job? = null

    @Volatile
    private var lastCursorSeq: Long = -1L

    @Volatile
    private var cursorAckSent: Boolean = false

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private var statsJob: Job? = null
    private var clientJob: Job? = null

    private val decoder = H264Decoder()

    init {
        val savedModeName = prefs.getString(PREF_RESOLUTION_MODE, ResolutionMode.AUTO.name)
        val savedMode = try {
            ResolutionMode.valueOf(savedModeName ?: ResolutionMode.AUTO.name)
        } catch (_: Exception) {
            ResolutionMode.AUTO
        }
        val initialRes = getTargetResolution(savedMode)
        _serverState = MutableStateFlow(
            ServerState(
                deviceId = deviceId,
                deviceName = deviceName,
                resolutionMode = savedMode,
                currentResolution = initialRes
            )
        )
        serverState = _serverState.asStateFlow()
        decoder.configureResolution(initialRes.first, initialRes.second)
    }

    fun getTargetResolution(mode: ResolutionMode = _serverState.value.resolutionMode): Pair<Int, Int> {
        if (mode.width != null && mode.height != null) {
            return Pair(mode.width, mode.height)
        }

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val display = windowManager?.defaultDisplay
            val supportedModes = display?.supportedModes
            if (!supportedModes.isNullOrEmpty()) {
                val maxMode = supportedModes.maxByOrNull { it.physicalWidth * it.physicalHeight }
                if (maxMode != null && maxMode.physicalWidth > 0 && maxMode.physicalHeight > 0) {
                    return Pair(maxMode.physicalWidth, maxMode.physicalHeight)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query display physical modes", e)
        }

        val displayMetrics = context.resources.displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    fun setResolutionMode(mode: ResolutionMode) {
        prefs.edit().putString(PREF_RESOLUTION_MODE, mode.name).apply()
        val targetRes = getTargetResolution(mode)
        _serverState.value = _serverState.value.copy(
            resolutionMode = mode,
            currentResolution = targetRes
        )
        decoder.configureResolution(targetRes.first, targetRes.second)
        Log.d(TAG, "Resolution mode set to ${mode.displayName}, target=${targetRes.first}x${targetRes.second}")
    }

    fun setSurface(surface: Surface?) {
        decoder.setSurface(surface)
        if (surface != null && _serverState.value.connectedClient != null) {
            requestKeyframe()
        }
    }

    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString(PREF_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        ips.add(address.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP addresses", e)
        }
        return ips
    }

    @Synchronized
    fun start(port: Int = DEFAULT_PORT) {
        if (_serverState.value.isListening || isStarting) return
        isStarting = true

        serverScope.launch {
            var targetPort = port
            try {
                val ss = try {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(targetPort))
                    }
                } catch (e: Exception) {
                    if (targetPort == DEFAULT_PORT) {
                        Log.w(TAG, "Port $targetPort is unavailable, trying fallback port $FALLBACK_PORT", e)
                        targetPort = FALLBACK_PORT
                        ServerSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(targetPort))
                        }
                    } else {
                        throw e
                    }
                }

                serverSocket = ss
                Log.d(TAG, "Server listening on port $targetPort")

                // Bind UDP listener for cursor side channel (Section 6.3)
                val udpTargetPort = targetPort + 1
                val ds = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(udpTargetPort))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Port $udpTargetPort unavailable for UDP cursor, trying ephemeral port", e)
                    try {
                        DatagramSocket(0)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to bind DatagramSocket for cursor side channel", ex)
                        null
                    }
                }
                udpSocket = ds
                val boundCursorPort = ds?.localPort
                Log.d(TAG, "UDP cursor listener bound on port $boundCursorPort")

                val localIps = getLocalIpAddresses()
                _serverState.value = _serverState.value.copy(
                    isListening = true,
                    port = targetPort,
                    cursorPort = boundCursorPort,
                    localIpAddresses = localIps,
                    lastError = null
                )

                if (ds != null) {
                    startUdpCursorLoop(ds)
                }

                // Register mDNS / Bonjour with bound port
                registerMdns(targetPort)

                while (isActive) {
                    val socket = ss.accept()
                    Log.d(TAG, "New connection accepted from ${socket.remoteSocketAddress}")
                    handleNewConnection(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server socket error", e)
                    _serverState.value = _serverState.value.copy(
                        isListening = false,
                        lastError = e.localizedMessage ?: "Server socket error"
                    )
                }
            } finally {
                isStarting = false
                stopInternal()
            }
        }
    }

    private fun handleNewConnection(socket: Socket) {
        // Drop previous active connection if exists (Section 1: adopt new, drop old)
        activeSocket?.let { oldSocket ->
            Log.d(TAG, "Closing previous active connection")
            try {
                oldSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing old socket", e)
            }
        }

        activeSocket = socket
        clientJob?.cancel()

        // Reset sequence number tracking and ack state for new TCP session
        lastCursorSeq = -1L
        cursorAckSent = false

        val clientAddress = socket.remoteSocketAddress.toString()
        _serverState.value = _serverState.value.copy(
            connectedClient = ClientState(clientAddress = clientAddress)
        )

        clientJob = serverScope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                activeOutputStream = output

                // 1. Send 'hello' message immediately (Section 6.1)
                sendHello(output)

                // 2. Start ping loop (every 2s) and stats loop (every 5s)
                startPingLoop(output)
                startStatsLoop(output)

                // 3. Read loop
                readLoop(input)
            } catch (e: Exception) {
                if (e !is EOFException && isActive) {
                    Log.d(TAG, "Client disconnected or error: ${e.message}")
                }
            } finally {
                Log.d(TAG, "Cleaning up client connection")
                pingJob?.cancel()
                statsJob?.cancel()
                try {
                    socket.close()
                } catch (_: Exception) {}
                if (activeSocket == socket) {
                    activeSocket = null
                    activeOutputStream = null
                    _serverState.value = _serverState.value.copy(connectedClient = null)
                }
            }
        }
    }

    private fun sendHello(output: DataOutputStream) {
        val targetRes = getTargetResolution()
        val pixelsWide = targetRes.first
        val pixelsHigh = targetRes.second
        val scale = if (pixelsWide >= 3840) 2.0 else 1.0

        decoder.configureResolution(pixelsWide, pixelsHigh)

        val helloJson = JSONObject().apply {
            put("type", "hello")
            put("pixelsWide", pixelsWide)
            put("pixelsHigh", pixelsHigh)
            put("scale", scale)
            put("device", Build.MODEL)
            put("id", deviceId)
            put("pv", 3)
            _serverState.value.cursorPort?.let {
                put("cursorPort", it)
            }
            val addrsArray = JSONArray()
            for (ip in getLocalIpAddresses()) {
                addrsArray.put(ip)
            }
            put("addrs", addrsArray)
        }

        writeFrame(output, helloJson.toString().toByteArray(Charsets.UTF_8))
        Log.d(TAG, "Sent hello with resolution ${pixelsWide}x${pixelsHigh} (scale=$scale): $helloJson")
    }

    private fun startPingLoop(output: DataOutputStream) {
        pingJob?.cancel()
        pingJob = serverScope.launch {
            while (isActive) {
                delay(2000L)
                try {
                    val pingJson = JSONObject().apply {
                        put("type", "ping")
                        put("t", System.currentTimeMillis())
                    }
                    writeFrame(output, pingJson.toString().toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending ping", e)
                    break
                }
            }
        }
    }

    private fun startStatsLoop(output: DataOutputStream) {
        statsJob?.cancel()
        statsJob = serverScope.launch {
            var lastVideoFrames = 0L
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()

            while (isActive) {
                delay(5000L)
                val now = System.currentTimeMillis()
                val dtSec = (now - lastTime) / 1000.0f
                if (dtSec <= 0) continue

                val client = _serverState.value.connectedClient ?: continue
                val currentVideoFrames = client.videoFramesReceived
                val currentBytes = client.bytesReceived

                val fps = (currentVideoFrames - lastVideoFrames) / dtSec
                val mbps = ((currentBytes - lastBytes) * 8f) / (dtSec * 1_000_000f)

                lastVideoFrames = currentVideoFrames
                lastBytes = currentBytes
                lastTime = now

                _serverState.value = _serverState.value.copy(
                    connectedClient = _serverState.value.connectedClient?.copy(
                        fps = fps,
                        mbps = mbps
                    )
                )

                try {
                    val statsJson = JSONObject().apply {
                        put("type", "stats")
                        put("transport", "WiFi")
                        put("fps", fps)
                        put("mbps", mbps)
                        client.rttMs?.let { put("rtt", it) }
                    }
                    writeFrame(output, statsJson.toString().toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending stats", e)
                    break
                }
            }
        }
    }

    @Synchronized
    private fun writeFrame(output: DataOutputStream, payload: ByteArray) {
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readLoop(input: DataInputStream) {
        var videoFramesCount = 0L
        var controlCount = 0L
        var totalBytesCount = 0L

        while (serverScope.isActive) {
            // Header: 4-byte big-endian length
            val length = input.readInt()
            if (length <= 0) break

            val payload = ByteArray(length)
            input.readFully(payload)
            totalBytesCount += (4 + length)

            val isJson = isJsonControlFrame(payload)

            if (isJson) {
                controlCount++
                val jsonStr = String(payload, Charsets.UTF_8)
                handleControlMessage(jsonStr)
            } else {
                videoFramesCount++
                handleVideoFrame(payload)
            }

            _serverState.value = _serverState.value.copy(
                connectedClient = _serverState.value.connectedClient?.copy(
                    videoFramesReceived = videoFramesCount,
                    controlMessagesReceived = controlCount,
                    bytesReceived = totalBytesCount
                )
            )
        }
    }

    /**
     * Section 4 Channel Demux Rule:
     * A frame is JSON control message if and only if:
     * 1. payload length < 32768 bytes, and
     * 2. the first byte is '{' (0x7B), and
     * 3. payload contains no NUL byte (0x00).
     */
    private fun isJsonControlFrame(payload: ByteArray): Boolean {
        if (payload.size >= 32768) return false
        if (payload.isEmpty() || payload[0] != '{'.code.toByte()) return false
        for (b in payload) {
            if (b == 0.toByte()) return false
        }
        return true
    }

    private fun handleControlMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", "")
            Log.d(TAG, "Received control message type: $type")

            val currentClient = _serverState.value.connectedClient ?: return
            var updatedClient = currentClient.copy(lastMessageType = type)

            when (type) {
                "welcome" -> {
                    val pv = json.optInt("pv", 1)
                    val min = json.optInt("min", 1)
                    updatedClient = updatedClient.copy(senderPv = pv, senderMinPv = min)
                    Log.d(TAG, "Sender welcome: pv=$pv, min=$min")
                }

                "pong" -> {
                    val t1 = json.optLong("t", 0L)
                    val mt = json.optLong("mt", 0L)
                    val t2 = System.currentTimeMillis()
                    if (t1 > 0) {
                        val rtt = t2 - t1
                        val offset = mt - (t1 + t2) / 2
                        updatedClient = updatedClient.copy(rttMs = rtt, clockOffsetMs = offset)
                        Log.d(TAG, "Pong received: RTT=${rtt}ms, offset=${offset}ms")
                    }
                }

                "cursor" -> {
                    val s = json.optLong("s", -1L)
                    if (s >= 0) {
                        if (s <= lastCursorSeq) {
                            // Superseded by newer cursor message
                            return
                        }
                        lastCursorSeq = s
                    }

                    val v = json.optInt("v", 0)
                    if (v == 1 && json.has("x") && json.has("y")) {
                        val x = json.optDouble("x", 0.0).toFloat()
                        val y = json.optDouble("y", 0.0).toFloat()
                        updatedClient = updatedClient.copy(
                            cursorVisible = true,
                            lastCursorPos = Pair(x, y)
                        )
                    } else {
                        updatedClient = updatedClient.copy(cursorVisible = false)
                    }
                }

                "cursorImg" -> {
                    val pngBase64 = json.optString("png", "")
                    val nw = if (json.has("nw")) json.optDouble("nw", 0.0).toFloat() else null
                    val nh = if (json.has("nh")) json.optDouble("nh", 0.0).toFloat() else null
                    val ax = json.optDouble("ax", 0.0).toFloat()
                    val ay = json.optDouble("ay", 0.0).toFloat()

                    var bitmap: Bitmap? = null
                    if (pngBase64.isNotEmpty()) {
                        try {
                            val bytes = Base64.decode(pngBase64, Base64.DEFAULT)
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to decode cursorImg PNG", e)
                        }
                    }

                    updatedClient = updatedClient.copy(
                        cursorBitmap = bitmap,
                        cursorNormWidth = nw,
                        cursorNormHeight = nh,
                        cursorHotspotX = ax,
                        cursorHotspotY = ay
                    )
                    Log.d(TAG, "Updated cursorImg: nw=$nw, nh=$nh, ax=$ax, ay=$ay, bitmap=${bitmap?.width}x${bitmap?.height}")
                }

                "updateRequired" -> {
                    val msg = json.optString("message", "Update required")
                    Log.w(TAG, "Update required by sender: $msg")
                    _serverState.value = _serverState.value.copy(lastError = "Update Required: $msg")
                }
            }

            _serverState.value = _serverState.value.copy(connectedClient = updatedClient)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON control message", e)
        }
    }

    private fun startUdpCursorLoop(socket: DatagramSocket) {
        udpJob?.cancel()
        udpJob = serverScope.launch {
            val buffer = ByteArray(1024)
            while (isActive && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val datagramStr = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    handleCursorDatagram(datagramStr)
                } catch (e: Exception) {
                    if (!isActive || socket.isClosed) break
                    Log.w(TAG, "Error receiving UDP cursor packet", e)
                }
            }
        }
    }

    private fun handleCursorDatagram(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", "")
            if (type != "cursor") return

            val s = json.optLong("s", -1L)
            if (s >= 0) {
                if (s <= lastCursorSeq) {
                    // Drop superseded or duplicate UDP cursor datagram
                    return
                }
                lastCursorSeq = s
            }

            val currentClient = _serverState.value.connectedClient ?: return
            val v = json.optInt("v", 0)
            val updatedClient = if (v == 1 && json.has("x") && json.has("y")) {
                val x = json.optDouble("x", 0.0).toFloat()
                val y = json.optDouble("y", 0.0).toFloat()
                currentClient.copy(
                    cursorVisible = true,
                    lastCursorPos = Pair(x, y),
                    udpCursorActive = true,
                    lastMessageType = "cursor(UDP)"
                )
            } else {
                currentClient.copy(
                    cursorVisible = false,
                    udpCursorActive = true,
                    lastMessageType = "cursor(UDP)"
                )
            }

            _serverState.value = _serverState.value.copy(connectedClient = updatedClient)

            if (!cursorAckSent) {
                sendCursorAck()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse UDP cursor datagram", e)
        }
    }

    private fun sendCursorAck() {
        val output = activeOutputStream ?: return
        cursorAckSent = true
        serverScope.launch {
            try {
                val ackJson = JSONObject().apply {
                    put("type", "cursorAck")
                }
                writeFrame(output, ackJson.toString().toByteArray(Charsets.UTF_8))
                Log.d(TAG, "Sent cursorAck over TCP")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send cursorAck", e)
            }
        }
    }

    private fun handleVideoFrame(payload: ByteArray) {
        // Section 5.1: Video frame layout [optional JSON telemetry prefix][00 00 00 01][NALU]...
        // Search for 4-byte start code 00 00 00 01
        var startCodeIdx = -1
        for (i in 0..payload.size - 4) {
            if (payload[i] == 0.toByte() &&
                payload[i + 1] == 0.toByte() &&
                payload[i + 2] == 0.toByte() &&
                payload[i + 3] == 1.toByte()
            ) {
                startCodeIdx = i
                break
            }
        }

        if (startCodeIdx < 0) {
            Log.w(TAG, "Video frame received without 00 00 00 01 start code")
            return
        }

        // Decode video payload starting at startCodeIdx
        val videoLength = payload.size - startCodeIdx
        decoder.decodeFrame(payload, startCodeIdx, videoLength)
    }

    /**
     * Send 'kf' message to request keyframe from sender
     */
    fun requestKeyframe() {
        val output = activeOutputStream ?: return
        serverScope.launch {
            try {
                val kfJson = JSONObject().apply {
                    put("type", "kf")
                }
                writeFrame(output, kfJson.toString().toByteArray(Charsets.UTF_8))
                Log.d(TAG, "Sent keyframe request ('kf')")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send keyframe request", e)
            }
        }
    }

    private fun registerMdns(port: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = deviceName
                serviceType = SERVICE_TYPE
                setPort(port)
                setAttribute("id", deviceId)
                setAttribute("pv", "3")
            }

            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.d(TAG, "mDNS service registered: ${NsdServiceInfo.serviceName}")
                    _serverState.value = _serverState.value.copy(mDnsRegistered = true)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS registration failed: errorCode $errorCode")
                    _serverState.value = _serverState.value.copy(mDnsRegistered = false)
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.d(TAG, "mDNS service unregistered")
                    _serverState.value = _serverState.value.copy(mDnsRegistered = false)
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS unregistration failed: errorCode $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register mDNS service", e)
        }
    }

    private fun unregisterMdns() {
        try {
            registrationListener?.let {
                nsdManager?.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister mDNS service", e)
        } finally {
            registrationListener = null
            _serverState.value = _serverState.value.copy(mDnsRegistered = false)
        }
    }

    @Synchronized
    fun stop() {
        if (!_serverState.value.isListening) return
        stopInternal()
    }

    private fun stopInternal() {
        // Send closing courtesy message if connected
        activeOutputStream?.let { output ->
            try {
                val closingJson = JSONObject().apply { put("type", "closing") }
                writeFrame(output, closingJson.toString().toByteArray(Charsets.UTF_8))
            } catch (_: Exception) {}
        }

        unregisterMdns()

        udpJob?.cancel()
        pingJob?.cancel()
        statsJob?.cancel()
        clientJob?.cancel()

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        activeOutputStream = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        decoder.stop()

        _serverState.value = _serverState.value.copy(
            isListening = false,
            cursorPort = null,
            mDnsRegistered = false,
            connectedClient = null
        )
        Log.d(TAG, "Server stopped")
    }
}
