package watchshark

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object Db {
    val lock = Any()
    lateinit var conn: Connection

    const val FULL_SCHEMA = """
CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE NOT NULL,email TEXT NOT NULL,password_hash TEXT NOT NULL,verified INTEGER DEFAULT 0,verify_token TEXT DEFAULT NULL,role TEXT DEFAULT 'user',avatar TEXT DEFAULT NULL,notify_uploads INTEGER DEFAULT 1,banned_until INTEGER DEFAULT 0,ban_reason TEXT DEFAULT NULL,deleted INTEGER DEFAULT 0,deleted_reason TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS videos(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,title TEXT NOT NULL,description TEXT DEFAULT '',filename TEXT NOT NULL,thumbnail TEXT DEFAULT NULL,mimetype TEXT NOT NULL,size INTEGER NOT NULL,views INTEGER DEFAULT 0,orientation TEXT DEFAULT 'h',renditions TEXT DEFAULT NULL,kind TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS likes(user_id INTEGER NOT NULL,video_id INTEGER NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(user_id,video_id));
CREATE TABLE IF NOT EXISTS comments(id INTEGER PRIMARY KEY AUTOINCREMENT,video_id INTEGER NOT NULL REFERENCES videos(id) ON DELETE CASCADE,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,body TEXT NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_videos_created ON videos(id DESC);
CREATE INDEX IF NOT EXISTS idx_videos_views ON videos(views DESC);
CREATE TABLE IF NOT EXISTS resets(token TEXT PRIMARY KEY,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,expires INTEGER NOT NULL,used INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS follows(follower_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,followed_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(follower_id,followed_id));
CREATE TABLE IF NOT EXISTS notifications(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,video_id INTEGER DEFAULT NULL,kind TEXT DEFAULT 'upload',title TEXT DEFAULT NULL,text TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,read INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS video_views(video_id INTEGER NOT NULL REFERENCES videos(id) ON DELETE CASCADE,user_id INTEGER NOT NULL,ip TEXT DEFAULT '',created_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE(video_id,user_id,ip));
DROP TABLE IF EXISTS reset_requests;
"""

    fun openDb() {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${Config.dbPath}")
        conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        conn.createStatement().use { it.execute("PRAGMA busy_timeout=5000") }
        synchronized(lock) {
            for (stmt in FULL_SCHEMA.split(";")) {
                val s = stmt.trim()
                if (s.isEmpty()) continue
                conn.createStatement().use { it.execute(s) }
            }
            val alters = listOf(
                "ALTER TABLE users ADD COLUMN verified INTEGER DEFAULT 0",
                "ALTER TABLE users ADD COLUMN verify_token TEXT DEFAULT NULL",
                "ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'user'",
                "ALTER TABLE users ADD COLUMN avatar TEXT DEFAULT NULL",
                "ALTER TABLE users ADD COLUMN notify_uploads INTEGER DEFAULT 1",
                "ALTER TABLE users ADD COLUMN banned_until INTEGER DEFAULT 0",
                "ALTER TABLE users ADD COLUMN ban_reason TEXT DEFAULT NULL",
                "ALTER TABLE users ADD COLUMN deleted INTEGER DEFAULT 0",
                "ALTER TABLE users ADD COLUMN deleted_reason TEXT DEFAULT NULL",
                "ALTER TABLE videos ADD COLUMN status TEXT DEFAULT 'ready'",
                "ALTER TABLE videos ADD COLUMN orientation TEXT DEFAULT 'h'",
                "ALTER TABLE videos ADD COLUMN renditions TEXT DEFAULT NULL",
                "ALTER TABLE videos ADD COLUMN kind TEXT DEFAULT NULL",
                "ALTER TABLE comments ADD COLUMN parent_id INTEGER DEFAULT NULL",
                "ALTER TABLE users ADD COLUMN last_seen INTEGER DEFAULT 0"
            )
            for (col in alters) {
                try {
                    conn.createStatement().use { it.execute(col) }
                } catch (e: Exception) {
                    if (!e.message.orEmpty().contains("duplicate column name")) throw e
                }
            }
            conn.createStatement().use { it.execute("UPDATE videos SET kind='wheel' WHERE kind IS NULL AND COALESCE(orientation,'h')='v'") }
            conn.createStatement().use { it.execute("UPDATE videos SET kind='video' WHERE kind IS NULL") }

            var hasNText = false
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(notifications)").use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == "text") hasNText = true
                    }
                }
            }
            if (!hasNText) {
                for (s in listOf(
                    "CREATE TABLE IF NOT EXISTS notifications_new(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,video_id INTEGER DEFAULT NULL,kind TEXT DEFAULT 'upload',title TEXT DEFAULT NULL,text TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,read INTEGER DEFAULT 0)",
                    "INSERT INTO notifications_new (id,user_id,video_id,kind,created_at,read) SELECT id,user_id,video_id,kind,created_at,read FROM notifications",
                    "DROP TABLE notifications",
                    "ALTER TABLE notifications_new RENAME TO notifications"
                )) {
                    conn.createStatement().use { it.execute(s) }
                }
            }
            var usql: String? = null
            try {
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT sql FROM sqlite_master WHERE name='users'").use { rs ->
                        if (rs.next()) usql = rs.getString(1)
                    }
                }
            } catch (_: Exception) {}
            if (usql != null && usql!!.contains("email TEXT UNIQUE")) {
                val want = listOf("id", "username", "email", "password_hash", "verified", "verify_token", "role", "avatar", "notify_uploads", "banned_until", "ban_reason", "deleted", "deleted_reason", "created_at")
                val have = mutableSetOf<String>()
                conn.createStatement().use { st ->
                    st.executeQuery("PRAGMA table_info(users)").use { rs ->
                        while (rs.next()) have.add(rs.getString("name"))
                    }
                }
                val cols = want.filter { have.contains(it) }
                val list = cols.joinToString(",")
                for (s in listOf(
                    "BEGIN",
                    "CREATE TABLE users_new(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE NOT NULL,email TEXT NOT NULL,password_hash TEXT NOT NULL,verified INTEGER DEFAULT 0,verify_token TEXT DEFAULT NULL,role TEXT DEFAULT 'user',avatar TEXT DEFAULT NULL,notify_uploads INTEGER DEFAULT 1,banned_until INTEGER DEFAULT 0,ban_reason TEXT DEFAULT NULL,deleted INTEGER DEFAULT 0,deleted_reason TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                    "INSERT INTO users_new ($list) SELECT $list FROM users",
                    "DROP TABLE users",
                    "ALTER TABLE users_new RENAME TO users",
                    "UPDATE sqlite_sequence SET seq=(SELECT MAX(id) FROM users) WHERE name='users'",
                    "COMMIT"
                )) {
                    conn.createStatement().use { it.execute(s) }
                }
            }
            conn.createStatement().use { it.execute("UPDATE users SET verified=1, verify_token=NULL WHERE verified=0") }
        }
    }

    fun lastInsertId(): Long {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    /** Online presence: online = active in the last 5 minutes. */
    private val seenWrite = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun touchSeen(uid: Long) {
        if (uid <= 0) return
        val now = System.currentTimeMillis() / 1000
        val last = seenWrite.putIfAbsent(uid, now) ?: 0L
        if (now - last < 300 && last != 0L) return
        seenWrite[uid] = now
        try {
            synchronized(lock) {
                conn.prepareStatement("UPDATE users SET last_seen=? WHERE id=?").use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, uid)
                    ps.executeUpdate()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun isOnline(lastSeen: Long): Boolean {
        if (lastSeen <= 0) return false
        return System.currentTimeMillis() / 1000 - lastSeen < 300
    }

    fun usernameOf(uid: Long): String? {
        synchronized(lock) {
            conn.prepareStatement("SELECT username FROM users WHERE id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return rs.getString(1)
                }
            }
        }
        return null
    }
}
