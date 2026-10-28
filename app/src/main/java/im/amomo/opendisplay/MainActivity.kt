package im.amomo.opendisplay

import android.app.Activity
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.*
import im.amomo.opendisplay.server.ClientState
import im.amomo.opendisplay.server.OpenDisplayServer
import im.amomo.opendisplay.server.ResolutionMode
import im.amomo.opendisplay.server.ServerState
import im.amomo.opendisplay.ui.theme.OpenDisplayAndroidTvTheme
import kotlinx.coroutines.delay
import java.util.Locale

fun ResolutionMode.getTitleResId(): Int = when (this) {
    ResolutionMode.AUTO -> R.string.resolution_mode_auto
    ResolutionMode.FHD -> R.string.resolution_mode_fhd
    ResolutionMode.QHD -> R.string.resolution_mode_qhd
    ResolutionMode.UHD_4K -> R.string.resolution_mode_uhd_4k
}

data class SupportedLanguage(
    val code: String,
    val displayNameRes: Int? = null,
    val nativeName: String
)

val SupportedLanguages = listOf(
    SupportedLanguage("", R.string.language_system_default, "System Default"),
    SupportedLanguage("en", null, "English"),
    SupportedLanguage("zh-CN", null, "简体中文"),
    SupportedLanguage("zh-TW", null, "繁體中文"),
    SupportedLanguage("ja", null, "日本語"),
    SupportedLanguage("ko", null, "한국어"),
    SupportedLanguage("de", null, "Deutsch"),
    SupportedLanguage("es", null, "Español"),
    SupportedLanguage("pt", null, "Português"),
    SupportedLanguage("fr", null, "Français")
)

class MainActivity : AppCompatActivity() {

    private lateinit var server: OpenDisplayServer

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        server = OpenDisplayServer(this)

