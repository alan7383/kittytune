    package com.alananasss.kittytune.ui.widget

    import androidx.glance.appwidget.GlanceAppWidget
    import androidx.glance.appwidget.GlanceAppWidgetReceiver

    class SearchWidgetReceiver : GlanceAppWidgetReceiver() {
        override val glanceAppWidget: GlanceAppWidget = SearchWidget()
    }

