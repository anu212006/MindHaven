package com.mindhaven.alzheimershelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class QuizFrame extends JFrame {

    private int userId;
    private int currentQuestion = 0;
    private int score = 0;

    private List<Question> questions;

    // UI
    private ButtonGroup optionsGroup;
    private JLabel      questionLabel;
    private JRadioButton option1, option2, option3, option4;
    private JButton     nextButton;
    private JLabel      progressLabel;
    private JLabel      timerLabel;
    private JPanel      quizPanel;

    // AIML metrics
    private List<Long>    responseTimes  = new ArrayList<>();
    private int           answerChanges  = 0;
    private int           lastSelected   = -1;
    private long          questionStartMs= 0;
    private Map<String, int[]> domainScores = new LinkedHashMap<>();
    private List<Integer> userAnswers    = new ArrayList<>();

    private javax.swing.Timer screenTimer;
    private long elapsedSeconds = 0;

    public QuizFrame(int userId) {
        this.userId = userId;

        // Initialise domain buckets
        domainScores.put(AIQuizService.DOMAIN_TIME,     new int[]{0, 0});
        domainScores.put(AIQuizService.DOMAIN_PLACE,    new int[]{0, 0});
        domainScores.put(AIQuizService.DOMAIN_MATH,     new int[]{0, 0});
        domainScores.put(AIQuizService.DOMAIN_LANGUAGE, new int[]{0, 0});
        domainScores.put(AIQuizService.DOMAIN_MEMORY,   new int[]{0, 0});
        domainScores.put(AIQuizService.DOMAIN_GENERAL,  new int[]{0, 0});

        setTitle("MindHaven - Memory Assessment Quiz");
        setSize(700, 640);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel() {
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
        mainPanel.setLayout(null);

        JLabel titleLabel = new JLabel("Memory Assessment Quiz");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(130, 20, 480, 40);
        mainPanel.add(titleLabel);

        progressLabel = new JLabel("Loading questions...");
        progressLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        progressLabel.setForeground(Color.WHITE);
        progressLabel.setBounds(200, 65, 300, 25);
        mainPanel.add(progressLabel);

        timerLabel = new JLabel("\u23f1 0s");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 14));
        timerLabel.setForeground(new Color(255, 240, 180));
        timerLabel.setBounds(580, 65, 90, 25);
        timerLabel.setVisible(false);
        mainPanel.add(timerLabel);

        quizPanel = new JPanel();
        quizPanel.setBackground(new Color(240, 235, 250));
        quizPanel.setBounds(50, 110, 600, 460);
        quizPanel.setLayout(null);

        // Loading state
        JLabel loadingLabel = new JLabel(
                "<html><center>Generating personalised cognitive<br>"
                        + "assessment questions\u2026<br><br>Please wait.</center></html>");
        loadingLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        loadingLabel.setForeground(new Color(100, 50, 180));
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setBounds(50, 120, 500, 120);
        quizPanel.add(loadingLabel);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBounds(150, 260, 300, 20);
        progressBar.setForeground(new Color(123, 63, 228));
        quizPanel.add(progressBar);

        // Quiz components (hidden until loaded)
        questionLabel = new JLabel();
        questionLabel.setFont(new Font("Arial", Font.BOLD, 17));
        questionLabel.setForeground(new Color(60, 30, 90));
        questionLabel.setBounds(25, 18, 550, 100);
        questionLabel.setVerticalAlignment(SwingConstants.TOP);
        questionLabel.setVisible(false);
        quizPanel.add(questionLabel);

        optionsGroup = new ButtonGroup();
        option1 = createOptionButton(25, 128);
        option2 = createOptionButton(25, 195);
        option3 = createOptionButton(25, 262);
        option4 = createOptionButton(25, 329);
        quizPanel.add(option1);
        quizPanel.add(option2);
        quizPanel.add(option3);
        quizPanel.add(option4);

        // Track answer changes
        ActionListener changeListener = e -> {
            int selected = selectedIndex();
            if (lastSelected != -1 && selected != lastSelected) answerChanges++;
            lastSelected = selected;
        };
        option1.addActionListener(changeListener);
        option2.addActionListener(changeListener);
        option3.addActionListener(changeListener);
        option4.addActionListener(changeListener);

        nextButton = new JButton("Next");
        nextButton.setBounds(200, 395, 200, 48);
        nextButton.setFont(new Font("Arial", Font.BOLD, 18));
        nextButton.setForeground(Color.WHITE);
        nextButton.setBackground(new Color(123, 63, 228));
        nextButton.setFocusPainted(false);
        nextButton.setBorderPainted(false);
        nextButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        nextButton.addActionListener(e -> handleNext());
        nextButton.setVisible(false);
        quizPanel.add(nextButton);

        mainPanel.add(quizPanel);
        add(mainPanel);
        setVisible(true);

        loadQuestionsAsync(loadingLabel, progressBar);
    }

    private void loadQuestionsAsync(JLabel loadingLabel, JProgressBar progressBar) {
        SwingWorker<List<Question>, Void> worker = new SwingWorker<>() {
            @Override protected List<Question> doInBackground() {
                return AIQuizService.generateQuestions();
            }
            @Override protected void done() {
                try { questions = get(); }
                catch (Exception e) { questions = new ArrayList<>(); }

                loadingLabel.setVisible(false);
                progressBar.setVisible(false);
                questionLabel.setVisible(true);
                option1.setVisible(true); option2.setVisible(true);
                option3.setVisible(true); option4.setVisible(true);
                nextButton.setVisible(true);
                timerLabel.setVisible(true);
                progressLabel.setText("Question 1 of " + questions.size());
                displayQuestion();
                quizPanel.repaint();
            }
        };
        worker.execute();
    }

    private void displayQuestion() {
        if (currentQuestion >= questions.size()) return;
        Question q = questions.get(currentQuestion);

        // Show difficulty badge in question text
        String diffTag = q.getDifficulty() == AIQuizService.HARD   ? " <font color='#cc0000'>[Hard]</font>"
                : q.getDifficulty() == AIQuizService.MEDIUM ? " <font color='#cc7700'>[Medium]</font>"
                : " <font color='#007700'>[Easy]</font>";
        questionLabel.setText("<html><body style='width:520px'>"
                + (currentQuestion + 1) + ". " + q.getQuestion() + diffTag + "</body></html>");

        String[] opts = q.getOptions();
        option1.setText(opts[0]); option2.setText(opts[1]);
        option3.setText(opts[2]); option4.setText(opts[3]);
        optionsGroup.clearSelection();
        lastSelected = -1;

        progressLabel.setText("Question " + (currentQuestion + 1) + " of " + questions.size());
        if (currentQuestion == questions.size() - 1) nextButton.setText("Finish");

        questionStartMs = System.currentTimeMillis();
        elapsedSeconds  = 0;
        timerLabel.setText("\u23f1 0s");
        if (screenTimer != null) screenTimer.stop();
        screenTimer = new javax.swing.Timer(1000, e -> {
            elapsedSeconds++;
            timerLabel.setText("\u23f1 " + elapsedSeconds + "s");
        });
        screenTimer.start();
    }

    private void handleNext() {
        if (selectedIndex() == -1) {
            JOptionPane.showMessageDialog(this, "Please select an answer!", "Warning",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        long elapsed = System.currentTimeMillis() - questionStartMs;
        responseTimes.add(elapsed);
        if (screenTimer != null) screenTimer.stop();

        Question q   = questions.get(currentQuestion);
        int sel      = selectedIndex();
        userAnswers.add(sel);

        String domain = q.getDomain();
        domainScores.computeIfAbsent(domain, k -> new int[]{0, 0});
        domainScores.get(domain)[1]++;
        if (sel == q.getCorrectAnswer()) {
            score++;
            domainScores.get(domain)[0]++;
        }

        currentQuestion++;
        if (currentQuestion < questions.size()) {
            displayQuestion();
        } else {
            showResults();
        }
    }

    private int selectedIndex() {
        if (option1.isSelected()) return 0;
        if (option2.isSelected()) return 1;
        if (option3.isSelected()) return 2;
        if (option4.isSelected()) return 3;
        return -1;
    }

    private void showResults() {
        DatabaseManager.saveQuizResult(userId, score, questions.size());

        // Show analysing dialog
        JDialog analysing = new JDialog(this, "Analysing...", false);
        analysing.setSize(300, 100);
        analysing.setLocationRelativeTo(this);
        analysing.setLayout(new BorderLayout());
        JLabel msg = new JLabel("AI is analysing your responses\u2026", SwingConstants.CENTER);
        msg.setFont(new Font("Arial", Font.PLAIN, 14));
        analysing.add(msg, BorderLayout.CENTER);
        analysing.setVisible(true);

        // Remove empty domain buckets
        domainScores.entrySet().removeIf(e -> e.getValue()[1] == 0);

        SwingWorker<CognitiveReport, Void> worker = new SwingWorker<>() {
            @Override protected CognitiveReport doInBackground() {
                return AIQuizService.analysePerformance(
                        userId, score, questions.size(),
                        responseTimes, answerChanges,
                        domainScores, questions, userAnswers);
            }
            @Override protected void done() {
                analysing.dispose();
                try {
                    CognitiveReport report = get();
                    DatabaseManager.saveCognitiveReport(report);
                    new QuizResultFrame(userId, report);
                    dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                    new DashboardFrame(userId);
                    dispose();
                }
            }
        };
        worker.execute();
    }

    private JRadioButton createOptionButton(int x, int y) {
        JRadioButton btn = new JRadioButton();
        btn.setBounds(x, y, 550, 50);
        btn.setFont(new Font("Arial", Font.PLAIN, 15));
        btn.setBackground(new Color(240, 235, 250));
        btn.setFocusPainted(false);
        btn.setVisible(false);
        optionsGroup.add(btn);
        return btn;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Question inner class — now carries domain AND difficulty
    // ─────────────────────────────────────────────────────────────────────

    public static class Question {
        private final String   question;
        private final String[] options;
        private final int      correctAnswer;
        private final String   domain;
        private final int      difficulty;  // EASY=1, MEDIUM=2, HARD=3

        public Question(String question, String[] options, int correctAnswer,
                        String domain, int difficulty) {
            this.question      = question;
            this.options       = options;
            this.correctAnswer = correctAnswer;
            this.domain        = domain;
            this.difficulty    = difficulty;
        }

        // Backwards-compat: no difficulty
        public Question(String question, String[] options, int correctAnswer, String domain) {
            this(question, options, correctAnswer, domain, AIQuizService.EASY);
        }

        // Backwards-compat: no domain, no difficulty
        public Question(String question, String[] options, int correctAnswer) {
            this(question, options, correctAnswer, AIQuizService.DOMAIN_GENERAL, AIQuizService.EASY);
        }

        public String   getQuestion()     { return question; }
        public String[] getOptions()      { return options; }
        public int      getCorrectAnswer(){ return correctAnswer; }
        public String   getDomain()       { return domain; }
        public int      getDifficulty()   { return difficulty; }
    }
}