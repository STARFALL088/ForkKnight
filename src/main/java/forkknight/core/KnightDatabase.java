package forkknight.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple SQLite wrapper that provides the tables required by {@link KnightMemory}.
 * The schema is created lazily on first connection.
 */
public final class KnightDatabase {

    private final String dbUrl;

    public KnightDatabase() {
        this("jdbc:sqlite:" + System.getProperty("user.home") + "/.forkknight/forkknight.db");
    }

    public KnightDatabase(String dbUrl) {
        this.dbUrl = dbUrl;
        if (dbUrl.startsWith("jdbc:sqlite:") && !dbUrl.equals("jdbc:sqlite::memory:")) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(dbUrl.substring("jdbc:sqlite:".length()));
                if (path.getParent() != null) {
                    java.nio.file.Files.createDirectories(path.getParent());
                }
            } catch (Exception e) {
                // ignore
            }
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Settings table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            // Notes table (hash, note)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS notes (hash TEXT PRIMARY KEY, note TEXT NOT NULL)");
            // Realm bookmarks table (realm_path, name)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS realm_bookmarks (realm_path TEXT PRIMARY KEY, name TEXT NOT NULL)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise KnightDatabase", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    // -------------------- Settings --------------------
    public Map<String, String> getAllSettings() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT key, value FROM settings";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    public Optional<String> getSetting(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public void setSetting(String key, String value) {
        String sql = "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteSetting(String key) {
        String sql = "DELETE FROM settings WHERE key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------- Notes --------------------
    public Optional<String> getNote(String hash) {
        String sql = "SELECT note FROM notes WHERE hash = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("note"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public void setNote(String hash, String note) {
        String sql = "INSERT INTO notes (hash, note) VALUES (?, ?) ON CONFLICT(hash) DO UPDATE SET note = excluded.note";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteNote(String hash) {
        String sql = "DELETE FROM notes WHERE hash = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------- Realm Bookmarks --------------------
    public Optional<String> getBookmarkName(String realmPath) {
        String sql = "SELECT name FROM realm_bookmarks WHERE realm_path = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realmPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public void setBookmark(String realmPath, String name) {
        String sql = "INSERT INTO realm_bookmarks (realm_path, name) VALUES (?, ?) ON CONFLICT(realm_path) DO UPDATE SET name = excluded.name";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realmPath);
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteBookmark(String realmPath) {
        String sql = "DELETE FROM realm_bookmarks WHERE realm_path = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realmPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> getAllBookmarks() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT realm_path, name FROM realm_bookmarks";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("realm_path"), rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return map;
    }
}
