package com.nuvio.tv.ui.screens.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/** Whether this device has already been set up with a code, so a person who later removes their addons isn't asked again. */
internal object ActivationPrefs {
    private const val FILE = "activation"
    private const val DONE = "done"

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(DONE, false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(DONE, true).apply()
    }
}

data class CodeActivationUiState(
    val code: String = "",
    val isWorking: Boolean = false,
    val error: ActivationError? = null,
    val activated: Boolean = false
)

enum class ActivationError { INVALID, NETWORK }

/**
 * First launch: the person types the short code they were given, the server answers with their addon link, and the
 * addon is installed. See Telegram-Stremio's app_code_routes.py for the other end.
 */
@HiltViewModel
class CodeActivationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val addonRepository: AddonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodeActivationUiState())
    val uiState: StateFlow<CodeActivationUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) {
        val clean = value.uppercase().filter { it.isLetterOrDigit() }.take(CODE_LENGTH)
        _uiState.update { it.copy(code = clean, error = null) }
    }

    fun activate() {
        val state = _uiState.value
        if (state.isWorking || state.code.length < CODE_LENGTH) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val manifestUrl = withContext(Dispatchers.IO) { redeem(state.code) }
            when (manifestUrl) {
                is Redeem.Link -> {
                    addonRepository.addAddon(manifestUrl.url)
                    ActivationPrefs.markDone(context)
                    _uiState.update { it.copy(isWorking = false, activated = true) }
                }
                Redeem.Invalid -> _uiState.update { it.copy(isWorking = false, error = ActivationError.INVALID) }
                Redeem.Unreachable -> _uiState.update { it.copy(isWorking = false, error = ActivationError.NETWORK) }
            }
        }
    }

    private sealed interface Redeem {
        data class Link(val url: String) : Redeem
        data object Invalid : Redeem
        data object Unreachable : Redeem
    }

    private fun redeem(code: String): Redeem = runCatching {
        val request = Request.Builder().url("${BuildConfig.ACTIVATION_BASE_URL}/app/redeem/$code").build()
        okHttpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> Redeem.Invalid
                !response.isSuccessful -> Redeem.Unreachable
                else -> {
                    val url = JSONObject(response.body?.string().orEmpty()).optString("manifest_url").trim()
                    if (url.isEmpty()) Redeem.Invalid else Redeem.Link(url)
                }
            }
        }
    }.getOrDefault(Redeem.Unreachable)

    private companion object {
        const val CODE_LENGTH = 6
    }
}

/** A phone-style layout, ten keys wide; the third row ends with the backspace key. */
private val KEY_ROWS = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

/**
 * The code is typed on a keyboard of its own on the right (the TV's would cover the screen), and shows on the left
 * in six slots. Once the sixth character is in, focus moves to Activar.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CodeActivationScreen(
    onActivated: () -> Unit = {},
    viewModel: CodeActivationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.activated) {
        if (uiState.activated) onActivated()
    }
    val firstKey = remember { FocusRequester() }
    val activateFocus = remember { FocusRequester() }
    val canActivate = !uiState.isWorking && uiState.code.length >= CODE_SLOTS
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    LaunchedEffect(uiState.code.length) {
        if (uiState.code.length == CODE_SLOTS) runCatching { activateFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            // Lit from the top, falling to near-black below.
            .background(Brush.verticalGradient(0f to GlowTop, 0.55f to GlowBottom, 1f to GlowBottom))
            .padding(horizontal = 64.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.activation_title),
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                CodeSlots(uiState.code)
                Spacer(modifier = Modifier.height(28.dp))
                var buttonFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            when {
                                buttonFocused -> Color.White
                                canActivate -> Color.White.copy(alpha = 0.18f)
                                else -> Color.White.copy(alpha = 0.06f)
                            },
                            RoundedCornerShape(NuvioTheme.radii.md)
                        )
                        .focusRequester(activateFocus)
                        .onFocusChanged { buttonFocused = it.isFocused }
                        .clickable(enabled = canActivate) { viewModel.activate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.isWorking) R.string.activation_working else R.string.activation_button
                        ),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            buttonFocused -> Color.Black
                            canActivate -> NuvioTheme.colors.TextPrimary
                            else -> NuvioTheme.colors.TextTertiary
                        }
                    )
                }
                // The line is always there, so an error never moves anything.
                Spacer(modifier = Modifier.height(14.dp))
                val error = uiState.error
                Text(
                    text = if (error == null) "" else stringResource(
                        if (error == ActivationError.INVALID) R.string.activation_error_invalid
                        else R.string.activation_error_network
                    ),
                    fontSize = 16.sp,
                    color = NuvioTheme.colors.Error,
                    minLines = 2,
                    maxLines = 2
                )
            }
            Column(
                modifier = Modifier.weight(1.4f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KEY_ROWS.forEachIndexed { row, chars ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The short bottom row is centred between two half-gaps.
                        if (row == KEY_ROWS.lastIndex) Spacer(modifier = Modifier.weight(1.5f))
                        chars.forEachIndexed { column, char ->
                            CodeKey(
                                label = char.toString(),
                                modifier = Modifier.weight(1f).height(44.dp),
                                requester = if (row == 0 && column == 0) firstKey else null,
                                onClick = { viewModel.onCodeChange(uiState.code + char) }
                            )
                        }
                        if (row == KEY_ROWS.lastIndex) Spacer(modifier = Modifier.weight(1.5f))
                        if (row == KEY_ROWS.lastIndex - 1) {
                            CodeKey(
                                label = "⌫",
                                modifier = Modifier.weight(1f).height(44.dp),
                                requester = null,
                                onClick = { viewModel.onCodeChange(uiState.code.dropLast(1)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CODE_SLOTS = 6

/** The six places the code fills in; the next one to fill is outlined. */
@Composable
private fun CodeSlots(code: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(CODE_SLOTS) { index ->
            val next = index == code.length
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(NuvioTheme.radii.md))
                    .border(
                        width = if (next) 2.dp else 1.dp,
                        color = if (next) Color.White else Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(NuvioTheme.radii.md)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code.getOrNull(index)?.toString().orEmpty(),
                    color = NuvioTheme.colors.TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CodeKey(label: String, modifier: Modifier, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(NuvioTheme.radii.sm)
            )
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.Black else NuvioTheme.colors.TextPrimary
        )
    }
}

private val GlowTop = Color(0xFF3A1E23)
private val GlowBottom = Color(0xFF0B0A0B)
