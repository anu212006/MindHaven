package com.mindhaven.alzheimershelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * QuizResultFrame - Shows the full AI Cognitive Assessment Report after a quiz.
 */
public class QuizResultFrame extends JFrame {

    private final int            userId;
    private final CognitiveReport report;

    public QuizResultFrame(int userId, CognitiveReport report) {
        this.userId = userId;
        this.report = report;

        setTitle("MindHaven - Cognitive Assessment Report");
        setSize(700, 860);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        // Background panel uses BorderLayout so buttons are always visible
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, new Color(142, 84, 233),
                        0, getHeight(), new Color(186, 143, 238));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        // Title at top
        JLabel titleLabel = new JLabel("Cognitive Assessment Report", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 26));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 10, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Scrollable card
        JPanel card = new JPanel();
        card.setLayout(null);
        card.setBackground(new Color(248, 246, 255));

        int y = 20;

        // ── Overall Performance ───────────────────────────────────────────
        y = addSection(card, "OVERALL PERFORMANCE", y);
        y = addRow(card, "Accuracy",
                String.format("%.0f%%  (%d/%d correct)", report.accuracyPercent,
                        report.correctAnswers, report.totalQuestions),
                colForAccuracy(), y);
        y = addRow(card, "Avg Response Time",
                String.format("%.1f seconds", report.avgResponseTimeSeconds),
                report.avgResponseTimeSeconds > 15 ? new Color(220, 100, 0) : new Color(30, 140, 30), y);
        y = addRow(card, "Answer Changes",
                report.answerChanges + "  (hesitation indicator)",
                report.answerChanges > 3 ? new Color(200, 60, 60) : new Color(80, 80, 80), y);
        y = addHealthScore(card, report.healthScore, y);

        // ── AI Trend Analysis ─────────────────────────────────────────────
        y += 12;
        y = addSection(card, "AI TREND ANALYSIS", y);

        String trendText = report.trendDirection != null ? report.trendDirection : "First Session";
        String trendIcon = "Improving".equals(trendText) ? " ↑" : "Declining".equals(trendText) ? " ↓" : " →";
        Color  trendColor= "Improving".equals(trendText) ? new Color(30,140,30)
                : "Declining".equals(trendText) ? new Color(200,50,50)
                : new Color(80,80,80);
        y = addRow(card, "Trend", trendText + trendIcon
                + (report.trendVelocity != 0
                ? String.format("  (%.0f pts vs last session)", report.trendVelocity)
                : ""), trendColor, y);
        y = addRow(card, "Session #", String.valueOf(report.sessionCount),
                new Color(80, 80, 80), y);
        y = addRow(card, "Fatigue Detected",
                report.fatigueDetected ? "Yes — performance dropped in 2nd half" : "No",
                report.fatigueDetected ? new Color(200, 80, 0) : new Color(30, 140, 30), y);
        y += 12;

        // ── Domain Analysis ───────────────────────────────────────────────
        y = addSection(card, "DOMAIN ANALYSIS", y);
        if (report.domainScores != null) {
            for (Map.Entry<String, int[]> e : report.domainScores.entrySet()) {
                int[] v = e.getValue();
                if (v[1] == 0) continue;
                double pct = v[0] * 100.0 / v[1];
                String label = report.domainLabels != null
                        ? report.domainLabels.getOrDefault(e.getKey(), "") : "";
                Color c = pct >= 80 ? new Color(30,140,30) : pct >= 60 ? new Color(190,120,0) : new Color(200,50,50);
                y = addRow(card, e.getKey(),
                        String.format("%.0f%%  %s  (%d/%d)", pct, label, v[0], v[1]), c, y);
            }
        }
        y += 12;

        // ── AI Patterns ───────────────────────────────────────────────────
        y = addSection(card, "AI ANALYSIS PATTERNS", y);
        if (report.aiPatterns != null)
            for (String p : report.aiPatterns)
                y = addBullet(card, p, new Color(190, 80, 0), y);
        y += 12;

        // ── Risk ──────────────────────────────────────────────────────────
        y = addSection(card, "RISK ASSESSMENT", y);
        y = addRiskBadge(card, report.riskLevel, y);
        y += 12;

        // ── Recommendations ───────────────────────────────────────────────
        y = addSection(card, "RECOMMENDATIONS", y);
        if (report.recommendations != null) {
            int n = 1;
            for (String rec : report.recommendations)
                y = addNumbered(card, n++, rec, new Color(50, 80, 160), y);
        }
        y += 20;

        card.setPreferredSize(new Dimension(580, y + 20));

        JScrollPane scroll = new JScrollPane(card);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(190, 170, 220), 2));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(new Color(142, 84, 233));

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        centerWrap.add(scroll, BorderLayout.CENTER);
        mainPanel.add(centerWrap, BorderLayout.CENTER);

        // Buttons panel — always anchored to bottom
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
        btnPanel.setOpaque(false);

        JButton dashBtn = styledButton("← Back to Dashboard", new Color(123, 63, 228));
        dashBtn.setPreferredSize(new Dimension(230, 48));
        dashBtn.addActionListener(e -> { new DashboardFrame(userId); dispose(); });
        btnPanel.add(dashBtn);

        JButton copyBtn = styledButton("Copy Report", new Color(76, 140, 80));
        copyBtn.setPreferredSize(new Dimension(180, 48));
        copyBtn.addActionListener(e -> copyReport());
        btnPanel.add(copyBtn);

        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Card building helpers
    // ─────────────────────────────────────────────────────────────────────

    private int addSection(JPanel card, String title, int y) {
        JPanel bar = new JPanel();
        bar.setBackground(new Color(123, 63, 228));
        bar.setBounds(10, y, 558, 28);
        bar.setLayout(null);
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(Color.WHITE);
        lbl.setBounds(0, 0, 558, 28);
        bar.add(lbl);
        card.add(bar);
        return y + 34;
    }

    private int addRow(JPanel card, String label, String value, Color valueColor, int y) {
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(new Color(60, 30, 90));
        lbl.setBounds(18, y, 185, 26);
        card.add(lbl);
        JLabel val = new JLabel(value);
        val.setFont(new Font("Arial", Font.PLAIN, 13));
        val.setForeground(valueColor);
        val.setBounds(205, y, 365, 26);
        card.add(val);
        return y + 30;
    }

    private int addHealthScore(JPanel card, int score, int y) {
        JLabel lbl = new JLabel("Health Score:");
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(new Color(60, 30, 90));
        lbl.setBounds(18, y, 185, 26);
        card.add(lbl);
        Color bg = score >= 75 ? new Color(30,140,30) : score >= 50 ? new Color(190,120,0) : new Color(200,50,50);
        JLabel badge = new JLabel(score + " / 100", SwingConstants.CENTER);
        badge.setFont(new Font("Arial", Font.BOLD, 13));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setBounds(205, y + 1, 85, 24);
        card.add(badge);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(score);
        bar.setForeground(bg);
        bar.setBackground(new Color(220, 210, 235));
        bar.setStringPainted(false);
        bar.setBounds(300, y + 5, 250, 16);
        card.add(bar);
        return y + 32;
    }

    private int addBullet(JPanel card, String text, Color color, int y) {
        JLabel lbl = new JLabel("<html><body style='width:510px'>⚠  " + text.replace("\n", "<br>&nbsp;&nbsp;&nbsp;") + "</body></html>");
        lbl.setFont(new Font("Arial", Font.PLAIN, 13));
        lbl.setForeground(color);
        lbl.setBounds(18, y, 540, 42);
        card.add(lbl);
        return y + 44;
    }

    private int addNumbered(JPanel card, int n, String text, Color color, int y) {
        JLabel lbl = new JLabel("<html><body style='width:510px'>" + n + ".  " + text + "</body></html>");
        lbl.setFont(new Font("Arial", Font.PLAIN, 13));
        lbl.setForeground(color);
        lbl.setBounds(18, y, 540, 32);
        card.add(lbl);
        return y + 34;
    }

    private int addRiskBadge(JPanel card, String risk, int y) {
        Color bg = "Low".equals(risk) ? new Color(30,140,30)
                : "Moderate".equals(risk) ? new Color(190,120,0)
                : new Color(200,50,50);
        JLabel badge = new JLabel("  RISK LEVEL:  " + (risk != null ? risk.toUpperCase() : "UNKNOWN") + "  ",
                SwingConstants.CENTER);
        badge.setFont(new Font("Arial", Font.BOLD, 15));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setBounds(18, y, 220, 32);
        card.add(badge);
        return y + 38;
    }

    private Color colForAccuracy() {
        if (report.accuracyPercent >= 80) return new Color(30, 140, 30);
        if (report.accuracyPercent >= 60) return new Color(190, 120, 0);
        return new Color(200, 50, 50);
    }

    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 15));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(bg.darker()); }
            public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    private void copyReport() {
        java.awt.datatransfer.StringSelection ss =
                new java.awt.datatransfer.StringSelection(report.formattedReport);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
        JOptionPane.showMessageDialog(this, "Report copied to clipboard!", "Copied",
                JOptionPane.INFORMATION_MESSAGE);
    }
}