package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentRole(val title: String, val badge: String, val skills: List<String>) {
    ORCHESTRATOR("Orchestrator Agent", "LEAD", listOf("cycle-coord")),
    SCANNER("Scanner Agent", "SCAN", listOf("india-scanner")),
    NEWS_ANALYST("News & Analyst Agent", "INTEL", listOf("india-news", "india-analyst", "india-insiders")),
    ENTRY("Entry Agent", "ENTRY", listOf("india-entry")),
    EXIT_TIMEMACHINE("Exit & TimeMachine Agent", "ODDS", listOf("india-exit", "india-timemachine")),
    RISK("Risk Agent (Veto Gate)", "RISK", listOf("india-budget", "india-risk")),
    ALERTS_BROKER("Alerts & Paper Agent", "EXEC", listOf("india-broker", "india-alerts"))
}

@Serializable
enum class AgentStageStatus {
    PENDING,
    RUNNING,
    PASSED,
    FILTERED,
    VETOED,
    BOOKED,
    FAILED
}

enum class AgentVisualState(val label: String) {
    IDLE("Idle"),
    PROCESSING("Processing"),
    SUCCESS("Success"),
    FAILED("Failed")
}

fun AgentStageStatus.toVisualState(): AgentVisualState = when (this) {
    AgentStageStatus.PENDING -> AgentVisualState.IDLE
    AgentStageStatus.RUNNING -> AgentVisualState.PROCESSING
    AgentStageStatus.PASSED,
    AgentStageStatus.FILTERED,
    AgentStageStatus.BOOKED -> AgentVisualState.SUCCESS
    AgentStageStatus.VETOED,
    AgentStageStatus.FAILED -> AgentVisualState.FAILED
}

@Serializable
enum class AgentVerdict(val label: String) {
    INFO("COORD"),
    SELECTED("WATCHLIST"),
    FILTERED("CLEARED"),
    PASS("APPROVED"),
    VETO_BLOCK("VETO BLOCKED"),
    PAPER_FILLED("PAPER BOOKED"),
    AI_FALLBACK("DETERMINISTIC"),
    ERROR("HALTED")
}

val ALL_TRADING_SKILLS: List<String> = listOf(
    "india-scanner",
    "india-news",
    "india-analyst",
    "india-insiders",
    "india-entry",
    "india-exit",
    "india-timemachine",
    "india-budget",
    "india-risk",
    "india-broker",
    "india-alerts"
)

@Serializable
data class AgentStageState(
    val role: AgentRole,
    val status: AgentStageStatus = AgentStageStatus.PENDING,
    val note: String = "Waiting",
    val activeSkill: String? = null,
    val completedSkills: List<String> = emptyList()
)

@Serializable
data class AgentLogMessage(
    val id: String,
    val cycleId: String,
    val agentRole: AgentRole,
    val agentTitle: String,
    val skillsUsed: List<String>,
    val timestampIst: String,
    val verdict: AgentVerdict,
    val headline: String,
    val reasoning: String,
    val aiSource: String,
    val symbol: String? = null,
    val jsonPayload: String
)

@Serializable
data class AgentCycleState(
    val cycleId: String,
    val startedAtIst: String,
    val completedAtIst: String = "",
    val dataMode: DataMode,
    val paperOnly: Boolean = true,
    val watchlistCandidates: List<ScanHit> = emptyList(),
    val selectedSymbol: String? = null,
    val newsAnalystSummary: String? = null,
    val newsAnalystFlags: List<String> = emptyList(),
    val entryCheck: EntryCheckResult? = null,
    val tradePlan: TradePlan? = null,
    val timeMachineSummary: TimeMachineSummaryDto? = null,
    val orderProposal: OrderProposal? = null,
    val riskVerdict: RiskVerdict? = null,
    val alertsArmedCount: Int = 0,
    val paperFillSummary: String? = null,
    val haltedByAgent: AgentRole? = null,
    val haltedReason: String? = null,
    val succeeded: Boolean = false,
    val durationMs: Long = 0L
)

@Serializable
data class TimeMachineSummaryDto(
    val symbol: String,
    val sessionsUsed: Int,
    val replays: Int,
    val pTarget: Double,
    val pStop: Double,
    val pNeither: Double,
    val pGreen: Double,
    val avgR: Double,
    val avgRNet: Double,
    val verdict: String
)

@Serializable
data class AgentCycleSnapshot(
    val cycleId: String,
    val startedAtIst: String,
    val selectedSymbol: String?,
    val outcome: String,
    val succeeded: Boolean,
    val aiCalls: Int,
    val fallbackCalls: Int,
    val messages: List<AgentLogMessage>,
    val finalStateJson: String
)
