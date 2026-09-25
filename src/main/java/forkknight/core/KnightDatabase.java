package forkknight.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite wrapper for the Knight's Ledger (~/.forkknight/forkknight.db).
 *
 * <p>Every change to the schema is an entry in {@link #MIGRATIONS}; versions are
 * recorded with {@code PRAGMA user_version} and applied once, in order, inside a
 * single transaction. The connection is opened once and reused for the lifetime
 * of this object (WAL journal, foreign keys on, a five second busy timeout),
 * with all access serialized on an internal lock.
 */
public final class KnightDatabase {

    /** One ledger change per version, applied in order. */
    @FunctionalInterface
    private interface Migration {
        void run(Connection conn) throws SQLException;
    }

    private static final List<Migration> MIGRATIONS = List.of(
        // v1 - the original ledger: settings, notes, realm bookmarks
        conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS notes (hash TEXT PRIMARY KEY, note TEXT NOT NULL)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS realm_bookmarks (realm_path TEXT PRIMARY KEY, name TEXT NOT NULL)");
            }
        }
    );

    private static final String DEFAULT_URL =
        "jdbc:sqlite:" + System.getProperty("user.home") + "/.forkknight/forkknight.db";

    private final String dbUrl;
    private final Object lock = new Object();
    private Connection conn;

    public KnightDatabase() {
        this(DEFAULT_URL);
    }

    public KnightDatabase(String dbUrl) {
        this.dbUrl = dbUrl;
        createParentDirectory();
        synchronized (lock) {
            ensureOpen();
        }
    }

    /** Closes the shared connection; later calls reopen it on demand. */
    public void close() {
        synchronized (lock) {
            closeQuietly(conn);
            conn = null;
        }
    }

    private void createParentDirectory() {
        if (!dbUrl.startsWith("jdbc:sqlite:") || dbUrl.equals("jdbc:sqlite::memory:")) {
            return;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(dbUrl.substring("jdbc:sqlite:".length()));
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
        } catch (Exception e) {
            // A missing directory still fails later, with a clear SQL error.
        }
    }

    private Connection connect() throws SQLException {
        Connection opened = DriverManager.getConnection(dbUrl);
        try (Statement stmt = opened.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        return opened;
    }

    private void ensureOpen() {
        try {
            if (conn != null && conn.isValid(2)) {
                return;
            }
        } catch (SQLException e) {
            // Fall through and reopen.
        }
        closeQuietly(conn);
        try {
            conn = connect();
            migrate(conn);
        } catch (SQLException e) {
            closeQuietly(conn);
            conn = null;
            throw new KnightDbException("Failed to initialise the Knight's Ledger", e);
        }
    }

    private void migrate(Connection conn) throws SQLException {
        int current = userVersion(conn);
        int target = MIGRATIONS.size();
        if (current >= target) {
            return;
        }
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int version = current; version < target; version++) {
                MIGRATIONS.get(version).run(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA user_version=" + (version + 1));
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new KnightDbException("Ledger migration failed from version " + current, e);
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private int userVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while closing.
        }
    }

    // -------------------- Settings --------------------
    public Map<String, String> getAllSettings() {
        synchronized (lock) {
            ensureOpen();
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT key, value FROM settings";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    map.put(rs.getString("key"), rs.getString("value"));
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read settings", e);
            }
            return map;
        }
    }

    public Optional<String> getSetting(String key) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT value FROM settings WHERE key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read setting " + key, e);
            }
            return Optional.empty();
        }
    }

    public void setSetting(String key, String value) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO settings (key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store setting " + key, e);
            }
        }
    }

    public void deleteSetting(String key) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM settings WHERE key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete setting " + key, e);
            }
        }
    }

    // -------------------- Notes --------------------
    public Optional<String> getNote(String hash) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT note FROM notes WHERE hash = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("note"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read the note for " + hash, e);
            }
            return Optional.empty();
        }
    }

    public void setNote(String hash, String note) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO notes (hash, note) VALUES (?, ?) "
                + "ON CONFLICT(hash) DO UPDATE SET note = excluded.note";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hash);
                ps.setString(2, note);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store the note for " + hash, e);
            }
        }
    }

    public void deleteNote(String hash) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM notes WHERE hash = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete the note for " + hash, e);
            }
        }
    }

    // -------------------- Realm Bookmarks --------------------
    public Optional<String> getBookmarkName(String realmPath) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT name FROM realm_bookmarks WHERE realm_path = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, realmPath);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read the bookmark for " + realmPath, e);
            }
            return Optional.empty();
        }
    }

    public void setBookmark(String realmPath, String name) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO realm_bookmarks (realm_path, name) VALUES (?, ?) "
                + "ON CONFLICT(realm_path) DO UPDATE SET name = excluded.name";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, realmPath);
                ps.setString(2, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store the bookmark for " + realmPath, e);
            }
        }
    }

    public void deleteBookmark(String realmPath) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM realm_bookmarks WHERE realm_path = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, realmPath);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete the bookmark for " + realmPath, e);
            }
        }
    }

    public Map<String, String> getAllBookmarks() {
        synchronized (lock) {
            ensureOpen();
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT realm_path, name FROM realm_bookmarks";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    map.put(rs.getString("realm_path"), rs.getString("name"));
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read the bookmarks", e);
            }
            return map;
        }
    }
}
