package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.alananasss.kittytune.domain.User

@Composable
fun MiniSocialProofAvatars(
    likers: List<User>,
    modifier: Modifier = Modifier
) {
    if (likers.isEmpty()) return
    val displayLikers = remember(likers) { likers.take(2) }
    val borderColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        displayLikers.forEachIndexed { index, user ->
            val fallbackTeal = Color(0xFF00897B)
            val avatarUrl = user.avatarUrl?.replace("large", "t500x500")

            Box(
                modifier = Modifier
                    .zIndex((displayLikers.size - index).toFloat())
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(fallbackTeal)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = user.username,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = user.username?.firstOrNull()?.uppercase() ?: "T",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
