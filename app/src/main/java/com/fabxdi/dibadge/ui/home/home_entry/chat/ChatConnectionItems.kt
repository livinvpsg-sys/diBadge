package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.util.UserColorUtils

/**
 * Card shown in the RECEIVING group's chat when another group asks to connect.
 * Pending: Decline / Accept (admins only). Afterwards the buttons are replaced by a grey line.
 */
@Composable
fun ConnectionRequestCard(
    message: ChatMessage,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onRespond: (connectionId: String, newStatus: String) -> Unit
) {
    val status = message.libraryRevision ?: "pending"
    val connId = message.libraryFileId ?: return
    val canRespond = message.libraryCount == 1
    val name = message.text
    val avatarColor = UserColorUtils.getColorForName(name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(cardColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = avatarColor, border = BorderStroke(1.dp, outlineColor)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                Text(
                    text = if (status == "pending") "wants to connect with this group" else "wanted to connect with this group",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }
        }

        if (status == "pending") {
            Text(
                "Connected groups can share messages and files.",
                fontSize = 12.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), thickness = 1.dp, color = outlineColor)

        when {
            status == "pending" && canRespond -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Decline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor,
                    modifier = Modifier.clickable { onRespond(connId, "declined") }.padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Text(
                    "Accept", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accentColor,
                    modifier = Modifier.clickable { onRespond(connId, "accepted") }.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            status == "pending" -> Text(
                "Only admins can respond", fontSize = 12.sp, color = secondaryTextColor,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            status == "accepted" -> Text(
                "Connected", fontSize = 13.sp, color = secondaryTextColor,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            else -> Text(
                "Request declined", fontSize = 13.sp, color = secondaryTextColor,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }
}

/** Dim centred line in the SENDING group's chat: "X declined the request" / "Now connected to X". */
@Composable
fun ConnectionNoticeLine(message: ChatMessage, secondaryTextColor: Color) {
    Text(
        text = message.text,
        fontSize = 12.sp,
        color = secondaryTextColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
    )
}
