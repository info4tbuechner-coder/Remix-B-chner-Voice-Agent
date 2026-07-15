package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.ui.theme.AgencyCyan
import com.example.ui.theme.AgencyEmerald
import com.example.ui.theme.AgencyViolet
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DarkBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceDark

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        VoiceAgentScreen()
      }
    }
  }
}

enum class AgentState {
  IDLE, DIALING, CONNECTING, ACTIVE
}

@Composable
fun VoiceAgentScreen(agentManager: VoiceAgentViewModel = viewModel()) {
  val context = LocalContext.current
  
  val agentState by agentManager.agentState.collectAsState()
  val logs by agentManager.logs.collectAsState()
  val listState = rememberLazyListState()

  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  var hasMicrophonePermission by remember {
      mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
  }

  val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { isGranted ->
          hasMicrophonePermission = isGranted
      }
  )

  DisposableEffect(Unit) {
      if (!hasMicrophonePermission) {
          permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
      onDispose {
          agentManager.destroy()
      }
  }

  // Auto-scroll logs
  LaunchedEffect(logs.size) {
    if (logs.isNotEmpty()) {
      listState.animateScrollToItem(logs.size - 1)
    }
  }

  val backgroundBrush = Brush.verticalGradient(
    colors = listOf(
      Color(0xFF0A0F1A), // Very dark blue/black
      DarkBg
    )
  )

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = Color.Transparent,
    topBar = { TopBranding() }
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 24.dp)
          .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(8.dp))

      // Status text
      Text(
        text = when (agentState) {
          AgentState.IDLE -> "SYSTEM BEREIT"
          AgentState.DIALING -> "VERBINDUNGS-AUFBAU..."
          AgentState.CONNECTING -> "KOGNITIVER KERN LÄDT..."
          AgentState.ACTIVE -> "VOICE AGENT AKTIV"
        },
        color = when (agentState) {
          AgentState.ACTIVE -> AgencyEmerald
          AgentState.IDLE -> Color.Gray
          else -> AgencyCyan
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
      )

      if (!hasMicrophonePermission) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              "Mikrofon-Berechtigung wird für Voice API benötigt.",
              color = Color.Red,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace
          )
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Main Orb/Button
      VoiceOrb(
        state = agentState,
        onClick = {
          if (!hasMicrophonePermission) {
              permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              return@VoiceOrb
          }
          if (agentState == AgentState.IDLE) {
            agentManager.startCall()
          } else {
            agentManager.endCall()
          }
        }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Text Input Fallback for Emulator
      var inputText by remember { mutableStateOf("") }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            enabled = agentState == AgentState.ACTIVE,
            placeholder = { Text("Fallback (Mikrofon Defekt?)...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AgencyCyan,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.LightGray,
                disabledTextColor = Color.DarkGray
            )
        )
        Button(
            onClick = {
                if (inputText.isNotBlank() && agentState == AgentState.ACTIVE) {
                    agentManager.processTextInput(inputText)
                    inputText = ""
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            },
            enabled = agentState == AgentState.ACTIVE,
            colors = ButtonDefaults.buttonColors(containerColor = AgencyCyan, disabledContainerColor = Color.DarkGray)
        ) {
            Text("Senden", color = Color.Black)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      var selectedTab by remember { mutableStateOf(0) }
      
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        TabButton(
            text = "TERMINAL",
            isSelected = selectedTab == 0,
            onClick = { 
                selectedTab = 0
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            text = "DASHBOARD",
            isSelected = selectedTab == 1,
            onClick = { 
                selectedTab = 1
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            text = "CONFIG",
            isSelected = selectedTab == 2,
            onClick = { 
                selectedTab = 2
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Content Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceDark)
          .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
      ) {
        if (selectedTab == 0) {
            Column {
              // Terminal Header
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color.Black.copy(alpha = 0.3f))
                  .padding(horizontal = 16.dp, vertical = 12.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if(agentState == AgentState.ACTIVE) AgencyEmerald else Color.Gray))
                  Text("TERMINAL / LIVE LOGS", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("v1.0.3", color = Color.DarkGray, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
              }
              
              HorizontalDivider(color = BorderDark)
              
              val infiniteTransition = rememberInfiniteTransition(label = "cursor")
              val cursorAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                  animation = tween(400, easing = LinearEasing),
                  repeatMode = RepeatMode.Reverse
                ),
                label = "cursorAlpha"
              )

              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                if (logs.isEmpty()) {
                  item {
                    Text(
                      text = ">>> BÜCHNE COGNITIVE ENGINE GESTARTET...",
                      color = AgencyCyan,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
                itemsIndexed(logs) { index, log ->
                  val isLast = index == logs.size - 1 && agentState == AgentState.ACTIVE
                  
                  Text(
                    text = buildAnnotatedString {
                      append(log)
                      if (isLast) {
                        withStyle(style = SpanStyle(color = AgencyEmerald.copy(alpha = cursorAlpha))) {
                          append(" █")
                        }
                      }
                    },
                    color = if (log.startsWith("[ERROR]")) Color.Red else if (isLast) AgencyEmerald else Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 14.sp
                  )
                }
              }
            }
        } else if (selectedTab == 1) {
            val dummyRecentCalls = listOf(
                CallRecord("1", "Siemens AG (Dr. Meyer)", "04:12", "Kunde interessiert an n8n Automatisierung für HR-Prozesse. Folge-Termin vereinbart für nächste Woche.", false),
                CallRecord("2", "Bosch Rexroth", "12:05", "Support-Anfrage bezüglich LangGraph Latenz. Ticket #90123 eröffnet und an Technik delegiert.", false),
                CallRecord("3", "Unbekannt", "00:45", "Anruf wurde vom Kunden abgebrochen, bevor KI den Qualifizierungsprozess starten konnte.", false)
            )
            val activeCall = if (agentState != AgentState.IDLE) {
                CallRecord("0", "Lead: +49 151 432 98 76", "LIVE", "Gespräch im Gange bezüglich Prozessautomatisierung...", true)
            } else null
            
            CallDashboardComponent(activeCall = activeCall, recentCalls = dummyRecentCalls)
        } else {
            val currentPersona by agentManager.personaPrompt.collectAsState()
            var editPersona by remember(currentPersona) { mutableStateOf(currentPersona) }
            
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PERSONA & SYSTEM PROMPT",
                    color = AgencyCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = editPersona,
                    onValueChange = { editPersona = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgencyCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        cursorColor = AgencyCyan
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
                
                Button(
                    onClick = { 
                        agentManager.updatePersonaPrompt(editPersona) 
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AgencyEmerald),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SPEICHERN", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
      }
    }
  }
}
}

@Composable
fun TopBranding() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 24.dp)
  ) {
    Text(
      text = buildAnnotatedString {
        withStyle(style = SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
          append("BÜCHNE")
        }
        withStyle(style = SpanStyle(color = AgencyCyan, fontWeight = FontWeight.Black)) {
          append(".COGNITIVE")
        }
      },
      fontFamily = FontFamily.Monospace,
      fontSize = 20.sp,
      letterSpacing = 2.sp
    )
    Text(
      text = "AUTONOMOUS ARCHITECTURES",
      color = Color.Gray,
      fontFamily = FontFamily.Monospace,
      fontSize = 9.sp,
      letterSpacing = 1.sp
    )
  }
}

