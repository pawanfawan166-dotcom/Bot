package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AgentRole
import com.example.model.AgentStageState
import com.example.model.AgentStageStatus
import com.example.model.AgentVisualState
import com.example.model.toVisualState
import com.example.ui.theme.LocalDeskColors

@Immutable
private data class AgentStatusVisualSpec(
    val state: AgentVisualState,
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color
)

@Composable
private fun visualSpecFor(state: AgentVisualState): AgentStatusVisualSpec {
    val desk = LocalDeskColors.current
    return when (state) {
        AgentVisualState.IDLE -> AgentStatusVisualSpec(
            state = state,
            label = stringResource(id = R.string.agent_state_idle),
            icon = Icons.Default.RadioButtonUnchecked,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = desk.borderSubtle
        )
        AgentVisualState.PROCESSING -> AgentStatusVisualSpec(
            state = state,
            label = stringResource(id = R.string.agent_state_processing),
            icon = Icons.Default.Sync,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = desk.dataSky,
            borderColor = desk.dataSky
        )
        AgentVisualState.SUCCESS -> AgentStatusVisualSpec(
            state = state,
            label = stringResource(id = R.string.agent_state_success),
            icon = Icons.Default.CheckCircle,
            containerColor = desk.bullContainer,
            contentColor = desk.bullEmerald,
            borderColor = desk.bullEmerald
        )
        AgentVisualState.FAILED -> AgentStatusVisualSpec(
            state = state,
            label = stringResource(id = R.string.agent_state_failed),
            icon = Icons.Default.Cancel,
            containerColor = desk.stopContainer,
            contentColor = desk.stopCrimson,
            borderColor = desk.stopCrimson
        )
    }
}

