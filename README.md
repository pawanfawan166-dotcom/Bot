# TradeDesk IN — Autonomous Multi-Agent Paper Trading System

Native Android application (Kotlin, Jetpack Compose, Material 3, Room, Firebase AI Logic) ported from `pawanfawan166-dotcom/india-day-trader-skills` (`india-scanner`, `india-news`, `india-entry`, `india-exit`, `india-timemachine`, `india-budget`, `india-risk`, `india-broker`, `india-alerts`, `india-analyst`, and `india-insiders`).

## Key Capabilities

- **7-Agent Autonomous Paper Pipeline (Agents Hub Home)**:
  1. **Orchestrator Agent**: Coordinates sequential execution, enforces `paper = true`, and passes typed JSON state (`AgentCycleState`) forward.
  2. **Scanner Agent (`india-scanner`)**: Runs ORB & Daily technical scans across NSE/CAS universes with hybrid Gemini 3.8 Flash prioritization.
  3. **News & Analyst Agent (`india-news`, `india-analyst`, `india-insiders`)**: Evaluates `!` announcements, Ind-AS / Banking XBRL quarterly results, and SEBI PIT / bulk-block deal flags.
  4. **Entry Agent (`india-entry`)**: Evaluates 5-minute ORB entries with 09:15 zero-volume bar exclusion and NSE tick rounding.
  5. **Exit & TimeMachine Agent (`india-exit`, `india-timemachine`)**: Builds tick-snapped stops/targets and runs 10,000-replay seeded Monte Carlo (`seed=19`, `window=2`) + historical ORB backtest.
  6. **Risk Agent — Hard Veto Gate (`india-risk`, `india-budget`)**: Enforces 4-cap sizing, Kite payload SHA-256 fingerprinting, and 14 NSE risk checks (holidays, `14:45` IST cutoff, daily loss caps). Blocks execution cleanly if any check fails.
  7. **Alerts & Paper Execution Agent (`india-broker`, `india-alerts`)**: Books FIFO paper fills and arms protective target, stop, flat-by, and Zerodha MIS square-off alert rungs.
- **Manual Run Cycle & 5-Minute Auto Mode**: Run a cycle on demand or enable automated 5-minute cycles with expandable live JSON conversation logs.
- **Dual Data Modes**: **Offline Replay** (bundled October 19–23, 2026 NSE/Yahoo/XBRL fixtures) and **Live Public Feeds** (Yahoo Finance, cookie-warmed NSE APIs, Google News India RSS).

> Educational & paper-trading desk using delayed/unofficial public feeds. Paper-only ledgering (`paper = true`), never transmits live broker orders.