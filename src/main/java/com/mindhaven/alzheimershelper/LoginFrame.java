package com.mindhaven.alzheimershelper;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel signUpLabel;

    public LoginFrame() {
        setTitle("MindHaven - Login");
        setSize(500, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        // Main Panel with gradient-like purple background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                Color color1 = new Color(142, 84, 233);
                Color color2 = new Color(186, 143, 238);
                GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        mainPanel.setLayout(null);

        // Alzheimer's Awareness Logo (Purple Ribbon) - Centered at top
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw white ribbon shape
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(10));

                // Left loop
                g2d.drawArc(15, 8, 35, 50, 45, 270);
                // Right loop
                g2d.drawArc(45, 8, 35, 50, 225, 270);
                // Bottom ribbons
                g2d.drawLine(32, 58, 27, 80);
                g2d.drawLine(63, 58, 68, 80);
            }
        };
        logoPanel.setBounds(200, 30, 100, 90);
        logoPanel.setOpaque(false);
        mainPanel.add(logoPanel);

        // Title Label
        JLabel titleLabel = new JLabel("MindHaven", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(50, 120, 400, 60);
        mainPanel.add(titleLabel);

        // Subtitle Label
        JLabel subtitleLabel = new JLabel("Your everyday support", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        subtitleLabel.setForeground(new Color(240, 240, 250));
        subtitleLabel.setBounds(50, 180, 400, 30);
        mainPanel.add(subtitleLabel);

        // Login Panel
        JPanel loginPanel = new JPanel();
        loginPanel.setBackground(new Color(250, 248, 255));
        loginPanel.setBounds(70, 240, 360, 310);
        loginPanel.setLayout(null);
        loginPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        // Username Label
        JLabel userLabel = new JLabel("Username");
        userLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userLabel.setForeground(new Color(80, 40, 120));
        userLabel.setBounds(40, 30, 280, 20);
        loginPanel.add(userLabel);

        // Username Field
        usernameField = new JTextField();
        usernameField.setBounds(40, 55, 280, 45);
        usernameField.setFont(new Font("Arial", Font.PLAIN, 16));
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        usernameField.setBackground(Color.WHITE);
        loginPanel.add(usernameField);

        // Password Label
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(new Font("Arial", Font.BOLD, 14));
        passLabel.setForeground(new Color(80, 40, 120));
        passLabel.setBounds(40, 115, 280, 20);
        loginPanel.add(passLabel);

        // Password Field
        passwordField = new JPasswordField();
        passwordField.setBounds(40, 140, 280, 45);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 16));
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        passwordField.setBackground(Color.WHITE);
        loginPanel.add(passwordField);

        // Login Button
        loginButton = new JButton("Login");
        loginButton.setBounds(40, 210, 280, 50);
        loginButton.setFont(new Font("Arial", Font.BOLD, 18));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(new Color(123, 63, 228));
        loginButton.setFocusPainted(false);
        loginButton.setBorderPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                loginButton.setBackground(new Color(100, 50, 200));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                loginButton.setBackground(new Color(123, 63, 228));
            }
        });
        loginPanel.add(loginButton);

        mainPanel.add(loginPanel);

        // Sign Up Label - Outside the panel
        signUpLabel = new JLabel("Don't have an account? Sign up", SwingConstants.CENTER);
        signUpLabel.setBounds(100, 565, 300, 30);
        signUpLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        signUpLabel.setForeground(Color.WHITE);
        signUpLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add underline effect on hover
        signUpLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                signUpLabel.setText("<html><u>Don't have an account? Sign up</u></html>");
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                signUpLabel.setText("Don't have an account? Sign up");
            }
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                openSignUpFrame();
            }
        });
        mainPanel.add(signUpLabel);

        // Action Listeners
        loginButton.addActionListener(e -> handleLogin());

        add(mainPanel);
        setVisible(true);
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int patientId = DatabaseManager.loginPatient(username, password);
        if (patientId != -1) {
            JOptionPane.showMessageDialog(this, "Login successful!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            new DashboardFrame(patientId);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Invalid username or password",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSignUpFrame() {
        new SignUpFrame();
        dispose();
    }
}

