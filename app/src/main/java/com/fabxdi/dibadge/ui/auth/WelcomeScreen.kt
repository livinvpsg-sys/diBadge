package com.fabxdi.dibadge.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R

val WelcomeBackgroundColor = Color(0xFF262624)
val WelcomeButtonColor = Color(0xFF14B8A6)
val WelcomeButtonLabelColor = Color(0xFF00201D)

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeBackgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Logo: Horizontally centered, vertically centered in the space above the button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 132.dp), // Leaves space for the bottom button
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_dibadge_logo),
                contentDescription = "diBadge logo",
                modifier = Modifier.size(width = 112.dp, height = 136.dp)
            )
        }

        // Button "Get started": Bottom edge 84dp above bottom of screen plus navigation insets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onGetStartedClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WelcomeButtonColor,
                    contentColor = WelcomeButtonLabelColor
                ),
                contentPadding = PaddingValues(horizontal = 32.dp),
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(max = 420.dp)
            ) {
                Text(
                    text = "Get started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WelcomeButtonLabelColor
                )
            }
        }
    }
}
