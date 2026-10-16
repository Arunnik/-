package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.crypto.Base91
import com.example.crypto.PackAndHideEngine
import com.example.crypto.PackResult
import com.example.crypto.PayloadType
import com.example.crypto.UnpackResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

enum class ActiveMode {
    DECRYPT,
    ENCRYPT
}

data class PresetsItem(
    val title: String,
    val description: String,
    val token: String,
    val key: String,
    val isSalted: Boolean,
    val salt: String
)

data class MainUiState(
    val inputText: String = "",
    val outputText: String = "",
    val key: String = "",
    val isKeyVisible: Boolean = false,
    val isSalted: Boolean = false,
    val saltText: String = "",
    val selectedPayloadType: PayloadType? = null, // null = auto
    val lastUnpackResult: UnpackResult? = null,
    val lastPackResult: PackResult? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val activeMode: ActiveMode = ActiveMode.DECRYPT
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val secureRandom = SecureRandom()

    val presets = listOf(
        PresetsItem(
            title = "Тест 1 (Русский текст)",
            description = "Ключ: pass123, без соли, CP1251",
            token = "8qo?F=1`(&BznlR8",
            key = "pass123",
            isSalted = false,
            salt = ""
        ),
        PresetsItem(
            title = "Тест 2 (С солью)",
            description = "Ключ: pass123, соль '1234'",
            token = "Sw_,\$kvf`o@MSSiWCqx@9",
            key = "pass123",
            isSalted = true,
            salt = "1234"
        ),
        PresetsItem(
            title = "Тест 3 (Без ключа)",
            description = "Без пароля, чистый Base91",
            token = ".Y]IX<oCi$4t}8!q",
            key = "",
            isSalted = false,
            salt = ""
        )
    )

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value, errorMessage = null, statusMessage = null) }
    }

    fun onOutputChanged(value: String) {
        _uiState.update { it.copy(outputText = value) }
    }

    fun onKeyChanged(value: String) {
        _uiState.update { it.copy(key = value, errorMessage = null) }
    }

    fun toggleKeyVisibility() {
        _uiState.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun toggleSalted(enabled: Boolean) {
        _uiState.update {
            val newSalt = if (enabled && it.saltText.isEmpty()) generateSaltString() else it.saltText
            it.copy(isSalted = enabled, saltText = newSalt)
        }
    }

    fun onSaltChanged(value: String) {
        _uiState.update { it.copy(saltText = value) }
    }

    fun generateRandomSalt() {
        _uiState.update { it.copy(isSalted = true, saltText = generateSaltString()) }
    }

    private fun generateSaltString(): String {
        val b = ByteArray(4)
        secureRandom.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun setSelectedPayloadType(type: PayloadType?) {
        _uiState.update { it.copy(selectedPayloadType = type) }
    }

    fun setActiveMode(mode: ActiveMode) {
        _uiState.update { it.copy(activeMode = mode, errorMessage = null, statusMessage = null) }
    }

    fun decrypt() {
        val state = _uiState.value
        val rawInput = state.inputText.trim()

        if (rawInput.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Поле ввода пусто! Вставьте зашифрованную строку (Base91 токен).",
                    statusMessage = null
                )
            }
            return
        }

        try {
            val key = state.key.ifEmpty { null }
            val result = PackAndHideEngine.unpackAndRead(rawInput, key)

            val saltDisplay = if (result.salt != null) {
                // Try ascii string if printable or hex
                val isPrintable = result.salt.all { it in 32..126 }
                if (isPrintable) {
                    "${String(result.salt, Charsets.US_ASCII)} (0x${result.salt.joinToString("") { "%02x".format(it) }})"
                } else {
                    "0x" + result.salt.joinToString("") { "%02x".format(it) }
                }
            } else ""

            _uiState.update {
                it.copy(
                    outputText = result.text,
                    lastUnpackResult = result,
                    lastPackResult = null,
                    errorMessage = null,
                    statusMessage = "Успешно расшифровано! (${result.payloadType.displayName}, ${if (result.isCompressed) "сжато Deflate" else "без сжатия"})",
                    isSalted = result.isSalted,
                    saltText = if (result.isSalted && result.salt != null) {
                        result.salt.joinToString("") { b -> "%02x".format(b) }
                    } else it.saltText
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = e.message ?: "Ошибка расшифровки данных",
                    statusMessage = null,
                    lastUnpackResult = null
                )
            }
        }
    }

    fun encrypt() {
        val state = _uiState.value
        val rawInput = state.inputText

        if (rawInput.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Введите данные для шифрования!",
                    statusMessage = null
                )
            }
            return
        }

        try {
            val key = state.key.ifEmpty { null }
            val customSaltBytes = if (state.isSalted && state.saltText.isNotEmpty()) {
                parseSaltBytes(state.saltText)
            } else null

            val result = PackAndHideEngine.packAndHide(
                data = rawInput,
                key = key,
                salted = state.isSalted,
                customSalt = customSaltBytes,
                preferredType = state.selectedPayloadType
            )

            _uiState.update {
                it.copy(
                    outputText = result.token,
                    lastPackResult = result,
                    lastUnpackResult = null,
                    errorMessage = null,
                    statusMessage = "Успешно зашифровано в Base91 токен! (${result.payloadType.displayName})"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = e.message ?: "Ошибка упаковки данных",
                    statusMessage = null,
                    lastPackResult = null
                )
            }
        }
    }

    private fun parseSaltBytes(saltText: String): ByteArray {
        val trimmed = saltText.trim()
        // If 8 hex chars (4 bytes)
        if (trimmed.length == 8 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            val out = ByteArray(4)
            for (i in 0 until 4) {
                out[i] = trimmed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return out
        }
        val ascii = trimmed.toByteArray(Charsets.UTF_8)
        return if (ascii.size >= 4) {
            ascii.copyOf(4)
        } else {
            val padded = ByteArray(4)
            System.arraycopy(ascii, 0, padded, 0, ascii.size)
            padded
        }
    }

    fun swapFields() {
        _uiState.update {
            it.copy(
                inputText = it.outputText,
                outputText = it.inputText,
                errorMessage = null,
                statusMessage = "Поля поменяны местами"
            )
        }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "", errorMessage = null, statusMessage = null) }
    }

    fun clearOutput() {
        _uiState.update { it.copy(outputText = "", lastUnpackResult = null, lastPackResult = null) }
    }

    fun clearAll() {
        _uiState.update {
            it.copy(
                inputText = "",
                outputText = "",
                key = "",
                isSalted = false,
                saltText = "",
                lastUnpackResult = null,
                lastPackResult = null,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun loadPreset(preset: PresetsItem) {
        _uiState.update {
            it.copy(
                inputText = preset.token,
                key = preset.key,
                isSalted = preset.isSalted,
                saltText = preset.salt,
                activeMode = ActiveMode.DECRYPT,
                errorMessage = null,
                statusMessage = "Загружен шаблон: ${preset.title}. Нажмите 'Расшифровать'!"
            )
        }
    }
}
