package com.nuvio.tv.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.util.Locale

private val APP_LOCALE_TAGS = listOf(
    "en", "ru", "ar", "bg", "bs", "da", "de", "el", "es", "es-419", "hu", "fr", "in", "it",
    "no", "pl", "pt-PT", "pt-BR", "tr", "uk", "cs", "sk", "sl", "sq", "sr-Latn", "sv", "ta", "ro", "ja",
    "nl", "vi", "hi", "lt", "he", "zh-CN", "zh-TW", "bn", "hr"
)

/**
 * The language the app is shown in; "System" follows the device. TMDB's titles and descriptions follow this too, so
 * there is no second language to set.
 */
@Composable
fun AppLanguageSettingsContent(initialFocusRequester: FocusRequester? = null) {
    val context = LocalContext.current
    val systemLabel = stringResource(R.string.appearance_language_system)
    val restartHint = stringResource(R.string.appearance_language_restart_hint)
    val locales = remember(systemLabel) {
        listOf<Pair<String?, String>>(null to systemLabel) + APP_LOCALE_TAGS.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            tag to locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
        }.sortedBy { it.second }
    }
    var selectedTag by remember {
        mutableStateOf(
            context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                .getString("locale_tag", null)
                .let { stored -> stored ?: com.nuvio.tv.DEFAULT_APP_LOCALE_TAG }
                .takeIf { it.isNotEmpty() }
        )
    }
    var showDialog by remember { mutableStateOf(false) }
    var pendingRestart by remember { mutableStateOf(false) }
    val currentName = locales.firstOrNull { it.first == selectedTag }?.second ?: systemLabel

    LaunchedEffect(pendingRestart, showDialog) {
        if (pendingRestart && !showDialog) {
            // Let the dialog window detach before recreating the Activity.
            delay(150)
            context.findHostActivity()?.recreate()
                ?: Toast.makeText(context, restartHint, Toast.LENGTH_LONG).show()
            pendingRestart = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.appearance_language),
            subtitle = stringResource(R.string.appearance_language_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            val addonLanguage = LocaleCache.addonTag
            SettingsActionRow(
                title = stringResource(R.string.appearance_language),
                subtitle = stringResource(
                    if (addonLanguage != null) R.string.appearance_language_by_addon else R.string.appearance_language_subtitle
                ),
                value = addonLanguage?.let { tag ->
                    Locale.forLanguageTag(tag).let { it.getDisplayName(it) }.replaceFirstChar { c -> c.uppercase() }
                } ?: currentName,
                enabled = addonLanguage == null,
                onClick = { showDialog = true },
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }

    if (showDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.appearance_language_dialog_title),
            options = locales.map { (tag, name) -> SettingsPickerOption(tag, name) },
            selectedValue = selectedTag,
            onOptionSelected = { tag ->
                val changed = tag != selectedTag
                val stored = tag ?: ""
                context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                    .edit().putString("locale_tag", stored).apply()
                LocaleCache.localeTag = stored
                selectedTag = tag
                showDialog = false
                if (changed) pendingRestart = true
            },
            onDismiss = { showDialog = false },
            width = 400.dp,
            maxHeight = 280.dp
        )
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
