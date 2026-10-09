package com.mindhaven.alzheimershelper;

import java.util.List;
import java.util.Map;

/**
 * CognitiveReport holds all data collected during a quiz session
 * and the AI-generated analysis results.
 */
public class CognitiveReport {

    // ── Raw quiz data ──────────────────────────────────────────────────────
    public int    userId;
    public int    totalQuestions;
    public int    correctAnswers;
    public double accuracyPercent;

    // Per-question timing (milliseconds)
    public List<Long> responseTimes;
    public double     avgResponseTimeSeconds;

    // How many times the user changed their answer
    public int answerChanges;

    // Domain -> [correct, total]
    public Map<String, int[]> domainScores;

    // ── AI Analysis ────────────────────────────────────────────────────────
    public int          healthScore;      // 0-100
    public String       riskLevel;        // "Low" / "Moderate" / "High"
    public List<String> aiPatterns;
    public List<String> recommendations;

    // Domain performance labels  e.g. "✓ Good"
    public Map<String, String> domainLabels;

    // ── Trend / Progression (AI feature) ──────────────────────────────────
    public String trendDirection;   // "First Session" / "Improving" / "Stable" / "Declining"
    public double trendVelocity;    // points gained/lost vs last session
    public int    sessionCount;     // how many sessions including this one
    public boolean fatigueDetected; // true if 2nd-half perf worse than 1st

    // ── Full text report ───────────────────────────────────────────────────
    public String formattedReport;
}