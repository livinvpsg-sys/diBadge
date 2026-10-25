package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun DimLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    isSingleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val tealAccent = DiBadgeTheme.colors.accent
    val outlineColor = colors.outline.copy(alpha = 0.60f)
    val errorColor = colors.error

    val lineThickness = if (isFocused) 2.dp else 1.dp
    val lineColor = when {
        isError -> errorColor
        isFocused -> tealAccent
        else -> outlineColor
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = isSingleLine,
                maxLines = if (isSingleLine) 1 else 6,
                keyboardOptions = keyboardOptions,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = primaryTextColor
                ),
                cursorBrush = SolidColor(tealAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .padding(bottom = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        HorizontalDivider(
            thickness = lineThickness,
            color = lineColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun filterNumberInput(input: String): String {
    val sb = StringBuilder()
    var hasDecimal = false
    input.forEachIndexed { index, char ->
        if (char.isDigit()) {
            sb.append(char)
        } else if (char == '-' && index == 0) {
            sb.append(char)
        } else if (char == '.' && !hasDecimal) {
            hasDecimal = true
            sb.append(char)
        }
    }
    return sb.toString()
}
