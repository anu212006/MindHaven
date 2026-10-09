package com.mindhaven.alzheimershelper;

import javax.swing.*;
import java.awt.*;

public class SignUpFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField ageField;
    private JTextField addressField;
    private JButton signUpButton;
    private JLabel loginLabel;

    public SignUpFrame() {
        setTitle("MindHaven - Sign Up");
        setSize(500, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

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

        // Alzheimer's Awareness Logo (Purple Ribbon)
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw purple ribbon shape
                g2d.setColor(new Color(142, 84, 233));
                g2d.setStroke(new BasicStroke(8));

                // Left loop
                g2d.drawArc(10, 5, 25, 35, 45, 270);
                // Right loop
                g2d.drawArc(30, 5, 25, 35, 225, 270);
                // Bottom ribbons
                g2d.drawLine(22, 40, 18, 55);
                g2d.drawLine(43, 40, 47, 55);
            }
        };
        logoPanel.setBounds(60, 20, 70, 70);
        logoPanel.setOpaque(false);
        mainPanel.add(logoPanel);

        JLabel titleLabel = new JLabel("Create Account");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(120, 20, 350, 50);
        mainPanel.add(titleLabel);

        JPanel signUpPanel = new JPanel();
        signUpPanel.setBackground(new Color(230, 220, 240));
        signUpPanel.setBounds(50, 85, 400, 580);
        signUpPanel.setLayout(null);

        // Full Name
        JLabel nameLabel = new JLabel("Full Name *");
        nameLabel.setBounds(40, 20, 150, 20);
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(nameLabel);

        fullNameField = createStyledTextField(40, 45);
        signUpPanel.add(fullNameField);

        // Username
        JLabel userLabel = new JLabel("Username *");
        userLabel.setBounds(40, 90, 150, 20);
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(userLabel);

        usernameField = createStyledTextField(40, 115);
        signUpPanel.add(usernameField);

        // Password
        JLabel passLabel = new JLabel("Password *");
        passLabel.setBounds(40, 160, 150, 20);
        passLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setBounds(40, 185, 320, 40);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 16));
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        signUpPanel.add(passwordField);

        // Email
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setBounds(40, 230, 150, 20);
        emailLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(emailLabel);

        emailField = createStyledTextField(40, 255);
        signUpPanel.add(emailField);

        // Phone
        JLabel phoneLabel = new JLabel("Phone");
        phoneLabel.setBounds(40, 300, 150, 20);
        phoneLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(phoneLabel);

        phoneField = createStyledTextField(40, 325);
        signUpPanel.add(phoneField);

        // Age
        JLabel ageLabel = new JLabel("Age");
        ageLabel.setBounds(40, 370, 150, 20);
        ageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(ageLabel);

        ageField = createStyledTextField(40, 395);
        signUpPanel.add(ageField);

        // Address
        JLabel addressLabel = new JLabel("Address");
        addressLabel.setBounds(40, 440, 150, 20);
        addressLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        signUpPanel.add(addressLabel);

        addressField = createStyledTextField(40, 465);
        signUpPanel.add(addressField);

        // Sign Up Button
        signUpButton = new JButton("Sign Up");
        signUpButton.setBounds(60, 515, 280, 45);
        signUpButton.setFont(new Font("Arial", Font.BOLD, 18));
        signUpButton.setForeground(Color.WHITE);
        signUpButton.setBackground(new Color(123, 63, 228));
        signUpButton.setFocusPainted(false);
        signUpButton.setBorderPainted(false);
        signUpButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        signUpPanel.add(signUpButton);

        mainPanel.add(signUpPanel);

        // Login Label
        loginLabel = new JLabel("Already have an account? Login");
        loginLabel.setBounds(140, 680, 250, 30);
        loginLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        loginLabel.setForeground(Color.WHITE);
        loginLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        mainPanel.add(loginLabel);

        signUpButton.addActionListener(e -> handleSignUp());
        loginLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                openLoginFrame();
            }
        });

        add(mainPanel);
        setVisible(true);
    }

    private JTextField createStyledTextField(int x, int y) {
        JTextField field = new JTextField();
        field.setBounds(x, y, 320, 40);
        field.setFont(new Font("Arial", Font.PLAIN, 16));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        return field;
    }

    private void handleSignUp() {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String ageStr = ageField.getText().trim();
        String address = addressField.getText().trim();

        // Validate required fields
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all required fields (Full Name, Username, Password)",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate age if provided
        int age = 0;
        if (!ageStr.isEmpty()) {
            try {
                age = Integer.parseInt(ageStr);
                if (age < 0 || age > 150) {
                    JOptionPane.showMessageDialog(this,
                            "Please enter a valid age (0-150)",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Age must be a number",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Register patient
        if (DatabaseManager.registerPatient(username, password, fullName, email, phone, age, address)) {
            JOptionPane.showMessageDialog(this,
                    "Account created successfully!\nPlease login to continue.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            openLoginFrame();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Username already exists or registration failed.\nPlease try again.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openLoginFrame() {
        new LoginFrame();
        dispose();
    }
}

