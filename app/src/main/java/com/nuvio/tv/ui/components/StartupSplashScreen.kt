package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    // Pure black for OLED; the logo is the only thing on screen besides the spinner.
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tivilatino_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxl))
            LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xxxl))
        }
    }
}
