@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dodocomm.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.util.OtpExtractor
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.ui.theme.AvatarPaletteDark
import com.dodocomm.app.ui.theme.AvatarPaletteLight
import com.dodocomm.app.ui.theme.CardShape
import com.dodocomm.app.ui.theme.FilteredDark
import com.dodocomm.app.ui.theme.FilteredLight
import com.dodocomm.app.ui.theme.LocalBrandGradient
import com.dodocomm.app.ui.theme.LocalIsDarkTheme
import com.dodocomm.app.ui.theme.PendingDark
import com.dodocomm.app.ui.theme.PendingLight
import com.dodocomm.app.ui.theme.SentDark
import com.dodocomm.app.ui.theme.SentLight
import com.dodocomm.app.ui.theme.TonalPair
import com.dodocomm.app.ui.theme.brandBrush
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Status semantics
// ---------------------------------------------------------------------------

fun statusLabel(status: ForwardStatus): String = when (status) {
    ForwardStatus.SENT -> "已送达"
    ForwardStatus.PENDING -> "待发送"
    ForwardStatus.FAILED -> "待重试"
    ForwardStatus.FILTERED -> "已过滤"
    ForwardStatus.SKIPPED -> "已跳过"
}

@Composable
fun statusTonalPair(status: ForwardStatus): TonalPair {
    val dark = LocalIsDarkTheme.current
    return when (status) {
        ForwardStatus.SENT -> if (dark) SentDark else SentLight
        ForwardStatus.PENDING -> if (dark) PendingDark else PendingLight
        ForwardStatus.FAILED -> TonalPair(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )

        ForwardStatus.FILTERED,
        ForwardStatus.SKIPPED,
        -> if (dark) FilteredDark else FilteredLight
    }
}

// ---------------------------------------------------------------------------
// Chips / avatars / stats
// ---------------------------------------------------------------------------

/** Compact status pill: coloured dot + Chinese label. */
@Composable
fun StatusChip(status: ForwardStatus, modifier: Modifier = Modifier) {
    val tonal = statusTonalPair(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tonal.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tonal.content.copy(alpha = 0.9f)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = tonal.content,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Rounded sender avatar with a stable pastel tone derived from the sender. */
@Composable
fun SenderAvatar(
    sender: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val palette = if (LocalIsDarkTheme.current) AvatarPaletteDark else AvatarPaletteLight
    val tonal = palette[Math.floorMod(sender.hashCode(), palette.size)]
    val initial = senderInitial(sender)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tonal.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = tonal.content,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Today's overview pill: big animated number + small label. */
@Composable
fun StatPill(
    value: Int,
    label: String,
    tonal: TonalPair,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = tonal.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { height -> -height / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { height -> height / 2 } + fadeOut())
                },
                label = "statValue",
            ) { animatedValue ->
                Text(
                    text = animatedValue.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = tonal.content,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tonal.content.copy(alpha = 0.9f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Custom gradient switch (hero card)
// ---------------------------------------------------------------------------

/** Pill switch whose on-state track uses the brand gradient. */
@Composable
fun GradientSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gradient = LocalBrandGradient.current
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "gradientSwitch",
    )
    val trackWidth = 60.dp
    val trackHeight = 36.dp
    val thumbSize = 30.dp
    val inset = 3.dp
    val travelPx = with(LocalDensity.current) { (trackWidth - thumbSize - inset * 2).toPx() }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(
                if (checked) {
                    androidx.compose.ui.graphics.Brush.horizontalGradient(gradient)
                } else {
                    SolidColor(Color.White.copy(alpha = 0.2f))
                },
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (checked) 0.34f else 0.3f),
                shape = CircleShape,
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(inset),
    ) {
        Box(
            Modifier
                .size(thumbSize)
                .offset { IntOffset((travelPx * progress).roundToInt(), 0) }
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// ---------------------------------------------------------------------------
// Settings rows
// ---------------------------------------------------------------------------

/** Title + optional subtitle + trailing switch; whole row is the touch target. */
private fun settingLeadingIcon(title: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    title.contains("更新") -> Icons.Rounded.SystemUpdate
    title.contains("通知") || title.contains("角标") -> Icons.Rounded.Notifications
    title.contains("备份") -> Icons.Rounded.Backup
    title.contains("恢复") -> Icons.Rounded.Restore
    title.contains("导出") -> Icons.Rounded.FileDownload
    title.contains("清理") || title.contains("删除") -> Icons.Rounded.Delete
    title.contains("锁") || title.contains("PIN") || title.contains("指纹") -> Icons.Rounded.Lock
    title.contains("保留") || title.contains("期限") -> Icons.Rounded.Schedule
    title.contains("语言") -> Icons.Rounded.Language
    title.contains("取色") -> Icons.Rounded.Palette
    title.contains("隐藏") -> Icons.Rounded.VisibilityOff
    title.contains("开机") -> Icons.Rounded.RestartAlt
    title.contains("电池") -> Icons.Rounded.BatterySaver
    title.contains("帮助") || title.contains("向导") || title.contains("保活") -> Icons.Rounded.Bolt
    title.contains("复制") -> Icons.Rounded.ContentCopy
    title.contains("记录") -> Icons.Rounded.History
    title.contains("服务器") -> Icons.Rounded.Cloud
    title.contains("Device") -> Icons.Rounded.Key
    title.contains("测试") -> Icons.Rounded.Send
    title.contains("锁定") -> Icons.Rounded.Timer
    else -> Icons.Rounded.Tune
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon ?: settingLeadingIcon(title), size = 34, iconSize = 18)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            ),
        )
    }
}

/** Chevron row that opens another screen or a system settings page. */
@Composable
fun SettingActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    showChevron: Boolean = true,
    destructive: Boolean = false,
) {
    val titleColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(
            icon = icon ?: settingLeadingIcon(title),
            size = 34,
            iconSize = 18,
            containerColor = if (destructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** Small "+ token" chip used by the title-template editor. */
@Composable
fun TokenChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Numbered gradient badge for the keep-alive guide steps. */
@Composable
fun StepNumberBadge(index: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(brandBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Record card (shared by home "recent" and history list)
// ---------------------------------------------------------------------------

@Composable
fun RecordCard(
    record: ForwardRecord,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SenderAvatar(sender = record.sender)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displaySender(record.sender),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(status = record.status)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(timeLabel, record.simLabel?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OtpExtractor.extract(record.body)?.let { otp ->
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "验证码 $otp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.body.ifBlank { "（无正文）" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val error = record.error
                if (record.status == ForwardStatus.FAILED && !error.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Section row used by simple two-column detail metadata. */
@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
