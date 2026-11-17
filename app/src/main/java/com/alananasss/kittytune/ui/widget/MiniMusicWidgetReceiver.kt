    package com.alananasss.kittytune.ui.widget

    import androidx.glance.appwidget.GlanceAppWidget
    import androidx.glance.appwidget.GlanceAppWidgetReceiver

    class MiniMusicWidgetReceiver : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = MiniMusicWidget()
    }

