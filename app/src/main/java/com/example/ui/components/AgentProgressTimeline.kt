package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ALL_TRADING_SKILLS
import com.example.model.AgentLogMessage
import com.example.model.AgentRole
import com.example.model.AgentStageState
import com.example.model.AgentStageStatus
import com.example.model.AgentVisualState
import com.example.model.toVisualState
import com.example.ui.theme.LocalDeskColors

/**
 * Visual vertical timeline tracing the sequential execution of all 7 agents
 * and highlighting each active/completed skill across the 11-skill pipeline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentProgressTimeline(
    stages: List<AgentStageState>,
    logs: List<AgentLogMessage>,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val stageByRole = remember(stages) { stages.associateBy { it.role } }
    val logByRole = remember(logs) { logs.associateBy { it.agentRole } }
    val expandedSteps = remember { mutableStateMapOf<AgentRole, Boolean>() }

    val orderedStages = remember(stageByRole) {
        AgentRole.entries.map { role ->
            stageByRole[role] ?: AgentStageState(role = role, status = AgentStageStatus.PENDING, note = "Queued")
        }
    }

    val completedSkillSet = remember(orderedStages) {
        orderedStages.flatMap { stage ->
            if (stage.completedSkills.isNotEmpty()) {
                stage.completedSkills
            } else if (stage.status.toVisualState() == AgentVisualState.SUCCESS) {
                stage.role.skills
            } else {
                emptyList()
            }
        }.filter { it in ALL_TRADING_SKILLS }.toSet()
    }

    val activeStage = remember(orderedStages) {
        orderedStages.firstOrNull { it.status == AgentStageStatus.RUNNING }
    }
    val activeSkill = activeStage?.activeSkill
        ?: activeStage?.role?.skills?.firstOrNull()

    val completedCount = completedSkillSet.size
    val totalSkills = ALL_TRADING_SKILLS.size
    val animatedProgress by animateFloatAsState(
        targetValue = (completedCount.toFloat() / totalSkills.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "timeline_skill_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "timeline_spin_pulse")
    val spinDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "timeline_sync_rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timeline_active_pulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_progress_timeline_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row + 11-skill completion counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.timeline_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = activeSkill?.let { "Active skill: $it" }
                            ?: stringResource(id = R.string.timeline_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeSkill != null) desk.dataSky else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (completedCount == totalSkills) desk.bullContainer else desk.cardElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (completedCount == totalSkills) desk.bullEmerald.copy(alpha = 0.55f) else desk.borderSubtle
                        )
                    ) {
                        Text(
                            text = "$completedCount/$totalSkills Skills",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (completedCount == totalSkills) desk.bullEmerald else desk.dataSky,
                            modifier = Modifier
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                                .testTag("timeline_skill_counter")
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Risk Veto Gate Enforced",
                        tint = desk.amberSaffron,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Overall 11-skill progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("timeline_progress_bar"),
                color = if (orderedStages.any { it.status.toVisualState() == AgentVisualState.FAILED }) {
                    desk.stopCrimson
                } else if (completedCount == totalSkills) {
                    desk.bullEmerald
                } else {
                    desk.dataSky
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            // Active skill highlight banner while pipeline is executing
            AnimatedVisibility(visible = activeStage != null) {
                activeStage?.let { running ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, desk.dataSky.copy(alpha = pulseAlpha)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timeline_active_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Running skill",
                                tint = desk.dataSky,
                                modifier = Modifier
                                    .size(15.dp)
                                    .graphicsLayer { rotationZ = spinDeg }
                            )
                            Text(
                                text = "${running.role.title} → ${running.activeSkill ?: running.role.skills.first()}: ${running.note}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Sequential vertical timeline for all 7 stages covering the 11 trading skills
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                orderedStages.forEachIndexed { idx, stage ->
                    val isLast = idx == orderedStages.lastIndex
                    val vState = stage.status.toVisualState()
                    val logMsg = logByRole[stage.role]
                    val isExpanded = expandedSteps[stage.role] == true

                    val (nodeBg, nodeFg, nodeBorder) = when (vState) {
                        AgentVisualState.IDLE -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            desk.borderSubtle
                        )
                        AgentVisualState.PROCESSING -> Triple(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            desk.dataSky,
                            desk.dataSky
                        )
                        AgentVisualState.SUCCESS -> Triple(
                            desk.bullContainer,
                            desk.bullEmerald,
                            desk.bullEmerald
                        )
                        AgentVisualState.FAILED -> Triple(
                            desk.stopContainer,
                            desk.stopCrimson,
                            desk.stopCrimson
                        )
                    }

                    val nextVisual = orderedStages.getOrNull(idx + 1)?.status?.toVisualState()
                    val railColor by animateColorAsState(
                        targetValue = when {
                            vState == AgentVisualState.FAILED -> desk.stopCrimson.copy(alpha = 0.45f)
                            vState == AgentVisualState.SUCCESS && nextVisual == AgentVisualState.FAILED -> desk.stopCrimson.copy(alpha = 0.7f)
                            vState == AgentVisualState.SUCCESS && nextVisual == AgentVisualState.PROCESSING -> desk.dataSky
                            vState == AgentVisualState.SUCCESS && nextVisual == AgentVisualState.SUCCESS -> desk.bullEmerald.copy(alpha = 0.8f)
                            vState == AgentVisualState.SUCCESS -> desk.bullEmerald.copy(alpha = 0.45f)
                            vState == AgentVisualState.PROCESSING -> desk.dataSky.copy(alpha = 0.6f)
                            else -> desk.borderSubtle
                        },
                        label = "timeline_rail_$idx"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = logMsg != null) {
                                expandedSteps[stage.role] = !isExpanded
                            }
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                contentDescription = "Step ${idx + 1}: ${stage.role.title}, ${vState.label}. ${stage.note}"
                            }
                            .testTag("timeline_step_${stage.role.badge.lowercase()}")
                    ) {
                        // Left timeline indicator rail + step node
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(34.dp)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(nodeBg)
                                    .border(
                                        width = if (vState == AgentVisualState.PROCESSING) 2.dp else 1.2.dp,
                                        color = if (vState == AgentVisualState.PROCESSING) {
                                            nodeBorder.copy(alpha = pulseAlpha)
                                        } else {
                                            nodeBorder
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (vState) {
                                    AgentVisualState.IDLE -> Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = nodeFg
                                    )
                                    AgentVisualState.PROCESSING -> Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Running",
                                        tint = nodeFg,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .graphicsLayer { rotationZ = spinDeg }
                                    )
                                    AgentVisualState.SUCCESS -> Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Done",
                                        tint = nodeFg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    AgentVisualState.FAILED -> Icon(
                                        imageVector = if (stage.status == AgentStageStatus.VETOED) Icons.Default.Block else Icons.Default.Cancel,
                                        contentDescription = "Failed",
                                        tint = nodeFg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 14.dp)
                                        .background(railColor)
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Right stage body + skill chips + optional expandable log
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 2.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stage.role.title.removeSuffix(" Agent"),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (vState == AgentVisualState.PROCESSING) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (vState == AgentVisualState.IDLE) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (logMsg != null) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse step details" else "Expand step details",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stage.note,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = nodeFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Skill Pills for this Agent Stage
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                stage.role.skills.forEach { skillId ->
                                    val isSkillDone = skillId in stage.completedSkills ||
                                        vState == AgentVisualState.SUCCESS
                                    val isSkillActive = !isSkillDone &&
                                        vState == AgentVisualState.PROCESSING &&
                                        (stage.activeSkill == skillId ||
                                            (stage.activeSkill == null && stage.role.skills.firstOrNull() == skillId))
                                    val isSkillFailed = !isSkillDone &&
                                        vState == AgentVisualState.FAILED &&
                                        (stage.activeSkill == skillId || stage.activeSkill == null)

                                    val (pillBg, pillFg, pillBorder) = when {
                                        isSkillActive -> Triple(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            desk.dataSky,
                                            desk.dataSky
                                        )
                                        isSkillDone -> Triple(
                                            desk.bullContainer.copy(alpha = 0.85f),
                                            desk.bullEmerald,
                                            desk.bullEmerald.copy(alpha = 0.55f)
                                        )
                                        isSkillFailed -> Triple(
                                            desk.stopContainer.copy(alpha = 0.85f),
                                            desk.stopCrimson,
                                            desk.stopCrimson.copy(alpha = 0.55f)
                                        )
                                        else -> Triple(
                                            desk.cardElevated,
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                            desk.borderSubtle
                                        )
                                    }

                                    Surface(
                                        color = pillBg,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            width = if (isSkillActive) 1.2.dp else 1.dp,
                                            color = pillBorder
                                        ),
                                        modifier = Modifier.testTag("timeline_skill_${skillId}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isSkillActive -> Icons.Default.Sync
                                                    isSkillDone -> Icons.Default.CheckCircle
                                                    isSkillFailed -> Icons.Default.Cancel
                                                    else -> Icons.Default.RadioButtonUnchecked
                                                },
                                                contentDescription = null,
                                                tint = pillFg,
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .graphicsLayer {
                                                        if (isSkillActive) rotationZ = spinDeg
                                                    }
                                            )
                                            Text(
                                                text = skillId,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSkillActive || isSkillDone) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = pillFg
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isExpanded && logMsg != null) {
                                logMsg?.let { msg ->
                                    Surface(
                                        color = desk.cardElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, desk.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = msg.headline,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = msg.reasoning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
