package com.mindhaven.alzheimershelper;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("===== MindHaven - Alzheimer's Helper =====");
        System.out.println("Initializing database...");
        DatabaseManager.initializeDatabase();
        System.out.println("Database initialization complete!");
        // Uncomment to add test users:
        // DatabaseManager.insertSampleData();
        System.out.println("==========================================\n");

        SwingUtilities.invokeLater(() -> new LoginFrame());
    }
}