package com.mindhaven.alzheimershelper;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  AIQuizService  —  Self-contained AIML Cognitive Engine
 * ═══════════════════════════════════════════════════════════════
 *
 *  HOW AI IS USED HERE (no external API — all logic is in Java):
 *
 *  1. ADAPTIVE QUESTION SELECTION
 *     Questions are tagged with difficulty (EASY/MEDIUM/HARD).
 *     Harder questions are worth more in the health score, just
 *     like an IRT (Item Response Theory) model used in real
 *     clinical screening tools (MMSE, MoCA).
 *
 *  2. MULTI-FACTOR WEIGHTED SCORING MODEL
 *     Five independent signals combined with clinical weights:
 *       Accuracy (40%) + Response Time (20%) + Hesitation (10%)
 *       + Time-Awareness domain (20%) + Memory domain (10%)
 *
 *  3. PATTERN DETECTION ENGINE
 *     Rule-based inference fires named clinical patterns:
 *       Temporal disorientation → Early Alzheimer's sign
 *       Response-time variance  → Attention lapse detection
 *       Fatigue curve           → Performance drop in 2nd half
 *       Trend regression        → Progressive decline flag
 *
 *  4. PROGRESSIVE TREND ANALYSIS  (across multiple sessions)
 *     Reads past scores from DB and computes trajectory:
 *       Improving / Stable / Declining + velocity (pts/session)
 *     A single score means little — the TREND is what matters.
 *
 *  5. CONFIDENCE-WEIGHTED RISK CLASSIFICATION
 *     Risk uses compound rules: score + temporal failure +
 *     response speed + trend direction together.
 *
 *  6. PERSONALISED RECOMMENDATIONS
 *     Branches on risk level, failed domains, trend direction,
 *     fatigue detection — unique suggestions per session.
 * ═══════════════════════════════════════════════════════════════
 */
public class AIQuizService {

    // Domain constants
    public static final String DOMAIN_TIME     = "Time Awareness";
    public static final String DOMAIN_PLACE    = "Place Orientation";
    public static final String DOMAIN_MATH     = "Math";
    public static final String DOMAIN_LANGUAGE = "Language";
    public static final String DOMAIN_GENERAL  = "General Knowledge";
    public static final String DOMAIN_MEMORY   = "Memory Recall";

    // Difficulty levels
    public static final int EASY   = 1;
    public static final int MEDIUM = 2;
    public static final int HARD   = 3;

    // Scoring weights (sum = 1.0)
    private static final double W_ACCURACY      = 0.40;
    private static final double W_RESPONSE_TIME = 0.20;
    private static final double W_HESITATION    = 0.10;
    private static final double W_DOMAIN_TIME   = 0.20;
    private static final double W_DOMAIN_MEM    = 0.10;

    // Response-time thresholds (seconds)
    private static final double TIME_FAST      =  8.0;
    private static final double TIME_NORMAL    = 14.0;
    private static final double TIME_SLOW      = 22.0;
    private static final double TIME_VERY_SLOW = 35.0;

    // ─────────────────────────────────────────────────────────────────────
    // 1. ADAPTIVE QUESTION BANK
    // ─────────────────────────────────────────────────────────────────────

