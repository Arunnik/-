package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.PackResult
import com.example.crypto.UnpackResult

@Composable
fun StatusAndMetadataCard(
    errorMessage: String?,
    statusMessage: String?,
    unpackResult: UnpackResult?,
    packResult: PackResult?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Error Banner
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Ошибка",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ошибка операции",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Success / Status Banner
        AnimatedVisibility(
            visible = statusMessage != null && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Успешно",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Unpack Metadata Card
        if (unpackResult != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Детали расшифрованного пакета:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Type
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("Тип: ${unpackResult.payloadType.displayName}", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        // Compression
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                Text(
                                    if (unpackResult.isCompressed) "Deflate: Сжато" else "Без сжатия",
                                    fontSize = 12.sp
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (unpackResult.isCompressed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        // Salt
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                val saltStr = if (unpackResult.salt != null) {
                                    val isAscii = unpackResult.salt.all { it in 32..126 }
                                    if (isAscii) "'${String(unpackResult.salt, Charsets.US_ASCII)}'"
                                    else "0x" + unpackResult.salt.joinToString("") { "%02x".format(it) }
                                } else "нет"
                                Text("Соль: $saltStr", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        // CRC
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                Text(
                                    "CRC4: 0x${unpackResult.crc4.toString(16).uppercase()} ✓",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        )
                    }
                }
            }
        }

        // Pack Metadata Card
        if (packResult != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Сводка шифрования пакета:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("Токен: ${packResult.token.length} симв.", fontSize = 12.sp) }
                        )

                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                val ratio = if (packResult.rawSize > 0) {
                                    (100 - (packResult.payloadSize * 100 / packResult.rawSize)).coerceAtLeast(0)
                                } else 0
                                Text(
                                    if (packResult.isCompressed) "Сжато: -${ratio}%" else "Без сжатия",
                                    fontSize = 12.sp
                                )
                            }
                        )

                        if (packResult.isSalted && packResult.salt != null) {
                            AssistChip(
                                onClick = {},
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        "Соль: 0x" + packResult.salt.joinToString("") { "%02x".format(it) },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            )
                        }

                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                Text(
                                    "CRC4: 0x${packResult.crc4.toString(16).uppercase()}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
