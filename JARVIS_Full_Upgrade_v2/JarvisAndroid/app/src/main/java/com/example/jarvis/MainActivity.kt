package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

private val VoidBlack = Color(0xFF050B13)
private val Panel = Color(0xFF0B1928)
private val Cyan = Color(0xFF59D9FF)
private val Muted = Color(0xFF8BAABD)
private val TextWhite = Color(0xFFEAF8FF)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                val engine = tts
                engine?.language = Locale.UK
                engine?.setSpeechRate(0.91f)
                val british = engine?.voices?.firstOrNull {
                    it.locale.language == "en" && it.locale.country == "GB"
                }
                if (british != null) engine.voice = british
            }
        }
        setContent { JarvisApp(this) }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }
    fun stopSpeaking() { tts?.stop() }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisApp(activity: MainActivity) {
    val context = activity
    val prefs = remember { Prefs(context) }
    val ai = remember { AIClient() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(prefs.loadHistory().ifEmpty {
        listOf(ChatMessage("assistant", "Good day. JARVIS systems are online. How may I assist you?"))
    }) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SYSTEMS NOMINAL") }
    var tab by remember { mutableStateOf("CHAT") }
    var provider by remember { mutableStateOf(prefs.provider) }
    var openKey by remember { mutableStateOf(prefs.openAiKey) }
    var claudeKey by remember { mutableStateOf(prefs.claudeKey) }
    var openModel by remember { mutableStateOf(prefs.openAiModel) }
    var claudeModel by remember { mutableStateOf(prefs.claudeModel) }
    var memory by remember { mutableStateOf(prefs.memory) }
    var voiceEnabled by remember { mutableStateOf(prefs.voiceEnabled) }
    var showKeys by remember { mutableStateOf(false) }

    fun addMessage(role: String, content: String) {
        messages = (messages + ChatMessage(role, content)).takeLast(80)
        prefs.saveHistory(messages)
    }

    fun launchApp(raw: String): String {
        val name = raw.trim().lowercase()
        val apps = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "settings" to listOf("com.android.settings"),
            "maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "play store" to listOf("com.android.vending"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms")
        )
        val candidates = apps[name]
            ?: return "I can open YouTube, Chrome, Camera, Settings, Maps, Gmail, Calculator, Play Store, Phone, or Messages."
        val intent = candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return "$name is not installed or is unavailable on this device."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening $name."
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        // Local, explicitly supported phone actions. No hidden device control.
        if (text.lowercase().startsWith("open ")) {
            val result = launchApp(text.substringAfter("open "))
            addMessage("user", text)
            addMessage("assistant", result)
            if (voiceEnabled) activity.speak(result)
            return
        }
        addMessage("user", text)
        busy = true
        status = "PROCESSING"
        scope.launch {
            try {
                val answer = ai.ask(
                    provider, messages, memory, openKey, claudeKey, openModel, claudeModel
                )
                addMessage("assistant", answer)
                status = "SYSTEMS NOMINAL"
                if (voiceEnabled) activity.speak(answer)
            } catch (e: Exception) {
                val error = e.message ?: "Unable to contact the AI provider."
                addMessage("assistant", "I couldn't complete that request. $error")
                status = "ATTENTION REQUIRED"
            } finally { busy = false }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = spoken
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            }
            runCatching { speechLauncher.launch(intent) }.onFailure { status = "VOICE INPUT UNAVAILABLE" }
        } else status = "MICROPHONE PERMISSION DENIED"
    }

    fun startSpeech() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            }
            runCatching { speechLauncher.launch(intent) }.onFailure { status = "VOICE INPUT UNAVAILABLE" }
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, onPrimary = VoidBlack, background = VoidBlack,
        surface = Panel, onSurface = TextWhite, onBackground = TextWhite,
        secondary = Color(0xFF37A9D3)
    )) {
        Scaffold(containerColor = VoidBlack, topBar = {
            Column(Modifier.fillMaxWidth().background(VoidBlack).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                        Text("J", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = Cyan, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("PERSONAL INTELLIGENCE SYSTEM", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                    IconButton(onClick = { tab = if (tab == "SETTINGS") "CHAT" else "SETTINGS" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan)
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("●  $status", color = if (busy) Color(0xFFFFC66D) else Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(provider.uppercase(), color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CHAT", "TOOLS", "SETTINGS").forEach { item ->
                        FilterChip(
                            selected = tab == item,
                            onClick = { tab = item },
                            label = { Text(item, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF12364A),
                                selectedLabelColor = Cyan,
                                labelColor = Muted
                            )
                        )
                    }
                }
            }
        }, bottomBar = {
            if (tab == "CHAT") {
                Column(Modifier.fillMaxWidth().background(VoidBlack).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message JARVIS...", color = Muted) },
                        shape = RoundedCornerShape(18.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { startSpeech() }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Cyan)
                                }
                                IconButton(onClick = { sendMessage() }, enabled = !busy) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = if (busy) Muted else Cyan)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF244155),
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            cursorColor = Cyan
                        )
                    )
                    Text("VOICE INPUT • UK ENGLISH VOICE OUTPUT", color = Muted, fontSize = 9.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 3.dp))
                }
            }
        }) { padding ->
            when (tab) {
                "CHAT" -> {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages) { message ->
                                val user = message.role == "user"
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                                    Card(
                                        modifier = Modifier.widthIn(max = 330.dp),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp, topEnd = 16.dp,
                                            bottomStart = if (user) 16.dp else 4.dp,
                                            bottomEnd = if (user) 4.dp else 16.dp
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (user) Color(0xFF12364A) else Panel
                                        )
                                    ) {
                                        Column(Modifier.padding(13.dp)) {
                                            Text(if (user) "YOU" else "JARVIS", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(message.content, color = TextWhite, fontSize = 14.sp, lineHeight = 21.sp)
                                        }
                                    }
                                }
                            }
                            if (busy) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(9.dp))
                                        Text("JARVIS IS THINKING...", color = Muted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { activity.stopSpeaking() }) {
                                Icon(Icons.Default.VolumeOff, null, tint = Muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("STOP VOICE", color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                messages = listOf(ChatMessage("assistant", "Conversation cleared. Ready for your next instruction."))
                                prefs.clearHistory(); prefs.saveHistory(messages)
                                activity.stopSpeaking()
                            }) {
                                Icon(Icons.Default.DeleteOutline, null, tint = Muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("CLEAR CHAT", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
                "TOOLS" -> {
                    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("DEVICE SHORTCUTS", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        item { Text("JARVIS can launch these apps when they are installed. Android will handle permissions and system prompts.", color = Muted, fontSize = 12.sp) }
                        val shortcuts = listOf(
                            "YouTube" to Icons.Default.PlayCircle,
                            "Chrome" to Icons.Default.Language,
                            "Camera" to Icons.Default.CameraAlt,
                            "Maps" to Icons.Default.Map,
                            "Gmail" to Icons.Default.Email,
                            "Settings" to Icons.Default.Settings,
                            "Calculator" to Icons.Default.Calculate,
                            "Play Store" to Icons.Default.Shop,
                            "Phone" to Icons.Default.Call,
                            "Messages" to Icons.Default.Message
                        )
                        items(shortcuts) { (name, icon) ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel),
                                onClick = {
                                    val result = launchApp(name)
                                    status = result.uppercase()
                                }) {
                                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, tint = Cyan)
                                    Spacer(Modifier.width(14.dp))
                                    Text(name, color = TextWhite, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                                }
                            }
                        }
                        item {
                            OutlinedButton(onClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")))
                                } else {
                                    context.startService(Intent(context, FloatingService::class.java))
                                    status = "FLOATING BUBBLE ENABLED"
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE FLOATING ASSISTANT") }
                        }
                        item {
                            OutlinedButton(onClick = {
                                context.stopService(Intent(context, FloatingService::class.java))
                                status = "FLOATING BUBBLE STOPPED"
                            }, modifier = Modifier.fillMaxWidth()) { Text("STOP FLOATING ASSISTANT") }
                        }
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("AI PROVIDER", color = Cyan, fontWeight = FontWeight.Bold) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = provider == "OpenAI", onClick = { provider = "OpenAI"; prefs.provider = provider }, label = { Text("OpenAI") })
                                FilterChip(selected = provider == "Claude", onClick = { provider = "Claude"; prefs.provider = provider }, label = { Text("Claude") })
                            }
                        }
                        item {
                            OutlinedTextField(value = openModel, onValueChange = { openModel = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("OpenAI model") }, singleLine = true)
                        }
                        item {
                            OutlinedTextField(value = claudeModel, onValueChange = { claudeModel = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Claude model") }, singleLine = true)
                        }
                        item {
                            OutlinedTextField(value = openKey, onValueChange = { openKey = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("OpenAI API key") },
                                singleLine = true, visualTransformation = if (showKeys) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                        }
                        item {
                            OutlinedTextField(value = claudeKey, onValueChange = { claudeKey = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Anthropic API key") },
                                singleLine = true, visualTransformation = if (showKeys) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                        }
                        item { TextButton(onClick = { showKeys = !showKeys }) { Text(if (showKeys) "HIDE API KEYS" else "SHOW API KEYS") } }
                        item { Divider(color = Color(0xFF244155)) }
                        item { Text("ON-DEVICE PERSONAL MEMORY", color = Cyan, fontWeight = FontWeight.Bold) }
                        item {
                            OutlinedTextField(value = memory, onValueChange = { memory = it },
                                modifier = Modifier.fillMaxWidth(), minLines = 3,
                                label = { Text("Facts JARVIS may use in conversations") },
                                placeholder = { Text("Add only information you want stored on this device.") })
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Speak AI replies", color = TextWhite, modifier = Modifier.weight(1f))
                                Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it })
                            }
                        }
                        item {
                            Button(onClick = {
                                prefs.openAiKey = openKey; prefs.claudeKey = claudeKey
                                prefs.provider = provider; prefs.openAiModel = openModel
                                prefs.claudeModel = claudeModel; prefs.memory = memory
                                prefs.voiceEnabled = voiceEnabled
                                status = "CONFIGURATION SAVED"
                            }, modifier = Modifier.fillMaxWidth()) { Text("SAVE CONFIGURATION") }
                        }
                        item {
                            Text("API requests are sent directly to your selected provider. API usage may cost money. Keys are stored using Android encrypted preferences on this device; this is not a substitute for a secure backend in a public app.",
                                color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        item {
                            OutlinedButton(onClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")))
                                } else {
                                    context.startService(Intent(context, FloatingService::class.java))
                                    status = "FLOATING BUBBLE ENABLED"
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE FLOATING BUBBLE") }
                        }
                    }
                }
            }
        }
    }
}
