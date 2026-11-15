package com.fabxdi.dibadge.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R

/**
 * Launch screen: the diBadge logo with the product name under it, and the company
 * ("from fabxdi") small at the bottom. Colors follow the app theme (light or dark).
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    // Fixed dark brand colours so it matches the system launch screen exactly
    val bg = Color(0xFF0E1114)
    val fg = Color(0xFFF0F3F5)
    val fgMuted = Color(0xFF8A9199)

    // The text eases in; the logo is already there
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val textAlpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "splashTextAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_dibadge_mark),
                contentDescription = "diBadge logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 86.dp, height = 113.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "diBadge",
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp,
                color = fg,
                modifier = Modifier.alpha(textAlpha)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 38.dp)
                .alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "from",
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = fgMuted
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "fabxdi",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = fg.copy(alpha = 0.85f)
            )
        }
    }
}