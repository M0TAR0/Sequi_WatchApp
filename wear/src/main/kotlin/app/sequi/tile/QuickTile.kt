package app.sequi.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import app.sequi.data.Repo
import app.sequi.engine.fmtClock
import app.sequi.engine.summary
import app.sequi.engine.totalSeconds
import app.sequi.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** One swipe from the watch face: your three most recent timers, tap to start. */
class QuickTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        Repo.init(this)
        val timers = Repo.quickTimers(3)

        val column = Column.Builder().setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        column.addContent(Spacer.Builder().setHeight(dp(18f)).build())
        if (timers.isEmpty()) {
            column.addContent(text("No timers yet", 14f, 0xFFECEFF1.toInt()))
        }
        timers.forEach { s ->
            column.addContent(row(s.id, s.name, s.spec.summary() + " · " + fmtClock(s.spec.totalSeconds() * 1000L)))
            column.addContent(Spacer.Builder().setHeight(dp(6f)).build())
        }
        val root = Box.Builder().setWidth(expand()).setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column.build()).build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(VERSION)
                .setTileTimeline(Timeline.fromLayoutElement(root))
                .setFreshnessIntervalMillis(0)
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(VERSION).build())

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = Text.Builder()
        .setText(t).setMaxLines(1)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(androidx.wear.protolayout.DimensionBuilders.sp(size))
            .setColor(argb(color)).apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }.build())
        .build()

    private fun row(id: String, name: String, sub: String): LayoutElementBuilders.LayoutElement {
        val click = ModifiersBuilders.Clickable.Builder().setId("start-$id").setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder().setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(MainActivity.EXTRA_START, ActionBuilders.stringExtra(id)).build()
            ).build()
        ).build()
        return Box.Builder().setWidth(dp(168f)).setHeight(dp(52f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(click)
                .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(0xFF123D2C.toInt()))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(26f)).build()).build())
                .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).build()).build())
            .addContent(Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .addContent(text(name, 14f, 0xFFC4F7DE.toInt(), bold = true))
                .addContent(text(sub, 10f, 0xFF8A949C.toInt())).build())
            .build()
    }

    companion object { private const val VERSION = "1" }
}
