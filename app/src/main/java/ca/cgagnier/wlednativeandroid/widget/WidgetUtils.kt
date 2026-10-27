package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.content.Intent
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler

fun WidgetStateData.toOpenWidgetInAppIntent(context: Context): Intent =
    DeepLinkHandler.createDeviceIntent(context, macAddress)
