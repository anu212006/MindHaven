package com.mindhaven.alzheimershelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ReminderFrame extends JFrame {
    private int userId;
    private JTable reminderTable;
    private DefaultTableModel tableModel;
    private JButton addButton;
    private JButton deleteButton;
    private JButton backButton;
    private List<Reminder> reminders;

    public ReminderFrame(int userId) {
        this.userId = userId;
        this.reminders = new ArrayList<>();

        // Load reminders from database
        loadReminders();

        setTitle("MindHaven - Daily Reminders");
        setSize(800, 650);
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

        // Header
        JLabel titleLabel = new JLabel("Daily Reminders");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(270, 20, 300, 45);
        mainPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Manage your daily medication and activity reminders");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(240, 240, 250));
        subtitleLabel.setBounds(220, 65, 400, 25);
        mainPanel.add(subtitleLabel);

        // Content Panel
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(new Color(250, 248, 255));
        contentPanel.setBounds(40, 110, 720, 450);
        contentPanel.setLayout(null);
        contentPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 2));

        // Table Panel
        String[] columnNames = {"Time", "Reminder", "Type", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reminderTable = new JTable(tableModel);
        reminderTable.setFont(new Font("Arial", Font.PLAIN, 14));
        reminderTable.setRowHeight(35);
        reminderTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        reminderTable.getTableHeader().setBackground(new Color(123, 63, 228));
        reminderTable.getTableHeader().setForeground(Color.WHITE);
        reminderTable.setSelectionBackground(new Color(200, 180, 230));
        reminderTable.setSelectionForeground(Color.BLACK);

        JScrollPane scrollPane = new JScrollPane(reminderTable);
        scrollPane.setBounds(20, 20, 680, 320);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 1));
        contentPanel.add(scrollPane);

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(250, 248, 255));
        buttonPanel.setBounds(20, 360, 680, 70);
        buttonPanel.setLayout(null);

        addButton = createStyledButton("Add Reminder", 50, 15, new Color(76, 175, 80));
        addButton.addActionListener(e -> showAddReminderDialog());
        buttonPanel.add(addButton);

        deleteButton = createStyledButton("Delete Selected", 270, 15, new Color(244, 67, 54));
        deleteButton.addActionListener(e -> deleteReminder());
        buttonPanel.add(deleteButton);

        backButton = createStyledButton("Back to Dashboard", 490, 15, new Color(123, 63, 228));
        backButton.addActionListener(e -> goBackToDashboard());
        buttonPanel.add(backButton);

        contentPanel.add(buttonPanel);
        mainPanel.add(contentPanel);

        // Populate table with reminders
        refreshTable();

        add(mainPanel);
        setVisible(true);
    }

    private JButton createStyledButton(String text, int x, int y, Color bgColor) {
        JButton button = new JButton(text);
        button.setBounds(x, y, 190, 45);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color originalColor = bgColor;
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    private void showAddReminderDialog() {
        JDialog addDialog = new JDialog(this, "Add New Reminder", true);
        addDialog.setSize(500, 400);
        addDialog.setLocationRelativeTo(this);
        addDialog.setLayout(null);
        addDialog.getContentPane().setBackground(new Color(250, 248, 255));

        JLabel titleLabel = new JLabel("Create New Reminder");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(new Color(123, 63, 228));
        titleLabel.setBounds(140, 20, 250, 30);
        addDialog.add(titleLabel);

        // Time Selection
        JLabel timeLabel = new JLabel("Time:");
        timeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        timeLabel.setBounds(50, 70, 100, 25);
        addDialog.add(timeLabel);

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        JComboBox<String> hourCombo = new JComboBox<>(hours);
        hourCombo.setBounds(150, 70, 80, 35);
        hourCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(hourCombo);

        JLabel colonLabel = new JLabel(":");
        colonLabel.setFont(new Font("Arial", Font.BOLD, 20));
        colonLabel.setBounds(235, 70, 20, 35);
        addDialog.add(colonLabel);

        String[] minutes = {"00", "15", "30", "45"};
        JComboBox<String> minuteCombo = new JComboBox<>(minutes);
        minuteCombo.setBounds(250, 70, 80, 35);
        minuteCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(minuteCombo);

        // Reminder Text
        JLabel reminderLabel = new JLabel("Reminder:");
        reminderLabel.setFont(new Font("Arial", Font.BOLD, 14));
        reminderLabel.setBounds(50, 125, 100, 25);
        addDialog.add(reminderLabel);

        JTextField reminderField = new JTextField();
        reminderField.setBounds(150, 125, 280, 35);
        reminderField.setFont(new Font("Arial", Font.PLAIN, 14));
        reminderField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        addDialog.add(reminderField);

        // Type Selection
        JLabel typeLabel = new JLabel("Type:");
        typeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        typeLabel.setBounds(50, 180, 100, 25);
        addDialog.add(typeLabel);

        String[] types = {"Medication", "Exercise", "Meal", "Appointment", "Other"};
        JComboBox<String> typeCombo = new JComboBox<>(types);
        typeCombo.setBounds(150, 180, 280, 35);
        typeCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(typeCombo);

        // Buttons
        JButton saveButton = new JButton("Save Reminder");
        saveButton.setBounds(100, 270, 140, 45);
        saveButton.setFont(new Font("Arial", Font.BOLD, 14));
        saveButton.setForeground(Color.WHITE);
        saveButton.setBackground(new Color(76, 175, 80));
        saveButton.setFocusPainted(false);
        saveButton.setBorderPainted(false);
        saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        saveButton.addActionListener(e -> {
            String time = hourCombo.getSelectedItem() + ":" + minuteCombo.getSelectedItem();
            String text = reminderField.getText().trim();
            String type = (String) typeCombo.getSelectedItem();

            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(addDialog, "Please enter reminder text",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Reminder reminder = new Reminder(time, text, type, "Active");
            reminders.add(reminder);
            DatabaseManager.saveReminder(userId, time, text, type);
            refreshTable();
            addDialog.dispose();
            JOptionPane.showMessageDialog(this, "Reminder added successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        addDialog.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBounds(260, 270, 140, 45);
        cancelButton.setFont(new Font("Arial", Font.BOLD, 14));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setBackground(new Color(158, 158, 158));
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancelButton.addActionListener(e -> addDialog.dispose());
        addDialog.add(cancelButton);

        addDialog.setVisible(true);
    }

    private void deleteReminder() {
        int selectedRow = reminderTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a reminder to delete",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this reminder?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            reminders.remove(selectedRow);
            refreshTable();
            JOptionPane.showMessageDialog(this, "Reminder deleted successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadReminders() {
        // Load reminders from database
        String reminderData = DatabaseManager.getReminders(userId);
        // For now, add some sample reminders if empty
        if (reminderData.isEmpty()) {
            reminders.add(new Reminder("08:00", "Take morning medication", "Medication", "Active"));
            reminders.add(new Reminder("12:00", "Lunch time", "Meal", "Active"));
            reminders.add(new Reminder("18:00", "Evening walk", "Exercise", "Active"));
            reminders.add(new Reminder("21:00", "Take evening medication", "Medication", "Active"));
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Reminder reminder : reminders) {
            tableModel.addRow(new Object[]{
                    reminder.getTime(),
                    reminder.getText(),
                    reminder.getType(),
                    reminder.getStatus()
            });
        }
    }

    private void goBackToDashboard() {
        new DashboardFrame(userId);
        dispose();
    }

    // Inner class for Reminder
    private static class Reminder {
        private String time;
        private String text;
        private String type;
        private String status;

        public Reminder(String time, String text, String type, String status) {
            this.time = time;
            this.text = text;
            this.type = type;
            this.status = status;
        }

        public String getTime() { return time; }
        public String getText() { return text; }
        public String getType() { return type; }
        public String getStatus() { return status; }
    }
}

