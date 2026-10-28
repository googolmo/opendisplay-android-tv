package im.amomo.opendisplay

import im.amomo.opendisplay.server.ResolutionMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

class OpenDisplayProtocolTest {

    private fun isJsonControlFrame(payload: ByteArray): Boolean {
        if (payload.size >= 32768) return false
        if (payload.isEmpty() || payload[0] != '{'.code.toByte()) return false
        for (b in payload) {
            if (b == 0.toByte()) return false
        }
        return true
    }

    @Test
    fun testFramingReadWrite() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        val samplePayload = "{\"type\":\"hello\",\"pv\":3}".toByteArray(Charsets.UTF_8)
        dos.writeInt(samplePayload.size)
        dos.write(samplePayload)
        dos.flush()

        val bis = ByteArrayInputStream(bos.toByteArray())
        val dis = DataInputStream(bis)

        val readLength = dis.readInt()
        assertEquals(samplePayload.size, readLength)

        val readPayload = ByteArray(readLength)
        dis.readFully(readPayload)

        assertArrayEquals(samplePayload, readPayload)
    }

    @Test
    fun testDemuxHeuristicJsonControlMessage() {
        val jsonPayload = "{\"type\":\"welcome\",\"pv\":3,\"min\":1}".toByteArray(Charsets.UTF_8)
        assertTrue(isJsonControlFrame(jsonPayload))
    }

    @Test
    fun testDemuxHeuristicVideoFrameWithStartCodes() {
        val videoPayload = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, 0x67.toByte(), 0x42.toByte(), 0x0A.toByte(), 0x8D.toByte()
        )
        assertFalse(isJsonControlFrame(videoPayload))
    }

    @Test
    fun testDemuxHeuristicLargePayloadIsVideo() {
        val largePayload = ByteArray(32768) { '{'.code.toByte() }
        assertFalse(isJsonControlFrame(largePayload))
    }

    @Test
    fun testDemuxHeuristicPayloadWithNulByteIsVideo() {
        val jsonWithNul = "{\"type\":\"test\"\u0000}".toByteArray(Charsets.UTF_8)
        assertFalse(isJsonControlFrame(jsonWithNul))
    }

    @Test
    fun testVideoFrameStartCodeDetection() {
        val telemetryPrefix = "{\"cap\":1000,\"snd\":1005}".toByteArray(Charsets.UTF_8)
        val naluData = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x88.toByte(), 0x84.toByte(), 0x00)

        val combinedPayload = ByteArray(telemetryPrefix.size + naluData.size)
        System.arraycopy(telemetryPrefix, 0, combinedPayload, 0, telemetryPrefix.size)
        System.arraycopy(naluData, 0, combinedPayload, telemetryPrefix.size, naluData.size)

        var startCodeIdx = -1
        for (i in 0..combinedPayload.size - 4) {
            if (combinedPayload[i] == 0.toByte() &&
                combinedPayload[i + 1] == 0.toByte() &&
                combinedPayload[i + 2] == 0.toByte() &&
                combinedPayload[i + 3] == 1.toByte()
            ) {
                startCodeIdx = i
                break
            }
        }

        assertEquals(telemetryPrefix.size, startCodeIdx)
        val extractedPrefixStr = String(combinedPayload, 0, startCodeIdx, Charsets.UTF_8)
        val json = JSONObject(extractedPrefixStr)
        assertEquals(1000L, json.getLong("cap"))
        assertEquals(1005L, json.getLong("snd"))
    }

    @Test
    fun testPortFallbackLogicWhenPort9000Occupied() {
        var dummySocket: ServerSocket? = null
        try {
            dummySocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(9000))
            }
        } catch (_: Exception) {
            // Port 9000 might already be in use
        }

        val defaultPort = 9000
        val fallbackPort = 9001
        var targetPort = defaultPort

        val boundSocket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(targetPort))
            }
        } catch (e: Exception) {
            if (targetPort == defaultPort) {
                targetPort = fallbackPort
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(targetPort))
                }
            } else {
                throw e
            }
        }

        try {
            if (dummySocket != null) {
                assertEquals(9001, targetPort)
                assertEquals(9001, boundSocket.localPort)
            } else {
                assertEquals(9000, targetPort)
                assertEquals(9000, boundSocket.localPort)
            }
        } finally {
            boundSocket.close()
            dummySocket?.close()
        }
    }

    @Test
    fun testResolutionModeParameters() {
        assertEquals(3840, ResolutionMode.UHD_4K.width)
        assertEquals(2160, ResolutionMode.UHD_4K.height)

        assertEquals(2560, ResolutionMode.QHD.width)
        assertEquals(1440, ResolutionMode.QHD.height)

        assertEquals(1920, ResolutionMode.FHD.width)
        assertEquals(1080, ResolutionMode.FHD.height)

        assertNull(ResolutionMode.AUTO.width)
        assertNull(ResolutionMode.AUTO.height)
    }

    @Test
    fun testResolutionModeTitleResIdMapping() {
        for (mode in ResolutionMode.entries) {
            val titleResId = mode.getTitleResId()
            assertTrue("Resource ID for mode $mode should be non-zero", titleResId != 0)
        }
    }

    @Test
    fun testCursorControlMessageParsing() {
        val cursorJsonStr = "{\"type\":\"cursor\",\"v\":1,\"x\":0.525,\"y\":0.312}"
        val json = JSONObject(cursorJsonStr)

        assertEquals("cursor", json.getString("type"))
        assertEquals(1, json.getInt("v"))
        assertEquals(0.525, json.getDouble("x"), 0.0001)
        assertEquals(0.312, json.getDouble("y"), 0.0001)
    }

    @Test
    fun testCursorImgControlMessageParsing() {
        val cursorImgJsonStr =
            "{\"type\":\"cursorImg\",\"nw\":0.02,\"nh\":0.035,\"ax\":0.1,\"ay\":0.1,\"png\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==\"}"
        val json = JSONObject(cursorImgJsonStr)

        assertEquals("cursorImg", json.getString("type"))
        assertEquals(0.02, json.getDouble("nw"), 0.0001)
        assertEquals(0.035, json.getDouble("nh"), 0.0001)
        assertEquals(0.1, json.getDouble("ax"), 0.0001)
        assertEquals(0.1, json.getDouble("ay"), 0.0001)
        assertTrue(json.has("png"))
    }

    @Test
    fun testUdpCursorDatagramParsingAndSequenceFiltering() {
        val datagramJsonStr1 = "{\"type\":\"cursor\",\"x\":0.4210,\"y\":0.7735,\"v\":1,\"s\":88}"
        val json1 = JSONObject(datagramJsonStr1)

        assertEquals("cursor", json1.getString("type"))
        assertEquals(1, json1.getInt("v"))
        assertEquals(0.4210, json1.getDouble("x"), 0.0001)
        assertEquals(0.7735, json1.getDouble("y"), 0.0001)
        assertEquals(88L, json1.getLong("s"))

        var lastSeq = -1L

        // Accept s=88
        val seq1 = json1.getLong("s")
        assertTrue(seq1 > lastSeq)
        lastSeq = seq1

        // Out of order s=85 should be dropped
        val datagramJsonStrOld = "{\"type\":\"cursor\",\"x\":0.4000,\"y\":0.7500,\"v\":1,\"s\":85}"
        val jsonOld = JSONObject(datagramJsonStrOld)
        val seqOld = jsonOld.getLong("s")
        assertFalse(seqOld > lastSeq)

        // New seq s=89 should be accepted
        val datagramJsonStr2 = "{\"type\":\"cursor\",\"x\":0.4300,\"y\":0.7800,\"v\":1,\"s\":89}"
        val json2 = JSONObject(datagramJsonStr2)
        val seq2 = json2.getLong("s")
        assertTrue(seq2 > lastSeq)
    }

    @Test
    fun testHelloMessageWithCursorPort() {
        val helloJson = JSONObject().apply {
            put("type", "hello")
            put("pixelsWide", 1920)
            put("pixelsHigh", 1080)
            put("scale", 1.0)
            put("cursorPort", 9001)
        }

        assertEquals("hello", helloJson.getString("type"))
        assertEquals(9001, helloJson.getInt("cursorPort"))
    }

    @Test
    fun testPongRoundTripAndClockOffsetCalculation() {
        val t1 = 1700000000000L
        val t2 = 1700000000030L
        val mt = 1700000000010L

        val rtt = (t2 - t1).coerceAtLeast(0)
        assertEquals(30L, rtt)

        val offset = mt - (t1 + rtt / 2)
        assertEquals(-5L, offset)
    }

    @Test
    fun testStatsMessageJsonPayload() {
        val statsJson = JSONObject().apply {
            put("type", "stats")
            put("transport", "WiFi")
            put("fps", 59.94)
            put("mbps", 18.5)
            put("rtt", 14L)
        }

        assertEquals("stats", statsJson.getString("type"))
        assertEquals("WiFi", statsJson.getString("transport"))
        assertEquals(59.94, statsJson.getDouble("fps"), 0.001)
        assertEquals(18.5, statsJson.getDouble("mbps"), 0.001)
        assertEquals(14L, statsJson.getLong("rtt"))
    }
}