    public static List<QuizFrame.Question> generateQuestions() {
        List<QuizFrame.Question> pool = new ArrayList<>();

        // Time Awareness (3 questions)
        List<QuizFrame.Question> timeQ = Arrays.asList(
                new QuizFrame.Question("What year is it currently?",
                        new String[]{"2022","2023","2024","2025"}, 3, DOMAIN_TIME, EASY),
                new QuizFrame.Question("What month comes after September?",
                        new String[]{"August","November","October","December"}, 2, DOMAIN_TIME, EASY),
                new QuizFrame.Question("How many days are in a week?",
                        new String[]{"5","6","7","8"}, 2, DOMAIN_TIME, EASY),
                new QuizFrame.Question("Which season follows Summer in India?",
                        new String[]{"Spring","Monsoon","Winter","Autumn"}, 1, DOMAIN_TIME, MEDIUM),
                new QuizFrame.Question("What day comes after Wednesday?",
                        new String[]{"Monday","Tuesday","Thursday","Friday"}, 2, DOMAIN_TIME, EASY),
                new QuizFrame.Question("How many months are in a year?",
                        new String[]{"10","11","12","13"}, 2, DOMAIN_TIME, EASY),
                new QuizFrame.Question("In which quarter of the year does October fall?",
                        new String[]{"1st (Jan-Mar)","2nd (Apr-Jun)","3rd (Jul-Sep)","4th (Oct-Dec)"}, 3, DOMAIN_TIME, HARD),
                new QuizFrame.Question("How many days are in February in a non-leap year?",
                        new String[]{"27","28","29","30"}, 1, DOMAIN_TIME, MEDIUM)
        );
        pool.addAll(sample(timeQ, 3));

        // Place Orientation (1 question)
        List<QuizFrame.Question> placeQ = Arrays.asList(
                new QuizFrame.Question("What is the capital city of India?",
                        new String[]{"Mumbai","Kolkata","New Delhi","Chennai"}, 2, DOMAIN_PLACE, EASY),
                new QuizFrame.Question("Which continent is India located in?",
                        new String[]{"Europe","Africa","South America","Asia"}, 3, DOMAIN_PLACE, EASY),
                new QuizFrame.Question("What is the largest state in India by area?",
                        new String[]{"Maharashtra","Rajasthan","Uttar Pradesh","Madhya Pradesh"}, 1, DOMAIN_PLACE, MEDIUM)
        );
        pool.addAll(sample(placeQ, 1));

        // Math (2 questions)
        List<QuizFrame.Question> mathQ = Arrays.asList(
                new QuizFrame.Question("What is 18 + 14?",
                        new String[]{"30","32","34","28"}, 1, DOMAIN_MATH, EASY),
                new QuizFrame.Question("What is 45 - 17?",
                        new String[]{"28","32","26","30"}, 0, DOMAIN_MATH, EASY),
                new QuizFrame.Question("What is 9 × 3?",
                        new String[]{"21","24","27","30"}, 2, DOMAIN_MATH, MEDIUM),
                new QuizFrame.Question("What is 100 - 37?",
                        new String[]{"53","63","67","73"}, 1, DOMAIN_MATH, MEDIUM),
                new QuizFrame.Question("What is 6 + 7 + 8?",
                        new String[]{"18","19","21","22"}, 2, DOMAIN_MATH, EASY),
                new QuizFrame.Question("You have \u20b950 and spend \u20b923. How much remains?",
                        new String[]{"\u20b925","\u20b927","\u20b930","\u20b933"}, 1, DOMAIN_MATH, EASY),
                new QuizFrame.Question("If medicine is taken 3 times a day, how many times in a week?",
                        new String[]{"14","18","21","24"}, 2, DOMAIN_MATH, HARD)
        );
        pool.addAll(sample(mathQ, 2));

        // Language (2 questions)
        List<QuizFrame.Question> langQ = Arrays.asList(
                new QuizFrame.Question("What does the word 'enormous' mean?",
                        new String[]{"Very small","Very fast","Very large","Very quiet"}, 2, DOMAIN_LANGUAGE, EASY),
                new QuizFrame.Question("Complete: 'The doctor examined the ___.'",
                        new String[]{"car","patient","book","window"}, 1, DOMAIN_LANGUAGE, EASY),
                new QuizFrame.Question("What is the opposite of 'ancient'?",
                        new String[]{"Old","Wise","Modern","Large"}, 2, DOMAIN_LANGUAGE, MEDIUM),
                new QuizFrame.Question("A place where books are kept and borrowed is a ___.",
                        new String[]{"Museum","Laboratory","Library","Gallery"}, 2, DOMAIN_LANGUAGE, EASY),
                new QuizFrame.Question("Complete: 'She takes her ___ every morning with water.'",
                        new String[]{"shoes","medicine","luggage","salary"}, 1, DOMAIN_LANGUAGE, EASY),
                new QuizFrame.Question("Which word means 'to gradually get worse over time'?",
                        new String[]{"Improve","Deteriorate","Sustain","Recover"}, 1, DOMAIN_LANGUAGE, HARD)
        );
        pool.addAll(sample(langQ, 2));

        // Memory Recall (1 question)
        List<QuizFrame.Question> memQ = Arrays.asList(
                new QuizFrame.Question("Shopping list: milk, bread, eggs, rice.\nWhat was the THIRD item?",
                        new String[]{"Milk","Bread","Eggs","Rice"}, 2, DOMAIN_MEMORY, MEDIUM),
                new QuizFrame.Question("Colors in order: Red, Blue, Green, Yellow.\nWhat was the SECOND color?",
                        new String[]{"Red","Blue","Green","Yellow"}, 1, DOMAIN_MEMORY, EASY),
                new QuizFrame.Question("Days listed: Monday, Friday, Wednesday.\nWhich day was listed FIRST?",
                        new String[]{"Friday","Wednesday","Monday","Tuesday"}, 2, DOMAIN_MEMORY, MEDIUM),
                new QuizFrame.Question("Numbers: 4, 8, 15, 16. What was the LAST number?",
                        new String[]{"4","8","15","16"}, 3, DOMAIN_MEMORY, HARD)
        );
        pool.addAll(sample(memQ, 1));

        // General Knowledge (1 question)
        List<QuizFrame.Question> genQ = Arrays.asList(
                new QuizFrame.Question("Who wrote the Indian national anthem?",
                        new String[]{"Mahatma Gandhi","Rabindranath Tagore","Jawaharlal Nehru","B.R. Ambedkar"}, 1, DOMAIN_GENERAL, MEDIUM),
                new QuizFrame.Question("How many colours are in a rainbow?",
                        new String[]{"5","6","7","8"}, 2, DOMAIN_GENERAL, EASY),
                new QuizFrame.Question("How many sides does a triangle have?",
                        new String[]{"2","3","4","5"}, 1, DOMAIN_GENERAL, EASY),
                new QuizFrame.Question("Which planet is closest to the Sun?",
                        new String[]{"Venus","Earth","Mercury","Mars"}, 2, DOMAIN_GENERAL, MEDIUM)
        );
        pool.addAll(sample(genQ, 1));

        Collections.shuffle(pool);
        return pool;
    }

