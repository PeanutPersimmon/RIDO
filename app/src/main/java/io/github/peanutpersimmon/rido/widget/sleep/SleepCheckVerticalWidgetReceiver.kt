package io.github.peanutpersimmon.rido.widget.sleep

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class SleepCheckVerticalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget =
        SleepCheckWidget(SleepWidgetLayoutType.Vertical)
}