@Composable
fun VoiceOrb(state: AgentState, onClick: () -> Unit) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  
  val scale1 by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (state == AgentState.ACTIVE || state == AgentState.DIALING) 1.25f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(if (state == AgentState.ACTIVE) 600 else 1500, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale1"
  )

  val scale2 by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (state == AgentState.ACTIVE || state == AgentState.DIALING) 1.5f else 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(if (state == AgentState.ACTIVE) 800 else 2000, easing = LinearOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale2"
  )

  val orbColor by animateColorAsState(
    targetValue = when (state) {
      AgentState.IDLE -> SurfaceDark
      AgentState.DIALING -> AgencyViolet
      AgentState.CONNECTING -> AgencyCyan
      AgentState.ACTIVE -> AgencyEmerald
    },
    animationSpec = tween(500),
    label = "color"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(200.dp)
  ) {
    // Outer Pulse Ring
    Box(
      modifier = Modifier
        .size(140.dp)
        .scale(scale2)
        .clip(CircleShape)
        .background(orbColor.copy(alpha = 0.1f))
    )
    
    // Inner Pulse Ring
    Box(
      modifier = Modifier
        .size(140.dp)
        .scale(scale1)
        .clip(CircleShape)
        .background(orbColor.copy(alpha = 0.2f))
    )
    
    // Core Button
    Box(
      modifier = Modifier
        .size(100.dp)
        .shadow(16.dp, CircleShape, spotColor = orbColor)
        .clip(CircleShape)
        .background(orbColor)
        .border(2.dp, orbColor.copy(alpha = 0.6f), CircleShape)
        .clickable { onClick() },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (state == AgentState.IDLE) Icons.Default.Call else Icons.Default.CallEnd,
        contentDescription = if (state == AgentState.IDLE) "Anruf starten" else "Anruf beenden",
        tint = if (state == AgentState.IDLE) AgencyCyan else Color.White,
        modifier = Modifier.size(36.dp)
      )
    }
  }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AgencyCyan.copy(alpha = 0.1f) else SurfaceDark)
            .border(1.dp, if (isSelected) AgencyCyan.copy(alpha = 0.5f) else BorderDark, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) AgencyCyan else Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