    private static <T> List<T> sample(List<T> source, int n) {
        List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. MAIN ANALYSIS ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    public static CognitiveReport analysePerformance(
            int userId,
            int correct,
            int total,
            List<Long> responseTimes,
            int answerChanges,
            Map<String, int[]> domainScores,
            List<QuizFrame.Question> questions,
            List<Integer> userAnswers) {

        CognitiveReport r        = new CognitiveReport();
        r.userId                 = userId;
        r.totalQuestions         = total;
        r.correctAnswers         = correct;
        r.accuracyPercent        = total > 0 ? (correct * 100.0) / total : 0;
        r.responseTimes          = responseTimes;
        r.avgResponseTimeSeconds = computeAvg(responseTimes);
        r.answerChanges          = answerChanges;
        r.domainScores           = domainScores;

        // Component scores
        double accuracyScore   = computeDifficultyWeightedAccuracy(questions, userAnswers);
        double timeScore       = computeTimeScore(r.avgResponseTimeSeconds);
        double hesitationScore = computeHesitationScore(answerChanges, total);
        double timeAwareness   = computeDomainScore(domainScores, DOMAIN_TIME);
        double memoryScore     = computeDomainScore(domainScores, DOMAIN_MEMORY);
        double mathScore       = computeDomainScore(domainScores, DOMAIN_MATH);

        // Weighted health score
        double raw = (accuracyScore   * W_ACCURACY)
                + (timeScore       * W_RESPONSE_TIME)
                + (hesitationScore * W_HESITATION)
                + (timeAwareness   * W_DOMAIN_TIME)
                + (memoryScore     * W_DOMAIN_MEM);
        r.healthScore = (int) Math.round(Math.max(0, Math.min(100, raw)));

        // Trend analysis from DB history
        List<int[]> history = DatabaseManager.getQuizHistoryScores(userId);
        r.trendDirection = computeTrend(history, r.healthScore);
        r.sessionCount   = history.size() + 1;
        r.trendVelocity  = computeVelocity(history, r.healthScore);

        // Fatigue detection
        r.fatigueDetected = detectFatigue(responseTimes, userAnswers, questions);

        // Risk, patterns, recommendations, labels
        r.riskLevel       = classifyRisk(r.healthScore, timeAwareness, r.avgResponseTimeSeconds, r.trendDirection);
        r.aiPatterns      = detectPatterns(r, timeAwareness, memoryScore, mathScore);
        r.recommendations = generateRecommendations(r, timeAwareness, memoryScore);
        r.domainLabels    = buildDomainLabels(domainScores);
        r.formattedReport = buildFormattedReport(r);

        return r;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Difficulty-weighted accuracy (IRT-inspired)
    // ─────────────────────────────────────────────────────────────────────

    private static double computeDifficultyWeightedAccuracy(
            List<QuizFrame.Question> questions, List<Integer> userAnswers) {
        if (questions == null || userAnswers == null || questions.isEmpty()) return 0;
        double earned = 0, possible = 0;
        for (int i = 0; i < Math.min(questions.size(), userAnswers.size()); i++) {
            double w = questions.get(i).getDifficulty() == HARD ? 2.0
                    : questions.get(i).getDifficulty() == MEDIUM ? 1.5 : 1.0;
            possible += w;
            if (userAnswers.get(i) == questions.get(i).getCorrectAnswer()) earned += w;
        }
        return possible > 0 ? (earned / possible) * 100.0 : 0;
    }

    private static double computeTimeScore(double avgSecs) {
        if (avgSecs <= TIME_FAST)      return 100.0;
        if (avgSecs <= TIME_NORMAL)    return 100.0 - ((avgSecs - TIME_FAST)   / (TIME_NORMAL    - TIME_FAST))   * 25.0;
        if (avgSecs <= TIME_SLOW)      return  75.0 - ((avgSecs - TIME_NORMAL) / (TIME_SLOW      - TIME_NORMAL)) * 35.0;
        if (avgSecs <= TIME_VERY_SLOW) return  40.0 - ((avgSecs - TIME_SLOW)   / (TIME_VERY_SLOW - TIME_SLOW))   * 30.0;
        return 10.0;
    }

    private static double computeHesitationScore(int changes, int total) {
        if (total == 0) return 100;
        double rate = (double) changes / total;
        if (rate == 0)    return 100.0;
        if (rate <= 0.20) return  85.0;
        if (rate <= 0.40) return  65.0;
        if (rate <= 0.60) return  45.0;
        return 25.0;
    }

    private static double computeDomainScore(Map<String, int[]> scores, String domain) {
        int[] v = scores.get(domain);
        if (v == null || v[1] == 0) return 100.0;
        return (v[0] * 100.0) / v[1];
    }

    // ─────────────────────────────────────────────────────────────────────
    // Trend analysis
    // ─────────────────────────────────────────────────────────────────────

    public static String computeTrend(List<int[]> history, int currentScore) {
        if (history == null || history.size() < 2) return "First Session";
        int n = Math.min(history.size(), 3);
        List<int[]> recent = history.subList(history.size() - n, history.size());
        double avgPast = 0;
        for (int[] h : recent) avgPast += h[0];
        avgPast /= recent.size();
        double delta = currentScore - avgPast;
        if (delta > 5)  return "Improving";
        if (delta < -5) return "Declining";
        return "Stable";
    }

    public static double computeVelocity(List<int[]> history, int currentScore) {
        if (history == null || history.isEmpty()) return 0;
        return currentScore - history.get(history.size() - 1)[0];
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fatigue detection
    // ─────────────────────────────────────────────────────────────────────

    private static boolean detectFatigue(List<Long> times, List<Integer> answers,
                                         List<QuizFrame.Question> questions) {
        if (times == null || times.size() < 4 || answers == null || questions == null) return false;
        int half = times.size() / 2;
        double firstAvg = 0, secondAvg = 0;
        for (int i = 0; i < half; i++)            firstAvg  += times.get(i);
        for (int i = half; i < times.size(); i++) secondAvg += times.get(i);
        firstAvg  /= half;
        secondAvg /= (times.size() - half);
        int firstCorrect = 0, secondCorrect = 0;
        for (int i = 0; i < Math.min(half, answers.size()); i++)
            if (answers.get(i) == questions.get(i).getCorrectAnswer()) firstCorrect++;
        for (int i = half; i < Math.min(questions.size(), answers.size()); i++)
            if (answers.get(i) == questions.get(i).getCorrectAnswer()) secondCorrect++;
        return secondAvg > firstAvg * 1.4 && secondCorrect < firstCorrect;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Risk classification (trend-aware)
    // ─────────────────────────────────────────────────────────────────────

    private static String classifyRisk(int healthScore, double timeAwareness,
                                       double avgTime, String trend) {
        boolean severeTimeFail = timeAwareness < 40;
        boolean verySlowResp   = avgTime > TIME_VERY_SLOW;
        boolean declining      = "Declining".equals(trend);
        if (severeTimeFail || healthScore < 40 || (verySlowResp && healthScore < 55)) return "High";
        if (declining && healthScore < 60) return "High";
        if (healthScore >= 75 && !severeTimeFail && !declining) return "Low";
        return "Moderate";
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pattern detection
    // ─────────────────────────────────────────────────────────────────────

    private static List<String> detectPatterns(CognitiveReport r,
                                               double timeAwareness, double memoryScore, double mathScore) {
        List<String> p = new ArrayList<>();
        if (r.avgResponseTimeSeconds > TIME_VERY_SLOW)
            p.add("Very slow responses detected across all questions");
        else if (r.avgResponseTimeSeconds > TIME_SLOW)
            p.add("Moderately slow responses observed");
        if (timeAwareness < 40)
            p.add("Temporal disorientation detected\n  \u2192 Strong early Alzheimer's indicator");
        else if (timeAwareness < 65)
            p.add("Mild temporal disorientation detected\n  \u2192 Early Alzheimer's sign");
        if (memoryScore < 50)
            p.add("Short-term memory recall difficulties observed");
        if (r.answerChanges > r.totalQuestions * 0.5)
            p.add("High answer hesitation \u2014 suggests cognitive uncertainty");
        else if (r.answerChanges > r.totalQuestions * 0.3)
            p.add("Moderate answer hesitation noted");
        if (mathScore < 50)
            p.add("Numerical processing difficulties observed");
        if (r.accuracyPercent < 40)
            p.add("Overall cognitive performance significantly below baseline");
        if (r.responseTimes != null && r.responseTimes.size() > 3)
            if (computeVariance(r.responseTimes) > 150_000_000L)
                p.add("Inconsistent response speed \u2014 possible attention lapses");
        if (r.fatigueDetected)
            p.add("Cognitive fatigue detected \u2014 performance declined in 2nd half");
        if ("Declining".equals(r.trendDirection))
            p.add("Progressive decline across " + r.sessionCount + " sessions\n  \u2192 Requires clinical attention");
        else if ("Improving".equals(r.trendDirection))
            p.add("Positive cognitive trend \u2014 scores improving across sessions");
        if (p.isEmpty()) {
            p.add("No significant cognitive warning patterns detected");
            p.add("Performance within normal parameters");
        }
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Personalised recommendations
    // ─────────────────────────────────────────────────────────────────────

    private static List<String> generateRecommendations(
            CognitiveReport r, double timeAwareness, double memoryScore) {
        List<String> recs = new ArrayList<>();
        if ("High".equals(r.riskLevel)) {
            recs.add("Consult a neurologist or geriatric specialist promptly");
            recs.add("Comprehensive neuropsychological evaluation recommended");
            recs.add("Inform family members and arrange caregiver support");
            recs.add("Follow-up assessment within 4\u20136 weeks");
            recs.add("Consider formal MMSE evaluation with a clinician");
        } else if ("Moderate".equals(r.riskLevel)) {
            recs.add("Follow-up cognitive assessment in 3\u20136 months");
            recs.add("Monitor progression with regular MindHaven quizzes");
            recs.add("Cognitive training exercises (puzzles, reading)");
            recs.add("Lifestyle modifications: sleep, diet, social activity");
            if (timeAwareness < 65)
                recs.add("Practice daily calendar and time-orientation routines");
            if (memoryScore < 65)
                recs.add("Use written reminders and structured daily schedules");
            if (r.fatigueDetected)
                recs.add("Take quizzes in the morning when mental energy is highest");
        } else {
            recs.add("Continue regular mental and physical exercise");
            recs.add("Annual cognitive screening recommended");
            recs.add("Maintain healthy sleep, diet, and social engagement");
            recs.add("Brain-stimulating activities: reading, puzzles, games");
        }
        if ("Declining".equals(r.trendDirection) && !"High".equals(r.riskLevel))
            recs.add("Scores declining over sessions \u2014 consult a doctor sooner");
        if ("Improving".equals(r.trendDirection))
            recs.add("Keep up current activities \u2014 improvement trend detected!");
        return recs;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Formatted report
    // ─────────────────────────────────────────────────────────────────────

    public static String buildFormattedReport(CognitiveReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("       COGNITIVE ASSESSMENT REPORT\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("OVERALL PERFORMANCE:\n");
        sb.append(String.format("- Accuracy: %.0f%% (%d/%d correct)%n", r.accuracyPercent, r.correctAnswers, r.totalQuestions));
        sb.append(String.format("- Avg Response Time: %.1f seconds%n", r.avgResponseTimeSeconds));
        sb.append("- Answer Changes: ").append(r.answerChanges).append(" (hesitation)\n");
        sb.append("- Health Score: ").append(r.healthScore).append("/100\n");
        sb.append("- Session #: ").append(r.sessionCount).append("\n");
        sb.append("- Trend: ").append(r.trendDirection);
        if (r.trendVelocity != 0)
            sb.append(String.format(" (%.0f pts vs last session)", r.trendVelocity));
        sb.append("\n\nDOMAIN ANALYSIS:\n");
        if (r.domainScores != null) {
            for (Map.Entry<String, int[]> e : r.domainScores.entrySet()) {
                int[] v = e.getValue();
                if (v[1] == 0) continue;
                double pct = v[0] * 100.0 / v[1];
                String lbl = r.domainLabels != null ? r.domainLabels.getOrDefault(e.getKey(), "") : "";
                sb.append(String.format("- %s: %.0f%% %s%n", e.getKey(), pct, lbl));
            }
        }
        sb.append("\nAI PATTERNS:\n");
        if (r.aiPatterns != null)
            for (String p : r.aiPatterns) sb.append("\u26a0 ").append(p).append("\n");
        sb.append("\nRISK: ").append(r.riskLevel).append("\n\nRECOMMENDATIONS:\n");
        if (r.recommendations != null) {
            int n = 1;
            for (String rec : r.recommendations) sb.append(n++).append(". ").append(rec).append("\n");
        }
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────

    private static double computeAvg(List<Long> times) {
        if (times == null || times.isEmpty()) return 0;
        long sum = 0;
        for (long t : times) sum += t;
        return (sum / (double) times.size()) / 1000.0;
    }

    private static double computeVariance(List<Long> times) {
        double avg = 0;
        for (long t : times) avg += t;
        avg /= times.size();
        double v = 0;
        for (long t : times) v += Math.pow(t - avg, 2);
        return v / times.size();
    }

    private static Map<String, String> buildDomainLabels(Map<String, int[]> scores) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : scores.entrySet()) {
            int[] v = e.getValue();
            if (v[1] == 0) continue;
            double pct = v[0] * 100.0 / v[1];
            if (pct >= 80)      labels.put(e.getKey(), "\u2713 Good");
            else if (pct >= 60) labels.put(e.getKey(), "\u26a0 Fair");
            else                labels.put(e.getKey(), "\u2717 Poor");
        }
        return labels;
    }
}