/**
 * Pinned dashboard status bar displaying live status of all 7 agents
 * across Idle, Processing, Success, and Failed states with color-coded icons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentStatusBar(
    stages: List<AgentStageState>,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val stageByRole = remember(stages) { stages.associateBy { it.role } }
    val orderedStages = remember(stageByRole) {
        AgentRole.entries.map { role ->
            stageByRole[role] ?: AgentStageState(role = role, status = AgentStageStatus.PENDING, note = "Idle")
        }
    }

    var pinnedRole by remember { mutableStateOf<AgentRole?>(null) }
    val hasProcessing = orderedStages.any { it.status.toVisualState() == AgentVisualState.PROCESSING }
    val hasFailed = orderedStages.any { it.status.toVisualState() == AgentVisualState.FAILED }
    val successCount = orderedStages.count { it.status.toVisualState() == AgentVisualState.SUCCESS }

    val infiniteTransition = rememberInfiniteTransition(label = "agent_status_spin")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "processing_icon_rotation"
    )

    val summarySpec = when {
        hasFailed -> visualSpecFor(AgentVisualState.FAILED)
        hasProcessing -> visualSpecFor(AgentVisualState.PROCESSING)
        successCount == AgentRole.entries.size -> visualSpecFor(AgentVisualState.SUCCESS)
        else -> visualSpecFor(AgentVisualState.IDLE)
    }

    val summaryText = when {
        hasFailed -> {
            val failedAgent = orderedStages.first { it.status.toVisualState() == AgentVisualState.FAILED }
            "${failedAgent.role.badge} Failed"
        }
        hasProcessing -> {
            val active = orderedStages.first { it.status.toVisualState() == AgentVisualState.PROCESSING }
            "${active.role.badge} Processing"
        }
        successCount == AgentRole.entries.size -> "7/7 Ready"
        successCount > 0 -> "$successCount/7 Active"
        else -> "7 Idle"
    }

    val autoFocusedStage = remember(orderedStages, pinnedRole) {
        pinnedRole?.let { role -> orderedStages.firstOrNull { it.role == role } }
            ?: orderedStages.firstOrNull { it.status.toVisualState() == AgentVisualState.PROCESSING }
            ?: orderedStages.firstOrNull { it.status.toVisualState() == AgentVisualState.FAILED }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_visual_status_bar"),
        shape = MaterialTheme.shapes.medium,
        color = desk.cardElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasProcessing || hasFailed) summarySpec.borderColor.copy(alpha = 0.55f) else desk.borderSubtle
        ),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            desk.cardElevated,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top bar header: title + overall status badge + 4-state icon legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = summarySpec.icon,
                        contentDescription = null,
                        tint = summarySpec.contentColor,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                if (hasProcessing) rotationZ = spinDegrees
                            }
                    )
                    Text(
                        text = stringResource(id = R.string.agent_status_bar_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = summarySpec.containerColor,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, summarySpec.borderColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = summarySpec.contentColor,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("agent_status_bar_summary")
                    )
                }
            }

            // 7-agent status nodes with color-coded icons & connector rail
            Box(modifier = Modifier.fillMaxWidth()) {
                // Continuous connector rail behind icon circles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (idx in 0 until orderedStages.size - 1) {
                        val leftState = orderedStages[idx].status.toVisualState()
                        val rightState = orderedStages[idx + 1].status.toVisualState()
                        val railColor by animateColorAsState(
                            targetValue = when {
                                rightState == AgentVisualState.FAILED -> desk.stopCrimson.copy(alpha = 0.7f)
                                rightState == AgentVisualState.PROCESSING -> desk.dataSky.copy(alpha = 0.85f)
                                leftState == AgentVisualState.SUCCESS && rightState == AgentVisualState.SUCCESS ->
                                    desk.bullEmerald.copy(alpha = 0.75f)
                                leftState == AgentVisualState.SUCCESS -> desk.bullEmerald.copy(alpha = 0.4f)
                                else -> desk.borderSubtle
                            },
                            label = "rail_color_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(railColor)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    orderedStages.forEach { stage ->
                        val vState = stage.status.toVisualState()
                        val spec = visualSpecFor(vState)
                        val isSelected = pinnedRole == stage.role
                        val bgColor by animateColorAsState(spec.containerColor, label = "node_bg_${stage.role}")
                        val fgColor by animateColorAsState(spec.contentColor, label = "node_fg_${stage.role}")
                        val borderColor by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.onSurface else spec.borderColor.copy(
                                alpha = if (vState == AgentVisualState.IDLE) 0.65f else 0.95f
                            ),
                            label = "node_border_${stage.role}"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    pinnedRole = if (pinnedRole == stage.role) null else stage.role
                                }
                                .semantics {
                                    contentDescription = "${stage.role.title}: ${spec.label}. ${stage.note}"
                                }
                                .padding(vertical = 2.dp)
                                .testTag("agent_status_node_${stage.role.badge.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .border(
                                        width = if (isSelected || vState == AgentVisualState.PROCESSING) 1.5.dp else 1.dp,
                                        color = borderColor,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = spec.label,
                                    tint = fgColor,
                                    modifier = Modifier
                                        .size(17.dp)
                                        .graphicsLayer {
                                            if (vState == AgentVisualState.PROCESSING) {
                                                rotationZ = spinDegrees
                                            }
                                        }
                                )
                            }

                            Text(
                                text = stage.role.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (vState != AgentVisualState.IDLE || isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (vState == AgentVisualState.IDLE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    fgColor
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Compact 4-state legend (Idle, Processing, Success, Failed)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_status_legend"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AgentVisualState.entries.forEach { state ->
                    val spec = visualSpecFor(state)
                    val count = orderedStages.count { it.status.toVisualState() == state }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = spec.contentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (count > 0) "${spec.label} ($count)" else spec.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (count > 0) spec.contentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contextual focus pill when an agent is Processing, Failed, or tapped by user
            AnimatedVisibility(visible = autoFocusedStage != null) {
                autoFocusedStage?.let { focused ->
                    val spec = visualSpecFor(focused.status.toVisualState())
                    Surface(
                        color = spec.containerColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, spec.borderColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agent_status_focus_pill")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = null,
                                    tint = spec.contentColor,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .graphicsLayer {
                                            if (spec.state == AgentVisualState.PROCESSING) {
                                                rotationZ = spinDegrees
                                            }
                                        }
                                )
                                Text(
                                    text = "${focused.role.title}: ${focused.note}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = spec.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = spec.contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
