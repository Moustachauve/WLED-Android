package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler

@ColorInt
fun brightenColor(@ColorInt color: Int, factor: Float): Int =
    ColorUtils.blendARGB(color, android.graphics.Color.WHITE, factor)

fun WidgetStateData.toOpenWidgetInAppIntent(context: Context): Intent =
    DeepLinkHandler.createDeviceIntent(context, macAddress)
