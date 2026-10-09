package com.mindhaven.alzheimershelper;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DashboardFrame extends JFrame {

    private int userId;
    private JButton quizButton;
    private JButton hospitalsButton;
    private JButton infoButton;
    private JButton remindersButton;
    private JButton logoutButton;
    private JButton viewReportButton;

    public DashboardFrame(int userId) {
        this.userId = userId;
        String userName = DatabaseManager.getPatientName(userId);

        setTitle("MindHaven - Dashboard");
        setSize(620, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, new Color(142, 84, 233),
                        0, getHeight(), new Color(186, 143, 238));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setLayout(null);

        // Ribbon logo
        JPanel logoPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(6));
                g2d.drawArc(5, 3, 20, 28, 45, 270);
                g2d.drawArc(22, 3, 20, 28, 225, 270);
                g2d.drawLine(15, 32, 12, 42);
                g2d.drawLine(32, 32, 35, 42);
            }
        };
        logoPanel.setBounds(130, 35, 50, 50);
        logoPanel.setOpaque(false);
        mainPanel.add(logoPanel);

        JLabel titleLabel = new JLabel("MindHaven");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 42));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(185, 30, 300, 50);
        mainPanel.add(titleLabel);

        JLabel welcomeLabel = new JLabel("Welcome, " + userName + "!");
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        welcomeLabel.setForeground(Color.WHITE);
        welcomeLabel.setBounds(185, 82, 340, 30);
        mainPanel.add(welcomeLabel);

        // ── Content Panel ───────────────────────────────────────────
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(new Color(240, 235, 250));
        contentPanel.setBounds(40, 130, 540, 510);
        contentPanel.setLayout(null);

        // ── Navigation buttons ───────────────────────────────────────
        quizButton = createDashboardButton("Take Memory Quiz", 40, 18);
        quizButton.addActionListener(e -> openQuizFrame());
        contentPanel.add(quizButton);

        hospitalsButton = createDashboardButton("Schedule Appointment", 40, 100);
        hospitalsButton.addActionListener(e -> openAppointmentScheduler());
        contentPanel.add(hospitalsButton);

        infoButton = createDashboardButton("Alzheimer's Information", 40, 182);
        infoButton.addActionListener(e -> openInfoFrame());
        contentPanel.add(infoButton);

        remindersButton = createDashboardButton("Daily Reminders", 40, 264);
        remindersButton.addActionListener(e -> openRemindersFrame());
        contentPanel.add(remindersButton);

        // ── AI Cognitive Report button (teal, distinct) ──────────────
        JButton aiReportButton = createDashboardButton("🧠  AI Cognitive Report", 40, 346);
        aiReportButton.setBackground(new Color(56, 142, 142));
        aiReportButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { aiReportButton.setBackground(new Color(38, 110, 110)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { aiReportButton.setBackground(new Color(56, 142, 142)); }
        });
        aiReportButton.addActionListener(e -> showAIReportDialog());
        contentPanel.add(aiReportButton);

        logoutButton = createDashboardButton("Logout", 40, 428);
        logoutButton.setBackground(new Color(180, 100, 200));
        logoutButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { logoutButton.setBackground(new Color(150, 70, 170)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { logoutButton.setBackground(new Color(180, 100, 200)); }
        });
        logoutButton.addActionListener(e -> logout());
        contentPanel.add(logoutButton);

        mainPanel.add(contentPanel);
        add(mainPanel);
        setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // AI Report Dialog — opens when button is clicked
    // ─────────────────────────────────────────────────────────────────────

    private void showAIReportDialog() {
        CognitiveReport latest = DatabaseManager.getLatestCognitiveReport(userId);

        if (latest == null) {
            JOptionPane.showMessageDialog(this,
                    "No cognitive assessment data found.\nPlease take the Memory Quiz first!",
                    "No Data Available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "AI Cognitive Health Report", true);
        dialog.setSize(560, 680);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout());

        // ── Header ────────────────────────────────────────────────────
        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, new Color(142, 84, 233),
                        0, getHeight(), new Color(56, 142, 142));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(560, 70));
        JLabel hTitle = new JLabel("🧠  AI Cognitive Health Summary", SwingConstants.CENTER);
        hTitle.setFont(new Font("Arial", Font.BOLD, 20));
        hTitle.setForeground(Color.WHITE);
        header.add(hTitle, BorderLayout.CENTER);
        dialog.add(header, BorderLayout.NORTH);

        // ── Scrollable content ────────────────────────────────────────
        JPanel content = new JPanel();
        content.setLayout(null);
        content.setBackground(new Color(248, 246, 255));

        int y = 16;
        int rowH = 38;

        // Health Score
        final int scoreY = y;
        addDialogLabel(content, "Health Score", scoreY);
        Color scoreC = latest.healthScore >= 75 ? new Color(30,140,30)
                : latest.healthScore >= 50 ? new Color(190,120,0) : new Color(200,50,50);
        JLabel badge = new JLabel(latest.healthScore + " / 100", SwingConstants.CENTER);
        badge.setFont(new Font("Arial", Font.BOLD, 14));
        badge.setForeground(Color.WHITE);
        badge.setOpaque(true); badge.setBackground(scoreC);
        badge.setBounds(175, scoreY + 8, 85, 22);
        content.add(badge);
        JProgressBar scoreBarW = new JProgressBar(0, 100);
        scoreBarW.setValue(latest.healthScore);
        scoreBarW.setForeground(scoreC); scoreBarW.setBackground(new Color(220,210,235));
        scoreBarW.setStringPainted(false);
        scoreBarW.setBounds(270, scoreY + 12, 240, 14);
        content.add(scoreBarW);
        y += rowH;

        // Risk Level
        y = addDialogSeparator(content, y);
        addDialogLabel(content, "Risk Level", y);
        Color rc = "Low".equals(latest.riskLevel) ? new Color(30,140,30)
                : "Moderate".equals(latest.riskLevel) ? new Color(190,120,0) : new Color(200,50,50);
        JLabel rBadge = new JLabel(latest.riskLevel != null ? latest.riskLevel.toUpperCase() : "N/A", SwingConstants.CENTER);
        rBadge.setFont(new Font("Arial", Font.BOLD, 14));
        rBadge.setForeground(Color.WHITE); rBadge.setOpaque(true); rBadge.setBackground(rc);
        rBadge.setBounds(175, y + 7, 100, 24);
        content.add(rBadge);
        y += rowH;

        // Trend
        y = addDialogSeparator(content, y);
        addDialogLabel(content, "Trend", y);
        String td = latest.trendDirection != null ? latest.trendDirection : "N/A";
        String ti = "Improving".equals(td) ? " ↑" : "Declining".equals(td) ? " ↓" : " →";
        Color tc = "Improving".equals(td) ? new Color(30,140,30)
                : "Declining".equals(td) ? new Color(200,50,50) : new Color(80,80,80);
        JLabel tVal = new JLabel(td + ti + (latest.trendVelocity != 0
                ? String.format("   (%+.0f pts vs last)", latest.trendVelocity) : ""));
        tVal.setFont(new Font("Arial", Font.BOLD, 15));
        tVal.setForeground(tc);
        tVal.setBounds(175, y + 8, 340, 24);
        content.add(tVal);
        y += rowH;

        // Sessions
        y = addDialogSeparator(content, y);
        addDialogLabel(content, "Sessions taken", y);
        JLabel sVal = new JLabel(String.valueOf(latest.sessionCount));
        sVal.setFont(new Font("Arial", Font.PLAIN, 15));
        sVal.setForeground(new Color(60,60,60));
        sVal.setBounds(175, y + 8, 100, 22);
        content.add(sVal);
        y += rowH;

        // Fatigue
        y = addDialogSeparator(content, y);
        addDialogLabel(content, "Fatigue Detected", y);
        JLabel fVal = new JLabel(latest.fatigueDetected ? "Yes — performance dropped in 2nd half" : "No");
        fVal.setFont(new Font("Arial", Font.BOLD, 14));
        fVal.setForeground(latest.fatigueDetected ? new Color(200,80,0) : new Color(30,140,30));
        fVal.setBounds(175, y + 8, 340, 22);
        content.add(fVal);
        y += rowH;

        // Score sparkline
        List<int[]> history = DatabaseManager.getQuizHistoryScores(userId);
        if (history != null && history.size() > 1) {
            y = addDialogSeparator(content, y);
            JLabel sparkTitle = new JLabel("Score History");
            sparkTitle.setFont(new Font("Arial", Font.BOLD, 13));
            sparkTitle.setForeground(new Color(60,30,90));
            sparkTitle.setBounds(16, y + 4, 150, 22);
            content.add(sparkTitle);
            JPanel spark = buildSparkLine(history, latest.healthScore);
            spark.setBounds(16, y + 28, 500, 75);
            content.add(spark);
            y += 110;
        }

        // AI Patterns section
        y = addDialogSeparator(content, y);
        y += 4;
        JLabel patTitle = new JLabel("  AI Patterns Detected");
        patTitle.setFont(new Font("Arial", Font.BOLD, 13));
        patTitle.setForeground(Color.WHITE);
        patTitle.setOpaque(true);
        patTitle.setBackground(new Color(123, 63, 228));
        patTitle.setBounds(16, y, 500, 26);
        content.add(patTitle);
        y += 30;
        if (latest.aiPatterns != null) {
            for (String p : latest.aiPatterns) {
                JLabel pl = new JLabel("<html><body style='width:460px'>⚠  " +
                        p.replace("\n", "<br>&nbsp;&nbsp;&nbsp;") + "</body></html>");
                pl.setFont(new Font("Arial", Font.PLAIN, 13));
                pl.setForeground(new Color(160, 70, 0));
                pl.setBounds(20, y, 490, 38);
                content.add(pl);
                y += 40;
            }
        }
        y += 10;
        content.setPreferredSize(new Dimension(530, y));

        JScrollPane scroll = new JScrollPane(content);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        dialog.add(scroll, BorderLayout.CENTER);

        // ── Bottom buttons ────────────────────────────────────────────
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 10));
        btnRow.setBackground(new Color(240, 235, 250));

        JButton viewFullBtn = new JButton("View Full Report");
        viewFullBtn.setFont(new Font("Arial", Font.BOLD, 14));
        viewFullBtn.setForeground(Color.WHITE);
        viewFullBtn.setBackground(new Color(56, 142, 142));
        viewFullBtn.setFocusPainted(false); viewFullBtn.setBorderPainted(false);
        viewFullBtn.setPreferredSize(new Dimension(180, 42));
        viewFullBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        viewFullBtn.addActionListener(e -> { dialog.dispose(); new QuizResultFrame(userId, latest); });
        btnRow.add(viewFullBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Arial", Font.BOLD, 14));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(new Color(150, 120, 180));
        closeBtn.setFocusPainted(false); closeBtn.setBorderPainted(false);
        closeBtn.setPreferredSize(new Dimension(130, 42));
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dialog.dispose());
        btnRow.add(closeBtn);

        dialog.add(btnRow, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // Helper: draw separator line
    private int addDialogSeparator(JPanel p, int y) {
        JSeparator sep = new JSeparator();
        sep.setBounds(16, y + 2, 510, 1);
        sep.setForeground(new Color(220, 210, 235));
        p.add(sep);
        return y;
    }

    // Helper: bold left label
    private void addDialogLabel(JPanel p, String text, int y) {
        JLabel lbl = new JLabel(text + ":");
        lbl.setFont(new Font("Arial", Font.BOLD, 14));
        lbl.setForeground(new Color(60, 30, 90));
        lbl.setBounds(16, y + 8, 155, 22);
        p.add(lbl);
    }

    private JPanel buildAICard() {
        JPanel card = new JPanel();
        card.setLayout(null);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(123, 63, 228), 2),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        // Header bar
        JPanel header = new JPanel();
        header.setBackground(new Color(123, 63, 228));
        header.setBounds(0, 0, 515, 34);
        header.setLayout(null);
        JLabel hTitle = new JLabel("  \uD83E\uDDE0  AI Cognitive Health Summary");
        hTitle.setFont(new Font("Arial", Font.BOLD, 14));
        hTitle.setForeground(Color.WHITE);
        hTitle.setBounds(0, 0, 515, 34);
        header.add(hTitle);
        card.add(header);

        CognitiveReport latest = DatabaseManager.getLatestCognitiveReport(userId);
        List<int[]> history    = DatabaseManager.getQuizHistoryScores(userId);

        if (latest == null) {
            JLabel noData = new JLabel(
                    "<html><center><br>No assessment data yet.<br><br>"
                            + "Take the <b>Memory Quiz</b> to generate your AI cognitive report!</center></html>");
            noData.setFont(new Font("Arial", Font.PLAIN, 14));
            noData.setForeground(new Color(120, 80, 160));
            noData.setHorizontalAlignment(SwingConstants.CENTER);
            noData.setBounds(10, 40, 495, 100);
            card.add(noData);
        } else {
            int rowY = 46;
            int rowH = 30;

            // ── Health Score ─────────────────────────────────────────
            addCardLabel(card, "Health Score:", 14, rowY);
            Color scoreColor = latest.healthScore >= 75 ? new Color(30,140,30)
                    : latest.healthScore >= 50 ? new Color(190,120,0)
                    : new Color(200,50,50);
            JLabel scoreBadge = new JLabel(latest.healthScore + "/100", SwingConstants.CENTER);
            scoreBadge.setFont(new Font("Arial", Font.BOLD, 13));
            scoreBadge.setForeground(Color.WHITE);
            scoreBadge.setOpaque(true);
            scoreBadge.setBackground(scoreColor);
            scoreBadge.setBounds(140, rowY, 72, 22);
            card.add(scoreBadge);
            JProgressBar scoreBar = new JProgressBar(0, 100);
            scoreBar.setValue(latest.healthScore);
            scoreBar.setForeground(scoreColor);
            scoreBar.setBackground(new Color(220,210,235));
            scoreBar.setStringPainted(false);
            scoreBar.setBounds(222, rowY + 4, 265, 14);
            card.add(scoreBar);
            rowY += rowH;

            // ── Risk Level ───────────────────────────────────────────
            addCardLabel(card, "Risk Level:", 14, rowY);
            Color riskColor = "Low".equals(latest.riskLevel) ? new Color(30,140,30)
                    : "Moderate".equals(latest.riskLevel) ? new Color(190,120,0)
                    : new Color(200,50,50);
            JLabel riskBadge = new JLabel(latest.riskLevel != null ? latest.riskLevel.toUpperCase() : "N/A",
                    SwingConstants.CENTER);
            riskBadge.setFont(new Font("Arial", Font.BOLD, 12));
            riskBadge.setForeground(Color.WHITE);
            riskBadge.setOpaque(true);
            riskBadge.setBackground(riskColor);
            riskBadge.setBounds(140, rowY + 1, 90, 20);
            card.add(riskBadge);
            rowY += rowH;

            // ── Trend ────────────────────────────────────────────────
            addCardLabel(card, "Trend:", 14, rowY);
            String trendText = latest.trendDirection != null ? latest.trendDirection : "N/A";
            String trendIcon = "Improving".equals(trendText) ? " ↑"
                    : "Declining".equals(trendText) ? " ↓" : " →";
            Color trendColor = "Improving".equals(trendText) ? new Color(30,140,30)
                    : "Declining".equals(trendText) ? new Color(200,50,50)
                    : new Color(80,80,80);
            JLabel trendVal = new JLabel(trendText + trendIcon);
            trendVal.setFont(new Font("Arial", Font.BOLD, 14));
            trendVal.setForeground(trendColor);
            trendVal.setBounds(140, rowY, 170, 22);
            card.add(trendVal);
            rowY += rowH;

            // ── Sessions ─────────────────────────────────────────────
            addCardLabel(card, "Sessions taken:", 14, rowY);
            JLabel sessVal = new JLabel(String.valueOf(latest.sessionCount));
            sessVal.setFont(new Font("Arial", Font.PLAIN, 14));
            sessVal.setForeground(new Color(60,60,60));
            sessVal.setBounds(160, rowY, 60, 22);
            card.add(sessVal);
            rowY += rowH + 6;

            // ── Spark line (own row, full width) ─────────────────────
            if (history != null && history.size() > 1) {
                JPanel spark = buildSparkLine(history, latest.healthScore);
                spark.setBounds(14, rowY, 487, 70);
                card.add(spark);
                rowY += 78;
            }

            // ── View Full Report button ───────────────────────────────
            rowY += 4;
            viewReportButton = new JButton("View Full Report →");
            viewReportButton.setBounds(14, rowY, 185, 32);
            viewReportButton.setFont(new Font("Arial", Font.BOLD, 13));
            viewReportButton.setForeground(Color.WHITE);
            viewReportButton.setBackground(new Color(123, 63, 228));
            viewReportButton.setFocusPainted(false);
            viewReportButton.setBorderPainted(false);
            viewReportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            viewReportButton.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) { viewReportButton.setBackground(new Color(100,50,200)); }
                public void mouseExited(java.awt.event.MouseEvent e)  { viewReportButton.setBackground(new Color(123,63,228)); }
            });
            viewReportButton.addActionListener(e -> new QuizResultFrame(userId, latest));
            card.add(viewReportButton);
        }

        return card;
    }

    private void addCardLabel(JPanel card, String text, int fontSize, int y) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Arial", Font.BOLD, fontSize));
        lbl.setForeground(new Color(60, 30, 90));
        lbl.setBounds(14, y, 125, 24);
        card.add(lbl);
    }

    /** Mini spark-line chart showing score history */
    private JPanel buildSparkLine(List<int[]> history, int currentScore) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                setBackground(new Color(245, 242, 255));

                // Collect last 5 + current
                int take = Math.min(history.size(), 4);
                List<Integer> pts = new java.util.ArrayList<>();
                for (int i = history.size() - take; i < history.size(); i++) pts.add(history.get(i)[0]);
                pts.add(currentScore);

                int n = pts.size();
                int w = getWidth() - 10, h = getHeight() - 15;
                int stepX = n > 1 ? w / (n - 1) : w;

                // Grid line at 75 (green threshold)
                g2d.setColor(new Color(200, 200, 200));
                int y75 = h - (int)(75.0 / 100 * h) + 5;
                g2d.drawLine(5, y75, w + 5, y75);

                // Line
                g2d.setStroke(new BasicStroke(2));
                g2d.setColor(new Color(123, 63, 228));
                for (int i = 0; i < n - 1; i++) {
                    int x1 = 5 + i * stepX, y1 = h - (int)(pts.get(i) / 100.0 * h) + 5;
                    int x2 = 5 + (i+1) * stepX, y2 = h - (int)(pts.get(i+1) / 100.0 * h) + 5;
                    g2d.drawLine(x1, y1, x2, y2);
                }
                // Dots
                for (int i = 0; i < n; i++) {
                    int x = 5 + i * stepX, y = h - (int)(pts.get(i) / 100.0 * h) + 5;
                    g2d.setColor(i == n-1 ? new Color(200, 50, 50) : new Color(123, 63, 228));
                    g2d.fillOval(x - 3, y - 3, 7, 7);
                }
                // Label
                g2d.setFont(new Font("Arial", Font.PLAIN, 9));
                g2d.setColor(new Color(120, 80, 160));
                g2d.drawString("Score trend", 5, getHeight() - 2);
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: create dashboard button
    // ─────────────────────────────────────────────────────────────────────

    private JButton createDashboardButton(String text, int x, int y) {
        JButton button = new JButton(text);
        button.setBounds(x, y, 460, 68);
        button.setFont(new Font("Arial", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(123, 63, 228));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { button.setBackground(new Color(100, 50, 200)); }
            public void mouseExited(java.awt.event.MouseEvent e)  { button.setBackground(new Color(123, 63, 228)); }
        });
        return button;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────

    private void openQuizFrame()           { new QuizFrame(userId);                dispose(); }
    private void openRemindersFrame()      { new ReminderFrame(userId);            dispose(); }
    private void openAppointmentScheduler(){ new AppointmentSchedulerFrame(userId);dispose(); }

    private void openInfoFrame() {
        JDialog infoDialog = new JDialog(this, "Alzheimer's Information", true);
        infoDialog.setSize(700, 650);
        infoDialog.setLocationRelativeTo(this);
        infoDialog.setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.setBackground(new Color(240, 235, 250));

        JPanel headerPanel = new JPanel();
        headerPanel.setBounds(0, 0, 700, 100);
        headerPanel.setBackground(new Color(123, 63, 228));
        headerPanel.setLayout(null);
        JLabel titleLabel = new JLabel("Alzheimer's Disease");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(200, 20, 400, 40);
        headerPanel.add(titleLabel);
        JLabel subtitleLabel = new JLabel("Understanding and Early Detection");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(new Color(230, 220, 240));
        subtitleLabel.setBounds(230, 60, 300, 25);
        headerPanel.add(subtitleLabel);
        mainPanel.add(headerPanel);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(null);
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setPreferredSize(new Dimension(640, 700));

        JLabel whatIsLabel = new JLabel("What is Alzheimer's Disease?");
        whatIsLabel.setFont(new Font("Arial", Font.BOLD, 20));
        whatIsLabel.setForeground(new Color(123, 63, 228));
        whatIsLabel.setBounds(20, 20, 600, 30);
        contentPanel.add(whatIsLabel);

        JTextArea whatIsText = createStyledTextArea(
                "Alzheimer's is a progressive brain disorder that slowly destroys memory and " +
                        "thinking skills, and eventually the ability to carry out simple tasks. It is " +
                        "the most common cause of dementia among older adults.");
        whatIsText.setBounds(20, 55, 600, 60);
        contentPanel.add(whatIsText);

        JLabel signsLabel = new JLabel("Early Warning Signs");
        signsLabel.setFont(new Font("Arial", Font.BOLD, 20));
        signsLabel.setForeground(new Color(123, 63, 228));
        signsLabel.setBounds(20, 130, 600, 30);
        contentPanel.add(signsLabel);

        String[] signs = {
                "Memory loss that disrupts daily life",
                "Challenges in planning or solving problems",
                "Difficulty completing familiar tasks",
                "Confusion with time or place",
                "Trouble understanding visual images",
                "Problems with words in speaking or writing",
                "Misplacing things and losing ability to retrace steps",
                "Decreased or poor judgment",
                "Withdrawal from work or social activities",
                "Changes in mood and personality"
        };
        int yPos = 170;
        for (String sign : signs) {
            JPanel sp = createSignPanel(sign);
            sp.setBounds(20, yPos, 600, 35);
            contentPanel.add(sp);
            yPos += 40;
        }

        JLabel resourcesLabel = new JLabel("Important Resources");
        resourcesLabel.setFont(new Font("Arial", Font.BOLD, 20));
        resourcesLabel.setForeground(new Color(123, 63, 228));
        resourcesLabel.setBounds(20, yPos + 10, 600, 30);
        contentPanel.add(resourcesLabel);
        JTextArea resourcesText = createStyledTextArea(
                "* Alzheimer's Association Helpline: 1800-123-4567\n" +
                        "* Online Support Groups and Communities\n" +
                        "* Memory Screening Programs\n" +
                        "* Caregiver Training and Education");
        resourcesText.setBounds(20, yPos + 45, 600, 80);
        contentPanel.add(resourcesText);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBounds(30, 120, 640, 430);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 2));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane);

        JButton closeButton = new JButton("Close");
        closeButton.setBounds(275, 570, 150, 40);
        closeButton.setFont(new Font("Arial", Font.BOLD, 16));
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(new Color(123, 63, 228));
        closeButton.setFocusPainted(false);
        closeButton.setBorderPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> infoDialog.dispose());
        mainPanel.add(closeButton);

        infoDialog.add(mainPanel);
        infoDialog.setVisible(true);
    }

    private JPanel createSignPanel(String text) {
        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new Color(250, 245, 255));
        panel.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 1));
        JLabel bullet = new JLabel("\u2022");
        bullet.setFont(new Font("Arial", Font.BOLD, 20));
        bullet.setForeground(new Color(123, 63, 228));
        bullet.setBounds(10, 5, 20, 25);
        panel.add(bullet);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Arial", Font.PLAIN, 14));
        lbl.setForeground(new Color(60, 30, 90));
        lbl.setBounds(35, 5, 550, 25);
        panel.add(lbl);
        return panel;
    }

    private JTextArea createStyledTextArea(String text) {
        JTextArea ta = new JTextArea(text);
        ta.setFont(new Font("Arial", Font.PLAIN, 14));
        ta.setForeground(new Color(80, 80, 80));
        ta.setBackground(Color.WHITE);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return ta;
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to logout?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new LoginFrame();
            dispose();
        }
    }
}