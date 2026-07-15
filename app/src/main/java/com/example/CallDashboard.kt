package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AgencyCyan
import com.example.ui.theme.AgencyEmerald
import com.example.ui.theme.BorderDark
import com.example.ui.theme.SurfaceDark

data class CallRecord(
    val id: String,
    val callerId: String,
    val duration: String,
    val summary: String,
    val isActive: Boolean
)

@Composable
fun CallDashboardComponent(activeCall: CallRecord?, recentCalls: List<CallRecord>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(title = "CALLS TODAY", value = "124", color = AgencyCyan, modifier = Modifier.weight(1f))
                MetricCard(title = "AVG CONFIDENCE", value = "96.4%", color = AgencyEmerald, modifier = Modifier.weight(1f))
            }
        }

        if (activeCall != null) {
            item {
                Text(
                    text = "ACTIVE CALL",
                    color = AgencyCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                CallRecordCard(record = activeCall)
            }
        }
        
        if (recentCalls.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RECENT CALLS",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(recentCalls) { call ->
                CallRecordCard(record = call)
            }
        }
    }
}

@Composable
fun CallRecordCard(record: CallRecord) {
    val statusColor = if (record.isActive) AgencyEmerald else Color.Gray

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderColor by animateColorAsState(
        targetValue = if (record.isActive) {
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            AgencyEmerald.copy(alpha = alpha)
        } else {
            BorderDark
        },
        label = "borderColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (record.isActive) Icons.Default.Phone else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = record.callerId,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = if (record.isActive) "LIVE" else record.duration,
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "SUMMARY TRANSCRIPT",
            color = Color.DarkGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = record.summary,
            color = Color.LightGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = if (title.contains("CALL")) Icons.Default.Phone else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color.copy(alpha = 0.7f),
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = title,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}

