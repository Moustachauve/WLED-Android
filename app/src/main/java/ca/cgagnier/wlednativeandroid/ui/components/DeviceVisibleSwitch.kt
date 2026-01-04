package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R

@Composable
fun DeviceVisibleSwitch(
    modifier: Modifier = Modifier,
    isHidden: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = {
                    onCheckedChange(!isHidden)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isVisibleText = if (isHidden) stringResource(R.string.device_is_hidden) else stringResource(
            R.string.device_is_visible)
        val isVisibleIcon = painterResource(if (isHidden) R.drawable.ic_baseline_visibility_off_24 else R.drawable.baseline_visibility_24)
        AnimatedContent(
            targetState = isVisibleText,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "isVisibleTextAnimation",
            modifier = Modifier.weight(1f)
        ) {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .padding(end = 16.dp),
                maxLines = 2,
            )
        }
        Switch(
            checked = !isHidden,
            onCheckedChange = {
                onCheckedChange(!it)
            },
            thumbContent = {
                Icon(
                    painter = isVisibleIcon,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        )
    }
}