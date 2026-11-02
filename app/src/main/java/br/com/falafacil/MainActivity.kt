package br.com.falafacil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingText: String? = null
    private var lastAutoReadText: String? = null

    private val purple = Color(0xFF7436C8)
    private val darkPurple = Color(0xFF241632)

    private var currentText by mutableStateOf("")
    private var status by mutableStateOf("Copie uma mensagem e abra o FalaFácil.")
    private var isSpeaking by mutableStateOf(false)
    private var selectedVoice by mutableStateOf("Padrão")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F5FC)
                ) {
                    FalaFacilScreen(
                        text = currentText,
                        status = status,
                        speaking = isSpeaking,
                        voice = selectedVoice,
                        onPaste = { readClipboard(manual = true) },
                        onSpeak = { speakCurrentText() },
                        onPause = { pauseSpeech() },
                        onStop = { stopSpeech() },
                        onRepeat = { speakCurrentText() },
                        onVoice = { selectedVoice = it; applyVoicePreference() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // O Android permite ler a área de transferência quando este app está em primeiro plano.
        // O sistema pode mostrar um aviso de privacidade ao acessar o conteúdo copiado.
        window.decorView.postDelayed({ readClipboard(manual = false) }, 350)
    }

    private fun readClipboard(manual: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        val copied = if (clipboard.hasPrimaryClip() && clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        } else ""

        if (copied.isBlank()) {
            if (manual) status = "Não encontrei texto copiado. Copie uma mensagem primeiro."
            return
        }

        currentText = copied
        status = "Mensagem encontrada! Vou ler para você."
        if (!manual && copied == lastAutoReadText) return
        lastAutoReadText = copied
        pendingText = copied
        if (ttsReady) speak(copied)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val br = tts?.setLanguage(Locale("pt", "BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (br == TextToSpeech.LANG_MISSING_DATA || br == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("pt", "PT"))
            }
            ttsReady = true
            applyVoicePreference()
            pendingText?.let { speak(it) }
        } else {
            status = "Não foi possível iniciar a voz. Verifique a configuração de voz do celular."
        }
    }

    private fun applyVoicePreference() {
        val engine = tts ?: return
        val voices: Set<Voice> = engine.voices ?: return
        val portuguese = voices.filter { it.locale.language == "pt" }
        val desired = when (selectedVoice) {
            "Feminina" -> portuguese.firstOrNull {
                val n = it.name.lowercase()
                n.contains("female") || n.contains("femin")
            }
            "Masculina" -> portuguese.firstOrNull {
                val n = it.name.lowercase()
                n.contains("male") || n.contains("masc")
            }
            else -> null
        }
        if (desired != null) engine.voice = desired
        // Se o aparelho não oferecer uma voz identificável, mantém a voz padrão.
    }

    private fun speakCurrentText() {
        if (currentText.isBlank()) {
            readClipboard(manual = true)
            if (currentText.isBlank()) return
        }
        if (ttsReady) speak(currentText) else pendingText = currentText
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        tts?.setSpeechRate(0.92f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "falafacil_message")
        isSpeaking = true
        status = "Lendo sua mensagem..."
    }

    private fun pauseSpeech() {
        // TextToSpeech não oferece pausa/resumo universal em todas as versões.
        // Aqui interrompemos a fala e permitimos iniciar novamente pelo botão continuar.
        tts?.stop()
        isSpeaking = false
        status = "Leitura pausada. Toque em continuar para ouvir novamente."
    }

    private fun stopSpeech() {
        tts?.stop()
        isSpeaking = false
        status = "Leitura encerrada."
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun FalaFacilScreen(
    text: String,
    status: String,
    speaking: Boolean,
    voice: String,
    onPaste: () -> Unit,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRepeat: () -> Unit,
    onVoice: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("FalaFácil", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF55218D))
        Text("Copiou? Nós lemos para você!", fontSize = 18.sp, color = darkText(), textAlign = TextAlign.Center)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SUA MENSAGEM", fontWeight = FontWeight.Bold, color = Color(0xFF55218D))
                Text(
                    text = if (text.isBlank()) "Quando você copiar uma mensagem e abrir o FalaFácil, ela aparecerá aqui." else text,
                    fontSize = 18.sp,
                    color = Color(0xFF25212B)
                )
            }
        }

        Button(
            onClick = onSpeak,
            modifier = Modifier.fillMaxWidth().height(94.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7436C8))
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(34.dp))
            Text("  OUVIR MENSAGEM", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }

        Text(status, fontSize = 15.sp, color = Color(0xFF4C4356), textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f).height(64.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Text("  Buscar texto", fontSize = 14.sp)
            }
            OutlinedButton(onClick = onRepeat, modifier = Modifier.weight(1f).height(64.dp)) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Text("  Repetir", fontSize = 14.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).height(60.dp)) {
                Icon(Icons.Default.Pause, contentDescription = null)
                Text("  Pausar")
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).height(60.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("  Parar")
            }
        }

        Text("TIPO DE VOZ", fontWeight = FontWeight.Bold, color = Color(0xFF55218D))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Padrão", "Feminina", "Masculina").forEach { option ->
                if (voice == option) {
                    Button(onClick = { onVoice(option) }, modifier = Modifier.weight(1f)) {
                        Text(option, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = { onVoice(option) }, modifier = Modifier.weight(1f)) {
                        Text(option, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            "A voz depende das opções instaladas no celular. Algumas vozes precisam ser baixadas nas configurações do Android.",
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun darkText() = Color(0xFF30283A)
