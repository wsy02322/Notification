package com.wsy.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wsy.notification.alert.AlertItem

@Composable
fun AlertScreen(
    items: List<AlertItem>,
    onConfirm: () -> Unit,
) {
    val latest = items.lastOrNull()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error)
            .systemBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (items.size <= 1) "消息命中" else "消息命中（${items.size} 条）",
                color = MaterialTheme.colorScheme.onError,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = latest?.appLabel.orEmpty().ifBlank { "未知应用" },
                color = MaterialTheme.colorScheme.onError,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!latest?.title.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = latest?.title.orEmpty(),
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (!latest?.text.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = latest?.text.orEmpty(),
                    color = MaterialTheme.colorScheme.onError.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onError,
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("我知道了", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
