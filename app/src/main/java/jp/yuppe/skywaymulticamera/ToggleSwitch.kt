package jp.yuppe.skywaymulticamera

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.yuppe.skywaymulticamera.ui.theme.SkyWayMultiCameraTheme

@Composable
fun ToggleSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = modifier) {
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(16.dp),
            style = MaterialTheme.typography.titleSmall
        )
        //Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ToggleSwitchPreview() {
    val checkedState = remember { mutableStateOf(true) }

    SkyWayMultiCameraTheme {
        ToggleSwitch("test", false) {
            checkedState.value = it
        }
    }
}