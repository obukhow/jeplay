package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.text.StaticLayout
import android.view.View
import com.shilapi.xcertplay.host.R

/** Local music card inside the existing projection; does not change any OEM cluster mode. */
internal class ClusterMusicView(context: Context) : View(context) {
    private val main = Handler(Looper.getMainLooper())
    private var stopState: (() -> Unit)? = null
    private var stopSettings: (() -> Unit)? = null
    private var snapshot = ClusterMusicState.Snapshot()
    private var visibleUntil = 0L
    private var lastTrackRevision: Long? = null
    private val expire = Runnable { updateVisibility() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }

    init { visibility = GONE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopSettings = ClusterMusicSettings.observe(context) {
            visibleUntil = SystemClock.elapsedRealtime() + TEMPORARY_MILLIS
            updateVisibility()
        }
        stopState = ClusterMusicState.observe {
            snapshot = it
            if (lastTrackRevision != it.revision) {
                lastTrackRevision = it.revision
                visibleUntil = it.changedAt + TEMPORARY_MILLIS
            }
            updateVisibility()
        }
    }

    override fun onDetachedFromWindow() {
        stopState?.invoke(); stopState = null
        stopSettings?.invoke(); stopSettings = null
        main.removeCallbacksAndMessages(null)
        visibility = GONE
        snapshot = ClusterMusicState.Snapshot()
        lastTrackRevision = null
        super.onDetachedFromWindow()
    }

    private fun updateVisibility() {
        main.removeCallbacks(expire)
        val hasTrack = !snapshot.info.title.isNullOrBlank() || !snapshot.info.artist.isNullOrBlank()
        val permanent = ClusterMusicSettings.permanent(context)
        val remaining = visibleUntil - SystemClock.elapsedRealtime()
        visibility = if (ClusterMusicSettings.enabled(context) && hasTrack && (permanent || remaining > 0)) VISIBLE else GONE
        // Temporary mode hides the whole card; permanent keeps cover and caption together.
        if (visibility == VISIBLE && !permanent && remaining > 0) main.postDelayed(expire, remaining)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val side = tileSide(width, height, ClusterMusicSettings.size(context))
        val gap = side * .10f
        // Anchor the cover alone so the horizontal slider can reach the right edge;
        // the caption uses a narrow column and shrinks if the cover sits near the rim.
        val coverLeft = (width * ClusterMusicSettings.x(context) / 100f - side / 2)
            .coerceIn(0f, width - side)
        val top = (height * ClusterMusicSettings.y(context) / 100f - side / 2).coerceIn(0f, height - side)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val room = if (rtl) (coverLeft - gap).coerceAtLeast(1f)
            else (width - coverLeft - side - gap).coerceAtLeast(1f)
        val textWidth = (side * 1.15f).coerceAtMost(room)
        val textLeft = if (rtl) coverLeft - gap - textWidth else coverLeft + side + gap
        val rect = RectF(coverLeft, top, coverLeft + side, top + side)
        // Match the speedometer's palette and corner radius relative to tile height.
        val radius = side * .22f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(217, 12, 14, 18)
        canvas.drawRoundRect(rect, radius, radius, paint)
        val inset = side * .05f
        val artRect = RectF(coverLeft + inset, top + inset, rect.right - inset, rect.bottom - inset)
        val clipped = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(artRect, radius - inset, radius - inset, Path.Direction.CW) })
        val art = snapshot.artwork?.takeUnless { it.isRecycled }
        paint.color = Color.WHITE
        if (art != null) canvas.drawBitmap(art, null, artRect, paint)
        else {
            paint.color = Color.rgb(28, 32, 40)
            canvas.drawRect(artRect, paint)
            textPaint.textSize = side * .40f
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("♪", rect.centerX(), top + side * .67f, textPaint)
        }
        canvas.restoreToCount(clipped)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = side * .012f
        paint.color = Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL

        val available = textWidth.toInt().coerceAtLeast(1)
        // Flush to the cover edge: left-aligned in LTR (text to the right of the tile).
        textPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = side * .16f
        val title = snapshot.info.title.orEmpty()
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, textPaint, available)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
            .setAlignment(if (rtl) android.text.Layout.Alignment.ALIGN_OPPOSITE
                else android.text.Layout.Alignment.ALIGN_NORMAL).build()
        textPaint.textSize = side * .12f
        val detail = snapshot.info.artist?.takeIf { it.isNotBlank() }
            ?: snapshot.info.album?.takeIf { it.isNotBlank() } ?: context.getString(R.string.settings_cluster_music_unknown)
        val detailLine = TextUtils.ellipsize(detail, textPaint, available.toFloat(), TextUtils.TruncateAt.END).toString()
        val detailHeight = side * .12f
        val blockHeight = layout.height + side * .045f + detailHeight
        val textTop = top + ((side - blockHeight) / 2f).coerceAtLeast(0f)
        val anchorX = if (rtl) textLeft + textWidth else textLeft
        val translated = canvas.save()
        canvas.translate(textLeft, textTop)
        layout.draw(canvas)
        canvas.restoreToCount(translated)
        textPaint.color = Color.rgb(220, 224, 232)
        canvas.drawText(detailLine, anchorX, textTop + layout.height + side * .04f + detailHeight * .85f, textPaint)
    }

    companion object {
        const val TEMPORARY_MILLIS = 8_000L
        // 100% matches the default speedometer tile height on a 1280x480 panel.
        fun tileSide(width: Int, height: Int, sizePercent: Int): Float =
            (height * .30f * sizePercent.coerceIn(50, 150) / 100f)
                .coerceAtLeast(minOf(80f, width.toFloat(), height.toFloat()))
                .coerceAtMost(minOf(width.toFloat(), height.toFloat()))
    }
}
