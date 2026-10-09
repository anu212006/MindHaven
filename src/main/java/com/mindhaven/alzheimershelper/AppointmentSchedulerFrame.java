package com.mindhaven.alzheimershelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AppointmentSchedulerFrame extends JFrame {
    private int userId;
    private JTable appointmentTable;
    private DefaultTableModel tableModel;
    private JButton addButton;
    private JButton deleteButton;
    private JButton backButton;
    private List<Appointment> appointments;

    public AppointmentSchedulerFrame(int userId) {
        this.userId = userId;
        this.appointments = new ArrayList<>();

        // Load appointments from database
        loadAppointments();

        setTitle("MindHaven - Appointment Scheduler");
        setSize(900, 700);
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
        JLabel titleLabel = new JLabel("Appointment Scheduler");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBounds(270, 20, 400, 45);
        mainPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Schedule and manage your hospital appointments");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(240, 240, 250));
        subtitleLabel.setBounds(270, 65, 400, 25);
        mainPanel.add(subtitleLabel);

        // Content Panel
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(new Color(250, 248, 255));
        contentPanel.setBounds(40, 110, 820, 500);
        contentPanel.setLayout(null);
        contentPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 2));

        // Table Panel
        String[] columnNames = {"Date", "Day", "Hospital", "Doctor", "Time", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        appointmentTable = new JTable(tableModel);
        appointmentTable.setFont(new Font("Arial", Font.PLAIN, 14));
        appointmentTable.setRowHeight(40);
        appointmentTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        appointmentTable.getTableHeader().setBackground(new Color(123, 63, 228));
        appointmentTable.getTableHeader().setForeground(Color.WHITE);
        appointmentTable.setSelectionBackground(new Color(200, 180, 230));
        appointmentTable.setSelectionForeground(Color.BLACK);

        JScrollPane scrollPane = new JScrollPane(appointmentTable);
        scrollPane.setBounds(20, 20, 780, 360);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 190, 220), 1));
        contentPanel.add(scrollPane);

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(250, 248, 255));
        buttonPanel.setBounds(20, 400, 780, 80);
        buttonPanel.setLayout(null);

        addButton = createStyledButton("Schedule Appointment", 50, 15, new Color(76, 175, 80));
        addButton.addActionListener(e -> showAddAppointmentDialog());
        buttonPanel.add(addButton);

        deleteButton = createStyledButton("Cancel Appointment", 310, 15, new Color(244, 67, 54));
        deleteButton.addActionListener(e -> deleteAppointment());
        buttonPanel.add(deleteButton);

        backButton = createStyledButton("Back to Dashboard", 570, 15, new Color(123, 63, 228));
        backButton.addActionListener(e -> goBackToDashboard());
        buttonPanel.add(backButton);

        contentPanel.add(buttonPanel);
        mainPanel.add(contentPanel);

        // Populate table with appointments
        refreshTable();

        add(mainPanel);
        setVisible(true);
    }

    private JButton createStyledButton(String text, int x, int y, Color bgColor) {
        JButton button = new JButton(text);
        button.setBounds(x, y, 210, 50);
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

    private void showAddAppointmentDialog() {
        JDialog addDialog = new JDialog(this, "Schedule New Appointment", true);
        addDialog.setSize(550, 550);
        addDialog.setLocationRelativeTo(this);
        addDialog.setLayout(null);
        addDialog.getContentPane().setBackground(new Color(250, 248, 255));

        JLabel titleLabel = new JLabel("Schedule New Appointment");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(new Color(123, 63, 228));
        titleLabel.setBounds(140, 20, 300, 30);
        addDialog.add(titleLabel);

        // Date Selection
        JLabel dateLabel = new JLabel("Appointment Date:");
        dateLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dateLabel.setBounds(50, 70, 150, 25);
        addDialog.add(dateLabel);

        JTextField dateField = new JTextField("YYYY-MM-DD");
        dateField.setBounds(200, 70, 280, 35);
        dateField.setFont(new Font("Arial", Font.PLAIN, 14));
        dateField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        addDialog.add(dateField);

        // Day Selection
        JLabel dayLabel = new JLabel("Day:");
        dayLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dayLabel.setBounds(50, 120, 150, 25);
        addDialog.add(dayLabel);

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JComboBox<String> dayCombo = new JComboBox<>(days);
        dayCombo.setBounds(200, 120, 280, 35);
        dayCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(dayCombo);

        // Hospital Name
        JLabel hospitalLabel = new JLabel("Hospital Name:");
        hospitalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        hospitalLabel.setBounds(50, 170, 150, 25);
        addDialog.add(hospitalLabel);

        JTextField hospitalField = new JTextField();
        hospitalField.setBounds(200, 170, 280, 35);
        hospitalField.setFont(new Font("Arial", Font.PLAIN, 14));
        hospitalField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        addDialog.add(hospitalField);

        // Doctor Name
        JLabel doctorLabel = new JLabel("Doctor Name:");
        doctorLabel.setFont(new Font("Arial", Font.BOLD, 14));
        doctorLabel.setBounds(50, 220, 150, 25);
        addDialog.add(doctorLabel);

        JTextField doctorField = new JTextField();
        doctorField.setBounds(200, 220, 280, 35);
        doctorField.setFont(new Font("Arial", Font.PLAIN, 14));
        doctorField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 190, 220), 2),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        addDialog.add(doctorField);

        // Time Selection
        JLabel timeLabel = new JLabel("Appointment Time:");
        timeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        timeLabel.setBounds(50, 270, 150, 25);
        addDialog.add(timeLabel);

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        JComboBox<String> hourCombo = new JComboBox<>(hours);
        hourCombo.setBounds(200, 270, 80, 35);
        hourCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(hourCombo);

        JLabel colonLabel = new JLabel(":");
        colonLabel.setFont(new Font("Arial", Font.BOLD, 20));
        colonLabel.setBounds(285, 270, 20, 35);
        addDialog.add(colonLabel);

        String[] minutes = {"00", "15", "30", "45"};
        JComboBox<String> minuteCombo = new JComboBox<>(minutes);
        minuteCombo.setBounds(300, 270, 80, 35);
        minuteCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(minuteCombo);

        // Status
        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBounds(50, 320, 150, 25);
        addDialog.add(statusLabel);

        String[] statuses = {"Scheduled", "Confirmed", "Pending"};
        JComboBox<String> statusCombo = new JComboBox<>(statuses);
        statusCombo.setBounds(200, 320, 280, 35);
        statusCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        addDialog.add(statusCombo);

        // Save Button
        JButton saveButton = new JButton("Schedule Appointment");
        saveButton.setBounds(100, 390, 160, 45);
        saveButton.setFont(new Font("Arial", Font.BOLD, 14));
        saveButton.setForeground(Color.WHITE);
        saveButton.setBackground(new Color(76, 175, 80));
        saveButton.setFocusPainted(false);
        saveButton.setBorderPainted(false);
        saveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        saveButton.addActionListener(e -> {
            String date = dateField.getText().trim();
            String day = (String) dayCombo.getSelectedItem();
            String hospital = hospitalField.getText().trim();
            String doctor = doctorField.getText().trim();
            String time = hourCombo.getSelectedItem() + ":" + minuteCombo.getSelectedItem();
            String status = (String) statusCombo.getSelectedItem();

            if (date.isEmpty() || date.equals("YYYY-MM-DD")) {
                JOptionPane.showMessageDialog(addDialog, "Please enter appointment date",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (hospital.isEmpty()) {
                JOptionPane.showMessageDialog(addDialog, "Please enter hospital name",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (doctor.isEmpty()) {
                JOptionPane.showMessageDialog(addDialog, "Please enter doctor name",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Appointment appointment = new Appointment(date, day, hospital, doctor, time, status);
            appointments.add(appointment);
            DatabaseManager.saveAppointment(userId, date, day, hospital, doctor, time, status);
            refreshTable();
            addDialog.dispose();
            JOptionPane.showMessageDialog(this, "Appointment scheduled successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        addDialog.add(saveButton);

        // Cancel Button
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBounds(290, 390, 160, 45);
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

    private void deleteAppointment() {
        int selectedRow = appointmentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an appointment to cancel",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel this appointment?",
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            appointments.remove(selectedRow);
            refreshTable();
            JOptionPane.showMessageDialog(this, "Appointment cancelled successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadAppointments() {
        String appointmentData = DatabaseManager.getAppointments(userId);

        if (appointmentData.isEmpty()) {
            // Load default sample appointment
            appointments.add(new Appointment("2025-02-15", "Thursday", "Apollo Hospital",
                    "Dr. Sharma", "10:00", "Scheduled"));
        } else {
            // Parse appointment data from database
            String[] lines = appointmentData.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 6) {
                        appointments.add(new Appointment(parts[0], parts[1], parts[2],
                                parts[3], parts[4], parts[5]));
                    }
                }
            }
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);

        for (Appointment appointment : appointments) {
            tableModel.addRow(new Object[]{
                    appointment.getDate(),
                    appointment.getDay(),
                    appointment.getHospital(),
                    appointment.getDoctor(),
                    appointment.getTime(),
                    appointment.getStatus()
            });
        }
    }

    private void goBackToDashboard() {
        new DashboardFrame(userId);
        dispose();
    }

    // Inner class for Appointment
    private static class Appointment {
        private String date;
        private String day;
        private String hospital;
        private String doctor;
        private String time;
        private String status;

        public Appointment(String date, String day, String hospital, String doctor,
                           String time, String status) {
            this.date = date;
            this.day = day;
            this.hospital = hospital;
            this.doctor = doctor;
            this.time = time;
            this.status = status;
        }

        public String getDate() { return date; }
        public String getDay() { return day; }
        public String getHospital() { return hospital; }
        public String getDoctor() { return doctor; }
        public String getTime() { return time; }
        public String getStatus() { return status; }
    }
}

