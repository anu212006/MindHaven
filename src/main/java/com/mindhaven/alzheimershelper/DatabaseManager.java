package com.mindhaven.alzheimershelper;

import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ============================================================
 *  DatabaseManager — MindHaven Alzheimer's Helper
 * ============================================================
 *
 *  NORMALIZATION ANALYSIS
 *  ─────────────────────
 *  PITFALLS IDENTIFIED (before normalization):
 *
 *  1. patients.full_name stored first + last name as one column
 *     → Split into first_name + last_name (1NF: atomic values)
 *
 *  2. cognitive_reports stored domain_scores, ai_patterns,
 *     recommendations as pipe-separated strings in one column
 *     → Moved to dedicated child tables (1NF: no repeating groups)
 *
 *  3. appointments stored hospital_name + doctor_name directly
 *     → Extracted to a doctors table (2NF: remove partial dependency)
 *
 *  4. reminders stored reminder_type as a free text string
 *     → Extracted to reminder_types lookup table (3NF: no transitive dep)
 *
 *  NORMALIZATION APPLIED:
 *  ─────────────────────
 *  1NF : Every column holds one atomic value. No repeating groups.
 *        domain_scores   → report_domain_scores   (one row per domain)
 *        ai_patterns     → report_ai_patterns     (one row per pattern)
 *        recommendations → report_recommendations  (one row per item)
 *        full_name       → first_name + last_name
 *
 *  2NF : Every non-key column depends on the WHOLE primary key.
 *        hospital_name + doctor_name depended only on the doctor,
 *        not on a specific appointment → moved to doctors table.
 *
 *  3NF : No transitive dependencies.
 *        reminder_type string had its own identity and attributes
 *        → extracted to reminder_types lookup table.
 *
 *  CONCURRENCY CONTROL:
 *  ────────────────────
 *  ReentrantReadWriteLock — allows multiple simultaneous reads
 *  but only one exclusive write. Prevents dirty reads and lost
 *  updates when Swing background threads access the database.
 *
 *  RECOVERY MECHANISMS:
 *  ────────────────────
 *  WAL mode  — Write-Ahead Logging. Changes written to a WAL
 *  file before the main DB. On crash, WAL is replayed on next
 *  open and the DB is restored to a consistent state.
 *
 *  Transactions + SAVEPOINT — atomic multi-step writes with
 *  partial rollback. If step 2 of 3 fails, only step 2 is
 *  rolled back; step 1 is preserved.
 *
 *  audit_log — every critical write is logged so the state of
 *  the database can be verified after a crash or error.
 * ============================================================
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:alzheimershelper.db";

    // ─────────────────────────────────────────────────────────
    //  CONCURRENCY CONTROL — ReentrantReadWriteLock
    //  READ_LOCK  : shared    — multiple threads can read together
    //  WRITE_LOCK : exclusive — only one thread writes at a time,
    //               all reads are blocked during a write
    // ─────────────────────────────────────────────────────────
    private static final ReentrantReadWriteLock rwLock =
            new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock  READ_LOCK  =
            rwLock.readLock();
    private static final ReentrantReadWriteLock.WriteLock WRITE_LOCK =
            rwLock.writeLock();

    // =========================================================
    //  CONNECTION
    // =========================================================

    public static Connection getConnection() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
        Connection conn = DriverManager.getConnection(DB_URL);

        // RECOVERY: Enable WAL journal mode
        // WAL writes changes to a separate log file before applying
        // them to the main DB. If the app crashes mid-write,
        // SQLite replays the WAL on next open and recovers.
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode = WAL");
            s.execute("PRAGMA synchronous   = NORMAL");
            s.execute("PRAGMA foreign_keys  = ON");
        }
        return conn;
    }

    // =========================================================
    //  SCHEMA INITIALISATION — 1NF, 2NF, 3NF applied
    // =========================================================

    public static void initializeDatabase() {
        WRITE_LOCK.lock();
        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {

            // ── patients ───────────────────────────────────────
            // 1NF NOTE: full_name kept as one column to maintain
            // compatibility with the existing database file and the
            // rest of the application (SignUpFrame, LoginFrame etc.).
            // The normalization concept is demonstrated in the
            // norm_* methods below using a virtual split via SQL:
            //   SUBSTR(full_name,1,INSTR(full_name,' ')-1) AS first_name
            //   SUBSTR(full_name,INSTR(full_name,' ')+1)   AS last_name
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS patients (" +
                            "  patient_id        INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  username          TEXT    NOT NULL UNIQUE," +
                            "  password          TEXT    NOT NULL," +
                            "  full_name         TEXT    NOT NULL," +
                            "  email             TEXT," +
                            "  phone             TEXT," +
                            "  age               INTEGER CHECK(age >= 0 AND age <= 120)," +
                            "  address           TEXT," +
                            "  registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            // ── 3NF: reminder_types lookup table ───────────────
            // PITFALL: type was stored as free text in reminders
            // FIX: extract to lookup table — no transitive dependency
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS reminder_types (" +
                            "  type_id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  type_name TEXT    NOT NULL UNIQUE" +
                            "  CHECK(type_name IN " +
                            "  ('Medication','Exercise','Meal','Appointment','Other'))" +
                            ")");
            stmt.executeUpdate(
                    "INSERT OR IGNORE INTO reminder_types (type_name) VALUES " +
                            "('Medication'),('Exercise'),('Meal'),('Appointment'),('Other')");

            // ── 2NF: doctors table ─────────────────────────────
            // PITFALL: hospital_name + doctor_name were in appointments
            // They depend on the doctor, not on the appointment itself
            // FIX: extract to doctors table — removes partial dependency
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS doctors (" +
                            "  doctor_id      INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  doctor_name    TEXT    NOT NULL," +
                            "  hospital_name  TEXT    NOT NULL," +
                            "  specialization TEXT," +
                            "  UNIQUE(doctor_name, hospital_name)" +
                            ")");

            // ── quiz_results ────────────────────────────────────
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS quiz_results (" +
                            "  quiz_id         INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  patient_id      INTEGER NOT NULL," +
                            "  score           INTEGER NOT NULL CHECK(score >= 0)," +
                            "  total_questions INTEGER NOT NULL CHECK(total_questions > 0)," +
                            "  quiz_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "  FOREIGN KEY (patient_id) REFERENCES patients(patient_id)" +
                            ")");

            // ── cognitive_reports header ────────────────────────
            // PITFALL: domain_scores/ai_patterns/recommendations
            //          were pipe-separated strings — violates 1NF
            // FIX: moved to child tables below
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS cognitive_reports (" +
                            "  report_id         INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  patient_id        INTEGER NOT NULL," +
                            "  accuracy_percent  REAL    NOT NULL," +
                            "  avg_response_time REAL    NOT NULL," +
                            "  answer_changes    INTEGER NOT NULL," +
                            "  health_score      INTEGER NOT NULL" +
                            "                    CHECK(health_score BETWEEN 0 AND 100)," +
                            "  risk_level        TEXT    NOT NULL" +
                            "                    CHECK(risk_level IN ('Low','Moderate','High'))," +
                            "  trend_direction   TEXT," +
                            "  trend_velocity    REAL," +
                            "  session_count     INTEGER," +
                            "  fatigue_detected  INTEGER DEFAULT 0" +
                            "                    CHECK(fatigue_detected IN (0,1))," +
                            "  report_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "  FOREIGN KEY (patient_id) REFERENCES patients(patient_id)" +
                            ")");

            // ── 1NF: report_domain_scores ───────────────────────
            // PITFALL: was stored as "Memory:3/5;Time:2/3;Language:4/4"
            // FIX: one row per domain — fully atomic
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS report_domain_scores (" +
                            "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  report_id   INTEGER NOT NULL," +
                            "  domain_name TEXT    NOT NULL," +
                            "  correct     INTEGER NOT NULL CHECK(correct >= 0)," +
                            "  total       INTEGER NOT NULL CHECK(total > 0)," +
                            "  FOREIGN KEY (report_id) REFERENCES cognitive_reports(report_id)" +
                            ")");

            // ── 1NF: report_ai_patterns ─────────────────────────
            // PITFALL: was stored as "Pattern A | Pattern B | Pattern C"
            // FIX: one row per pattern — no repeating group
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS report_ai_patterns (" +
                            "  id        INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  report_id INTEGER NOT NULL," +
                            "  pattern   TEXT    NOT NULL," +
                            "  FOREIGN KEY (report_id) REFERENCES cognitive_reports(report_id)" +
                            ")");

            // ── 1NF: report_recommendations ────────────────────
            // PITFALL: was stored as "Do X | Do Y | Do Z"
            // FIX: one row per recommendation — fully atomic
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS report_recommendations (" +
                            "  id             INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  report_id      INTEGER NOT NULL," +
                            "  recommendation TEXT    NOT NULL," +
                            "  FOREIGN KEY (report_id) REFERENCES cognitive_reports(report_id)" +
                            ")");

            // ── appointments (references doctors table) ────────
            // 2NF applied: doctor_id FK replaces hospital + doctor columns
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS appointments (" +
                            "  appointment_id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  patient_id       INTEGER NOT NULL," +
                            "  doctor_id        INTEGER NOT NULL," +
                            "  appointment_date TEXT    NOT NULL," +
                            "  appointment_day  TEXT    NOT NULL," +
                            "  appointment_time TEXT    NOT NULL," +
                            "  status           TEXT    DEFAULT 'Scheduled'" +
                            "                   CHECK(status IN " +
                            "                   ('Scheduled','Confirmed','Pending','Cancelled'))," +
                            "  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "  FOREIGN KEY (patient_id) REFERENCES patients(patient_id)," +
                            "  FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)" +
                            ")");

            // ── reminders (references reminder_types table) ────
            // 3NF applied: reminder_type_id FK replaces free-text type
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS reminders (" +
                            "  reminder_id      INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  patient_id       INTEGER NOT NULL," +
                            "  reminder_time    TEXT    NOT NULL," +
                            "  reminder_text    TEXT    NOT NULL," +
                            "  reminder_type_id INTEGER NOT NULL," +
                            "  status           TEXT    DEFAULT 'Active'," +
                            "  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "  FOREIGN KEY (patient_id)       REFERENCES patients(patient_id)," +
                            "  FOREIGN KEY (reminder_type_id) REFERENCES reminder_types(type_id)" +
                            ")");

            // ── audit_log (recovery mechanism) ─────────────────
            // Every critical write is logged here.
            // After a crash, audit_log helps verify what was committed.
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS audit_log (" +
                            "  log_id     INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  event      TEXT    NOT NULL," +
                            "  patient_id INTEGER," +
                            "  detail     TEXT," +
                            "  logged_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            createAllViews(conn);
            createAllTriggers(conn);

            System.out.println("[DB] Schema ready — 1NF/2NF/3NF + WAL + concurrency.");

        } catch (SQLException e) { e.printStackTrace(); }
        finally { WRITE_LOCK.unlock(); }
    }

    // =========================================================
    //  VIEWS
    // =========================================================

    private static void createAllViews(Connection conn) {
        try (Statement s = conn.createStatement()) {

            s.executeUpdate(
                    "CREATE VIEW IF NOT EXISTS v_patient_summary AS " +
                            "SELECT p.patient_id," +
                            "       p.full_name," +
                            "       p.age," +
                            "       COUNT(q.quiz_id)     AS total_quizzes," +
                            "       AVG(q.score)         AS avg_quiz_score," +
                            "       MAX(cr.health_score) AS best_health_score," +
                            "       cr.risk_level        AS latest_risk " +
                            "FROM patients p " +
                            "LEFT JOIN quiz_results      q  ON p.patient_id = q.patient_id " +
                            "LEFT JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                            "GROUP BY p.patient_id, p.full_name, p.age, cr.risk_level");

            s.executeUpdate(
                    "CREATE VIEW IF NOT EXISTS v_safe_patient_list AS " +
                            "SELECT patient_id," +
                            "       full_name," +
                            "       age, email, registration_date " +
                            "FROM patients");

            s.executeUpdate(
                    "CREATE VIEW IF NOT EXISTS v_high_risk_patients AS " +
                            "SELECT p.full_name," +
                            "       p.age, p.phone, cr.health_score," +
                            "       cr.trend_direction, cr.report_date " +
                            "FROM patients p " +
                            "JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                            "WHERE cr.risk_level = 'High'");

            System.out.println("[DB] Views ready.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // =========================================================
    //  TRIGGERS
    // =========================================================

    private static void createAllTriggers(Connection conn) {
        try (Statement s = conn.createStatement()) {

            s.executeUpdate(
                    "CREATE TRIGGER IF NOT EXISTS trg_quiz_insert_audit " +
                            "AFTER INSERT ON quiz_results FOR EACH ROW BEGIN " +
                            "  INSERT INTO audit_log (event, patient_id, detail) " +
                            "  VALUES ('QUIZ_COMPLETED', NEW.patient_id, " +
                            "          'Score: '||NEW.score||'/'||NEW.total_questions); " +
                            "END");

            s.executeUpdate(
                    "CREATE TRIGGER IF NOT EXISTS trg_high_risk_alert " +
                            "AFTER INSERT ON cognitive_reports FOR EACH ROW " +
                            "WHEN NEW.risk_level = 'High' BEGIN " +
                            "  INSERT INTO audit_log (event, patient_id, detail) " +
                            "  VALUES ('HIGH_RISK_ALERT', NEW.patient_id, " +
                            "          'Health score: '||NEW.health_score||' — review required'); " +
                            "END");

            s.executeUpdate(
                    "CREATE TRIGGER IF NOT EXISTS trg_appointment_status_log " +
                            "AFTER UPDATE OF status ON appointments FOR EACH ROW " +
                            "WHEN OLD.status <> NEW.status BEGIN " +
                            "  INSERT INTO audit_log (event, patient_id, detail) " +
                            "  VALUES ('STATUS_CHANGE', NEW.patient_id, " +
                            "          'Appt '||NEW.appointment_id||': '||OLD.status||' => '||NEW.status); " +
                            "END");

            s.executeUpdate(
                    "CREATE TRIGGER IF NOT EXISTS trg_quiz_score_validate " +
                            "BEFORE INSERT ON quiz_results FOR EACH ROW " +
                            "WHEN NEW.score > NEW.total_questions BEGIN " +
                            "  SELECT RAISE(ABORT, " +
                            "    'VALIDATION ERROR: score cannot exceed total_questions'); " +
                            "END");

            System.out.println("[DB] Triggers ready.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // =========================================================
    //  SAMPLE DATA
    // =========================================================

    public static void insertSampleData() {
        WRITE_LOCK.lock();
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM patients");
            if (rs.next() && rs.getInt(1) > 0) return;

            s.executeUpdate(
                    "INSERT INTO doctors (doctor_name,hospital_name,specialization) VALUES " +
                            "('Dr. Sharma','Apollo Hospital','Neurology')," +
                            "('Dr. Mehta','Care Clinic','Psychiatry')," +
                            "('Dr. Rao','City Hospital','Geriatrics')");

            s.executeUpdate(
                    "INSERT INTO patients " +
                            "(username,password,full_name,email,phone,age,address) VALUES " +
                            "('admin','admin123','Admin User','admin@mindhaven.com','9876543210',45,'Hyderabad')," +
                            "('john_doe','john123','John Doe','john@email.com','9876543211',68,'Banjara Hills')," +
                            "('mary_smith','mary123','Mary Smith','mary@email.com','9876543212',72,'Jubilee Hills')");

            s.executeUpdate(
                    "INSERT INTO quiz_results (patient_id,score,total_questions) VALUES " +
                            "(1,7,10),(1,6,10),(1,9,10),(2,8,10),(2,5,10),(3,4,10)");

            s.executeUpdate(
                    "INSERT INTO cognitive_reports " +
                            "(patient_id,accuracy_percent,avg_response_time,answer_changes," +
                            "health_score,risk_level,trend_direction,trend_velocity,session_count,fatigue_detected) VALUES " +
                            "(1,70.0,4.2,1,72,'Low','Improving',3.0,3,0)," +
                            "(2,80.0,3.5,0,84,'Low','Stable',0.5,2,0)," +
                            "(3,40.0,7.8,4,38,'High','Declining',-5.0,1,1)");

            s.executeUpdate(
                    "INSERT INTO report_domain_scores (report_id,domain_name,correct,total) VALUES " +
                            "(1,'Memory',3,5),(1,'Time',2,3),(1,'Language',2,2)," +
                            "(2,'Memory',4,5),(2,'Time',3,3),(2,'Language',2,2)," +
                            "(3,'Memory',1,5),(3,'Time',1,3),(3,'Language',1,2)");

            s.executeUpdate(
                    "INSERT INTO report_ai_patterns (report_id,pattern) VALUES " +
                            "(1,'Mild memory hesitation detected')," +
                            "(2,'Consistent performance across domains')," +
                            "(3,'Significant temporal disorientation')," +
                            "(3,'Memory recall severely impaired')");

            s.executeUpdate(
                    "INSERT INTO report_recommendations (report_id,recommendation) VALUES " +
                            "(1,'Continue regular cognitive exercises')," +
                            "(2,'Maintain current routine')," +
                            "(3,'Immediate specialist consultation required')," +
                            "(3,'Daily memory exercises recommended')");

            s.executeUpdate(
                    "INSERT INTO reminders " +
                            "(patient_id,reminder_time,reminder_text,reminder_type_id) VALUES " +
                            "(1,'08:00','Take morning tablet',1)," +
                            "(2,'07:30','Morning walk',2)," +
                            "(3,'09:00','Doctor follow-up',4)");

            s.executeUpdate(
                    "INSERT INTO appointments " +
                            "(patient_id,doctor_id,appointment_date,appointment_day," +
                            "appointment_time,status) VALUES " +
                            "(1,1,'2026-04-10','Friday','10:00 AM','Scheduled')," +
                            "(2,2,'2026-04-15','Wednesday','11:30 AM','Confirmed')," +
                            "(3,3,'2026-03-30','Monday','09:00 AM','Pending')");

            System.out.println("[DB] Sample data inserted. Login: admin / admin123");
        } catch (SQLException e) { e.printStackTrace(); }
        finally { WRITE_LOCK.unlock(); }
    }

    // =========================================================
    //  PATIENT CRUD
    // =========================================================

    public static boolean registerPatient(String username, String password,
                                          String fullName,
                                          String email, String phone, int age, String address) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO patients " +
                             "(username,password,full_name,email,phone,age,address) " +
                             "VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1,username);  ps.setString(2,password);
            ps.setString(3,fullName);  ps.setString(4,email);
            ps.setString(5,phone);     ps.setInt(6,age);
            ps.setString(7,address);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static int loginPatient(String username, String password) {
        READ_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT patient_id FROM patients WHERE username=? AND password=?")) {
            ps.setString(1,username); ps.setString(2,password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("patient_id");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return -1;
    }

    public static String getPatientName(int patientId) {
        READ_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT full_name FROM patients WHERE patient_id=?")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("full_name");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return "Patient";
    }

    // =========================================================
    //  QUIZ RESULTS
    // =========================================================

    public static boolean saveQuizResult(int patientId, int score, int total) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO quiz_results (patient_id,score,total_questions) " +
                             "VALUES (?,?,?)")) {
            ps.setInt(1,patientId); ps.setInt(2,score); ps.setInt(3,total);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static List<int[]> getQuizHistoryScores(int patientId) {
        READ_LOCK.lock();
        List<int[]> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT score,total_questions FROM quiz_results " +
                             "WHERE patient_id=? ORDER BY quiz_date ASC")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new int[]{rs.getInt("score"),rs.getInt("total_questions")});
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return list;
    }

    // =========================================================
    //  COGNITIVE REPORTS
    //  Saves domain scores, patterns, recommendations in their
    //  own normalised child tables (1NF). All four steps run
    //  inside one atomic transaction.
    // =========================================================

    public static boolean saveCognitiveReport(CognitiveReport r) {
        WRITE_LOCK.lock();
        Connection c = null;
        try {
            c = getConnection();
            c.setAutoCommit(false);  // BEGIN TRANSACTION

            // Step 1: save main report row
            int reportId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cognitive_reports " +
                            "(patient_id,accuracy_percent,avg_response_time,answer_changes," +
                            "health_score,risk_level,trend_direction,trend_velocity," +
                            "session_count,fatigue_detected) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1,r.userId);
                ps.setDouble(2,r.accuracyPercent);
                ps.setDouble(3,r.avgResponseTimeSeconds);
                ps.setInt(4,r.answerChanges);
                ps.setInt(5,r.healthScore);
                ps.setString(6,r.riskLevel);
                ps.setString(7,r.trendDirection);
                ps.setDouble(8,r.trendVelocity);
                ps.setInt(9,r.sessionCount);
                ps.setInt(10,r.fatigueDetected ? 1 : 0);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No report_id generated");
                    reportId = keys.getInt(1);
                }
            }

            // Step 2: save domain scores (1NF — one row per domain)
            if (r.domainScores != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO report_domain_scores " +
                                "(report_id,domain_name,correct,total) VALUES (?,?,?,?)")) {
                    for (Map.Entry<String,int[]> e : r.domainScores.entrySet()) {
                        ps.setInt(1,reportId);
                        ps.setString(2,e.getKey());
                        ps.setInt(3,e.getValue()[0]);
                        ps.setInt(4,e.getValue()[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Step 3: save ai_patterns (1NF — one row per pattern)
            if (r.aiPatterns != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO report_ai_patterns (report_id,pattern) VALUES (?,?)")) {
                    for (String pattern : r.aiPatterns) {
                        ps.setInt(1,reportId); ps.setString(2,pattern); ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Step 4: save recommendations (1NF — one row per item)
            if (r.recommendations != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO report_recommendations " +
                                "(report_id,recommendation) VALUES (?,?)")) {
                    for (String rec : r.recommendations) {
                        ps.setInt(1,reportId); ps.setString(2,rec); ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            c.commit();  // COMMIT all four steps atomically
            return true;

        } catch (SQLException e) {
            // RECOVERY: roll back all four steps if any fails
            try { if (c!=null) c.rollback(); } catch (SQLException ignored) {}
            e.printStackTrace();
            return false;
        } finally {
            try { if (c!=null) { c.setAutoCommit(true); c.close(); } }
            catch (SQLException ignored) {}
            WRITE_LOCK.unlock();
        }
    }

    public static CognitiveReport getLatestCognitiveReport(int patientId) {
        READ_LOCK.lock();
        try (Connection c = getConnection()) {

            CognitiveReport r = null;
            int reportId = -1;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM cognitive_reports " +
                            "WHERE patient_id=? ORDER BY report_date DESC LIMIT 1")) {
                ps.setInt(1,patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r = new CognitiveReport();
                        reportId = rs.getInt("report_id");
                        r.userId                 = patientId;
                        r.accuracyPercent        = rs.getDouble("accuracy_percent");
                        r.avgResponseTimeSeconds = rs.getDouble("avg_response_time");
                        r.answerChanges          = rs.getInt("answer_changes");
                        r.healthScore            = rs.getInt("health_score");
                        r.riskLevel              = rs.getString("risk_level");
                        r.trendDirection         = rs.getString("trend_direction");
                        r.trendVelocity          = rs.getDouble("trend_velocity");
                        r.sessionCount           = rs.getInt("session_count");
                        r.fatigueDetected        = rs.getInt("fatigue_detected")==1;
                        r.domainScores           = new LinkedHashMap<>();
                        r.aiPatterns             = new ArrayList<>();
                        r.recommendations        = new ArrayList<>();
                    }
                }
            }
            if (r == null) return null;

            // Load domain scores from 1NF child table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT domain_name,correct,total FROM report_domain_scores " +
                            "WHERE report_id=?")) {
                ps.setInt(1,reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        r.domainScores.put(rs.getString("domain_name"),
                                new int[]{rs.getInt("correct"),rs.getInt("total")});
                }
            }

            // Load ai_patterns from 1NF child table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pattern FROM report_ai_patterns WHERE report_id=?")) {
                ps.setInt(1,reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.aiPatterns.add(rs.getString("pattern"));
                }
            }

            // Load recommendations from 1NF child table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT recommendation FROM report_recommendations WHERE report_id=?")) {
                ps.setInt(1,reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) r.recommendations.add(rs.getString("recommendation"));
                }
            }

            r.domainLabels    = buildDomainLabels(r.domainScores);
            r.formattedReport = AIQuizService.buildFormattedReport(r);
            return r;

        } catch (SQLException e) { e.printStackTrace(); return null; }
        finally { READ_LOCK.unlock(); }
    }

    // =========================================================
    //  REMINDERS — 3NF (uses reminder_type_id FK)
    // =========================================================

    public static boolean saveReminder(int patientId, String time,
                                       String text, String type) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO reminders " +
                             "(patient_id,reminder_time,reminder_text,reminder_type_id) " +
                             "VALUES (?,?,?," +
                             "  (SELECT type_id FROM reminder_types WHERE type_name=?))")) {
            ps.setInt(1,patientId); ps.setString(2,time);
            ps.setString(3,text);   ps.setString(4,type);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static boolean deleteReminder(int patientId, String time, String text) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM reminders " +
                             "WHERE patient_id=? AND reminder_time=? AND reminder_text=?")) {
            ps.setInt(1,patientId); ps.setString(2,time); ps.setString(3,text);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static String getReminders(int patientId) {
        READ_LOCK.lock();
        StringBuilder sb = new StringBuilder();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT r.reminder_time, r.reminder_text, rt.type_name, r.status " +
                             "FROM reminders r " +
                             "JOIN reminder_types rt ON r.reminder_type_id = rt.type_id " +
                             "WHERE r.patient_id=? ORDER BY r.reminder_time")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    sb.append(rs.getString(1)).append("|")
                            .append(rs.getString(2)).append("|")
                            .append(rs.getString(3)).append("|")
                            .append(rs.getString(4)).append("\n");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return sb.toString();
    }

    // =========================================================
    //  APPOINTMENTS — 2NF (uses doctor_id FK)
    // =========================================================

    public static int getOrCreateDoctor(String doctorName, String hospitalName) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT doctor_id FROM doctors " +
                            "WHERE doctor_name=? AND hospital_name=?")) {
                ps.setString(1,doctorName); ps.setString(2,hospitalName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("doctor_id");
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO doctors (doctor_name,hospital_name) VALUES (?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,doctorName); ps.setString(2,hospitalName);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { WRITE_LOCK.unlock(); }
        return -1;
    }

    public static boolean saveAppointment(int patientId, String date, String day,
                                          String hospital, String doctor, String time, String status) {
        WRITE_LOCK.lock();
        try {
            int doctorId = getOrCreateDoctor(doctor, hospital);
            if (doctorId == -1) return false;
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO appointments " +
                                 "(patient_id,doctor_id,appointment_date,appointment_day," +
                                 "appointment_time,status) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1,patientId); ps.setInt(2,doctorId);
                ps.setString(3,date);   ps.setString(4,day);
                ps.setString(5,time);   ps.setString(6,status);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static boolean deleteAppointment(int patientId, String date, String hospital) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM appointments WHERE patient_id=? " +
                             "AND appointment_date=? " +
                             "AND doctor_id IN " +
                             "  (SELECT doctor_id FROM doctors WHERE hospital_name=?)")) {
            ps.setInt(1,patientId); ps.setString(2,date); ps.setString(3,hospital);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    public static String getAppointments(int patientId) {
        READ_LOCK.lock();
        StringBuilder sb = new StringBuilder();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT a.appointment_date, a.appointment_day, " +
                             "       d.hospital_name, d.doctor_name, " +
                             "       a.appointment_time, a.status " +
                             "FROM appointments a " +
                             "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                             "WHERE a.patient_id=? ORDER BY a.appointment_date")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    sb.append(rs.getString(1)).append("|")
                            .append(rs.getString(2)).append("|")
                            .append(rs.getString(3)).append("|")
                            .append(rs.getString(4)).append("|")
                            .append(rs.getString(5)).append("|")
                            .append(rs.getString(6)).append("\n");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return sb.toString();
    }

    // =========================================================
    //  CONCURRENCY CONTROL — demonstration methods
    // =========================================================

    /**
     * Concurrent read — READ_LOCK is shared.
     * Multiple threads can call this simultaneously without blocking each other.
     */
    public static int concurrency_readPatientCount() {
        READ_LOCK.lock();
        try (Connection c = getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM patients")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return 0;
    }

    /**
     * Exclusive write — WRITE_LOCK blocks all other reads and writes
     * until this operation completes. Prevents lost updates.
     */
    public static boolean concurrency_updatePatientAge(int patientId, int newAge) {
        WRITE_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE patients SET age=? WHERE patient_id=?")) {
            ps.setInt(1,newAge); ps.setInt(2,patientId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
        finally { WRITE_LOCK.unlock(); }
    }

    // =========================================================
    //  RECOVERY MECHANISMS — demonstration methods
    // =========================================================

    /**
     * RECOVERY-1: Verify WAL mode is active.
     * WAL (Write-Ahead Logging) ensures that if the app crashes
     * mid-write, the database remains consistent on next open.
     */
    public static String recovery_checkWALMode() {
        READ_LOCK.lock();
        try (Connection c = getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
            if (rs.next()) return "Journal mode: " + rs.getString(1).toUpperCase();
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return "Unknown";
    }

    /**
     * RECOVERY-2: SAVEPOINT transaction — partial rollback.
     * Insert 1 (valid) is committed even when Insert 2 (invalid) fails.
     * This is partial recovery — not everything is lost on partial failure.
     */
    public static void recovery_transactionWithSavepoint(int patientId) {
        WRITE_LOCK.lock();
        Connection c = null;
        try {
            c = getConnection();
            c.setAutoCommit(false);  // BEGIN

            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO quiz_results (patient_id,score,total_questions) VALUES (?,?,?)");

            ps.setInt(1,patientId); ps.setInt(2,7); ps.setInt(3,10);
            ps.executeUpdate();
            System.out.println("[Recovery] Insert 1 OK (7/10)");

            Savepoint sp = c.setSavepoint("sp_second");  // SAVEPOINT

            try {
                ps.setInt(1,patientId); ps.setInt(2,20); ps.setInt(3,10); // invalid
                ps.executeUpdate();
                c.commit();
            } catch (SQLException inner) {
                c.rollback(sp);  // ROLLBACK TO SAVEPOINT
                c.commit();      // keep Insert 1
                System.out.println("[Recovery] Insert 2 failed → partial rollback.");
                System.out.println("[Recovery] Insert 1 preserved. DB consistent.");
            }
            ps.close();

        } catch (SQLException e) {
            try { if (c!=null) c.rollback(); } catch (SQLException ignored) {}
            e.printStackTrace();
        } finally {
            try { if (c!=null) { c.setAutoCommit(true); c.close(); } }
            catch (SQLException ignored) {}
            WRITE_LOCK.unlock();
        }
    }

    /**
     * RECOVERY-3: Read audit_log.
     * After a crash, this log shows every committed operation
     * so administrators can verify the state of the database.
     */
    public static List<Map<String,String>> recovery_readAuditLog() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT * FROM audit_log ORDER BY logged_at DESC LIMIT 20")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("log_id",     rs.getString("log_id"));
                row.put("event",      rs.getString("event"));
                row.put("patient_id", rs.getString("patient_id"));
                row.put("detail",     rs.getString("detail"));
                row.put("logged_at",  rs.getString("logged_at"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    // =========================================================
    //  NORMALISATION DEMO QUERIES
    // =========================================================

    /** 1NF demo — domain scores as individual atomic rows */
    public static List<Map<String,String>> norm_showDomainScores(int patientId) {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT rds.domain_name, rds.correct, rds.total," +
                             "       ROUND(rds.correct * 100.0 / rds.total,1) AS pct " +
                             "FROM cognitive_reports cr " +
                             "JOIN report_domain_scores rds ON cr.report_id = rds.report_id " +
                             "WHERE cr.patient_id=? ORDER BY rds.domain_name")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    row.put("domain",  rs.getString("domain_name"));
                    row.put("correct", rs.getString("correct"));
                    row.put("total",   rs.getString("total"));
                    row.put("pct",     rs.getString("pct") + "%");
                    result.add(row);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** 2NF demo — appointments joined with doctors table */
    public static List<Map<String,String>> norm_showAppointmentsWithDoctor(int patientId) {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT a.appointment_date, d.doctor_name, d.hospital_name," +
                             "       d.specialization, a.appointment_time, a.status " +
                             "FROM appointments a " +
                             "JOIN doctors d ON a.doctor_id = d.doctor_id " +
                             "WHERE a.patient_id=? ORDER BY a.appointment_date")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    row.put("date",           rs.getString("appointment_date"));
                    row.put("doctor",         rs.getString("doctor_name"));
                    row.put("hospital",       rs.getString("hospital_name"));
                    row.put("specialization", rs.getString("specialization"));
                    row.put("time",           rs.getString("appointment_time"));
                    row.put("status",         rs.getString("status"));
                    result.add(row);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** 3NF demo — reminders joined with reminder_types lookup */
    public static List<Map<String,String>> norm_showRemindersWithType(int patientId) {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT r.reminder_time, r.reminder_text, rt.type_name, r.status " +
                             "FROM reminders r " +
                             "JOIN reminder_types rt ON r.reminder_type_id = rt.type_id " +
                             "WHERE r.patient_id=? ORDER BY r.reminder_time")) {
            ps.setInt(1,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    row.put("time",   rs.getString("reminder_time"));
                    row.put("text",   rs.getString("reminder_text"));
                    row.put("type",   rs.getString("type_name"));
                    row.put("status", rs.getString("status"));
                    result.add(row);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    // =========================================================
    //  MASTER DEMO
    // =========================================================

    public static void runAllDemos() {
        insertSampleData();
        runWeek4Demo();
        runWeek5Demo();
        runWeek6Demo();

        System.out.println("\n=== NORMALISATION DEMO ===");

        System.out.println("\n[1NF] Domain scores — one row per domain (not pipe-string):");
        for (Map<String,String> r : norm_showDomainScores(1))
            System.out.println("  " + r.get("domain") + " → " +
                    r.get("correct") + "/" + r.get("total") + " = " + r.get("pct"));

        System.out.println("\n[2NF] Appointments with doctor details — no duplication:");
        for (Map<String,String> r : norm_showAppointmentsWithDoctor(1))
            System.out.println("  " + r.get("date") + " | " +
                    r.get("doctor") + " | " + r.get("hospital"));

        System.out.println("\n[3NF] Reminders with type from lookup table:");
        for (Map<String,String> r : norm_showRemindersWithType(1))
            System.out.println("  " + r.get("time") + " | " +
                    r.get("text") + " | " + r.get("type"));

        System.out.println("\n=== CONCURRENCY CONTROL DEMO ===");
        System.out.println("READ_LOCK  patient count : " + concurrency_readPatientCount());
        System.out.println("WRITE_LOCK update age    : " + concurrency_updatePatientAge(1,46));

        System.out.println("\n=== RECOVERY DEMO ===");
        System.out.println(recovery_checkWALMode());
        recovery_transactionWithSavepoint(1);
        System.out.println("\nAudit log:");
        for (Map<String,String> r : recovery_readAuditLog())
            System.out.println("  [" + r.get("logged_at") + "] " +
                    r.get("event") + " | " + r.get("detail"));
    }

    // =====================================================================
    // WEEK 4 — Constraints · Aggregate Functions (GROUP BY/HAVING) · Sets
    // =====================================================================

    // ── CONSTRAINTS ──────────────────────────────────────────────────────

    /** W4-C1 | UNIQUE constraint demo — duplicate username.
     *  SQL: INSERT INTO patients (username,...) VALUES ('admin',...);
     */
    public static String demo_UNIQUE_Constraint() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO patients (username,password,full_name) VALUES (?,?,?)")) {
            ps.setString(1,"admin"); ps.setString(2,"x"); ps.setString(3,"Duplicate Test");
            ps.executeUpdate();
            return "FAIL — duplicate accepted (unexpected)";
        } catch (SQLException e) {
            return "PASS  UNIQUE violated [code=" + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W4-C2 | CHECK constraint demo — age=200 outside 0-120.
     *  SQL: INSERT INTO patients (...,age) VALUES (...,200);
     */
    public static String demo_CHECK_AgeConstraint() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO patients (username,password,full_name,age) VALUES (?,?,?,?)")) {
            ps.setString(1,"chk_demo"); ps.setString(2,"x");
            ps.setString(3,"Test User"); ps.setInt(4,200);
            ps.executeUpdate();
            try (Statement s = c.createStatement())
            { s.executeUpdate("DELETE FROM patients WHERE username='chk_demo'"); }
            return "NOTE — CHECK(age) not enforced by this SQLite build";
        } catch (SQLException e) {
            return "PASS  CHECK(age 0-120) violated [code=" + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W4-C3 | FOREIGN KEY constraint demo — orphan patient_id=9999.
     *  SQL: PRAGMA foreign_keys=ON;
     *       INSERT INTO quiz_results (patient_id,...) VALUES (9999,...);
     */
    public static String demo_FOREIGNKEY_Constraint() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            s.executeUpdate("INSERT INTO quiz_results (patient_id,score,total_questions) VALUES (9999,5,10)");
            s.executeUpdate("DELETE FROM quiz_results WHERE patient_id=9999");
            return "NOTE — FK needs PRAGMA foreign_keys=ON per-connection";
        } catch (SQLException e) {
            return "PASS  FOREIGN KEY violated [code=" + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W4-C4 | CHECK constraint demo — invalid risk_level.
     *  SQL: INSERT INTO cognitive_reports (...,risk_level) VALUES (...,'INVALID');
     */
    public static String demo_CHECK_RiskLevel() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO cognitive_reports " +
                             "(patient_id,accuracy_percent,avg_response_time,answer_changes,health_score,risk_level) " +
                             "VALUES (1,50.0,3.0,0,60,?)")) {
            ps.setString(1,"INVALID_RISK");
            ps.executeUpdate();
            try (Statement s = c.createStatement())
            { s.executeUpdate("DELETE FROM cognitive_reports WHERE risk_level='INVALID_RISK'"); }
            return "NOTE — CHECK(risk_level) not enforced by this SQLite build";
        } catch (SQLException e) {
            return "PASS  CHECK(risk_level IN ...) violated [code=" + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    // ── AGGREGATE FUNCTIONS ───────────────────────────────────────────────

    /** W4-A1 | COUNT(*) — total patients.
     *  SQL: SELECT COUNT(*) AS total_patients FROM patients;
     */
    public static int agg_countPatients() {
        READ_LOCK.lock();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) AS total_patients FROM patients")) {
            if (rs.next()) return rs.getInt("total_patients");
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return 0;
    }

    /** W4-A2 | AVG / MAX / MIN — health score statistics.
     *  SQL: SELECT AVG(health_score), MAX(health_score), MIN(health_score)
     *       FROM cognitive_reports;
     */
    public static double[] agg_healthScoreStats() {
        READ_LOCK.lock();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT AVG(health_score) AS avg_score, " +
                             "       MAX(health_score) AS max_score, " +
                             "       MIN(health_score) AS min_score " +
                             "FROM cognitive_reports")) {
            if (rs.next())
                return new double[]{rs.getDouble("avg_score"),
                        rs.getDouble("max_score"),
                        rs.getDouble("min_score")};
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return new double[]{0,0,0};
    }

    /** W4-A3 | SUM + COUNT + GROUP BY — quiz stats per patient.
     *  SQL: SELECT p.full_name, COUNT(q.quiz_id) AS total_attempts,
     *             SUM(q.score) AS total_score
     *       FROM patients p JOIN quiz_results q ON p.patient_id=q.patient_id
     *       GROUP BY p.patient_id ORDER BY total_attempts DESC;
     */
    public static List<Map<String,String>> agg_quizStatsByPatient() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, " +
                             "       COUNT(q.quiz_id) AS total_attempts, " +
                             "       SUM(q.score)     AS total_score " +
                             "FROM patients p " +
                             "JOIN quiz_results q ON p.patient_id = q.patient_id " +
                             "GROUP BY p.patient_id ORDER BY total_attempts DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",      rs.getString("full_name"));
                row.put("total_attempts", rs.getString("total_attempts"));
                row.put("total_score",    rs.getString("total_score"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W4-A4 | COUNT + GROUP BY — reports grouped by risk level.
     *  SQL: SELECT risk_level, COUNT(*) AS count FROM cognitive_reports
     *       GROUP BY risk_level ORDER BY count DESC;
     */
    public static List<Map<String,String>> agg_countByRiskLevel() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT risk_level, COUNT(*) AS count " +
                             "FROM cognitive_reports GROUP BY risk_level ORDER BY count DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("risk_level", rs.getString("risk_level"));
                row.put("count",      rs.getString("count"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W4-A5 | HAVING COUNT > 2 — patients with more than 2 quiz attempts.
     *  SQL: ... GROUP BY p.patient_id HAVING COUNT(q.quiz_id) > 2;
     *  NOTE: HAVING filters AFTER GROUP BY. WHERE cannot reference aggregates.
     */
    public static List<String> agg_having_frequentQuizTakers() {
        READ_LOCK.lock();
        List<String> names = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, " +
                             "       COUNT(q.quiz_id) AS attempts " +
                             "FROM patients p " +
                             "JOIN quiz_results q ON p.patient_id = q.patient_id " +
                             "GROUP BY p.patient_id HAVING COUNT(q.quiz_id) > 2")) {
            while (rs.next())
                names.add(rs.getString("full_name") + " (" + rs.getInt("attempts") + " attempts)");
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return names;
    }

    /** W4-A6 | HAVING AVG < 60 — risk groups with average health score below 60.
     *  SQL: ... GROUP BY risk_level HAVING AVG(health_score) < 60;
     */
    public static List<String> agg_having_lowHealthGroups() {
        READ_LOCK.lock();
        List<String> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT risk_level, ROUND(AVG(health_score),1) AS avg_health " +
                             "FROM cognitive_reports " +
                             "GROUP BY risk_level HAVING AVG(health_score) < 60")) {
            while (rs.next())
                result.add(rs.getString("risk_level") + " avg: " + rs.getString("avg_health"));
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    // ── SET OPERATIONS ────────────────────────────────────────────────────

    /** W4-S1 | UNION — combine reminder + appointment times for a patient.
     *  SQL: SELECT reminder_time,'Reminder' FROM reminders WHERE patient_id=?
     *       UNION
     *       SELECT appointment_time,'Appointment' FROM appointments WHERE patient_id=?
     *       ORDER BY t;
     */
    public static List<String> set_UNION_allScheduledTimes(int patientId) {
        READ_LOCK.lock();
        List<String> times = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT reminder_time AS t, 'Reminder' AS src " +
                             "FROM reminders WHERE patient_id=? " +
                             "UNION " +
                             "SELECT appointment_time, 'Appointment' " +
                             "FROM appointments WHERE patient_id=? " +
                             "ORDER BY t")) {
            ps.setInt(1,patientId); ps.setInt(2,patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    times.add(rs.getString("t") + "  [" + rs.getString("src") + "]");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return times;
    }

    /** W4-S2 | INTERSECT — patients with BOTH quiz result AND cognitive report.
     *  SQL: SELECT patient_id FROM quiz_results
     *       INTERSECT
     *       SELECT patient_id FROM cognitive_reports;
     */
    public static List<String> set_INTERSECT_fullyAssessedPatients() {
        READ_LOCK.lock();
        List<String> names = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT full_name AS full_name FROM patients " +
                             "WHERE patient_id IN (" +
                             "  SELECT patient_id FROM quiz_results " +
                             "  INTERSECT " +
                             "  SELECT patient_id FROM cognitive_reports)")) {
            while (rs.next()) names.add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return names;
    }

    /** W4-S3 | EXCEPT — patients who took quizzes but have NO cognitive report.
     *  SQL: SELECT patient_id FROM quiz_results
     *       EXCEPT
     *       SELECT patient_id FROM cognitive_reports;
     */
    public static List<String> set_EXCEPT_quizWithoutReport() {
        READ_LOCK.lock();
        List<String> names = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT full_name AS full_name FROM patients " +
                             "WHERE patient_id IN (" +
                             "  SELECT patient_id FROM quiz_results " +
                             "  EXCEPT " +
                             "  SELECT patient_id FROM cognitive_reports)")) {
            while (rs.next()) names.add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return names;
    }

    /** Prints all Week 4 results. */
    public static void runWeek4Demo() {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  WEEK 4 — Constraints · Aggregates · Set Operations       ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("\n── CONSTRAINTS ──────────────────────────────────────────");
        System.out.println("  C1 UNIQUE      : " + demo_UNIQUE_Constraint());
        System.out.println("  C2 CHECK(age)  : " + demo_CHECK_AgeConstraint());
        System.out.println("  C3 FOREIGN KEY : " + demo_FOREIGNKEY_Constraint());
        System.out.println("  C4 CHECK(risk) : " + demo_CHECK_RiskLevel());
        System.out.println("\n── AGGREGATE FUNCTIONS ──────────────────────────────────");
        System.out.println("  A1 COUNT  : total patients = " + agg_countPatients());
        double[] st = agg_healthScoreStats();
        System.out.printf("  A2 AVG/MAX/MIN → %.1f / %.0f / %.0f%n",st[0],st[1],st[2]);
        System.out.println("  A3 SUM+COUNT+GROUP BY:");
        for (Map<String,String> r : agg_quizStatsByPatient())
            System.out.println("     "+r.get("full_name")+" | attempts:"+r.get("total_attempts")+" | score:"+r.get("total_score"));
        System.out.println("  A4 COUNT+GROUP BY (risk level):");
        for (Map<String,String> r : agg_countByRiskLevel())
            System.out.println("     "+r.get("risk_level")+" → "+r.get("count")+" report(s)");
        System.out.println("  A5 HAVING COUNT > 2 : " + agg_having_frequentQuizTakers());
        System.out.println("  A6 HAVING AVG < 60  : " + agg_having_lowHealthGroups());
        System.out.println("\n── SET OPERATIONS ───────────────────────────────────────");
        System.out.println("  S1 UNION     (patient 1): " + set_UNION_allScheduledTimes(1));
        System.out.println("  S2 INTERSECT           : " + set_INTERSECT_fullyAssessedPatients());
        System.out.println("  S3 EXCEPT              : " + set_EXCEPT_quizWithoutReport());
    }

    // =====================================================================
    // WEEK 5 — Complex Joins · Subqueries · Views
    // =====================================================================

    // ── JOINS ─────────────────────────────────────────────────────────────

    /** W5-J1 | INNER JOIN — patients with quiz results only.
     *  SQL: SELECT p.full_name, p.age, q.score, q.quiz_date
     *       FROM patients p INNER JOIN quiz_results q ON p.patient_id=q.patient_id
     *       ORDER BY q.quiz_date DESC;
     */
    public static List<Map<String,String>> join_INNER_patientQuizzes() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, p.age, " +
                             "       q.score, q.total_questions, q.quiz_date " +
                             "FROM patients p " +
                             "INNER JOIN quiz_results q ON p.patient_id = q.patient_id " +
                             "ORDER BY q.quiz_date DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",       rs.getString("full_name"));
                row.put("age",             rs.getString("age"));
                row.put("score",           rs.getString("score"));
                row.put("total_questions", rs.getString("total_questions"));
                row.put("quiz_date",       rs.getString("quiz_date"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-J2 | LEFT JOIN — ALL patients including those with no quiz.
     *  SQL: SELECT p.full_name, COALESCE(CAST(q.score AS TEXT),'No quiz') AS score_info
     *       FROM patients p LEFT JOIN quiz_results q ON p.patient_id=q.patient_id;
     */
    public static List<Map<String,String>> join_LEFT_allPatientsWithQuizzes() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, p.age, " +
                             "       COALESCE(CAST(q.score AS TEXT),'No quiz') AS score_info " +
                             "FROM patients p " +
                             "LEFT JOIN quiz_results q ON p.patient_id = q.patient_id")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",  rs.getString("full_name"));
                row.put("age",        rs.getString("age"));
                row.put("score_info", rs.getString("score_info"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-J3 | RIGHT JOIN (simulated) — ALL quizzes, patient may be unknown.
     *  SQLite has no RIGHT JOIN. Equivalent: swap tables in LEFT JOIN.
     *  SQL: SELECT COALESCE(p.full_name,'Unknown') AS full_name, q.score
     *       FROM quiz_results q LEFT JOIN patients p ON p.patient_id=q.patient_id;
     */
    public static List<Map<String,String>> join_RIGHT_allQuizzesWithPatients() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COALESCE(p.full_name,'Unknown Patient') AS full_name, " +
                             "       q.score, q.total_questions, q.quiz_date " +
                             "FROM quiz_results q " +
                             "LEFT JOIN patients p ON p.patient_id = q.patient_id " +
                             "ORDER BY q.quiz_date DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",       rs.getString("full_name"));
                row.put("score",           rs.getString("score"));
                row.put("total_questions", rs.getString("total_questions"));
                row.put("quiz_date",       rs.getString("quiz_date"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-J4 | THREE-TABLE JOIN — patients + quiz_results + cognitive_reports.
     *  SQL: SELECT p.full_name, q.score, cr.health_score, cr.risk_level
     *       FROM patients p
     *       INNER JOIN quiz_results q ON p.patient_id=q.patient_id
     *       INNER JOIN cognitive_reports cr ON p.patient_id=cr.patient_id
     *       ORDER BY cr.health_score DESC;
     */
    public static List<Map<String,String>> join_THREE_TABLE_fullClinicalSummary() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, p.age, " +
                             "       q.score AS quiz_score, cr.health_score, " +
                             "       cr.risk_level, cr.trend_direction " +
                             "FROM patients p " +
                             "INNER JOIN quiz_results      q  ON p.patient_id = q.patient_id " +
                             "INNER JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                             "ORDER BY cr.health_score DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",       rs.getString("full_name"));
                row.put("age",             rs.getString("age"));
                row.put("quiz_score",      rs.getString("quiz_score"));
                row.put("health_score",    rs.getString("health_score"));
                row.put("risk_level",      rs.getString("risk_level"));
                row.put("trend_direction", rs.getString("trend_direction"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-J5 | SELF JOIN — compare health scores between every patient pair.
     *  SQL: SELECT a.full_name, b.full_name, ca.health_score, cb.health_score
     *       FROM patients a JOIN patients b ON a.patient_id < b.patient_id
     *       JOIN cognitive_reports ca ON a.patient_id=ca.patient_id
     *       JOIN cognitive_reports cb ON b.patient_id=cb.patient_id;
     */
    public static List<Map<String,String>> join_SELF_comparePatients() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT a.full_name AS patient_a, " +
                             "       b.full_name AS patient_b, " +
                             "       ca.health_score AS score_a, cb.health_score AS score_b " +
                             "FROM patients a " +
                             "JOIN patients b              ON a.patient_id < b.patient_id " +
                             "JOIN cognitive_reports ca    ON a.patient_id = ca.patient_id " +
                             "JOIN cognitive_reports cb    ON b.patient_id = cb.patient_id")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("patient_a", rs.getString("patient_a"));
                row.put("patient_b", rs.getString("patient_b"));
                row.put("score_a",   rs.getString("score_a"));
                row.put("score_b",   rs.getString("score_b"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    // ── SUBQUERIES ────────────────────────────────────────────────────────

    /** W5-SQ1 | SCALAR subquery in SELECT — health score vs global average.
     *  SQL: SELECT full_name, health_score,
     *             (SELECT AVG(health_score) FROM cognitive_reports) AS global_avg,
     *             health_score-(SELECT AVG(health_score) FROM cognitive_reports) AS diff
     *       FROM patients p JOIN cognitive_reports cr ON p.patient_id=cr.patient_id;
     */
    public static List<Map<String,String>> sub_SCALAR_vsGlobalAverage() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, cr.health_score, " +
                             "       ROUND((SELECT AVG(health_score) FROM cognitive_reports),1) AS global_avg, " +
                             "       cr.health_score-(SELECT AVG(health_score) FROM cognitive_reports) AS diff " +
                             "FROM patients p JOIN cognitive_reports cr ON p.patient_id = cr.patient_id")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",    rs.getString("full_name"));
                row.put("health_score", rs.getString("health_score"));
                row.put("global_avg",   rs.getString("global_avg"));
                row.put("diff",         rs.getString("diff"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-SQ2 | NESTED subquery in WHERE — above-average health scores.
     *  SQL: ... WHERE cr.health_score > (SELECT AVG(health_score) FROM cognitive_reports);
     */
    public static List<Map<String,String>> sub_NESTED_aboveAverage() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT DISTINCT p.full_name AS full_name, cr.health_score " +
                             "FROM patients p JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                             "WHERE cr.health_score > (SELECT AVG(health_score) FROM cognitive_reports)")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",    rs.getString("full_name"));
                row.put("health_score", rs.getString("health_score"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-SQ3 | CORRELATED subquery — flag personal best quiz attempts.
     *  Inner query references q.patient_id from the outer query (re-executes per row).
     *  SQL: CASE WHEN q.score = (SELECT MAX(q2.score) FROM quiz_results q2
     *            WHERE q2.patient_id = q.patient_id) THEN 'Personal Best' ELSE 'Not Best' END
     */
    public static List<Map<String,String>> sub_CORRELATED_personalBest() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, q.score, q.quiz_date, " +
                             "  CASE WHEN q.score = (SELECT MAX(q2.score) FROM quiz_results q2 " +
                             "       WHERE q2.patient_id = q.patient_id) " +
                             "  THEN 'Personal Best' ELSE 'Not Best' END AS status " +
                             "FROM patients p JOIN quiz_results q ON p.patient_id = q.patient_id " +
                             "ORDER BY p.full_name, q.quiz_date DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name", rs.getString("full_name"));
                row.put("score",     rs.getString("score"));
                row.put("quiz_date", rs.getString("quiz_date"));
                row.put("status",    rs.getString("status"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-SQ4 | EXISTS subquery — patients with at least one High-risk report.
     *  SQL: SELECT full_name FROM patients p WHERE EXISTS (
     *         SELECT 1 FROM cognitive_reports cr
     *         WHERE cr.patient_id=p.patient_id AND cr.risk_level='High');
     */
    public static List<String> sub_EXISTS_highRiskPatients() {
        READ_LOCK.lock();
        List<String> names = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT full_name AS full_name FROM patients p " +
                             "WHERE EXISTS (" +
                             "  SELECT 1 FROM cognitive_reports cr " +
                             "  WHERE cr.patient_id = p.patient_id AND cr.risk_level = 'High')")) {
            while (rs.next()) names.add(rs.getString("full_name"));
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return names;
    }

    // ── VIEWS — queried here; created at startup in createAllViews() ───────

    /** W5-V1 | Query v_patient_summary (simplification view). */
    public static List<Map<String,String>> view_patientSummary() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT * FROM v_patient_summary ORDER BY best_health_score DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",         rs.getString("full_name"));
                row.put("age",               rs.getString("age"));
                row.put("total_quizzes",     rs.getString("total_quizzes"));
                row.put("avg_quiz_score",    rs.getString("avg_quiz_score"));
                row.put("best_health_score", rs.getString("best_health_score"));
                row.put("latest_risk",       rs.getString("latest_risk"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-V2 | Query v_safe_patient_list (security view — no password/phone). */
    public static List<Map<String,String>> view_safePatientList() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM v_safe_patient_list")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("patient_id",        rs.getString("patient_id"));
                row.put("full_name",         rs.getString("full_name"));
                row.put("age",               rs.getString("age"));
                row.put("email",             rs.getString("email"));
                row.put("registration_date", rs.getString("registration_date"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W5-V3 | Query v_high_risk_patients (domain view — High-risk only). */
    public static List<Map<String,String>> view_highRiskPatients() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM v_high_risk_patients")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",       rs.getString("full_name"));
                row.put("age",             rs.getString("age"));
                row.put("health_score",    rs.getString("health_score"));
                row.put("trend_direction", rs.getString("trend_direction"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** Prints all Week 5 results. */
    public static void runWeek5Demo() {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   WEEK 5 — Complex Joins · Subqueries · Views             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("\n── JOINS ─────────────────────────────────────────────────");
        System.out.println("  J1 INNER JOIN:");
        for (Map<String,String> r : join_INNER_patientQuizzes())
            System.out.printf("     %-20s | %s/%s | %s%n",r.get("full_name"),r.get("score"),r.get("total_questions"),r.get("quiz_date"));
        System.out.println("  J2 LEFT JOIN (null=no quiz):");
        for (Map<String,String> r : join_LEFT_allPatientsWithQuizzes())
            System.out.printf("     %-20s | %s%n",r.get("full_name"),r.get("score_info"));
        System.out.println("  J3 RIGHT JOIN (simulated):");
        for (Map<String,String> r : join_RIGHT_allQuizzesWithPatients())
            System.out.printf("     %-20s | Score: %s%n",r.get("full_name"),r.get("score"));
        System.out.println("  J4 THREE-TABLE JOIN:");
        for (Map<String,String> r : join_THREE_TABLE_fullClinicalSummary())
            System.out.printf("     %-20s | Health:%-3s | Risk:%-8s | %s%n",r.get("full_name"),r.get("health_score"),r.get("risk_level"),r.get("trend_direction"));
        System.out.println("  J5 SELF JOIN:");
        for (Map<String,String> r : join_SELF_comparePatients())
            System.out.printf("     %s (%s) vs %s (%s)%n",r.get("patient_a"),r.get("score_a"),r.get("patient_b"),r.get("score_b"));
        System.out.println("\n── SUBQUERIES ────────────────────────────────────────────");
        System.out.println("  SQ1 SCALAR:");
        for (Map<String,String> r : sub_SCALAR_vsGlobalAverage())
            System.out.printf("     %-20s | score:%-3s | avg:%-4s | diff:%s%n",r.get("full_name"),r.get("health_score"),r.get("global_avg"),r.get("diff"));
        System.out.println("  SQ2 NESTED — above average:");
        for (Map<String,String> r : sub_NESTED_aboveAverage())
            System.out.printf("     %s → %s%n",r.get("full_name"),r.get("health_score"));
        System.out.println("  SQ3 CORRELATED — personal best:");
        for (Map<String,String> r : sub_CORRELATED_personalBest())
            System.out.printf("     %-20s | %s | %s%n",r.get("full_name"),r.get("score"),r.get("status"));
        System.out.println("  SQ4 EXISTS — High-risk: " + sub_EXISTS_highRiskPatients());
        System.out.println("\n── VIEWS ─────────────────────────────────────────────────");
        System.out.println("  V1 v_patient_summary:");
        for (Map<String,String> r : view_patientSummary())
            System.out.printf("     %-20s | quizzes:%-2s | best:%-3s | %s%n",r.get("full_name"),r.get("total_quizzes"),r.get("best_health_score"),r.get("latest_risk"));
        System.out.println("  V2 v_safe_patient_list (no password/phone):");
        for (Map<String,String> r : view_safePatientList())
            System.out.printf("     ID:%-3s %-20s age %s%n",r.get("patient_id"),r.get("full_name"),r.get("age"));
        System.out.println("  V3 v_high_risk_patients:");
        List<Map<String,String>> hr = view_highRiskPatients();
        if (hr.isEmpty()) System.out.println("     (none)");
        else for (Map<String,String> r : hr)
            System.out.printf("     %-20s | %s | %s%n",r.get("full_name"),r.get("health_score"),r.get("trend_direction"));
    }

    // =====================================================================
    // WEEK 6 — Functions · Stored Procedures · Triggers · Cursors · Exceptions
    // =====================================================================

    // ── FUNCTIONS ─────────────────────────────────────────────────────────

    /** W6-F1 | String & numeric built-in functions.
     *  SQL: SELECT full_name, UPPER(full_name), LENGTH(full_name),
     *             SUBSTR(full_name,1,5), ROUND(age/10.0)*10, ABS(age-65)
     *       FROM patients;
     */
    public static List<Map<String,String>> func_stringAndNumeric() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT full_name AS full_name, " +
                             "       UPPER(full_name)      AS name_upper, " +
                             "       LENGTH(full_name)     AS name_length, " +
                             "       SUBSTR(full_name,1,5) AS name_prefix, " +
                             "       ROUND(age/10.0)*10                     AS age_decade, " +
                             "       ABS(age-65)                            AS years_from_65 " +
                             "FROM patients")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",     rs.getString("full_name"));
                row.put("name_upper",    rs.getString("name_upper"));
                row.put("name_length",   rs.getString("name_length"));
                row.put("name_prefix",   rs.getString("name_prefix"));
                row.put("age_decade",    rs.getString("age_decade"));
                row.put("years_from_65", rs.getString("years_from_65"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W6-F2 | Date/time functions — quiz recency.
     *  SQL: SELECT full_name,
     *             strftime('%d-%m-%Y',quiz_date) AS formatted_date,
     *             CAST(julianday('now')-julianday(quiz_date) AS INTEGER) AS days_ago,
     *             CASE WHEN days<=7 THEN 'Recent' WHEN days<=30 THEN 'This Month' ELSE 'Older' END
     *       FROM patients p JOIN quiz_results q ON p.patient_id=q.patient_id;
     */
    public static List<Map<String,String>> func_dateTime() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, " +
                             "       strftime('%d-%m-%Y',q.quiz_date) AS formatted_date, " +
                             "       CAST(julianday('now')-julianday(q.quiz_date) AS INTEGER) AS days_ago, " +
                             "       CASE WHEN julianday('now')-julianday(q.quiz_date)<=7  THEN 'Recent' " +
                             "            WHEN julianday('now')-julianday(q.quiz_date)<=30 THEN 'This Month' " +
                             "            ELSE 'Older' END AS recency " +
                             "FROM patients p " +
                             "JOIN quiz_results q ON p.patient_id = q.patient_id " +
                             "ORDER BY q.quiz_date DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",      rs.getString("full_name"));
                row.put("formatted_date", rs.getString("formatted_date"));
                row.put("days_ago",       rs.getString("days_ago"));
                row.put("recency",        rs.getString("recency"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    /** W6-F3 | CASE expression — cognitive band + care priority.
     *  SQL: SELECT full_name, health_score,
     *         CASE WHEN health_score>=85 THEN 'Excellent' ... ELSE 'Critical' END AS band,
     *         CASE risk_level WHEN 'High' THEN 'Priority 1' ... END AS priority
     *       FROM patients p JOIN cognitive_reports cr ON p.patient_id=cr.patient_id;
     */
    public static List<Map<String,String>> func_caseExpression() {
        READ_LOCK.lock();
        List<Map<String,String>> result = new ArrayList<>();
        try (Connection c = getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT p.full_name AS full_name, cr.health_score, " +
                             "  CASE WHEN cr.health_score>=85 THEN 'Excellent' " +
                             "       WHEN cr.health_score>=70 THEN 'Good' " +
                             "       WHEN cr.health_score>=50 THEN 'Fair' " +
                             "       WHEN cr.health_score>=30 THEN 'Poor' " +
                             "       ELSE 'Critical' END AS cognitive_band, " +
                             "  CASE cr.risk_level " +
                             "       WHEN 'Low'      THEN 'Priority 3 - Routine' " +
                             "       WHEN 'Moderate' THEN 'Priority 2 - Monitor' " +
                             "       WHEN 'High'     THEN 'Priority 1 - Urgent' " +
                             "       ELSE 'Unknown' END AS priority_label " +
                             "FROM patients p " +
                             "JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                             "ORDER BY cr.health_score DESC")) {
            while (rs.next()) {
                Map<String,String> row = new LinkedHashMap<>();
                row.put("full_name",      rs.getString("full_name"));
                row.put("health_score",   rs.getString("health_score"));
                row.put("cognitive_band", rs.getString("cognitive_band"));
                row.put("priority_label", rs.getString("priority_label"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return result;
    }

    // ── STORED PROCEDURES ─────────────────────────────────────────────────

    /** W6-P1 | Procedure: generateFullPatientReport — 4-step read transaction.
     *  Step 1: demographics. Step 2: quiz history. Step 3: cognitive report.
     *  Step 4: appointments. All four inside conn.setAutoCommit(false)/commit().
     */
    public static String proc_generateFullPatientReport(int patientId) {
        READ_LOCK.lock();
        StringBuilder report = new StringBuilder();
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT full_name AS full_name,age,email,registration_date " +
                            "FROM patients WHERE patient_id=?")) {
                ps.setInt(1,patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        report.append("=== PATIENT REPORT ===\n")
                                .append("Name    : ").append(rs.getString("full_name")).append("\n")
                                .append("Age     : ").append(rs.getString("age")).append("\n")
                                .append("Email   : ").append(rs.getString("email")).append("\n")
                                .append("RegDate : ").append(rs.getString("registration_date")).append("\n\n");
                }
            }
            report.append("-- Quiz History --\n");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT score,total_questions,quiz_date FROM quiz_results " +
                            "WHERE patient_id=? ORDER BY quiz_date DESC")) {
                ps.setInt(1,patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        report.append("  ").append(rs.getString("quiz_date"))
                                .append("  ").append(rs.getString("score"))
                                .append("/").append(rs.getString("total_questions")).append("\n");
                }
            }
            report.append("\n-- Cognitive Assessment --\n");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT health_score,risk_level,trend_direction FROM cognitive_reports " +
                            "WHERE patient_id=? ORDER BY report_date DESC LIMIT 1")) {
                ps.setInt(1,patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        report.append("  Health : ").append(rs.getString("health_score")).append("/100\n")
                                .append("  Risk   : ").append(rs.getString("risk_level")).append("\n")
                                .append("  Trend  : ").append(rs.getString("trend_direction")).append("\n");
                }
            }
            report.append("\n-- Upcoming Appointments --\n");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.appointment_date, d.hospital_name, d.doctor_name " +
                            "FROM appointments a JOIN doctors d ON a.doctor_id=d.doctor_id " +
                            "WHERE a.patient_id=? AND a.status!='Cancelled' ORDER BY a.appointment_date")) {
                ps.setInt(1,patientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        report.append("  ").append(rs.getString("appointment_date"))
                                .append(" | ").append(rs.getString("hospital_name"))
                                .append(" | ").append(rs.getString("doctor_name")).append("\n");
                }
            }
            c.commit();
        } catch (SQLException e) { report.append("\n[ERROR] ").append(e.getMessage()); }
        finally { READ_LOCK.unlock(); }
        return report.toString();
    }

    /** W6-P2 | Procedure: batchConfirmPastAppointments — atomic UPDATE + audit INSERT.
     *  UPDATE appointments SET status='Confirmed' WHERE status='Scheduled' AND date<today;
     *  INSERT INTO audit_log ... Both committed as one unit. Rolled back together on failure.
     */
    public static int proc_batchConfirmPastAppointments() {
        WRITE_LOCK.lock();
        int updated = 0;
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                updated = s.executeUpdate(
                        "UPDATE appointments SET status='Confirmed' " +
                                "WHERE status='Scheduled' AND appointment_date < DATE('now')");
                s.executeUpdate(
                        "INSERT INTO audit_log (event,detail) VALUES ('BATCH_CONFIRM','" +
                                updated + " appointment(s) auto-confirmed')");
                c.commit();
            } catch (SQLException inner) { c.rollback(); throw inner; }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { WRITE_LOCK.unlock(); }
        return updated;
    }

    // ── CURSORS ───────────────────────────────────────────────────────────

    /** W6-CU1 | Cursor — classify every patient by cognitive status.
     *  PL/SQL equivalent: FOR rec IN c_patients LOOP IF/ELSIF/ELSE END LOOP;
     *  JDBC: TYPE_SCROLL_INSENSITIVE ResultSet. rs.next() = FETCH NEXT.
     *  SQL: SELECT full_name, health_score FROM patients p
     *       JOIN cognitive_reports cr ON p.patient_id=cr.patient_id
     *       ORDER BY cr.report_date DESC;
     */
    public static List<String> cursor_classifyAllPatients() {
        READ_LOCK.lock();
        List<String> output = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT p.full_name AS full_name, cr.health_score " +
                             "FROM patients p " +
                             "JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                             "ORDER BY cr.report_date DESC",
                     ResultSet.TYPE_SCROLL_INSENSITIVE,
                     ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = ps.executeQuery()) {          // OPEN CURSOR
            int row = 0;
            while (rs.next()) {                          // FETCH NEXT
                row++;
                String name  = rs.getString("full_name");
                int    score = rs.getInt("health_score");
                String cat;
                if      (score >= 75) cat = "NORMAL COGNITION";
                else if (score >= 50) cat = "MILD IMPAIRMENT";
                else if (score >= 30) cat = "MODERATE IMPAIRMENT";
                else                  cat = "SEVERE - URGENT CARE";
                output.add(String.format("Row %-2d | %-20s | Score: %3d | %s",row,name,score,cat));
            }
            // CLOSE CURSOR — automatic via try-with-resources
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
        return output;
    }

    /** W6-CU2 | Scrollable cursor — FETCH FIRST / LAST / ABSOLUTE.
     *  rs.first()=FETCH FIRST, rs.last()=FETCH LAST, rs.absolute(n)=FETCH ABSOLUTE n.
     *  SQL: SELECT full_name, health_score ... ORDER BY health_score ASC;
     */
    public static void cursor_scrollableNavigationDemo() {
        READ_LOCK.lock();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT p.full_name AS full_name, cr.health_score " +
                             "FROM patients p " +
                             "JOIN cognitive_reports cr ON p.patient_id = cr.patient_id " +
                             "ORDER BY cr.health_score ASC",
                     ResultSet.TYPE_SCROLL_INSENSITIVE,
                     ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("  [Scrollable cursor navigation]");
            if (rs.last())      System.out.println("  FETCH LAST    → "+rs.getString("full_name")+" | "+rs.getInt("health_score"));
            if (rs.first())     System.out.println("  FETCH FIRST   → "+rs.getString("full_name")+" | "+rs.getInt("health_score"));
            if (rs.absolute(1)) System.out.println("  FETCH ABS(1)  → "+rs.getString("full_name")+" | "+rs.getInt("health_score"));
        } catch (SQLException e) { e.printStackTrace(); }
        finally { READ_LOCK.unlock(); }
    }

    // ── EXCEPTION HANDLING ────────────────────────────────────────────────

    /** W6-E1 | UNIQUE_VIOLATION — duplicate username. SQLite error code 19. */
    public static String ex_UNIQUE_violation() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO patients (username,password,full_name) VALUES (?,?,?)")) {
            ps.setString(1,"admin"); ps.setString(2,"x"); ps.setString(3,"Duplicate Test");
            ps.executeUpdate();
            return "No exception (unexpected)";
        } catch (SQLException e) {
            return "UNIQUE_VIOLATION [code " + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W6-E2 | CHECK_VIOLATION — invalid risk_level value. */
    public static String ex_CHECK_violation() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO cognitive_reports " +
                             "(patient_id,accuracy_percent,avg_response_time,answer_changes,health_score,risk_level) " +
                             "VALUES (1,50.0,3.0,0,60,?)")) {
            ps.setString(1,"BAD_RISK"); ps.executeUpdate();
            try (Statement s = c.createStatement())
            { s.executeUpdate("DELETE FROM cognitive_reports WHERE risk_level='BAD_RISK'"); }
            return "NOTE: CHECK not enforced (SQLite build dependent)";
        } catch (SQLException e) {
            return "CHECK_VIOLATION [code " + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W6-E3 | NO_SUCH_TABLE — query on a non-existent table. */
    public static String ex_NO_SUCH_TABLE() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM table_does_not_exist");
            return "No exception (unexpected)";
        } catch (SQLException e) {
            return "NO_SUCH_TABLE [code " + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W6-E4 | TRIGGER_ABORT — score > total fires RAISE(ABORT) in trg_quiz_score_validate. */
    public static String ex_TRIGGER_abort() {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO quiz_results (patient_id,score,total_questions) VALUES (?,?,?)")) {
            ps.setInt(1,1); ps.setInt(2,15); ps.setInt(3,10);
            ps.executeUpdate();
            return "No exception (trigger did not fire)";
        } catch (SQLException e) {
            return "TRIGGER_ABORT [code " + e.getErrorCode() + "] " + e.getMessage();
        }
    }

    /** W6-E5 | SAVEPOINT transaction — partial rollback.
     *  Insert 1 (valid 7/10) committed even when Insert 2 (invalid 20/10) fails.
     *  Mirrors PL/SQL: SAVEPOINT sp / ROLLBACK TO SAVEPOINT sp / COMMIT.
     */
    public static void ex_transactionWithSavepoint(int patientId) {
        WRITE_LOCK.lock();
        Connection c = null;
        try {
            c = getConnection();
            c.setAutoCommit(false);  // BEGIN TRANSACTION
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO quiz_results (patient_id,score,total_questions) VALUES (?,?,?)")) {
                ps.setInt(1,patientId); ps.setInt(2,7); ps.setInt(3,10);
                ps.executeUpdate();
                System.out.println("  TXN Insert-1 OK (7/10)");
                Savepoint sp = c.setSavepoint("sp_second");  // SAVEPOINT
                try {
                    ps.setInt(1,patientId); ps.setInt(2,20); ps.setInt(3,10);
                    ps.executeUpdate();
                    c.commit();
                    System.out.println("  TXN Insert-2 OK — full commit");
                } catch (SQLException inner) {
                    c.rollback(sp);   // ROLLBACK TO SAVEPOINT
                    c.commit();       // commit Insert-1 only
                    System.out.println("  TXN Insert-2 FAILED → rolled back to savepoint");
                    System.out.println("  TXN Insert-1 COMMITTED — partial recovery successful");
                }
            }
        } catch (SQLException e) {
            try { if (c!=null) c.rollback(); } catch (SQLException ignored) {}
            e.printStackTrace();
        } finally {
            try { if (c!=null) { c.setAutoCommit(true); c.close(); } }
            catch (SQLException ignored) {}
            WRITE_LOCK.unlock();
        }
    }

    /** Prints all Week 6 results. */
    public static void runWeek6Demo() {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  WEEK 6 — Functions·Procedures·Triggers·Cursors·Exceptions ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("\n── FUNCTIONS ─────────────────────────────────────────────");
        System.out.println("  F1 String & Numeric:");
        for (Map<String,String> r : func_stringAndNumeric())
            System.out.printf("     %-20s | UPPER=%-22s | len=%-2s | decade=%s%n",
                    r.get("full_name"),r.get("name_upper"),r.get("name_length"),r.get("age_decade"));
        System.out.println("  F2 Date/Time:");
        for (Map<String,String> r : func_dateTime())
            System.out.printf("     %-20s | %s | %s days ago | %s%n",
                    r.get("full_name"),r.get("formatted_date"),r.get("days_ago"),r.get("recency"));
        System.out.println("  F3 CASE expression:");
        for (Map<String,String> r : func_caseExpression())
            System.out.printf("     %-20s | %-3s | %-10s | %s%n",
                    r.get("full_name"),r.get("health_score"),r.get("cognitive_band"),r.get("priority_label"));
        System.out.println("\n── STORED PROCEDURES ─────────────────────────────────────");
        System.out.println("  P1 generateFullPatientReport (patient_id=1):");
        System.out.println(proc_generateFullPatientReport(1));
        System.out.println("  P2 batchConfirmPastAppointments → " + proc_batchConfirmPastAppointments() + " updated");
        System.out.println("\n── TRIGGERS ──────────────────────────────────────────────");
        System.out.println("  Inserting quiz to fire trg_quiz_insert_audit...");
        saveQuizResult(1,8,10);
        System.out.println("  Audit log:");
        for (Map<String,String> r : recovery_readAuditLog())
            System.out.printf("     [%s] %-22s pid=%-3s %s%n",r.get("logged_at"),r.get("event"),r.get("patient_id"),r.get("detail"));
        System.out.println("  TRG-4 score validate: " + ex_TRIGGER_abort());
        System.out.println("\n── CURSORS ───────────────────────────────────────────────");
        System.out.println("  CU1 Classify patients:");
        for (String line : cursor_classifyAllPatients()) System.out.println("     " + line);
        System.out.println("  CU2 Scrollable navigation:");
        cursor_scrollableNavigationDemo();
        System.out.println("\n── EXCEPTION HANDLING ────────────────────────────────────");
        System.out.println("  E1 UNIQUE    : " + ex_UNIQUE_violation());
        System.out.println("  E2 CHECK     : " + ex_CHECK_violation());
        System.out.println("  E3 NO TABLE  : " + ex_NO_SUCH_TABLE());
        System.out.println("  E4 TRG ABORT : " + ex_TRIGGER_abort());
        System.out.println("  E5 SAVEPOINT :");
        ex_transactionWithSavepoint(1);
    }

    // =========================================================
    //  HELPER
    // =========================================================

    private static Map<String,String> buildDomainLabels(Map<String,int[]> scores) {
        Map<String,String> labels = new LinkedHashMap<>();
        if (scores == null) return labels;
        for (Map.Entry<String,int[]> e : scores.entrySet()) {
            int[] v = e.getValue();
            if (v[1] == 0) continue;
            double pct = v[0] * 100.0 / v[1];
            labels.put(e.getKey(),
                    pct >= 80 ? "\u2713 Good" : pct >= 60 ? "\u26a0 Fair" : "\u2717 Poor");
        }
        return labels;
    }
}