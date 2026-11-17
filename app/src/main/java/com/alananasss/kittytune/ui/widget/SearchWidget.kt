    package com.alananasss.kittytune.ui.widget

    import android.content.Context
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.glance.GlanceId
    import androidx.glance.GlanceModifier
    import androidx.glance.GlanceTheme
    import androidx.glance.Image
    import androidx.glance.ImageProvider
    import androidx.glance.LocalContext
    import androidx.glance.action.clickable
    import androidx.glance.appwidget.GlanceAppWidget
    import androidx.glance.appwidget.action.actionRunCallback
    import androidx.glance.appwidget.cornerRadius
    import androidx.glance.appwidget.provideContent
    import androidx.glance.background
    import androidx.glance.layout.Alignment
    import androidx.glance.layout.Box
    import androidx.glance.layout.Row
    import androidx.glance.layout.Spacer
    import androidx.glance.layout.fillMaxSize
    import androidx.glance.layout.height
    import androidx.glance.layout.padding
    import androidx.glance.layout.size
    import androidx.glance.layout.width
    import androidx.glance.text.FontWeight
    import androidx.glance.text.Text
    import androidx.glance.text.TextStyle
    import com.alananasss.kittytune.R

    class SearchWidget : GlanceAppWidget() {

        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                GlanceTheme {
                    SearchContent()
                }
            }
        }

        @Composable
        private fun SearchContent() {
            val context = LocalContext.current

            // the outer container uses the main surface color, consistent with other widgets
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(32.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // this inner pill now specifically handles the 'open search' action
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(48.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(24.dp)
                        .padding(horizontal = 16.dp)
                        .clickable(actionRunCallback<OpenSearchAction>()), // action to open search
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_search_widget),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp)
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    Text(
                        text = context.getString(R.string.widget_search_hint),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                Spacer(GlanceModifier.width(8.dp))

                // the circular button now handles the simple 'open app' action
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(24.dp)
                        .clickable(actionRunCallback<OpenAppAction>()), // action to just open the app
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        // icon size increased for better visibility
                        modifier = GlanceModifier.size(67.dp)
                    )
                }
            }
        }
    }

