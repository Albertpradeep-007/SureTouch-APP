package com.example.suretouchapp.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.suretouchapp.data.model.accountRoleLabel

@Composable
internal fun AccountRoleChoiceDialog(
    roles: List<String>,
    forPasswordReset: Boolean = false,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (roles.distinct().size < 2) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forPasswordReset) "Reset password for which account?" else "Which account do you want to use?") },
        text = {
            Column {
                Text("This email is linked to these roles. Choose your account.")
                roles.distinct().forEach { role ->
                    TextButton(onClick = { onSelect(role) }, modifier = Modifier.fillMaxWidth()) {
                        Text(accountRoleLabel(role))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
