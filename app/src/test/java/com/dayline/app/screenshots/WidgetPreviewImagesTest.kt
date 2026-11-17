package com.dayline.app.screenshots

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every widget's picker preview layout to a PNG at its default home-screen size. These
 * pictures ship as `previewImage`, the fallback for launchers that can't draw layout previews.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetPreviewImagesTest {

    private fun render(name: String, layout: Int, widthDp: Int, heightDp: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val root = FrameLayout(context)
        val content = LayoutInflater.from(context).inflate(layout, root, false)
        root.addView(content, FrameLayout.LayoutParams(w, h))
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        // Draw straight to a transparent bitmap (Robolectric's native graphics renders real pixels).
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val out = java.io.File("build/outputs/roborazzi/widget_previews/widget_preview_$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tasks() = render("tasks", R.layout.preview_tasks, 300, 230)
    @Test fun habits() = render("habits", R.layout.preview_habits, 150, 150)
    @Test fun focus() = render("focus", R.layout.preview_focus, 300, 150)
    @Test fun progress() = render("progress", R.layout.preview_progress, 150, 150)
    @Test fun quickAdd() = render("quickadd", R.layout.preview_quickadd, 72, 72)
    @Test fun nextUp() = render("next_up", R.layout.preview_next_up, 300, 72)
    @Test fun habitFlower() = render("habit_flower", R.layout.preview_habit_flower, 150, 150)
}
