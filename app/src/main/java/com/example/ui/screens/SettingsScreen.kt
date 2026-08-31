package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HumanTelemetryConfig
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val apiKey by viewModel.apiKey.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val telemetry by viewModel.humanTelemetry.collectAsState()
    val learnedRules by viewModel.learnedRules.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()

    var showApiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var newRuleInput by remember { mutableStateOf("") }

    val voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Zephyr")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Screen Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CONFIGURATION",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "API Key, Model Lock & Human Telemetry",
                    fontSize = 12.sp,
                    color = CyanGlow
                )
            }

            Button(
                onClick = { viewModel.saveAndLockSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("save_and_lock_button")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lock & Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Gemini API Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GOOGLE GEMINI API KEY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { viewModel.setApiKey(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = CyanGlow)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = TextSecondary
                            )
                        }
                    }
                )

                Text(
                    text = "Stored securely in local encrypted storage. Injected via BuildConfig.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        // Dynamic Model Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DYNAMIC MODEL SELECTOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )

                    Button(
                        onClick = { viewModel.fetchModelsFromGoogle() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainerDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        enabled = !isFetchingModels,
                        modifier = Modifier.testTag("fetch_models_button")
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CyanGlow, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fetch Models", fontSize = 11.sp, color = CyanGlow, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Selected Model dropdown
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("model_selector_dropdown"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = NeonGreen,
                            unfocusedTextColor = NeonGreen
                        ),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyanGlow)
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.background(CardSurface)
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model,
                                        color = if (model == selectedModel) CyanGlow else Color.White,
                                        fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.setSelectedModel(model)
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Recommended: gemini-3.5-flash for lowest latency & highest vision OCR accuracy.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        // Live Voice Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GEMINI LIVE AUDIO VOICE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    voices.take(4).forEach { voice ->
                        val isSel = selectedVoice == voice
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setSelectedVoice(voice) }
                                .testTag("voice_$voice"),
                            color = if (isSel) PrimaryContainerDark else Color(0xFF020617),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) CyanGlow else CardBorder
                            )
                        ) {
                            Text(
                                text = voice,
                                modifier = Modifier.padding(vertical = 8.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) CyanGlow else TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Human Telemetry Physics Sliders
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "HUMAN TELEMETRY EMULATION SLIDERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                // Keystroke Pacing
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keystroke Flight Pacing", fontSize = 12.sp, color = Color.White)
                        Text(
                            "${telemetry.minKeystrokeMs}ms - ${telemetry.maxKeystrokeMs}ms",
                            fontSize = 11.sp,
                            color = CyanGlow,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = telemetry.maxKeystrokeMs.toFloat(),
                        onValueChange = {
                            viewModel.updateHumanTelemetry(
                                telemetry.copy(minKeystrokeMs = (it * 0.6f).toInt(), maxKeystrokeMs = it.toInt())
                            )
                        },
                        valueRange = 150f..500f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanGlow,
                            activeTrackColor = CyanGlow,
                            inactiveTrackColor = CardBorder
                        )
                    )
                }

                // Cognitive Hesitation Probability
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cognitive Hesitation Mid-Word Pause", fontSize = 12.sp, color = Color.White)
                        Text(
                            "${(telemetry.hesitationProbability * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = NeonAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = telemetry.hesitationProbability,
                        onValueChange = {
                            viewModel.updateHumanTelemetry(telemetry.copy(hesitationProbability = it))
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonAmber,
                            activeTrackColor = NeonAmber,
                            inactiveTrackColor = CardBorder
                        )
                    )
                }

                // Typo Probability
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Realistic Typo Simulation Rate", fontSize = 12.sp, color = Color.White)
                        Text(
                            String.format(java.util.Locale.US, "%.1f%%", telemetry.typoProbability * 100),
                            fontSize = 11.sp,
                            color = NeonRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = telemetry.typoProbability,
                        onValueChange = {
                            viewModel.updateHumanTelemetry(telemetry.copy(typoProbability = it))
                        },
                        valueRange = 0f..0.10f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonRed,
                            activeTrackColor = NeonRed,
                            inactiveTrackColor = CardBorder
                        )
                    )
                }

                // Touch Hold Pressure Dwell
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Glass Pressure Touch Hold", fontSize = 12.sp, color = Color.White)
                        Text(
                            "${telemetry.touchHoldMinMs}ms - ${telemetry.touchHoldMaxMs}ms",
                            fontSize = 11.sp,
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = telemetry.touchHoldMaxMs.toFloat(),
                        onValueChange = {
                            viewModel.updateHumanTelemetry(
                                telemetry.copy(touchHoldMinMs = (it * 0.4f).toLong(), touchHoldMaxMs = it.toLong())
                            )
                        },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = CardBorder
                        )
                    )
                }
            }
        }

        // Self-Learning Dynamic Reflection Memory Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUTONOMOUS LEARNED RULES (${learnedRules.size}/15)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple
                    )
                    if (learnedRules.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllLearnedRules() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear All", fontSize = 11.sp, color = NeonRed)
                        }
                    }
                }

                if (learnedRules.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF020617)
                    ) {
                        Text(
                            text = "No error reflections yet. The engine will autonomously formulate rules when red correction banners appear.",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    learnedRules.forEachIndexed { index, rule ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF020617),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. $rule",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteLearnedRule(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Add Manual Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newRuleInput,
                        onValueChange = { newRuleInput = it },
                        placeholder = { Text("Add custom heuristic rule...", fontSize = 11.sp, color = TextSecondary) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            viewModel.addManualRule(newRuleInput)
                            newRuleInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainerDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Add", fontSize = 11.sp, color = CyanGlow, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