        setContent {
            OpenDisplayAndroidTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    ReceiverScreen(server = server)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReceiverScreen(server: OpenDisplayServer) {
    val serverState by server.serverState.collectAsState()
    ReceiverScreen(
        serverState = serverState,
        onStartServer = { server.start() },
        onStopServer = { server.stop() },
        onSetSurface = { server.setSurface(it) },
        onSetResolutionMode = { server.setResolutionMode(it) }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReceiverScreen(
    serverState: ServerState,
    onStartServer: () -> Unit = {},
    onStopServer: () -> Unit = {},
    onSetSurface: (Surface?) -> Unit = {},
    onSetResolutionMode: (ResolutionMode) -> Unit = {}
) {
    val context = LocalContext.current
    val isConnected = serverState.connectedClient != null
    var isFullScreen by remember { mutableStateOf(false) }

    // Keep screen on while server is listening or client is connected
    DisposableEffect(serverState.isListening) {
        val activity = context as? Activity
        val window = activity?.window
        if (serverState.isListening && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Automatically trigger full screen mode whenever a connection is created
    LaunchedEffect(isConnected) {
        isFullScreen = isConnected
    }

    if (isFullScreen && isConnected) {
        BackHandler {
            isFullScreen = false
        }

        FullScreenVideoView(
            serverState = serverState,
            onSetSurface = onSetSurface,
            onExitFullScreen = { isFullScreen = false }
        )
    } else {
        DashboardScreen(
            serverState = serverState,
            onStartServer = onStartServer,
            onStopServer = onStopServer,
            onSetSurface = onSetSurface,
            onSetResolutionMode = onSetResolutionMode,
            onEnterFullScreen = { isFullScreen = true }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FullScreenVideoView(
    serverState: ServerState,
    onSetSurface: (Surface?) -> Unit,
    onExitFullScreen: () -> Unit
) {
    val context = LocalContext.current
    var showHud by remember { mutableStateOf(true) }

    // Auto-hide HUD overlay after 4 seconds
    LaunchedEffect(showHud) {
        if (showHud) {
            delay(4000L)
            showHud = false
        }
    }

    // Toggle immersive full screen mode (hide status and navigation bars)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showHud = !showHud },
        contentAlignment = Alignment.Center
    ) {
        // Video Surface View filling the entire screen
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSetSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSetSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Mouse Cursor Overlay
        CursorOverlay(
            clientState = serverState.connectedClient,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay / HUD Controls
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(24.dp)
            ) {
                // Top HUD Bar: Connection Info & Exit Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val client = serverState.connectedClient
                    Column {
                        val clientName = client?.clientAddress ?: stringResource(R.string.default_client_name)
                        Text(
                            text = stringResource(R.string.hud_connected, clientName),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        client?.let {
                            val latencyStr = it.rttMs?.let { rtt -> stringResource(R.string.latency_ms_format, rtt) }
                                ?: stringResource(R.string.latency_measuring)
                            Text(
                                text = stringResource(
                                    R.string.hud_stats_format,
                                    serverState.currentResolution.first,
                                    serverState.currentResolution.second,
                                    it.fps,
                                    it.mbps,
                                    latencyStr
                                ),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Button(
                        onClick = onExitFullScreen,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_exit_full_screen), fontSize = 14.sp)
                    }
                }

                // Bottom Hint
                Text(
                    text = stringResource(R.string.hud_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DashboardScreen(
    serverState: ServerState,
    onStartServer: () -> Unit = {},
    onStopServer: () -> Unit = {},
    onSetSurface: (Surface?) -> Unit = {},
    onSetResolutionMode: (ResolutionMode) -> Unit = {},
    onEnterFullScreen: () -> Unit = {}
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        BackHandler {
            showLanguageDialog = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_header_title),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ServerStatusBadge(state = serverState)
                    }
                    Text(
                        text = stringResource(R.string.protocol_version, 3, 3),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language Switcher Button
                    Button(
                        onClick = { showLanguageDialog = true },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "🌐 " + stringResource(R.string.btn_language),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }

                    // Start / Stop Button
                    Button(
                        onClick = {
                            if (serverState.isListening) {
                                onStopServer()
                            } else {
                                onStartServer()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (serverState.isListening) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(
                                if (serverState.isListening) R.string.stop_server else R.string.start_server
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Resolution Selector Card
            ResolutionSelectorCard(
                currentMode = serverState.resolutionMode,
                currentResolution = serverState.currentResolution,
                onSetResolutionMode = onSetResolutionMode
            )

            // Main Content Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Server Info Card
                Column(modifier = Modifier.weight(1f)) {
                    ServerInfoCard(state = serverState)
                }

                // Client Info Card
                Column(modifier = Modifier.weight(1f)) {
                    ClientInfoCard(clientState = serverState.connectedClient)
                }
            }

            // Video Surface Display Section
            VideoSurfaceCard(
                isListening = serverState.isListening,
                clientState = serverState.connectedClient,
                onSetSurface = onSetSurface,
                onEnterFullScreen = onEnterFullScreen
            )
        }

        // In-app Language Selection Dialog Overlay
        if (showLanguageDialog) {
            LanguageSelectionDialog(
                onDismissRequest = { showLanguageDialog = false },
                onLanguageSelected = { langCode ->
                    val locales = if (langCode.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(langCode)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                    showLanguageDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LanguageSelectionDialog(
    onDismissRequest: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val activeTags = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()
    val focusRequester = remember { FocusRequester() }

    val selectedIndex = remember(activeTags) {
        SupportedLanguages.indexOfFirst { lang ->
            when {
                lang.code.isEmpty() -> activeTags.isEmpty()
                lang.code.equals("zh-TW", ignoreCase = true) ->
                    activeTags.contains("zh-TW", ignoreCase = true) ||
                            activeTags.contains("zh-Hant", ignoreCase = true) ||
                            activeTags.contains("zh-HK", ignoreCase = true)

                lang.code.equals("zh-CN", ignoreCase = true) ->
                    (activeTags.startsWith("zh", ignoreCase = true) &&
                            !activeTags.contains("TW", ignoreCase = true) &&
                            !activeTags.contains("Hant", ignoreCase = true) &&
                            !activeTags.contains("HK", ignoreCase = true))

                else -> activeTags.startsWith(lang.code, ignoreCase = true)
            }
        }.let { if (it >= 0) it else 0 }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(440.dp)
                    .heightIn(max = 560.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.language_dialog_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    SupportedLanguages.forEachIndexed { index, lang ->
                        val isSelected = index == selectedIndex
                        val title = if (lang.displayNameRes != null) {
                            stringResource(lang.displayNameRes)
                        } else {
                            lang.nativeName
                        }

                        Button(
                            onClick = { onLanguageSelected(lang.code) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelected) Modifier.focusRequester(focusRequester) else Modifier),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF1565C0) else Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Text(text = "✓", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.LightGray
                        )
                    ) {
                        Text(text = stringResource(R.string.dialog_close), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResolutionSelectorCard(
    currentMode: ResolutionMode,
    currentResolution: Pair<Int, Int>,
    onSetResolutionMode: (ResolutionMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.display_resolution_mode),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.target_resolution_format,
                        currentResolution.first,
                        currentResolution.second
                    ),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionMode.entries.forEach { mode ->
                    val isSelected = currentMode == mode
                    Button(
                        onClick = { onSetResolutionMode(mode) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) Color(0xFF1565C0) else Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(mode.getTitleResId()),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerStatusBadge(state: ServerState) {
    val bgColor = when {
        state.connectedClient != null -> Color(0xFF1565C0)
        state.isListening -> Color(0xFF2E7D32)
        else -> Color(0xFF424242)
    }
    val statusText = when {
        state.connectedClient != null -> stringResource(R.string.status_connected, state.connectedClient.clientAddress)
        state.isListening -> stringResource(R.string.status_listening, state.port)
        else -> stringResource(R.string.status_stopped)
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, shape = RoundedCornerShape(4.dp))
            )
            Text(
                text = statusText,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerInfoCard(state: ServerState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.server_details),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            InfoRow(
                stringResource(R.string.label_status),
                if (state.isListening) stringResource(R.string.status_running) else stringResource(R.string.status_stopped_text)
            )
            InfoRow(
                stringResource(R.string.label_target_resolution),
                "${state.currentResolution.first}x${state.currentResolution.second}"
            )
            InfoRow(
                stringResource(R.string.label_resolution_mode),
                stringResource(state.resolutionMode.getTitleResId())
            )
            InfoRow(
                stringResource(R.string.label_listening_port),
                stringResource(R.string.port_format_tcp, state.port)
            )
            InfoRow(
                stringResource(R.string.label_udp_cursor_port),
                if (state.cursorPort != null) stringResource(
                    R.string.port_format_udp,
                    state.cursorPort
                ) else stringResource(R.string.udp_port_disabled)
            )
            InfoRow(
                stringResource(R.string.label_local_ips),
                if (state.localIpAddresses.isNotEmpty()) state.localIpAddresses.joinToString(", ") else stringResource(R.string.ip_offline)
            )
            InfoRow(stringResource(R.string.label_device_name), state.deviceName)
            InfoRow(stringResource(R.string.label_mdns_service), "_opensidecar._tcp")
            InfoRow(
                stringResource(R.string.label_mdns_registered),
                if (state.mDnsRegistered) stringResource(R.string.common_yes) else stringResource(R.string.common_no)
            )
            InfoRow(stringResource(R.string.label_device_id), state.deviceId)

            state.lastError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.error_prefix, err),
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClientInfoCard(clientState: ClientState?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.client_session),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (clientState != null) {
                InfoRow(stringResource(R.string.label_client_address), clientState.clientAddress)
                InfoRow(
                    stringResource(R.string.label_sender_pv),
                    clientState.senderPv?.toString() ?: stringResource(R.string.common_unknown)
                )
                InfoRow(
                    stringResource(R.string.label_sender_min_pv),
                    clientState.senderMinPv?.toString() ?: stringResource(R.string.common_unknown)
                )
                InfoRow(
                    stringResource(R.string.label_rtt_latency),
                    clientState.rttMs?.let { stringResource(R.string.latency_ms_format, it) }
                        ?: stringResource(R.string.latency_measuring)
                )
                InfoRow(
                    stringResource(R.string.label_clock_offset),
                    clientState.clockOffsetMs?.let { stringResource(R.string.latency_ms_format, it) }
                        ?: stringResource(R.string.latency_measuring)
                )
                InfoRow(stringResource(R.string.label_video_frames), "${clientState.videoFramesReceived}")
                InfoRow(stringResource(R.string.label_control_messages), "${clientState.controlMessagesReceived}")
                InfoRow(stringResource(R.string.label_fps), String.format(Locale.US, "%.1f", clientState.fps))
                InfoRow(
                    stringResource(R.string.label_bandwidth),
                    String.format(Locale.US, "%.2f Mbps", clientState.mbps)
                )
                InfoRow(
                    stringResource(R.string.label_cursor_channel),
                    if (clientState.udpCursorActive) stringResource(R.string.cursor_channel_udp) else stringResource(R.string.cursor_channel_tcp)
                )
                InfoRow(
                    stringResource(R.string.label_cursor_visible),
                    if (clientState.cursorVisible) stringResource(R.string.common_yes) else stringResource(R.string.common_no)
                )
                InfoRow(
                    stringResource(R.string.label_cursor_position),
                    clientState.lastCursorPos?.let {
                        stringResource(R.string.cursor_pos_format, it.first, it.second)
                    } ?: stringResource(R.string.cursor_hidden)
                )
                InfoRow(
                    stringResource(R.string.label_last_message),
                    clientState.lastMessageType ?: stringResource(R.string.common_none)
                )
            } else {
                Text(
                    text = stringResource(R.string.no_active_connection),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = stringResource(R.string.connect_instruction),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoSurfaceCard(
    isListening: Boolean,
    clientState: ClientState?,
    onSetSurface: (Surface?) -> Unit = {},
    onEnterFullScreen: () -> Unit = {}
) {
    val isConnected = clientState != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    onSetSurface(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    onSetSurface(null)
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isConnected) {
                    CursorOverlay(
                        clientState = clientState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.waiting_for_stream),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Button(
                            onClick = onEnterFullScreen,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                stringResource(R.string.btn_full_screen),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.server_stopped_hint),
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CursorOverlay(
    clientState: ClientState?,
    modifier: Modifier = Modifier
) {
    if (clientState == null || !clientState.cursorVisible || clientState.lastCursorPos == null) {
        return
    }

    val (normX, normY) = clientState.lastCursorPos
    val cursorBitmap = clientState.cursorBitmap
    val imageBitmap = remember(cursorBitmap) { cursorBitmap?.asImageBitmap() }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        if (containerWidthPx > 0f && containerHeightPx > 0f) {
            val targetX = normX * containerWidthPx
            val targetY = normY * containerHeightPx

            if (imageBitmap != null) {
                val spriteWidthPx = if (clientState.cursorNormWidth != null && clientState.cursorNormWidth > 0f) {
                    clientState.cursorNormWidth * containerWidthPx
                } else {
                    imageBitmap.width.toFloat()
                }

                val spriteHeightPx = if (clientState.cursorNormHeight != null && clientState.cursorNormHeight > 0f) {
                    clientState.cursorNormHeight * containerHeightPx
                } else {
                    imageBitmap.height.toFloat()
                }

                val hotspotX = spriteWidthPx * clientState.cursorHotspotX
                val hotspotY = spriteHeightPx * clientState.cursorHotspotY

                val leftPx = targetX - hotspotX
                val topPx = targetY - hotspotY

                val leftDp = with(density) { leftPx.toDp() }
                val topDp = with(density) { topPx.toDp() }
                val widthDp = with(density) { spriteWidthPx.toDp() }
                val heightDp = with(density) { spriteHeightPx.toDp() }

                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = leftDp, y = topDp)
                        .size(width = widthDp, height = heightDp)
                )
            } else {
                DefaultCursorPointer(
                    targetX = targetX,
                    targetY = targetY
                )
            }
        }
    }
}

@Composable
fun DefaultCursorPointer(
    targetX: Float,
    targetY: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val scale = density
        val pX = targetX
        val pY = targetY

        val path = Path().apply {
            moveTo(pX, pY)
            lineTo(pX, pY + 20f * scale)
            lineTo(pX + 5f * scale, pY + 15f * scale)
            lineTo(pX + 10f * scale, pY + 20f * scale)
            lineTo(pX + 12.5f * scale, pY + 17.5f * scale)
            lineTo(pX + 7.5f * scale, pY + 12.5f * scale)
            lineTo(pX + 14f * scale, pY + 12.5f * scale)
            close()
        }

        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 2.5f * scale)
        )
        drawPath(
            path = path,
            color = Color.White,
            style = Fill
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(device = "id:tv_1080p", showBackground = true)
@Composable
fun ReceiverScreenPreview() {
    OpenDisplayAndroidTvTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape
        ) {
            ReceiverScreen(
                serverState = ServerState(
                    isListening = true,
                    port = 9000,
                    localIpAddresses = listOf("192.168.1.100"),
                    deviceId = "550e8400-e29b-41d4-a716-446655440000",
                    deviceName = "OpenDisplay-TV",
                    mDnsRegistered = true,
                    connectedClient = ClientState(
                        clientAddress = "192.168.1.105:54321",
                        senderPv = 3,
                        senderMinPv = 3,
                        rttMs = 12,
                        clockOffsetMs = -2,
                        videoFramesReceived = 1240,
                        controlMessagesReceived = 45,
                        fps = 60.0f,
                        mbps = 15.4f,
                        lastMessageType = "VIDEO"
                    )
                )
            )
        }
    }
}
