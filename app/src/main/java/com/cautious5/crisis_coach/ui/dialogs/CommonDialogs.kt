package com.cautious5.crisis_coach.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cautious5.crisis_coach.ui.components.LoadingIndicator

/**
 * A dialog that shows when the AI model is being reloaded or reconfigured.
 */
@Composable
fun ModelReloadingDialog(isApplyingParams: Boolean) {
    Dialog(
        onDismissRequest = { /* Non-dismissible */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val title = if (isApplyingParams) "Applying Parameters" else "Reconfiguring Model"
                val subtitle = if (isApplyingParams) {
                    "Please wait while new generation settings are applied..."
                } else {
                    "Please wait while the hardware configuration is updated..."
                }

                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LoadingIndicator(subtitle)
            }
        }
    }
}