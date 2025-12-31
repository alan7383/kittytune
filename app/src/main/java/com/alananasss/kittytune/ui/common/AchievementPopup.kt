package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R

@Composable
fun AchievementPopup(notification: AchievementNotification) {
    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E).copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                val circleColor = Color(0xFF50C878)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = circleColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6f)
                    )
                }
                if(notification.iconEmoji != null) {
                    Text(text = notification.iconEmoji, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (notification.xpReward != null) {
                        Text(
                            text = stringResource(id = R.string.achievement_xp, notification.xpReward),
                            color = Color(0xFFD4AF37),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " - ",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = notification.subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}