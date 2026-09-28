package forkknight.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
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
 *
 * <p>Since migration v2 every row belongs to an {@link Account}: settings, notes
 * and realm bookmarks are addressed by <em>user id + key</em>, so two knights
 * can share a realm, a note or a window size without ever colliding.
 */
public final class KnightDatabase {

    /** One ledger change per version, applied in order. */
    @FunctionalInterface
    private interface Migration {
        void run(Connection conn) throws SQLException;
    }

    private static final String KEEPER = "keeper";
    private static final String KEEPER_TITLE = "Keeper of the Ledger";
    private static final String GUEST = "guest";
    private static final String GUEST_TITLE = "Wandering Guest";

    private static final List<Migration> MIGRATIONS = List.of(
        // v1 - the original ledger: settings, notes, realm bookmarks
        conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS notes (hash TEXT PRIMARY KEY, note TEXT NOT NULL)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS realm_bookmarks (realm_path TEXT PRIMARY KEY, name TEXT NOT NULL)");
            }
        },
        // v2 - accounts; every existing row is adopted by the keeper
        conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                    + "display_name TEXT NOT NULL, "
                    + "password_hash TEXT NOT NULL, "
                    + "is_guest INTEGER NOT NULL DEFAULT 0, "
                    + "created_at TEXT NOT NULL)");

                long keeper = findKeeper(stmt);
                if (keeper < 0) {
                    stmt.executeUpdate("INSERT INTO users "
                        + "(username, display_name, password_hash, is_guest, created_at) VALUES ('"
                        + KEEPER + "', '" + KEEPER_TITLE + "', '" + lockedPasswordHash() + "', 0, '"
                        + Instant.now() + "')");
                    keeper = findKeeper(stmt);
                }

                // Composite keys: (user_id, key) so accounts never share a row.
                scopeTable(conn, keeper, "settings",
                    "CREATE TABLE settings (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (user_id, key))",
                    "user_id, key, value", "key, value");
                scopeTable(conn, keeper, "notes",
                    "CREATE TABLE notes (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "hash TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY (user_id, hash))",
                    "user_id, hash, note", "hash, note");
                scopeTable(conn, keeper, "realm_bookmarks",
                    "CREATE TABLE realm_bookmarks (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "realm_path TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY (user_id, realm_path))",
                    "user_id, realm_path, name", "realm_path, name");
            }
        },
        // v3 - app_state: facts about the ledger itself, not about any one knight
        conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS app_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            }
        },
        // v4 - the knight's own page: profile columns on every account
        conn -> {
            try (Statement stmt = conn.createStatement()) {
                if (!columnExists(stmt, "users", "bio")) {
                    stmt.executeUpdate("ALTER TABLE users ADD COLUMN bio TEXT NOT NULL DEFAULT ''");
                }
                if (!columnExists(stmt, "users", "title")) {
                    stmt.executeUpdate("ALTER TABLE users ADD COLUMN title TEXT NOT NULL DEFAULT 'Squire'");
                }
            }
        }
    );

    private static final String DEFAULT_URL =
        "jdbc:sqlite:" + System.getProperty("user.home") + "/.forkknight/forkknight.db";

    /** The version a fully migrated ledger reports (tests assert against it). */
    static int schemaVersion() {
        return MIGRATIONS.size();
    }

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

    // -------------------- Accounts --------------------

    /** Reads an account by primary key. */
    public Optional<Account> findUserById(long id) {
        synchronized (lock) {
            ensureOpen();
            return queryAccount("SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE id = ?", ps -> ps.setLong(1, id));
        }
    }

    /** Reads an account by name; matching ignores case, as it should. */
    public Optional<Account> findUserByUsername(String username) {
        synchronized (lock) {
            ensureOpen();
            return queryAccount("SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE username = ?",
                ps -> ps.setString(1, username));
        }
    }

    /** Every registered knight, by name. */
    public List<Account> listUsers() {
        synchronized (lock) {
            ensureOpen();
            List<Account> accounts = new ArrayList<>();
            String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM users ORDER BY username COLLATE NOCASE, id";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(readAccount(rs));
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read the knights", e);
            }
            return accounts;
        }
    }

    /** True when a knight of that name already rides (case-insensitive). */
    public boolean usernameExists(String username) {
        return findUserByUsername(username).isPresent();
    }

    /** Registers a knight, or raises a ledger failure when the name is taken. */
    public Account createUser(String username, String displayName, String passwordHash, boolean guest) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO users (username, display_name, password_hash, is_guest, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
            String name = displayName == null || displayName.isBlank() ? username : displayName;
            String hash = passwordHash == null ? "" : passwordHash;
            String createdAt = Instant.now().toString();
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, name);
                ps.setString(3, hash);
                ps.setBoolean(4, guest);
                ps.setString(5, createdAt);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Account(keys.getLong(1), username, name, hash, guest, createdAt);
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                    throw new KnightDbException("A knight named '" + username + "' already rides", e);
                }
                throw new KnightDbException("Could not register " + username, e);
            }
            return findUserByUsername(username)
                .orElseThrow(() -> new KnightDbException("Could not read back " + username));
        }
    }

    /** The passwordless wanderer; created on first ask. */
    public Account guestAccount() {
        synchronized (lock) {
            ensureOpen();
            Optional<Account> existing = findUserByUsername(GUEST);
            if (existing.isPresent()) {
                return existing.get();
            }
            return createUser(GUEST, GUEST_TITLE, "", true);
        }
    }

    /** Replaces a knight's password hash (including the keeper's lock). */
    public void setPassword(long userId, String passwordHash) {
        synchronized (lock) {
            ensureOpen();
            String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, passwordHash);
                ps.setLong(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store the password", e);
            }
        }
    }

    /** Removes a knight; settings, notes and bookmarks fall with them (CASCADE). */
    public void deleteUser(long userId) {
        synchronized (lock) {
            ensureOpen();
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not dismiss the knight", e);
            }
        }
    }

    // -------------------- The knight's own page --------------------

    /** Writes a knight's display name, bio and title (UPDATE). */
    public void updateProfile(long userId, String displayName, String bio, String title) {
        synchronized (lock) {
            ensureOpen();
            String sql = "UPDATE users SET display_name = ?, bio = ?, title = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, displayName);
                ps.setString(2, bio);
                ps.setString(3, title);
                ps.setLong(4, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not seal the knight's profile", e);
            }
        }
    }

    /** How many notes and realm bookmarks belong to this knight (SELECT COUNT). */
    public ProfileStats profileStats(long userId) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT "
                + "(SELECT COUNT(*) FROM notes WHERE user_id = ?) AS notes, "
                + "(SELECT COUNT(*) FROM realm_bookmarks WHERE user_id = ?) AS bookmarks";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setLong(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new ProfileStats(rs.getLong("notes"), rs.getLong("bookmarks"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not count the knight's keepsakes", e);
            }
            return new ProfileStats(0, 0);
        }
    }

    /** The keepsakes a knight has gathered: counts of notes and bookmarks. */
    public record ProfileStats(long notes, long bookmarks) {
    }

    // -------------------- App state (not owned by any one knight) --------------------

    public Optional<String> getGlobal(String key) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT value FROM app_state WHERE key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read ledger state " + key, e);
            }
            return Optional.empty();
        }
    }

    public void setGlobal(String key, String value) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO app_state (key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store ledger state " + key, e);
            }
        }
    }

    // -------------------- Settings --------------------
    public Map<String, String> getAllSettings(long userId) {
        synchronized (lock) {
            ensureOpen();
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT key, value FROM settings WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString("key"), rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read settings", e);
            }
            return map;
        }
    }

    public Optional<String> getSetting(long userId, String key) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT value FROM settings WHERE user_id = ? AND key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, key);
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

    public void setSetting(long userId, String key, String value) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO settings (user_id, key, value) VALUES (?, ?, ?) "
                + "ON CONFLICT(user_id, key) DO UPDATE SET value = excluded.value";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, key);
                ps.setString(3, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store setting " + key, e);
            }
        }
    }

    public void deleteSetting(long userId, String key) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM settings WHERE user_id = ? AND key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, key);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete setting " + key, e);
            }
        }
    }

    // -------------------- Notes --------------------
    public Optional<String> getNote(long userId, String hash) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT note FROM notes WHERE user_id = ? AND hash = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, hash);
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

    public void setNote(long userId, String hash, String note) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO notes (user_id, hash, note) VALUES (?, ?, ?) "
                + "ON CONFLICT(user_id, hash) DO UPDATE SET note = excluded.note";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, hash);
                ps.setString(3, note);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store the note for " + hash, e);
            }
        }
    }

    public void deleteNote(long userId, String hash) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM notes WHERE user_id = ? AND hash = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, hash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete the note for " + hash, e);
            }
        }
    }

    // -------------------- Realm Bookmarks --------------------
    public Optional<String> getBookmarkName(long userId, String realmPath) {
        synchronized (lock) {
            ensureOpen();
            String sql = "SELECT name FROM realm_bookmarks WHERE user_id = ? AND realm_path = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, realmPath);
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

    public void setBookmark(long userId, String realmPath, String name) {
        synchronized (lock) {
            ensureOpen();
            String sql = "INSERT INTO realm_bookmarks (user_id, realm_path, name) VALUES (?, ?, ?) "
                + "ON CONFLICT(user_id, realm_path) DO UPDATE SET name = excluded.name";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, realmPath);
                ps.setString(3, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not store the bookmark for " + realmPath, e);
            }
        }
    }

    public void deleteBookmark(long userId, String realmPath) {
        synchronized (lock) {
            ensureOpen();
            String sql = "DELETE FROM realm_bookmarks WHERE user_id = ? AND realm_path = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, realmPath);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new KnightDbException("Could not delete the bookmark for " + realmPath, e);
            }
        }
    }

    public Map<String, String> getAllBookmarks(long userId) {
        synchronized (lock) {
            ensureOpen();
            Map<String, String> map = new HashMap<>();
            String sql = "SELECT realm_path, name FROM realm_bookmarks WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString("realm_path"), rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                throw new KnightDbException("Could not read the bookmarks", e);
            }
            return map;
        }
    }

    // -------------------- Internals --------------------

    private static final String ACCOUNT_COLUMNS =
        "id, username, display_name, password_hash, is_guest, created_at";

    /** True when the table already carries the column (idempotent upgrades). */
    private static boolean columnExists(Statement stmt, String table, String column)
            throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<Account> queryAccount(String sql, Binder binder) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readAccount(rs));
                }
            }
        } catch (SQLException e) {
            throw new KnightDbException("Could not read the knight", e);
        }
        return Optional.empty();
    }

    private static Account readAccount(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
            rs.getString("password_hash"), rs.getBoolean("is_guest"), rs.getString("created_at"));
    }

    /** A password nobody knows, so the keeper stays locked until claimed. */
    private static String lockedPasswordHash() {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        StringBuilder sb = new StringBuilder("locked$");
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static long findKeeper(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT id FROM users WHERE username = '" + KEEPER + "'")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /**
     * Rebuilds a legacy table with a composite (user_id, ...) key and hands
     * every row it held to the keeper, so no memory is lost to the upgrade.
     */
    private static void scopeTable(Connection conn, long ownerId, String table, String createSql,
                                   String insertColumns, String selectColumns) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " RENAME TO " + table + "_legacy");
            stmt.executeUpdate(createSql);
        }
        String copy = "INSERT INTO " + table + " (" + insertColumns + ") SELECT ?, " + selectColumns
            + " FROM " + table + "_legacy";
        try (PreparedStatement ps = conn.prepareStatement(copy)) {
            ps.setLong(1, ownerId);
            ps.executeUpdate();
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE " + table + "_legacy");
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
}
