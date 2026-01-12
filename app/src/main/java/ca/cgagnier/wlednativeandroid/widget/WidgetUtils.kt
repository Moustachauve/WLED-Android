package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import ca.cgagnier.wlednativeandroid.ui.MainActivity

@ColorInt
fun brightenColor(@ColorInt color: Int, factor: Float): Int =
    ColorUtils.blendARGB(color, android.graphics.Color.WHITE, factor)

fun WidgetStateData.toOpenWidgetInAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_DEVICE_MAC_ADDRESS, macAddress)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
