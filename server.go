package main

import (
"bytes"
"context"
"crypto/hmac"
"crypto/rand"
"crypto/sha256"
"database/sql"
"encoding/base64"
"encoding/json"
"fmt"
"golang.org/x/crypto/pbkdf2"
"io"
"io/fs"
"net"
"net/http"
"os"
"os/exec"
"os/signal"
"path/filepath"
"sort"
"strconv"
"strings"
"sync"
"syscall"
"time"
_ "modernc.org/sqlite"
)

var (
	db          *sql.DB
	dbPath      string
	dataDir     string
	videosDir   string
	thumbsDir   string
	avatarsDir  string
	publicDir   string
	jwtSecret   string
	maxBytes    uint64
	quotaBytes  uint64
	adminUser   string
	appURL      string
	smtpHost    string
	smtpPort    string
	smtpUser    string
	smtpPass    string
	smtpFrom    string
	dbMu        sync.Mutex
	transcodeSem = make(chan struct{}, 2)
	startTime   = time.Now()
)

func envOr(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func envULL(k string, d uint64) uint64 {
	v := os.Getenv(k)
	if v == "" {
		return d
	}
	n, err := strconv.ParseUint(v, 10, 64)
	if err != nil {
		return d
	}
	return n
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"error": msg})
}

func clientIP(r *http.Request) string {
	if f := r.Header.Get("X-Forwarded-For"); f != "" {
		if i := strings.Index(f, ","); i >= 0 {
			f = f[:i]
		}
		if ip := strings.TrimSpace(f); ip != "" {
			return ip
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

type rateLimiter struct {
	mu     sync.Mutex
	hits   map[string]*rateEntry
	max    uint
	window time.Duration
}

type rateEntry struct {
	count int
	reset time.Time
}

func newRateLimiter(max uint, window time.Duration) *rateLimiter {
	return &rateLimiter{hits: make(map[string]*rateEntry), max: max, window: window}
}

func (l *rateLimiter) allow(ip string) bool {
	now := time.Now()
	l.mu.Lock()
	defer l.mu.Unlock()
	e, ok := l.hits[ip]
	if !ok || now.Sub(e.reset) >= l.window {
		l.hits[ip] = &rateEntry{count: 1, reset: now}
		return true
	}
	e.count++
	return uint(e.count) <= l.max
}

var (
	authLimiter *rateLimiter
	apiLimiter  *rateLimiter
)

func limitMiddleware(l *rateLimiter, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !l.allow(clientIP(r)) {
			writeErr(w, 429, "Rate limit exceeded, slow down")
			return
		}
		next(w, r)
	}
}

func withSecurity(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		next(w, r)
	}
}

var mediaTypes = map[string]string{
	".webm": "video/webm",
	".ogg":  "audio/ogg",
	".opus": "audio/ogg",
	".mp3":  "audio/mpeg",
	".m4a":  "audio/mp4",
	".flac": "audio/flac",
	".wav":  "audio/wav",
	".mkv":  "video/x-matroska",
	".mp4":  "video/mp4",
	".avi":  "video/x-msvideo",
	".mov":  "video/quicktime",
	".ts":   "video/mp2t",
	".flv":  "video/x-flv",
	".wmv":  "video/x-ms-wmv",
	".mpg":  "video/mpeg",
	".mpeg": "video/mpeg",
	".ogv":  "video/ogg",
	".3gp":  "video/3gpp",
	".webp": "image/webp",
	".jpg":  "image/jpeg",
	".jpeg": "image/jpeg",
	".png":  "image/png",
	".gif":  "image/gif",
	".html": "text/html; charset=utf-8",
	".css":  "text/css; charset=utf-8",
	".js":   "text/javascript; charset=utf-8",
	".json": "application/json",
	".txt":  "text/plain; charset=utf-8",
	".ico":  "image/x-icon",
}

func cleanName(nm string) bool {
	if nm == "" || len(nm) > 128 || nm[0] == '.' {
		return false
	}
	if strings.Contains(nm, "/") || strings.Contains(nm, "..") {
		return false
	}
	return true
}

func serveMedia(w http.ResponseWriter, r *http.Request, full, name string, immutable ...bool) {
	ext := strings.ToLower(filepath.Ext(name))
	ct, ok := mediaTypes[ext]
	if !ok {
		ct = "application/octet-stream"
	}
	f, err := os.Open(full)
	if err != nil {
		writeErr(w, 404, "Not found")
		return
	}
	defer f.Close()
	fi, err := f.Stat()
	if err != nil || !fi.Mode().IsRegular() {
		writeErr(w, 404, "Not found")
		return
	}
	w.Header().Set("Content-Type", ct)
	if len(immutable) > 0 && immutable[0] {
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	}
	http.ServeContent(w, r, name, fi.ModTime(), f)
}

func serveStatic(w http.ResponseWriter, r *http.Request, uri string) {
	if uri == "/" {
		serveMedia(w, r, filepath.Join(publicDir, "index.html"), "index.html")
		return
	}
	if strings.HasPrefix(uri, "/v/") || strings.HasPrefix(uri, "/t/") || strings.HasPrefix(uri, "/a/") {
		nm := uri[3:]
		if !cleanName(nm) {
			writeErr(w, 404, "Not found")
			return
		}
		var base string
		switch uri[1] {
		case 'v':
			base = videosDir
		case 't':
			base = thumbsDir
		default:
			base = avatarsDir
		}
		serveMedia(w, r, filepath.Join(base, nm), nm, true)
		return
	}
	if strings.Contains(uri, "..") {
		writeErr(w, 404, "Not found")
		return
	}
	full := filepath.Join(publicDir, filepath.FromSlash(strings.TrimPrefix(uri, "/")))
	serveMedia(w, r, full, filepath.Base(full))
}

func parseID(s string) (int64, string, bool) {
	i := 0
	for i < len(s) && s[i] >= '0' && s[i] <= '9' {
		i++
	}
	if i == 0 {
		return 0, s, false
	}
	id, err := strconv.ParseInt(s[:i], 10, 64)
	if err != nil || id <= 0 {
		return 0, s, false
	}
	return id, s[i:], true
}

const fullSchema = `
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
`

func openDB() error {
	var err error
	db, err = sql.Open("sqlite", dbPath)
	if err != nil {
		return err
	}
	db.SetMaxOpenConns(1)
	if _, err = db.Exec("PRAGMA journal_mode=WAL"); err != nil {
		return err
	}
	if _, err = db.Exec("PRAGMA foreign_keys=ON"); err != nil {
		return err
	}
	if _, err = db.Exec("PRAGMA busy_timeout=5000"); err != nil {
		return err
	}
	if _, err = db.Exec(fullSchema); err != nil {
		return err
	}
	for _, col := range []string{
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
	} {
		if _, err = db.Exec(col); err != nil && !strings.Contains(err.Error(), "duplicate column name") {
			return err
		}
	}
	_, _ = db.Exec("UPDATE videos SET kind='wheel' WHERE kind IS NULL AND COALESCE(orientation,'h')='v'")
	_, _ = db.Exec("UPDATE videos SET kind='video' WHERE kind IS NULL")
	hasNText := false
	if rows, err := db.Query("PRAGMA table_info(notifications)"); err == nil {
		for rows.Next() {
			var cid int
			var name, ctype string
			var nn, pk int
			var dflt sql.NullString
			if err := rows.Scan(&cid, &name, &ctype, &nn, &dflt, &pk); err == nil && name == "text" {
				hasNText = true
			}
		}
		rows.Close()
	}
	if !hasNText {
		for _, s := range []string{
			"CREATE TABLE IF NOT EXISTS notifications_new(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,video_id INTEGER DEFAULT NULL,kind TEXT DEFAULT 'upload',title TEXT DEFAULT NULL,text TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,read INTEGER DEFAULT 0)",
			"INSERT INTO notifications_new (id,user_id,video_id,kind,created_at,read) SELECT id,user_id,video_id,kind,created_at,read FROM notifications",
			"DROP TABLE notifications",
			"ALTER TABLE notifications_new RENAME TO notifications",
		} {
			if _, err := db.Exec(s); err != nil {
				return err
			}
		}
	}
	var usql string
	err = db.QueryRow("SELECT sql FROM sqlite_master WHERE name='users'").Scan(&usql)
	if err == nil && strings.Contains(usql, "email TEXT UNIQUE") {
		want := []string{"id", "username", "email", "password_hash", "verified", "verify_token", "role", "avatar", "notify_uploads", "banned_until", "ban_reason", "deleted", "deleted_reason", "created_at"}
	rows, err := db.Query("PRAGMA table_info(users)")
	if err != nil {
		return err
	}
	have := map[string]bool{}
	for rows.Next() {
		var cid int
		var name, ctype string
		var nn, pk int
		var dflt sql.NullString
		if err := rows.Scan(&cid, &name, &ctype, &nn, &dflt, &pk); err != nil {
			rows.Close()
			return err
		}
		have[name] = true
	}
	rows.Close()
	cols := []string{}
	for _, c := range want {
		if have[c] {
			cols = append(cols, c)
		}
	}
	list := strings.Join(cols, ",")
	stmts := []string{
		"BEGIN",
		"CREATE TABLE users_new(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE NOT NULL,email TEXT NOT NULL,password_hash TEXT NOT NULL,verified INTEGER DEFAULT 0,verify_token TEXT DEFAULT NULL,role TEXT DEFAULT 'user',avatar TEXT DEFAULT NULL,notify_uploads INTEGER DEFAULT 1,banned_until INTEGER DEFAULT 0,ban_reason TEXT DEFAULT NULL,deleted INTEGER DEFAULT 0,deleted_reason TEXT DEFAULT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
		"INSERT INTO users_new (" + list + ") SELECT " + list + " FROM users",
		"DROP TABLE users",
		"ALTER TABLE users_new RENAME TO users",
		"UPDATE sqlite_sequence SET seq=(SELECT MAX(id) FROM users) WHERE name='users'",
		"COMMIT",
	}
	for _, s := range stmts {
		if _, err := db.Exec(s); err != nil {
			return err
		}
	}
	}
	_, _ = db.Exec("UPDATE users SET verified=1, verify_token=NULL WHERE verified=0")
	return nil
}

func userVersion() int64 {
	var v int64
	_ = db.QueryRow("PRAGMA user_version").Scan(&v)
	return v
}

func mkdirs(path string) error {
	return os.MkdirAll(path, 0755)
}

func dataFile(names ...string) string {
 parts := append([]string{dataDir}, names...)
	return filepath.Join(parts...)
}

type claims struct {
	Sub      int64  `json:"sub"`
	Username string `json:"username"`
	Iat      int64  `json:"iat"`
	Exp      int64  `json:"exp"`
}

func b64urlEnc(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}

func b64urlDec(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

func issueToken(id int64, username string) string {
	now := time.Now().Unix()
	h, _ := json.Marshal(map[string]any{"alg": "HS256", "typ": "JWT"})
	p, _ := json.Marshal(claims{Sub: id, Username: username, Iat: now, Exp: now + 7*24*3600})
	hs := b64urlEnc(h)
	ps := b64urlEnc(p)
	mac := hmac.New(sha256.New, []byte(jwtSecret))
	mac.Write([]byte(hs + "." + ps))
	return hs + "." + ps + "." + b64urlEnc(mac.Sum(nil))
}

func verifyToken(tok string) (int64, string, bool) {
	parts := strings.Split(tok, ".")
	if len(parts) != 3 {
		return 0, "", false
	}
	mac := hmac.New(sha256.New, []byte(jwtSecret))
	mac.Write([]byte(parts[0] + "." + parts[1]))
	sig, err := b64urlDec(parts[2])
	if err != nil || !hmac.Equal(sig, mac.Sum(nil)) {
		return 0, "", false
	}
	payload, err := b64urlDec(parts[1])
	if err != nil {
		return 0, "", false
	}
	var c claims
	if err := json.Unmarshal(payload, &c); err != nil {
		return 0, "", false
	}
	if c.Exp <= time.Now().Unix() || c.Sub <= 0 {
		return 0, "", false
	}
	return c.Sub, c.Username, true
}

func authUser(r *http.Request) (int64, string, bool) {
	ck, err := r.Cookie("ws_token")
	if err != nil || ck.Value == "" {
		return 0, "", false
	}
	return verifyToken(ck.Value)
}

func setAuthCookie(w http.ResponseWriter, id int64, username string) {
	http.SetCookie(w, &http.Cookie{
		Name:     "ws_token",
		Value:    issueToken(id, username),
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   7 * 24 * 3600,
	})
}

func clearAuthCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name:     "ws_token",
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   0,
	})
}

func randHex(nbytes int) string {
	b := make([]byte, nbytes)
	_, _ = rand.Read(b)
	const hexd = "0123456789abcdef"
	out := make([]byte, nbytes*2)
	for i, v := range b {
		out[i*2] = hexd[v>>4]
		out[i*2+1] = hexd[v&15]
	}
	return string(out)
}

func pwHash(pw string) (string, bool) {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", false
	}
	h := pbkdf2.Key([]byte(pw), salt, 210000, 32, sha256.New)
	return "pbkdf2$210000$" + b64urlEnc(salt) + "$" + b64urlEnc(h), true
}

func pwVerify(pw, stored string) bool {
	if !strings.HasPrefix(stored, "pbkdf2$") {
		return false
	}
	parts := strings.Split(stored, "$")
	if len(parts) != 4 {
		return false
	}
	iters, err := strconv.Atoi(parts[1])
	if err != nil || iters <= 0 || iters > 2000000 {
		return false
	}
	salt, err := b64urlDec(parts[2])
	if err != nil {
		return false
	}
	want, err := b64urlDec(parts[3])
	if err != nil || len(want) != 32 {
		return false
	}
	got := pbkdf2.Key([]byte(pw), salt, iters, 32, sha256.New)
	return hmac.Equal(got, want)
}

func validUsername(u string) bool {
	if len(u) < 3 || len(u) > 30 {
		return false
	}
	for _, c := range u {
		if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_') {
			return false
		}
	}
	return true
}

func validEmail(e string) bool {
	if len(e) > 120 || strings.Contains(e, " ") {
		return false
	}
	a := strings.Index(e, "@")
	if a <= 0 {
		return false
	}
	return strings.Contains(e[a:], ".")
}

func isAdmin(uid int64, username string) bool {
	lu := strings.ToLower(username)
	if adminUser != "" && lu == adminUser {
		return true
	}
	var role, email sql.NullString
	dbMu.Lock()
	err := db.QueryRow("SELECT role,email FROM users WHERE id=?", uid).Scan(&role, &email)
	dbMu.Unlock()
	if err != nil {
		return false
	}
	if role.Valid && role.String == "admin" {
		return true
	}
	if adminUser != "" && email.Valid && strings.ToLower(email.String) == adminUser {
		return true
	}
	return false
}

func getBanState(uid int64) (banned bool, daysLeft float64, reason string, deleted bool, delReason string) {
	var until sql.NullInt64
	var breason, dreason sql.NullString
	var del int
	dbMu.Lock()
	err := db.QueryRow("SELECT banned_until,ban_reason,deleted,deleted_reason FROM users WHERE id=?", uid).Scan(&until, &breason, &del, &dreason)
	dbMu.Unlock()
	if err != nil {
		return false, 0, "", false, ""
	}
	if del != 0 {
		dr := ""
		if dreason.Valid {
			dr = dreason.String
		}
		return false, 0, "", true, dr
	}
	if until.Valid && until.Int64 > time.Now().Unix() {
		r := ""
		if breason.Valid {
			r = breason.String
		}
		dl := float64(until.Int64-time.Now().Unix()) / 86400.0
		if until.Int64 > 4102444800 {
			dl = -1
		}
		return true, dl, r, false, ""
	}
	return false, 0, "", false, ""
}

func isBlockedFromUpload(uid int64) (bool, string) {
	banned, daysLeft, reason, deleted, delReason := getBanState(uid)
	if deleted {
		if delReason != "" {
			return true, "Your account has been deleted. Reason: " + delReason
		}
		return true, "Your account has been deleted."
	}
	if banned {
		if daysLeft < 0 {
			if reason != "" {
				return true, "You have been banned permanently. Reason: " + reason
			}
			return true, "You have been banned permanently."
		}
		ds := strconv.FormatFloat(daysLeft, 'f', 1, 64)
		if reason != "" {
			return true, "You have been banned for " + ds + " days. Reason: " + reason
		}
		return true, "You have been banned for " + ds + " days."
	}
	return false, ""
}

type apiUser struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
}

func readJSONBody(w http.ResponseWriter, r *http.Request, v any) bool {
	defer r.Body.Close()
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 65536))
	if err := dec.Decode(v); err != nil {
		writeErr(w, 400, "Bad request")
		return false
	}
	return true
}

func handleSignup(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Username string `json:"username"`
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	u := strings.TrimSpace(b.Username)
	e := strings.ToLower(strings.TrimSpace(b.Email))
	p := b.Password
	if !validUsername(u) {
		writeErr(w, 400, "Username: 3-30 chars, letters/numbers/_")
		return
	}
	if !validEmail(e) {
		writeErr(w, 400, "Invalid email")
		return
	}
	if len(p) < 6 || len(p) > 200 {
		writeErr(w, 400, "Password must be 6+ chars")
		return
	}
	hh, ok := pwHash(p)
	if !ok {
		writeErr(w, 500, "Hashing failed")
		return
	}
	dbMu.Lock()
	var nusers, nmail int64
	_ = db.QueryRow("SELECT COUNT(*) FROM users").Scan(&nusers)
	_ = db.QueryRow("SELECT COUNT(*) FROM users WHERE lower(email)=?", e).Scan(&nmail)
	role := "user"
	if nusers == 0 {
		role = "admin"
	}
	var id int64
	var conflict, cap bool
	if nmail >= 5 {
		cap = true
	} else {
		res, err := db.Exec("INSERT INTO users (username,email,password_hash,verified,verify_token,role) VALUES (?,?,?,?,?,?)", u, e, hh, 1, nil, role)
		if err != nil {
			if strings.Contains(err.Error(), "UNIQUE constraint") {
				conflict = true
			}
		} else {
			id, _ = res.LastInsertId()
		}
	}
	dbMu.Unlock()
	if cap {
		writeErr(w, 400, "Max 5 accounts per email")
		return
	}
	if conflict || id == 0 {
		if conflict {
			writeErr(w, 409, "Username or email already taken")
		} else {
			writeErr(w, 500, "Signup failed")
		}
		return
	}
	sendMail(e, "Welcome to WatchShark", "Welcome to WatchShark, "+u+"!\n\nYour account is ready — just log in and start watching.\n")
	setAuthCookie(w, id, u)
	writeJSON(w, 200, map[string]any{"ok": true, "user": apiUser{ID: id, Username: u}})
}

func handleLogin(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Login    string `json:"login"`
		Password string `json:"password"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	l := strings.ToLower(strings.TrimSpace(b.Login))
	var id int64
	var un, hh string
	var verified int
	dbMu.Lock()
	err := db.QueryRow("SELECT id,username,password_hash,verified FROM users WHERE lower(username)=? OR lower(email)=?", l, l).Scan(&id, &un, &hh, &verified)
	dbMu.Unlock()
	if err != nil || !pwVerify(b.Password, hh) {
		writeErr(w, 401, "Wrong login or password")
		return
	}
	if verified == 0 {
		writeErr(w, 403, "Waiting for admin approval")
		return
	}
	setAuthCookie(w, id, un)
	banned, daysLeft, reason, deleted, delReason := getBanState(id)
	writeJSON(w, 200, map[string]any{"ok": true, "user": apiUser{ID: id, Username: un},
		"banned": banned, "ban_days_left": daysLeft, "ban_reason": reason,
		"deleted": deleted, "deleted_reason": delReason})
}

func handleLogout(w http.ResponseWriter, r *http.Request) {
	clearAuthCookie(w)
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleVerify(w http.ResponseWriter, r *http.Request) {
	tok := r.URL.Query().Get("token")
	if tok == "" {
		writeErr(w, 400, "Missing token")
		return
	}
	dbMu.Lock()
	res, err := db.Exec("UPDATE users SET verified=1, verify_token=NULL WHERE verify_token=?", tok)
	var changed int64
	if err == nil {
		changed, _ = res.RowsAffected()
	}
	dbMu.Unlock()
	if err != nil || changed == 0 {
		writeErr(w, 400, "Invalid or expired link")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleResend(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Email string `json:"email"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	e := strings.ToLower(strings.TrimSpace(b.Email))
	dbMu.Lock()
	var id int64
	_ = db.QueryRow("SELECT id FROM users WHERE lower(email)=? AND verified=0", e).Scan(&id)
	vtok := ""
	if id != 0 {
		vtok = randHex(32)
		_, _ = db.Exec("UPDATE users SET verify_token=? WHERE id=?", vtok, id)
	}
	dbMu.Unlock()
	if id != 0 && e != "" {
		link := appURL + "/verify?token=" + vtok
		sendMail(e, "Confirm your WatchShark account", "Welcome to WatchShark!\n\nConfirm your account by opening this link:\n"+link+"\n")
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleForgot(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Email string `json:"email"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	e := strings.ToLower(strings.TrimSpace(b.Email))
	type acc struct {
		id   int64
		name string
		tok  string
		made bool
	}
	var accs []acc
	if e != "" {
		dbMu.Lock()
		rows, err := db.Query("SELECT id,username FROM users WHERE lower(email)=? LIMIT 5", e)
		if err == nil {
			for rows.Next() {
				var a acc
				if err := rows.Scan(&a.id, &a.name); err == nil {
					accs = append(accs, a)
				}
			}
			rows.Close()
		}
		exp := time.Now().Unix() + 3600
		for i := range accs {
			tok := randHex(32)
			_, _ = db.Exec("DELETE FROM resets WHERE user_id=?", accs[i].id)
			if _, err := db.Exec("INSERT INTO resets (token,user_id,expires,used) VALUES (?,?,?,0)", tok, accs[i].id, exp); err == nil {
				accs[i].tok = tok
				accs[i].made = true
			}
		}
		dbMu.Unlock()
		var sb strings.Builder
		sb.WriteString("Someone asked to reset WatchShark password(s).\n")
		sent := false
		for _, a := range accs {
			if !a.made {
				continue
			}
			sent = true
			sb.WriteString("\nAccount '" + a.name + "' (valid 1 hour):\n" + appURL + "/reset?token=" + a.tok + "\n")
		}
		if sent {
			sb.WriteString("\nIgnore this if it was not you.\n")
			sendMail(e, "Reset your WatchShark password", sb.String())
		}
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleReset(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Token    string `json:"token"`
		Password string `json:"password"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	if b.Token == "" {
		writeErr(w, 400, "Missing token")
		return
	}
	if len(b.Password) < 6 || len(b.Password) > 200 {
		writeErr(w, 400, "Password must be 6+ chars")
		return
	}
	now := time.Now().Unix()
	var uid, exp int64
	var used int
	dbMu.Lock()
	err := db.QueryRow("SELECT user_id,expires,used FROM resets WHERE token=?", b.Token).Scan(&uid, &exp, &used)
	ok := false
	if err == nil && used == 0 && exp > now && uid != 0 {
		if hh, good := pwHash(b.Password); good {
			if _, err := db.Exec("UPDATE users SET password_hash=?, verified=1 WHERE id=?", hh, uid); err == nil {
				if _, err := db.Exec("UPDATE resets SET used=1 WHERE token=?", b.Token); err == nil {
					ok = true
				}
			}
		}
	}
	dbMu.Unlock()
	if !ok {
		writeErr(w, 400, "Invalid or expired link")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleChange(w http.ResponseWriter, r *http.Request, uid int64) {
	var b struct {
		Current  string `json:"current"`
		Password string `json:"password"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	if len(b.Password) < 6 || len(b.Password) > 200 {
		writeErr(w, 400, "Password must be 6+ chars")
		return
	}
	dbMu.Lock()
	var hh string
	err := db.QueryRow("SELECT password_hash FROM users WHERE id=?", uid).Scan(&hh)
	ok := false
	if err == nil && pwVerify(b.Current, hh) {
		if nh, good := pwHash(b.Password); good {
			if _, err := db.Exec("UPDATE users SET password_hash=? WHERE id=?", nh, uid); err == nil {
				ok = true
			}
		}
	}
	dbMu.Unlock()
	if !ok {
		writeErr(w, 401, "Wrong current password")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleUsername(w http.ResponseWriter, r *http.Request, uid int64) {
	var b struct {
		Username string `json:"username"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	u := strings.TrimSpace(b.Username)
	if !validUsername(u) {
		writeErr(w, 400, "Username: 3-30 chars, letters/numbers/_")
		return
	}
	dbMu.Lock()
	_, err := db.Exec("UPDATE users SET username=? WHERE id=?", u, uid)
	conflict := err != nil && strings.Contains(err.Error(), "UNIQUE constraint")
	ok := err == nil
	dbMu.Unlock()
	if conflict {
		writeErr(w, 409, "Username already taken")
		return
	}
	if !ok {
		writeErr(w, 500, "Change failed")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleNotifSet(w http.ResponseWriter, r *http.Request, uid int64) {
	var b struct {
		Uploads *bool `json:"uploads"`
	}
	if !readJSONBody(w, r, &b) || b.Uploads == nil {
		return
	}
	on := 0
	if *b.Uploads {
		on = 1
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE users SET notify_uploads=? WHERE id=?", on, uid)
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"ok": true})
}


func handleMe(w http.ResponseWriter, r *http.Request) {
	id, un, ok := authUser(r)
	if !ok {
		writeJSON(w, 200, map[string]any{"user": nil})
		return
	}
	var avatar sql.NullString
	var notif int
	var since string
	var iat, exp int64
	if ck, err := r.Cookie("ws_token"); err == nil {
		if parts := strings.Split(ck.Value, "."); len(parts) == 3 {
			if p, err := b64urlDec(parts[1]); err == nil {
				var c claims
				if json.Unmarshal(p, &c) == nil {
					iat, exp = c.Iat, c.Exp
				}
			}
		}
	}
	dbMu.Lock()
	_ = db.QueryRow("SELECT avatar,notify_uploads,created_at FROM users WHERE id=?", id).Scan(&avatar, &notif, &since)
	dbMu.Unlock()
	av := any(nil)
	if avatar.Valid {
		av = "/a/" + avatar.String
	}
	banned, daysLeft, reason, deleted, delReason := getBanState(id)
	writeJSON(w, 200, map[string]any{"user": map[string]any{
		"id": id, "username": un, "iat": iat, "exp": exp,
		"admin": isAdmin(id, un), "avatar": av,
		"notify_uploads": notif != 0, "since": since,
		"banned": banned, "ban_days_left": daysLeft, "ban_reason": reason,
		"deleted": deleted, "deleted_reason": delReason,
	}})
}

func recordViewLocked(id, viewer int64, ip string) {
	var st *sql.Stmt
	var err error
	st, err = db.Prepare("INSERT OR IGNORE INTO video_views (video_id,user_id,ip) VALUES (?,?,?)")
	if err != nil {
		return
	}
	defer st.Close()
	res, err := st.Exec(id, viewer, ip)
	if err != nil {
		return
	}
	if n, _ := res.RowsAffected(); n > 0 {
		_, _ = db.Exec("UPDATE videos SET views=views+1 WHERE id=?", id)
	}
}

func videoJSON(id, viewer int64) (map[string]any, bool) {
	dbMu.Lock()
	defer dbMu.Unlock()
	var vid, uid, sz, views, likes, comments, followers int64
	var title, desc, fn, mt, ca, un string
	var th, status, uav, rend, ori, kk sql.NullString
	err := db.QueryRow(`SELECT v.id,v.user_id,v.title,v.description,v.filename,v.thumbnail,v.mimetype,v.size,v.views,v.created_at,v.status,u.username,u.avatar,v.renditions,v.orientation,v.kind FROM videos v JOIN users u ON u.id=v.user_id WHERE v.id=?`, id).Scan(&vid, &uid, &title, &desc, &fn, &th, &mt, &sz, &views, &ca, &status, &un, &uav, &rend, &ori, &kk)
	if err != nil {
		return nil, false
	}
	_ = db.QueryRow("SELECT COUNT(*) FROM likes WHERE video_id=?", vid).Scan(&likes)
	liked := false
	if viewer >= 0 {
		var one int
		if err := db.QueryRow("SELECT 1 FROM likes WHERE user_id=? AND video_id=?", viewer, vid).Scan(&one); err == nil {
			liked = true
		}
	}
	_ = db.QueryRow("SELECT COUNT(*) FROM comments WHERE video_id=?", vid).Scan(&comments)
	_ = db.QueryRow("SELECT COUNT(*) FROM follows WHERE followed_id=?", uid).Scan(&followers)
	following := false
	if viewer >= 0 && viewer != uid {
		var one int
		if err := db.QueryRow("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?", viewer, uid).Scan(&one); err == nil {
			following = true
		}
	}
	thumb := any(nil)
	if th.Valid {
		thumb = "/t/" + th.String
	}
	st := "ready"
	if status.Valid && status.String != "" {
		st = status.String
	}
	oriStr := "h"
	if ori.Valid && ori.String == "v" {
		oriStr = "v"
	}
	kindStr := "video"
	if kk.Valid && (kk.String == "wheel" || kk.String == "music") {
		kindStr = kk.String
	}
	av := any(nil)
	if uav.Valid {
		av = "/a/" + uav.String
	}
	var renditions any
	if rend.Valid && rend.String != "" {
		var m map[string]any
		if err := json.Unmarshal([]byte(rend.String), &m); err == nil && len(m) > 0 {
			renditions = m
		}
	}
	return map[string]any{
		"id": vid, "title": title, "description": desc,
		"username": un, "user_id": uid,
		"src": "/v/" + fn, "thumbnail": thumb,
		"mimetype": mt, "size": sz, "views": views,
		"likes": likes, "liked": liked, "comments": comments,
		"followers": followers, "following": following,
		"created_at": ca, "status": st, "avatar": av,
		"renditions": renditions, "orientation": oriStr, "kind": kindStr,
	}, true
}

func handleList(w http.ResponseWriter, r *http.Request) {
	viewer := int64(-1)
	if id, _, ok := authUser(r); ok {
		viewer = id
	}
	q := r.URL.Query()
	search := q.Get("q")
	if rs := []rune(search); len(rs) > 100 {
		search = string(rs[:100])
	}
	search = strings.TrimSpace(search)
	popular := q.Get("sort") == "popular"
	order := "v.id DESC"
	if popular {
		order = "v.views DESC, v.id DESC"
	}
	page, _ := strconv.ParseInt(q.Get("page"), 10, 64)
	if page < 1 {
		page = 1
	}
	limit, _ := strconv.ParseInt(q.Get("limit"), 10, 64)
	if limit < 1 {
		limit = 12
	}
	if limit > 24 {
		limit = 24
	}
	off := (page - 1) * limit
	mine := q.Get("mine") == "1"
	kind := q.Get("kind")
	if kind != "music" {
		kind = "video"
	}
	if mine && viewer < 0 {
		writeErr(w, 401, "Login required")
		return
	}
	dbMu.Lock()
	var ids []int64
	var total int64
	if mine {
		like := "%" + escapeLike(search) + "%"
		rows, err := db.Query("SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE v.user_id=? AND COALESCE(v.kind,'video')!='music' AND (?='' OR v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\') ORDER BY "+order+" LIMIT ? OFFSET ?", viewer, search, like, like, like, limit, off)
		if err == nil {
			for rows.Next() {
				var id int64
				if err := rows.Scan(&id); err == nil {
					ids = append(ids, id)
				}
			}
			rows.Close()
		}
		_ = db.QueryRow("SELECT COUNT(*) FROM videos v JOIN users u ON u.id=v.user_id WHERE v.user_id=? AND COALESCE(v.kind,'video')!='music' AND (?='' OR v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')", viewer, search, like, like, like).Scan(&total)
	} else if search == "" {
		rows, err := db.Query("SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? ORDER BY "+order+" LIMIT ? OFFSET ?", kind, limit, off)
		if err == nil {
			for rows.Next() {
				var id int64
				if err := rows.Scan(&id); err == nil {
					ids = append(ids, id)
				}
			}
			rows.Close()
		}
		_ = db.QueryRow("SELECT COUNT(*) FROM videos WHERE COALESCE(kind,'video')=?", kind).Scan(&total)
	} else {
		like := "%" + escapeLike(search) + "%"
		rows, err := db.Query("SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\') ORDER BY "+order+" LIMIT ? OFFSET ?", kind, like, like, like, limit, off)
		if err == nil {
			for rows.Next() {
				var id int64
				if err := rows.Scan(&id); err == nil {
					ids = append(ids, id)
				}
			}
			rows.Close()
		}
		_ = db.QueryRow("SELECT COUNT(*) FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')", kind, like, like, like).Scan(&total)
	}
	dbMu.Unlock()
	videos := []any{}
	for _, id := range ids {
		if v, ok := videoJSON(id, viewer); ok {
			videos = append(videos, v)
		} else {
			videos = append(videos, nil)
		}
	}
	pages := (total + limit - 1) / limit
	if limit < 1 {
		pages = 0
	}
	writeJSON(w, 200, map[string]any{"videos": videos, "page": page, "pages": pages, "total": total})
}

func handleGetVideo(w http.ResponseWriter, r *http.Request, id int64) {	viewer := int64(-1)
	if vid, _, ok := authUser(r); ok {
		viewer = vid
	}
	var exists bool
	dbMu.Lock()
	var one int
	exists = db.QueryRow("SELECT 1 FROM videos WHERE id=?", id).Scan(&one) == nil
	if exists && viewer >= 0 {
		recordViewLocked(id, viewer, "")
	}
	dbMu.Unlock()
	if !exists {
		writeErr(w, 404, "Not found")
		return
	}
	v, ok := videoJSON(id, viewer)
	if !ok {
		writeErr(w, 404, "Not found")
		return
	}
	type comment struct {
		ID             int64  `json:"id"`
		Body           string `json:"body"`
		CreatedAt      string `json:"created_at"`
		Username       string `json:"username"`
		Avatar         any    `json:"avatar"`
		ParentID       any    `json:"parent_id"`
		ParentUsername any    `json:"parent_username"`
	}
	comments := []comment{}
	dbMu.Lock()
	rows, err := db.Query(`SELECT c.id,c.body,c.created_at,u.username,u.avatar,c.parent_id,pu.username FROM comments c JOIN users u ON u.id=c.user_id LEFT JOIN comments pc ON pc.id=c.parent_id LEFT JOIN users pu ON pu.id=pc.user_id WHERE c.video_id=? ORDER BY c.id DESC LIMIT 50`, id)
	if err == nil {
		for rows.Next() {
			var c comment
			var av sql.NullString
			var pid sql.NullInt64
			var pun sql.NullString
			if err := rows.Scan(&c.ID, &c.Body, &c.CreatedAt, &c.Username, &av, &pid, &pun); err == nil {
				if av.Valid {
					c.Avatar = "/a/" + av.String
				}
				if pid.Valid {
					c.ParentID = pid.Int64
				}
				if pun.Valid {
					c.ParentUsername = pun.String
				}
				comments = append(comments, c)
			}
		}
		rows.Close()
	}
	dbMu.Unlock()
	if comments == nil {
		comments = []comment{}
	}
	writeJSON(w, 200, map[string]any{"video": v, "comments": comments})
}

func handleDeleteVideo(w http.ResponseWriter, r *http.Request, id int64) {
	uid, un, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return
	}
	var b struct {
		Reason string `json:"reason"`
	}
	if r.Body != nil {
		if data, err := io.ReadAll(io.LimitReader(r.Body, 4096)); err == nil && len(data) > 0 {
			_ = json.Unmarshal(data, &b)
		}
		r.Body.Close()
	}
	reason := truncateRunes(b.Reason, 500)
	admin := isAdmin(uid, un)
	var owner int64
	var fn string
	var title string
	var th sql.NullString
	found := false
	dbMu.Lock()
	err := db.QueryRow("SELECT user_id,filename,thumbnail,title FROM videos WHERE id=?", id).Scan(&owner, &fn, &th, &title)
	if err == nil {
		found = true
		if owner != uid && !admin {
			dbMu.Unlock()
			writeErr(w, 403, "Not yours")
			return
		}
		if owner != uid && reason == "" {
			dbMu.Unlock()
			writeErr(w, 400, "Reason required")
			return
		}
		if owner != uid {
			_, _ = db.Exec("INSERT INTO notifications (user_id,video_id,kind,title,text) VALUES (?,NULL,'delete',?,?)", owner, title, reason)
		}
		_, _ = db.Exec("DELETE FROM likes WHERE video_id=?", id)
		_, _ = db.Exec("DELETE FROM videos WHERE id=?", id)
	}
	dbMu.Unlock()
	if !found {
		writeErr(w, 404, "Not found")
		return
	}
	os.Remove(filepath.Join(videosDir, fn))
	unlinkRenditions(fn)
	if th.Valid {
		os.Remove(filepath.Join(thumbsDir, th.String))
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleThumb(w http.ResponseWriter, r *http.Request, id int64) {
	uid, un, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return
	}
	admin := isAdmin(uid, un)
	var owner int64
	var oldTh sql.NullString
	dbMu.Lock()
	err := db.QueryRow("SELECT user_id,thumbnail FROM videos WHERE id=?", id).Scan(&owner, &oldTh)
	dbMu.Unlock()
	if err != nil {
		writeErr(w, 404, "Not found")
		return
	}
	if owner != uid && !admin {
		writeErr(w, 403, "Not yours")
		return
	}
	ct := r.Header.Get("Content-Type")
	if ct == "" || !strings.HasPrefix(ct, "image/") {
		writeErr(w, 400, "Send an image file")
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, 10*1024*1024+1))
	r.Body.Close()
	if err != nil || len(body) == 0 || int64(len(body)) > 10*1024*1024 {
		writeErr(w, 400, "Empty or too big (max 10MB)")
		return
	}
	stem := randHex(16)
	tmp := filepath.Join(thumbsDir, stem+".up")
	if err := os.WriteFile(tmp, body, 0644); err != nil {
		writeErr(w, 500, "Cannot store file")
		return
	}
	name, good := convertThumb(tmp, stem)
	os.Remove(tmp)
	if !good {
		writeErr(w, 400, "Not an image file")
		return
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE videos SET thumbnail=? WHERE id=?", name, id)
	dbMu.Unlock()
	if oldTh.Valid && oldTh.String != "" && oldTh.String != name {
		os.Remove(filepath.Join(thumbsDir, oldTh.String))
	}
	writeJSON(w, 200, map[string]any{"ok": true, "thumbnail": "/t/" + name})
}

func handleEditVideo(w http.ResponseWriter, r *http.Request, id int64) {
	uid, un, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return
	}
	admin := isAdmin(uid, un)
	var owner int64
	dbMu.Lock()
	err := db.QueryRow("SELECT user_id FROM videos WHERE id=?", id).Scan(&owner)
	dbMu.Unlock()
	if err != nil {
		writeErr(w, 404, "Not found")
		return
	}
	if owner != uid && !admin {
		writeErr(w, 403, "Not yours")
		return
	}
	if err := r.ParseMultipartForm(12 << 20); err != nil {
		writeErr(w, 400, "Bad form")
		return
	}
	title := truncateRunes(r.FormValue("title"), 120)
	desc := truncateRunes(r.FormValue("description"), 2000)
	if title == "" {
		writeErr(w, 400, "Title required")
		return
	}
	thumbName := ""
	hasThumb := false
	if f, _, ferr := r.FormFile("thumb"); ferr == nil {
		body, err := io.ReadAll(io.LimitReader(f, 10*1024*1024+1))
		f.Close()
		if err != nil || len(body) == 0 || int64(len(body)) > 10*1024*1024 {
			writeErr(w, 400, "Thumbnail too big (max 10MB)")
			return
		}
		stem := randHex(16)
		tmp := filepath.Join(thumbsDir, stem+".up")
		if err := os.WriteFile(tmp, body, 0644); err != nil {
			writeErr(w, 500, "Cannot store file")
			return
		}
		name, good := convertThumb(tmp, stem)
		os.Remove(tmp)
		if !good {
			writeErr(w, 400, "Not an image file")
			return
		}
		thumbName, hasThumb = name, true
	}
	dbMu.Lock()
	if hasThumb {
		var oldTh sql.NullString
		_ = db.QueryRow("SELECT thumbnail FROM videos WHERE id=?", id).Scan(&oldTh)
		_, _ = db.Exec("UPDATE videos SET title=?,description=?,thumbnail=? WHERE id=?", title, desc, thumbName, id)
		dbMu.Unlock()
		if oldTh.Valid && oldTh.String != "" && oldTh.String != thumbName {
			os.Remove(filepath.Join(thumbsDir, oldTh.String))
		}
	} else {
		_, _ = db.Exec("UPDATE videos SET title=?,description=? WHERE id=?", title, desc, id)
		dbMu.Unlock()
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleLike(w http.ResponseWriter, r *http.Request, id int64) {
	uid, _, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return
	}
	dbMu.Lock()
	var one int
	exists := db.QueryRow("SELECT 1 FROM videos WHERE id=?", id).Scan(&one) == nil
	has := false
	if exists {
		has = db.QueryRow("SELECT 1 FROM likes WHERE user_id=? AND video_id=?", uid, id).Scan(&one) == nil
		if has {
			_, _ = db.Exec("DELETE FROM likes WHERE user_id=? AND video_id=?", uid, id)
		} else {
			_, _ = db.Exec("INSERT INTO likes (user_id,video_id) VALUES (?,?)", uid, id)
		}
	}
	var likes int64
	_ = db.QueryRow("SELECT COUNT(*) FROM likes WHERE video_id=?", id).Scan(&likes)
	dbMu.Unlock()
	if !exists {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "liked": !has, "likes": likes})
}

func truncateRunes(s string, n int) string {
	r := []rune(strings.TrimSpace(s))
	if len(r) > n {
		r = r[:n]
	}
	return strings.TrimSpace(string(r))
}

func escapeLike(s string) string {
	s = strings.ReplaceAll(s, "\\", "\\\\")
	s = strings.ReplaceAll(s, "%", "\\%")
	s = strings.ReplaceAll(s, "_", "\\_")
	return s
}

func handleComment(w http.ResponseWriter, r *http.Request, id int64) {
	uid, _, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return
	}
	var b struct {
		Body     string `json:"body"`
		ParentID *int64 `json:"parent_id"`
	}
	if !readJSONBody(w, r, &b) {
		return
	}
	body := truncateRunes(b.Body, 2000)
	if body == "" {
		writeErr(w, 400, "Empty comment")
		return
	}
	dbMu.Lock()
	var one int
	exists := db.QueryRow("SELECT 1 FROM videos WHERE id=?", id).Scan(&one) == nil
	var pid any
	if b.ParentID != nil {
		var pv int64
		if err := db.QueryRow("SELECT video_id FROM comments WHERE id=?", *b.ParentID).Scan(&pv); err != nil || pv != id {
			dbMu.Unlock()
			writeErr(w, 400, "Bad parent comment")
			return
		}
		pid = *b.ParentID
	}
	var cid int64
	if exists {
		if res, err := db.Exec("INSERT INTO comments (video_id,user_id,body,parent_id) VALUES (?,?,?,?)", id, uid, body, pid); err == nil {
			cid, _ = res.LastInsertId()
		}
	}
	dbMu.Unlock()
	if !exists {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "id": cid})
}

func handleWheels(w http.ResponseWriter, r *http.Request) {
	viewer := int64(-1)
	if id, _, ok := authUser(r); ok {
		viewer = id
	}
	seen := []int64{}
	if s := r.URL.Query().Get("seen"); s != "" {
		for _, p := range strings.Split(s, ",") {
			if len(seen) >= 128 {
				break
			}
			if v, err := strconv.ParseInt(strings.TrimSpace(p), 10, 64); err == nil && v > 0 {
				seen = append(seen, v)
			}
		}
	}
	var sb strings.Builder
	sb.WriteString("SELECT id FROM videos WHERE status='ready' AND COALESCE(kind,'video')='wheel'")
	args := []any{}
	if len(seen) > 0 {
		sb.WriteString(" AND id NOT IN (")
		for i, v := range seen {
			if i > 0 {
				sb.WriteString(",")
			}
			sb.WriteString("?")
			args = append(args, v)
		}
		sb.WriteString(")")
	}
	sb.WriteString(" ORDER BY id DESC LIMIT 1")
	var id int64
	dbMu.Lock()
	err := db.QueryRow(sb.String(), args...).Scan(&id)
	if err != nil {
		dbMu.Unlock()
		writeErr(w, 404, "No videos yet")
		return
	}
	if viewer >= 0 {
		recordViewLocked(id, viewer, "")
	}
	dbMu.Unlock()
	v, ok := videoJSON(id, viewer)
	if !ok {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"video": v})
}

func handleUpload(w http.ResponseWriter, r *http.Request, uid int64) {
	if blocked, msg := isBlockedFromUpload(uid); blocked {
		writeErr(w, 403, msg)
		return
	}
	ct := r.Header.Get("Content-Type")
	if !strings.HasPrefix(ct, "multipart/form-data") || !strings.Contains(ct, "boundary=") {
		writeErr(w, 400, "Bad multipart")
		return
	}
	if storedBytes() >= quotaBytes {
		writeErr(w, 507, "Server storage full (50GB limit reached)")
		return
	}
	if r.ContentLength >= 0 && uint64(r.ContentLength)+storedBytes() > quotaBytes {
		writeErr(w, 507, "Server storage full (50GB limit reached)")
		return
	}
	mr, err := r.MultipartReader()
	if err != nil {
		writeErr(w, 400, "Bad multipart")
		return
	}
	stem := randHex(16)
	partPath := filepath.Join(videosDir, stem+".part")
	thumbTmp := filepath.Join(thumbsDir, stem+".ctmp")
	var title, desc, kind string
	kind = "video"
	var savedSize int64
	var saved, thumbSaved bool
	var outFile *os.File
	for {
		part, err := mr.NextPart()
		if err == io.EOF {
			break
		}
		if err != nil {
			if outFile != nil {
				outFile.Close()
			}
			os.Remove(partPath)
			writeErr(w, 400, "Upload failed")
			return
		}
		name := part.FormName()
		switch name {
		case "title", "description", "kind":
			b, err := io.ReadAll(io.LimitReader(part, 8192))
			if err != nil {
				if outFile != nil {
					outFile.Close()
				}
				os.Remove(partPath)
				writeErr(w, 400, "Upload failed")
				return
			}
			if name == "title" {
				title = truncateRunes(string(b), 120)
			} else if name == "description" {
				desc = truncateRunes(string(b), 2000)
			} else {
				k := truncateRunes(string(b), 16)
				if k == "video" || k == "wheel" || k == "music" {
					kind = k
				}
			}
		case "thumb":
			if thumbSaved {
				_, _ = io.Copy(io.Discard, part)
				continue
			}
			tf, terr := os.Create(thumbTmp)
			if terr != nil {
				os.Remove(partPath)
				os.Remove(thumbTmp)
				writeErr(w, 500, "Cannot store file")
				return
			}
			twritten, terr := io.Copy(tf, io.LimitReader(part, 10*1024*1024+1))
			tf.Close()
			if terr != nil || twritten == 0 || twritten > 10*1024*1024 {
				os.Remove(partPath)
				os.Remove(thumbTmp)
				writeErr(w, 400, "Thumbnail too big (max 10MB)")
				return
			}
			thumbSaved = true
		case "file":
			if saved {
				_, _ = io.Copy(io.Discard, part)
				continue
			}
			f, err := os.Create(partPath)
			if err != nil {
				writeErr(w, 500, "Cannot store file")
				return
			}
			outFile = f
			written, err := io.Copy(outFile, io.LimitReader(part, int64(maxBytes)+1))
			outFile.Close()
			outFile = nil
			if err != nil {
				os.Remove(partPath)
				writeErr(w, 400, "Upload failed")
				return
			}
			if written == 0 {
				os.Remove(partPath)
				writeErr(w, 400, "Empty file")
				return
			}
			if uint64(written) > maxBytes {
				os.Remove(partPath)
				writeErr(w, 413, fmt.Sprintf("File too big (max %dMB)", maxBytes/1024/1024))
				return
			}
			savedSize = written
			saved = true
		default:
			_, _ = io.Copy(io.Discard, part)
		}
	}
	if outFile != nil {
		outFile.Close()
	}
	if !saved {
		writeErr(w, 400, "No file")
		return
	}
	if title == "" {
		title = "Untitled"
	}
	if storedBytes() > quotaBytes {
		os.Remove(partPath)
		writeErr(w, 507, "Server storage full (50GB limit reached)")
		return
	}
	if kind == "music" {
		if !probeHasAudio(partPath) {
			os.Remove(partPath)
			writeErr(w, 400, "Not an audio file")
			return
		}
	} else if !probeHasVideo(partPath) {
		os.Remove(partPath)
		writeErr(w, 400, "Not a video file")
		return
	}
	dbMu.Lock()
	var id int64
	pfn := stem + ".part"
	res, err := db.Exec("INSERT INTO videos (user_id,title,description,filename,thumbnail,mimetype,size,kind,status) VALUES (?,?,?,?,NULL,?,?,?,'processing')", uid, title, desc, pfn, "video/mp4", savedSize, kind)
	if err == nil {
		id, _ = res.LastInsertId()
	}
	dbMu.Unlock()
	if id == 0 {
		os.Remove(partPath)
		os.Remove(thumbTmp)
		writeErr(w, 500, "DB insert failed")
		return
	}
	customThumb := ""
	if thumbSaved {
		customThumb = thumbTmp
	}
	if kind == "music" {
		go processMusic(id, uid, partPath, stem, customThumb)
	} else {
		go processUpload(id, uid, partPath, stem, customThumb)
	}
	writeJSON(w, 200, map[string]any{"ok": true, "id": id, "kind": kind})
}

func handleFollow(w http.ResponseWriter, r *http.Request, uid, target int64) {
	if target <= 0 || target == uid {
		writeErr(w, 400, "Bad request")
		return
	}
	dbMu.Lock()
	var one int
	exists := db.QueryRow("SELECT 1 FROM users WHERE id=?", target).Scan(&one) == nil
	var has bool
	var followers int64
	if exists {
		has = db.QueryRow("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?", uid, target).Scan(&one) == nil
		if has {
			_, _ = db.Exec("DELETE FROM follows WHERE follower_id=? AND followed_id=?", uid, target)
		} else {
			_, _ = db.Exec("INSERT INTO follows (follower_id,followed_id) VALUES (?,?)", uid, target)
		}
		_ = db.QueryRow("SELECT COUNT(*) FROM follows WHERE followed_id=?", target).Scan(&followers)
	}
	dbMu.Unlock()
	if !exists {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "following": !has, "followers": followers})
}

func handleNotifications(w http.ResponseWriter, r *http.Request, uid int64) {
	type notif struct {
		ID        int64  `json:"id"`
		VideoID   any    `json:"video_id"`
		CreatedAt string `json:"created_at"`
		Read      bool   `json:"read"`
		Title     string `json:"title"`
		Username  string `json:"username"`
		Kind      string `json:"kind"`
		Text      string `json:"text"`
	}
	items := []notif{}
	var unread int64
	dbMu.Lock()
	rows, err := db.Query("SELECT n.id,n.video_id,n.created_at,n.read,n.kind,n.title,n.text,v.title,u.username FROM notifications n LEFT JOIN videos v ON v.id=n.video_id LEFT JOIN users u ON u.id=v.user_id WHERE n.user_id=? ORDER BY n.id DESC LIMIT 30", uid)
	if err == nil {
		for rows.Next() {
			var n notif
			var rd int
			var vid sql.NullInt64
			var kind, ntitle, ntext, vtitle, uname sql.NullString
			if err := rows.Scan(&n.ID, &vid, &n.CreatedAt, &rd, &kind, &ntitle, &ntext, &vtitle, &uname); err == nil {
				n.Read = rd != 0
				if vid.Valid {
					n.VideoID = vid.Int64
				}
				if vtitle.Valid && vtitle.String != "" {
					n.Title = vtitle.String
				} else if ntitle.Valid {
					n.Title = ntitle.String
				}
				if uname.Valid {
					n.Username = uname.String
				}
				if kind.Valid {
					n.Kind = kind.String
				}
				if ntext.Valid {
					n.Text = ntext.String
				}
				items = append(items, n)
			}
		}
		rows.Close()
	}
	_ = db.QueryRow("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read=0", uid).Scan(&unread)
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"notifications": items, "unread": unread})
}

func handleNotifRead(w http.ResponseWriter, r *http.Request, uid int64) {
	var b struct {
		ID *int64 `json:"id"`
	}
	_ = readJSONBodyQuiet(w, r, &b)
	dbMu.Lock()
	if b.ID != nil && *b.ID > 0 {
		_, _ = db.Exec("UPDATE notifications SET read=1 WHERE id=? AND user_id=?", *b.ID, uid)
	} else {
		_, _ = db.Exec("UPDATE notifications SET read=1 WHERE user_id=?", uid)
	}
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"ok": true})
}

func readJSONBodyQuiet(w http.ResponseWriter, r *http.Request, v any) bool {
	defer r.Body.Close()
	return json.NewDecoder(io.LimitReader(r.Body, 65536)).Decode(v) == nil
}

func handlePfp(w http.ResponseWriter, r *http.Request, uid int64) {
	ct := r.Header.Get("Content-Type")
	if ct == "" || !strings.HasPrefix(ct, "image/") {
		writeErr(w, 400, "Send an image file")
		return
	}
	if storedBytes() >= quotaBytes {
		writeErr(w, 507, "Server storage full (50GB limit reached)")
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, 50*1024*1024+1))
	r.Body.Close()
	if err != nil || len(body) == 0 {
		writeErr(w, 400, "Empty or too big (max 50MB)")
		return
	}
	if int64(len(body)) > 50*1024*1024 {
		writeErr(w, 400, "Empty or too big (max 50MB)")
		return
	}
	stem := randHex(16)
	tmp := filepath.Join(avatarsDir, stem+".up")
	if err := os.WriteFile(tmp, body, 0644); err != nil {
		writeErr(w, 500, "Cannot store file")
		return
	}
	if !probeHasVideo(tmp) {
		os.Remove(tmp)
		writeErr(w, 400, "Not an image file")
		return
	}
	isGif := strings.Contains(probeFormat(tmp), "gif")
	var out, name string
	convertOK := false
	if isGif {
		out = filepath.Join(avatarsDir, stem+".webm")
		name = stem + ".webm"
		args := []string{"-y", "-i", tmp, "-vf", "scale=256:256:force_original_aspect_ratio=increase,crop=256:256", "-c:v", "libvpx-vp9", "-deadline", "good", "-cpu-used", "5", "-crf", "32", "-b:v", "0", "-an", "-f", "webm", out}
		if !runFFmpeg(args, 300*time.Second) || !pfpSmallEnough(out) {
			os.Remove(out)
			args2 := []string{"-y", "-i", tmp, "-vf", "scale=128:128:force_original_aspect_ratio=increase,crop=128:128", "-c:v", "libvpx-vp9", "-deadline", "good", "-cpu-used", "5", "-crf", "38", "-b:v", "0", "-an", "-f", "webm", out}
			if !runFFmpeg(args2, 300*time.Second) || !pfpSmallEnough(out) {
				os.Remove(tmp)
				os.Remove(out)
				writeErr(w, 500, "Conversion failed")
				return
			}
		}
		convertOK = true
	} else {
		out = filepath.Join(avatarsDir, stem+".webp")
		name = stem + ".webp"
		args := []string{"-y", "-i", tmp, "-vf", "scale=256:256:force_original_aspect_ratio=increase,crop=256:256", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "80", out}
		if !runFFmpeg(args, 120*time.Second) || !pfpSmallEnough(out) {
			os.Remove(out)
			args2 := []string{"-y", "-i", tmp, "-vf", "scale=128:128:force_original_aspect_ratio=increase,crop=128:128", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "60", out}
			if !runFFmpeg(args2, 120*time.Second) || !pfpSmallEnough(out) {
				os.Remove(tmp)
				os.Remove(out)
				writeErr(w, 500, "Conversion failed")
				return
			}
		}
		convertOK = true
	}
	os.Remove(tmp)
	if !convertOK {
		writeErr(w, 500, "Conversion failed")
		return
	}
	var old sql.NullString
	dbMu.Lock()
	_ = db.QueryRow("SELECT avatar FROM users WHERE id=?", uid).Scan(&old)
	_, _ = db.Exec("UPDATE users SET avatar=? WHERE id=?", name, uid)
	dbMu.Unlock()
	if old.Valid && old.String != "" && old.String != name {
		os.Remove(filepath.Join(avatarsDir, old.String))
	}
	writeJSON(w, 200, map[string]any{"ok": true, "avatar": "/a/" + name})
}

func handleChannel(w http.ResponseWriter, r *http.Request, name string, viewer int64) {
	clean := make([]rune, 0, len(name))
	for _, c := range name {
		if c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' {
			clean = append(clean, c)
		}
	}
	if len(clean) == 0 {
		writeErr(w, 404, "Not found")
		return
	}
	dbMu.Lock()
	var uid int64
	var un, ca string
	var av sql.NullString
	err := db.QueryRow("SELECT id,username,avatar,created_at FROM users WHERE lower(username)=?", strings.ToLower(string(clean))).Scan(&uid, &un, &av, &ca)
	if err != nil {
		dbMu.Unlock()
		writeErr(w, 404, "Not found")
		return
	}
	var followers, nvideos, views int64
	_ = db.QueryRow("SELECT COUNT(*) FROM follows WHERE followed_id=?", uid).Scan(&followers)
	_ = db.QueryRow("SELECT COUNT(*),COALESCE(SUM(views),0) FROM videos WHERE user_id=?", uid).Scan(&nvideos, &views)
	var nV, nW, nM int64
	_ = db.QueryRow("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='video'", uid).Scan(&nV)
	_ = db.QueryRow("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='wheel'", uid).Scan(&nW)
	_ = db.QueryRow("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='music'", uid).Scan(&nM)
	following := false
	if viewer >= 0 && viewer != uid {
		var one int
		if err := db.QueryRow("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?", viewer, uid).Scan(&one); err == nil {
			following = true
		}
	}
	var ids []int64
	rows, err := db.Query("SELECT id FROM videos WHERE user_id=? ORDER BY id DESC LIMIT 120", uid)
	if err == nil {
		for rows.Next() {
			var id int64
			if err := rows.Scan(&id); err == nil {
				ids = append(ids, id)
			}
		}
		rows.Close()
	}
	dbMu.Unlock()
	avatar := any(nil)
	if av.Valid {
		avatar = "/a/" + av.String
	}
	user := map[string]any{"id": uid, "username": un, "avatar": avatar, "created_at": ca, "followers": followers, "videos": nvideos, "views": views, "following": following, "counts": map[string]any{"video": nV, "wheel": nW, "music": nM}}
	videos := []any{}
	for _, id := range ids {
		if v, ok := videoJSON(id, viewer); ok {
			videos = append(videos, v)
		} else {
			videos = append(videos, nil)
		}
	}
	writeJSON(w, 200, map[string]any{"user": user, "videos": videos})
}

func unlinkRenditions(fn string) {
	stem := fn
	if i := strings.LastIndex(stem, "."); i >= 0 {
		stem = stem[:i]
	}
	os.Remove(filepath.Join(videosDir, stem+"-720p.webm"))
	os.Remove(filepath.Join(videosDir, stem+"-480p.webm"))
	os.Remove(filepath.Join(videosDir, stem+"-360p.webm"))
}

func requireAdmin(w http.ResponseWriter, r *http.Request) (int64, string, bool) {
	uid, un, ok := authUser(r)
	if !ok {
		writeErr(w, 401, "Login required")
		return 0, "", false
	}
	if !isAdmin(uid, un) {
		writeErr(w, 403, "Forbidden")
		return 0, "", false
	}
	return uid, un, true
}

func handleAdminPending(w http.ResponseWriter, r *http.Request) {
	type pending struct {
		ID        int64  `json:"id"`
		Username  string `json:"username"`
		CreatedAt string `json:"created_at"`
	}
	items := []pending{}
	dbMu.Lock()
	rows, err := db.Query("SELECT id,username,created_at FROM users WHERE verified=0 ORDER BY id ASC")
	if err == nil {
		for rows.Next() {
			var p pending
			if err := rows.Scan(&p.ID, &p.Username, &p.CreatedAt); err == nil {
				items = append(items, p)
			}
		}
		rows.Close()
	}
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"pending": items})
}

func handleAdminApprove(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID int64 `json:"id"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	dbMu.Lock()
	res, err := db.Exec("UPDATE users SET verified=1 WHERE id=? AND verified=0", b.ID)
	var changed int64
	if err == nil {
		changed, _ = res.RowsAffected()
	}
	dbMu.Unlock()
	if err != nil || changed == 0 {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func adminDeluserRow(id int64) bool {
	var fns, ths []string
	var uav sql.NullString
	dbMu.Lock()
	rows, err := db.Query("SELECT filename,thumbnail FROM videos WHERE user_id=? LIMIT 64", id)
	if err == nil {
		for rows.Next() {
			var fn string
			var th sql.NullString
			if err := rows.Scan(&fn, &th); err == nil {
				fns = append(fns, fn)
				if th.Valid {
					ths = append(ths, th.String)
				} else {
					ths = append(ths, "")
				}
			}
		}
		rows.Close()
	}
	_ = db.QueryRow("SELECT avatar FROM users WHERE id=?", id).Scan(&uav)
	_, _ = db.Exec("DELETE FROM likes WHERE user_id=?", id)
	_, _ = db.Exec("DELETE FROM likes WHERE video_id IN (SELECT id FROM videos WHERE user_id=?)", id)
	res, err := db.Exec("DELETE FROM users WHERE id=?", id)
	var changed int64
	if err == nil {
		changed, _ = res.RowsAffected()
	}
	dbMu.Unlock()
	if changed == 0 {
		return false
	}
	for i, fn := range fns {
		os.Remove(filepath.Join(videosDir, fn))
		unlinkRenditions(fn)
		if ths[i] != "" {
			os.Remove(filepath.Join(thumbsDir, ths[i]))
		}
	}
	if uav.Valid && uav.String != "" {
		os.Remove(filepath.Join(avatarsDir, uav.String))
	}
	return true
}

func handleAdminReject(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID int64 `json:"id"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	if !adminDeluserRow(b.ID) {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleAdminUsers(w http.ResponseWriter, r *http.Request) {
	type user struct {
		ID            int64   `json:"id"`
		Username      string  `json:"username"`
		Verified      bool    `json:"verified"`
		Role          string  `json:"role"`
		CreatedAt     string  `json:"created_at"`
		Banned        bool    `json:"banned"`
		BanDaysLeft   float64 `json:"ban_days_left"`
		BanReason     string  `json:"ban_reason"`
		BannedUntil   int64   `json:"banned_until"`
		Deleted       bool    `json:"deleted"`
		DeletedReason string  `json:"deleted_reason"`
	}
	items := []user{}
	now := time.Now().Unix()
	dbMu.Lock()
	rows, err := db.Query("SELECT id,username,verified,role,created_at,banned_until,ban_reason,deleted,deleted_reason FROM users ORDER BY id ASC")
	if err == nil {
		for rows.Next() {
			var u user
			var v, del int
			var role, breason, dreason sql.NullString
			var until sql.NullInt64
			if err := rows.Scan(&u.ID, &u.Username, &v, &role, &u.CreatedAt, &until, &breason, &del, &dreason); err == nil {
				u.Verified = v != 0
				if role.Valid {
					u.Role = role.String
				} else {
					u.Role = "user"
				}
				if until.Valid {
					u.BannedUntil = until.Int64
					if until.Int64 > now && del == 0 {
						u.Banned = true
						if until.Int64 > 4102444800 {
							u.BanDaysLeft = -1
						} else {
							u.BanDaysLeft = float64(until.Int64-now) / 86400.0
						}
					}
				}
				if breason.Valid {
					u.BanReason = breason.String
				}
				u.Deleted = del != 0
				if dreason.Valid {
					u.DeletedReason = dreason.String
				}
				items = append(items, u)
			}
		}
		rows.Close()
	}
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"users": items})
}

func handleAdminBan(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID        int64  `json:"id"`
		Days      float64 `json:"days"`
		Hours     float64 `json:"hours"`
		Minutes   float64 `json:"minutes"`
		Permanent *bool  `json:"permanent"`
		Reason    string `json:"reason"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	reason := truncateRunes(b.Reason, 500)
	var until int64
	perm := b.Permanent != nil && *b.Permanent
	if perm {
		until = 9999999999
	} else {
		totalSec := b.Days*86400 + b.Hours*3600 + b.Minutes*60
		if totalSec <= 0 {
			writeErr(w, 400, "Give a ban duration")
			return
		}
		until = time.Now().Unix() + int64(totalSec)
	}
	dbMu.Lock()
	res, err := db.Exec("UPDATE users SET banned_until=?, ban_reason=? WHERE id=?", until, reason, b.ID)
	var changed int64
	if err == nil {
		changed, _ = res.RowsAffected()
	}
	dbMu.Unlock()
	if err != nil || changed == 0 {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "banned_until": until})
}

func handleAdminUnban(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID int64 `json:"id"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE users SET banned_until=0, ban_reason=NULL WHERE id=?", b.ID)
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleAdminSoftDelete(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID     int64  `json:"id"`
		Reason string `json:"reason"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	reason := truncateRunes(b.Reason, 500)
	var fns, ths []string
	dbMu.Lock()
	rows, err := db.Query("SELECT filename,thumbnail FROM videos WHERE user_id=?", b.ID)
	if err == nil {
		for rows.Next() {
			var fn string
			var th sql.NullString
			if err := rows.Scan(&fn, &th); err == nil {
				fns = append(fns, fn)
				if th.Valid {
					ths = append(ths, th.String)
				} else {
					ths = append(ths, "")
				}
			}
		}
		rows.Close()
	}
	_, _ = db.Exec("DELETE FROM likes WHERE video_id IN (SELECT id FROM videos WHERE user_id=?)", b.ID)
	_, _ = db.Exec("DELETE FROM videos WHERE user_id=?", b.ID)
	res, err := db.Exec("UPDATE users SET deleted=1, deleted_reason=?, banned_until=0 WHERE id=?", reason, b.ID)
	var changed int64
	if err == nil {
		changed, _ = res.RowsAffected()
	}
	dbMu.Unlock()
	if err != nil || changed == 0 {
		writeErr(w, 404, "Not found")
		return
	}
	for i, fn := range fns {
		os.Remove(filepath.Join(videosDir, fn))
		unlinkRenditions(fn)
		if ths[i] != "" {
			os.Remove(filepath.Join(thumbsDir, ths[i]))
		}
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleAdminRestore(w http.ResponseWriter, r *http.Request) {
	var b struct {
		ID int64 `json:"id"`
	}
	if !readJSONBody(w, r, &b) || b.ID <= 0 {
		writeErr(w, 400, "Bad request")
		return
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE users SET deleted=0, deleted_reason=NULL, banned_until=0, ban_reason=NULL WHERE id=?", b.ID)
	dbMu.Unlock()
	writeJSON(w, 200, map[string]any{"ok": true})
}

func handleAdminDelUser(w http.ResponseWriter, r *http.Request, id int64, self int64) {
	if id == self {
		writeErr(w, 400, "Cannot remove yourself")
		return
	}
	if !adminDeluserRow(id) {
		writeErr(w, 404, "Not found")
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func dirSize(path string) uint64 {
	var total uint64
	filepath.WalkDir(path, func(_ string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if !d.IsDir() {
			if fi, err := d.Info(); err == nil {
				total += uint64(fi.Size())
			}
		}
		return nil
	})
	return total
}

func storedBytes() uint64 {
	return dirSize(videosDir) + dirSize(thumbsDir) + dirSize(avatarsDir)
}

func runOut(name string, args ...string) (string, bool) {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, name, args...)
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = nil
	if err := cmd.Run(); err != nil {
		return "", false
	}
	return strings.TrimSpace(out.String()), true
}

func shQuote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "'\\''") + "'"
}

func probeHasVideo(path string) bool {
	out, ok := runOut("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", path)
	return ok && strings.Contains(out, "video")
}

func probeHasAudio(path string) bool {
	out, ok := runOut("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", path)
	return ok && strings.Contains(out, "audio")
}

func probeDuration(path string) float64 {
	out, ok := runOut("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path)
	if !ok {
		return 0
	}
	d, _ := strconv.ParseFloat(strings.TrimSpace(out), 64)
	if d < 0 {
		return 0
	}
	return d
}

func probeFormat(path string) string {
	out, ok := runOut("ffprobe", "-v", "error", "-show_entries", "format=format_name", "-of", "csv=p=0", path)
	if !ok {
		return ""
	}
	return strings.ToLower(strings.TrimSpace(out))
}

func probeAV(path string) (vc, ac, fm string, ok bool) {
	out, good := runOut("ffprobe", "-v", "error", "-show_entries", "stream=codec_name,codec_type", "-show_entries", "format=format_name", "-of", "csv=p=0", path)
	if !good {
		return "", "", "", false
	}
	for _, ln := range strings.Split(out, "\n") {
		ln = strings.Trim(ln, "\" \r")
		if strings.Contains(ln, ",video") {
			if i := strings.Index(ln, ","); i > 0 {
				vc = ln[:i]
			}
		} else if strings.Contains(ln, ",audio") {
			if i := strings.Index(ln, ","); i > 0 {
				ac = ln[:i]
			}
		} else if ln != "" {
			fm = ln
		}
	}
	return vc, ac, fm, vc != ""
}

func probeDims(path string) (int64, int64, bool) {
	out, ok := runOut("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", path)
	if !ok {
		return 0, 0, false
	}
	parts := strings.Split(strings.TrimSpace(out), ",")
	if len(parts) != 2 {
		return 0, 0, false
	}
	w, _ := strconv.ParseInt(strings.TrimSpace(parts[0]), 10, 64)
	h, _ := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
	if w <= 0 || h <= 0 {
		return 0, 0, false
	}
	return w, h, true
}

func runFFmpeg(args []string, timeout time.Duration) bool {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, "ffmpeg", args...)
	cmd.Stdout = nil
	cmd.Stderr = nil
	err := cmd.Run()
	return err == nil
}

func makeThumb(vpath, tpath string) bool {
	ok := runFFmpeg([]string{"-y", "-ss", "00:00:01", "-i", vpath, "-vframes", "1", "-vf", "scale=640:-1", "-c:v", "libwebp", "-q:v", "80", tpath}, 60*time.Second)
	if !ok {
		return false
	}
	fi, err := os.Stat(tpath)
	return err == nil && fi.Size() > 0
}

func convertThumb(src, stem string) (string, bool) {
	if !probeHasVideo(src) {
		return "", false
	}
	name := stem + ".webp"
	out := filepath.Join(thumbsDir, name)
	if !runFFmpeg([]string{"-y", "-i", src, "-vf", "scale=640:-1", "-c:v", "libwebp", "-q:v", "80", out}, 120*time.Second) {
		os.Remove(out)
		return "", false
	}
	if fileSize(out) <= 0 {
		os.Remove(out)
		return "", false
	}
	return name, true
}

func transcode(inp, outp string, dur float64, target int64, scale string) bool {
	transcodeSem <- struct{}{}
	defer func() { <-transcodeSem }()
	vbr := int64(1500000)
	if dur > 1.0 {
		vbr = int64((float64(target)*8.0 - 96000.0*dur) / dur)
		if vbr < 200000 {
			vbr = 200000
		}
		if vbr > 8000000 {
			vbr = 8000000
		}
	}
	vk := fmt.Sprintf("%dk", vbr/1000)
	return runFFmpeg([]string{"-y", "-i", inp, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", vk, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", outp}, 4*time.Hour)
}

func fileSize(path string) int64 {
	fi, err := os.Stat(path)
	if err != nil || fi.Size() <= 0 {
		return -1
	}
	return fi.Size()
}

func pfpSmallEnough(path string) bool {
	fi, err := os.Stat(path)
	return err == nil && fi.Size() > 0 && fi.Size() <= 10*1024*1024
}

func spawnRenditions(id int64, stem, src string, h int64) {
	if h <= 480 {
		return
	}
	go processRenditions(id, stem, src, h)
}

func rendOne(src, dst, scale, br string) bool {
	transcodeSem <- struct{}{}
	defer func() { <-transcodeSem }()
	return runFFmpeg([]string{"-y", "-i", src, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", br, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", dst}, 4*time.Hour)
}

func processRenditions(id int64, stem, src string, h int64) {
	p720 := filepath.Join(videosDir, stem+"-720p.webm")
	p480 := filepath.Join(videosDir, stem+"-480p.webm")
	p360 := filepath.Join(videosDir, stem+"-360p.webm")
	has720, has480, has360 := false, false, false
	if h > 720 {
		has720 = rendOne(src, p720, "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "2500k")
	}
	if h > 480 {
		has480 = rendOne(src, p480, "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "1000k")
	}
	if h > 360 {
		has360 = rendOne(src, p360, "scale=640:360:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "500k")
	}
	if !has720 && !has480 && !has360 {
		return
	}
	parts := []string{}
	if has720 {
		parts = append(parts, `"720p":"/v/`+stem+`-720p.webm"`)
	}
	if has480 {
		parts = append(parts, `"480p":"/v/`+stem+`-480p.webm"`)
	}
	if has360 {
		parts = append(parts, `"360p":"/v/`+stem+`-360p.webm"`)
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE videos SET renditions=? WHERE id=?", "{"+strings.Join(parts, ",")+"}", id)
	dbMu.Unlock()
}

func markFailed(id int64) {
	dbMu.Lock()
	_, _ = db.Exec("UPDATE videos SET status='failed' WHERE id=?", id)
	dbMu.Unlock()
}

func notifyFollowers(id, author int64) {
	dbMu.Lock()
	_, _ = db.Exec("INSERT INTO notifications (user_id,video_id,kind) SELECT f.follower_id,?,'upload' FROM follows f JOIN users u ON u.id=f.follower_id WHERE f.followed_id=? AND f.follower_id!=? AND u.notify_uploads=1", id, author, author)
	dbMu.Unlock()
}

func backupOnce() {
	dir := filepath.Join(dataDir, "backups")
	_ = mkdirs(dir)
	dst := filepath.Join(dir, "backup-"+time.Now().Format("20060102-150405")+".sqlite")
	dbMu.Lock()
	_, err := db.Exec("VACUUM INTO ?", dst)
	dbMu.Unlock()
	if err != nil {
		return
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	names := []string{}
	for _, e := range entries {
		n := e.Name()
		if strings.HasPrefix(n, "backup-") && !e.IsDir() {
			names = append(names, n)
		}
	}
	sort.Strings(names)
	for len(names) > 24 {
		os.Remove(filepath.Join(dir, names[0]))
		names = names[1:]
	}
}

func backupLoop() {
	backupOnce()
	t := time.NewTicker(time.Hour)
	defer t.Stop()
	for range t.C {
		backupOnce()
	}
}

func writeMsmtprc() {
	if smtpHost == "" {
		return
	}
	content := "defaults\nauth on\ntls on\ntls_starttls on\ntls_trust_file /etc/ssl/certs/ca-certificates.crt\naccount default\nhost " + smtpHost + "\nport " + smtpPort + "\nfrom " + smtpFrom + "\nuser " + smtpUser + "\npassword " + smtpPass + "\n"
	_ = os.WriteFile("/tmp/msmtprc", []byte(content), 0600)
}

func sendMail(to, subject, body string) {
	if smtpHost == "" {
		println("[mail to=" + to + " subject=" + subject + "]\n" + body)
		return
	}
	cmd := exec.Command("msmtp", "-C", "/tmp/msmtprc", "--from="+smtpFrom, "-t")
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return
	}
	if err := cmd.Start(); err != nil {
		return
	}
	stdin.Write([]byte("To: " + to + "\nFrom: " + smtpFrom + "\nSubject: " + subject + "\nContent-Type: text/plain; charset=utf-8\n\n" + body + "\n"))
	stdin.Close()
	_ = cmd.Wait()
}

func recoverJobs() {
	type row struct {
		id     int64
		author int64
		fn     string
		kind   sql.NullString
	}
	var rows []row
	dbMu.Lock()
	qrows, err := db.Query("SELECT id,user_id,filename,kind FROM videos WHERE status='processing' LIMIT 256")
	if err == nil {
		for qrows.Next() {
			var r row
			if err := qrows.Scan(&r.id, &r.author, &r.fn, &r.kind); err == nil {
				if len(r.fn) >= 6 && r.fn[len(r.fn)-5:] == ".part" {
					rows = append(rows, r)
				} else {
					_, _ = db.Exec("UPDATE videos SET status='failed' WHERE id=?", r.id)
				}
			}
		}
		qrows.Close()
	}
	dbMu.Unlock()
	for _, r := range rows {
		tmp := filepath.Join(videosDir, r.fn)
		fi, err := os.Stat(tmp)
		if err != nil || fi.Size() == 0 {
			markFailed(r.id)
			continue
		}
		stem := r.fn[:len(r.fn)-5]
		id, author := r.id, r.author
		if r.kind.Valid && r.kind.String == "music" {
			go processMusic(id, author, tmp, stem, "")
		} else {
			go processUpload(id, author, tmp, stem, "")
		}
	}
	entries, err := os.ReadDir(videosDir)
	if err != nil {
		return
	}
	for _, e := range entries {
		nm := e.Name()
		if len(nm) < 6 || nm[len(nm)-5:] != ".part" {
			continue
		}
		var one int
		dbMu.Lock()
		err := db.QueryRow("SELECT 1 FROM videos WHERE filename=? AND status='processing'", nm).Scan(&one)
		dbMu.Unlock()
		if err != nil {
			os.Remove(filepath.Join(videosDir, nm))
		}
	}
}

func sweepOrphans() {
	keep := map[string]bool{}
	dbMu.Lock()
	rows, err := db.Query("SELECT filename,thumbnail FROM videos")
	if err == nil {
		for rows.Next() {
			var fn string
			var th sql.NullString
			if err := rows.Scan(&fn, &th); err == nil {
				keep["v:"+fn] = true
				if th.Valid && th.String != "" {
					keep["t:"+th.String] = true
				}
				if i := strings.LastIndex(fn, "."); i > 0 {
					stem := fn[:i]
					keep["v:"+stem+"-720p.webm"] = true
					keep["v:"+stem+"-480p.webm"] = true
					keep["v:"+stem+"-360p.webm"] = true
				}
			}
		}
		rows.Close()
	}
	arows, err := db.Query("SELECT avatar FROM users WHERE avatar IS NOT NULL")
	if err == nil {
		for arows.Next() {
			var av string
			if err := arows.Scan(&av); err == nil && av != "" {
				keep["a:"+av] = true
			}
		}
		arows.Close()
	}
	dbMu.Unlock()
	removed := 0
	for _, d := range []struct {
		dir    string
		prefix string
	}{ {videosDir, "v:"}, {thumbsDir, "t:"}, {avatarsDir, "a:"} } {
		entries, err := os.ReadDir(d.dir)
		if err != nil {
			continue
		}
		for _, e := range entries {
			nm := e.Name()
			if strings.HasPrefix(nm, ".") {
				continue
			}
			if strings.HasSuffix(nm, ".part") {
				continue
			}
			info, err := e.Info()
			if err != nil || !info.Mode().IsRegular() {
				continue
			}
			if !keep[d.prefix+nm] {
				if os.Remove(filepath.Join(d.dir, nm)) == nil {
					removed++
				}
			}
		}
	}
	if removed > 0 {
		fmt.Println("[watchshark] removed", removed, "orphan files")
	}
}

func backfillOrientation() {
	dbMu.Lock()
	var uv int64
	_ = db.QueryRow("PRAGMA user_version").Scan(&uv)
	dbMu.Unlock()
	if uv >= 1 {
		return
	}
	type row struct {
		id int64
		fn string
	}
	var rows []row
	dbMu.Lock()
	qrows, err := db.Query("SELECT id,filename FROM videos LIMIT 512")
	if err == nil {
		for qrows.Next() {
			var r row
			if err := qrows.Scan(&r.id, &r.fn); err == nil {
				rows = append(rows, r)
			}
		}
		qrows.Close()
	}
	dbMu.Unlock()
	for _, r := range rows {
		o := "h"
		if w, h, ok := probeDims(filepath.Join(videosDir, r.fn)); ok && h > w {
			o = "v"
		}
		dbMu.Lock()
		_, _ = db.Exec("UPDATE videos SET orientation=? WHERE id=?", o, r.id)
		dbMu.Unlock()
	}
	dbMu.Lock()
	_, _ = db.Exec("PRAGMA user_version=1")
	dbMu.Unlock()
}

func processUpload(id, author int64, tmp, stem, customThumb string) {
	out := ""
	th := filepath.Join(thumbsDir, stem+".webp")
	thname := stem + ".webp"
	fn := ""
	mt := ""
	var size int64
	good := false
	if fi, err := os.Stat(tmp); err == nil && fi.Size() > 0 {
		if vc, ac, fm, ok := probeAV(tmp); ok {
			if vc == "av1" && (ac == "" || ac == "opus") && (strings.Contains(fm, "webm") || strings.Contains(fm, "matroska")) && fi.Size() <= 100*1024*1024 {
				fn = stem + ".webm"
				mt = "video/webm"
				out = filepath.Join(videosDir, fn)
				if os.Rename(tmp, out) == nil {
					size = fi.Size()
					good = true
				}
			} else {
				dur := probeDuration(tmp)
				fn = stem + ".webm"
				mt = "video/webm"
				out = filepath.Join(videosDir, fn)
				scale := "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
				if dur > 120.0 {
					scale = "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
				}
				transcode(tmp, out, dur, 95*1024*1024, scale)
				if sz := fileSize(out); sz < 0 || sz > 100*1024*1024 {
					os.Remove(out)
					transcode(tmp, out, dur, 80*1024*1024, "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2")
					sz = fileSize(out)
				}
				os.Remove(tmp)
				if sz := fileSize(out); sz > 0 && sz <= 100*1024*1024 {
					size = sz
					good = true
				}
			}
		}
	}
	if !good {
		os.Remove(tmp)
		if out != "" {
			os.Remove(out)
		}
		markFailed(id)
		return
	}
	var thumb *string
	if customThumb != "" {
		if name, ok := convertThumb(customThumb, stem); ok {
			thumb = &name
		}
		os.Remove(customThumb)
	}
	if thumb == nil && makeThumb(out, th) {
		thumb = &thname
	}
	var mimetype *string = &mt
	vw, vh := int64(0), int64(0)
	if w, h, ok := probeDims(out); ok {
		vw, vh = w, h
	}
	ori := "h"
	if vh > vw {
		ori = "v"
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE videos SET filename=?, size=?, thumbnail=?, mimetype=?, orientation=?, status='ready' WHERE id=?", fn, size, thumb, mimetype, ori, id)
	dbMu.Unlock()
	notifyFollowers(id, author)
	if vh > 480 {
		spawnRenditions(id, stem, out, vh)
	}
}

func processMusic(id, author int64, tmp, stem, customThumb string) {
	out := filepath.Join(videosDir, stem+".ogg")
	th := filepath.Join(thumbsDir, stem+".webp")
	thname := stem + ".webp"
	if fi, err := os.Stat(tmp); err != nil || fi.Size() == 0 {
		os.Remove(tmp)
		markFailed(id)
		return
	}
	if !probeHasAudio(tmp) {
		os.Remove(tmp)
		markFailed(id)
		return
	}
	ok := runFFmpeg([]string{"-y", "-i", tmp, "-map", "0:a", "-c:a", "libopus", "-b:a", "128k", out}, 30*time.Minute)
	os.Remove(tmp)
	size := fileSize(out)
	if !ok || size <= 0 || size > 100*1024*1024 {
		os.Remove(out)
		markFailed(id)
		return
	}
	var thumb *string
	if customThumb != "" {
		if name, ok := convertThumb(customThumb, stem); ok {
			thumb = &name
		}
		os.Remove(customThumb)
	}
	if thumb == nil && runFFmpeg([]string{"-y", "-i", out, "-filter_complex", "showwavespic=s=640x360", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "80", th}, 120*time.Second) && fileSize(th) > 0 {
		thumb = &thname
	}
	dbMu.Lock()
	_, _ = db.Exec("UPDATE videos SET filename=?, size=?, thumbnail=?, mimetype=?, orientation=?, status='ready' WHERE id=?", stem+".ogg", size, thumb, "audio/ogg", "h", id)
	dbMu.Unlock()
	notifyFollowers(id, author)
}

var attrEsc = strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;", "\"", "&quot;")

func siteBase() string {
	return strings.TrimSuffix(appURL, "/")
}

func isCrawler(r *http.Request) bool {
	ua := strings.ToLower(r.Header.Get("User-Agent"))
	for _, s := range []string{"whatsapp", "discordbot", "twitterbot", "facebookexternalhit", "telegrambot", "slackbot", "linkedinbot", "pinterest", "googlebot"} {
		if strings.Contains(ua, s) {
			return true
		}
	}
	return false
}

func metaTag(prop, content string) string {
	if content == "" {
		return ""
	}
	return `<meta property="` + prop + `" content="` + attrEsc.Replace(content) + "\">\n"
}

func servePageWithMeta(w http.ResponseWriter, r *http.Request, file, tags string) {
	b, err := os.ReadFile(filepath.Join(publicDir, file))
	if err != nil {
		serveMedia(w, r, filepath.Join(publicDir, file), file)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	if r.Method == "HEAD" {
		w.WriteHeader(200)
		return
	}
	io.WriteString(w, strings.Replace(string(b), "</head>", tags+"</head>", 1))
}

func watchMeta(id int64) string {
	v, ok := videoJSON(id, -1)
	if !ok {
		return ""
	}
	str := func(k string) string {
		if s, ok := v[k].(string); ok {
			return s
		}
		return ""
	}
	title := str("title")
	if title == "" {
		title = "WatchShark video"
	}
	desc := truncateRunes(str("description"), 200)
	if desc == "" {
		viewsStr := ""
		if vw, ok := v["views"].(int64); ok {
			viewsStr = strconv.FormatInt(vw, 10) + " views"
		}
		by := str("username")
		if by != "" {
			by = "@" + by
		}
		desc = strings.TrimSpace(by + " • " + viewsStr)
		if desc == "" {
			desc = title
		}
	}
	kind := str("kind")
	src := str("src")
	thumb, _ := v["thumbnail"].(string)
	var sb strings.Builder
	sb.WriteString(metaTag("og:site_name", "WatchShark"))
	sb.WriteString(`<meta name="twitter:card" content="summary_large_image">` + "\n")
	sb.WriteString(metaTag("og:url", siteBase()+"/watch?id="+strconv.FormatInt(id, 10)))
	sb.WriteString(metaTag("og:title", title))
	sb.WriteString(metaTag("og:description", desc))
	if kind == "music" {
		sb.WriteString(metaTag("og:type", "music.song"))
		if src != "" {
			sb.WriteString(metaTag("og:audio", siteBase()+src))
			sb.WriteString(metaTag("og:audio:type", "audio/ogg"))
		}
		if thumb != "" {
			sb.WriteString(metaTag("og:image", siteBase()+thumb))
		} else {
			sb.WriteString(metaTag("og:image", siteBase()+"/watchshark.webp"))
		}
		return sb.String()
	}
	sb.WriteString(metaTag("og:type", "video.other"))
	if thumb != "" {
		sb.WriteString(metaTag("og:image", siteBase()+thumb))
	} else {
		sb.WriteString(metaTag("og:image", siteBase()+"/watchshark.webp"))
	}
	if src != "" {
		sb.WriteString(metaTag("og:video", siteBase()+src))
		sb.WriteString(metaTag("og:video:secure_url", siteBase()+src))
		mt := str("mimetype")
		if mt == "" {
			mt = "video/mp4"
		}
		sb.WriteString(metaTag("og:video:type", mt))
		if w, h, ok := probeDims(filepath.Join(videosDir, strings.TrimPrefix(src, "/v/"))); ok {
			sb.WriteString(metaTag("og:video:width", strconv.FormatInt(w, 10)))
			sb.WriteString(metaTag("og:video:height", strconv.FormatInt(h, 10)))
		}
	}
	return sb.String()
}

func channelMeta(name string) string {
	clean := make([]rune, 0, len(name))
	for _, c := range name {
		if c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' {
			clean = append(clean, c)
		}
	}
	if len(clean) == 0 {
		return ""
	}
	var uid int64
	var un string
	var av sql.NullString
	var followers, nvideos int64
	dbMu.Lock()
	err := db.QueryRow("SELECT id,username,avatar FROM users WHERE lower(username)=?", strings.ToLower(string(clean))).Scan(&uid, &un, &av)
	if err == nil {
		_ = db.QueryRow("SELECT COUNT(*) FROM follows WHERE followed_id=?", uid).Scan(&followers)
		_ = db.QueryRow("SELECT COUNT(*) FROM videos WHERE user_id=?", uid).Scan(&nvideos)
	}
	dbMu.Unlock()
	if err != nil {
		return ""
	}
	img := siteBase() + "/watchshark.webp"
	if av.Valid && av.String != "" {
		img = siteBase() + "/a/" + av.String
	}
	var sb strings.Builder
	sb.WriteString(metaTag("og:site_name", "WatchShark"))
	sb.WriteString(metaTag("og:type", "website"))
	sb.WriteString(`<meta name="twitter:card" content="summary_large_image">` + "\n")
	sb.WriteString(metaTag("og:url", siteBase()+"/channel?user="+un))
	sb.WriteString(metaTag("og:title", "@"+un+" on WatchShark"))
	sb.WriteString(metaTag("og:description", strconv.FormatInt(nvideos, 10)+" videos • "+strconv.FormatInt(followers, 10)+" followers on WatchShark."))
	sb.WriteString(metaTag("og:image", img))
	return sb.String()
}

func route(w http.ResponseWriter, r *http.Request) {
	m := r.Method
	u := r.URL.Path
	if strings.HasPrefix(u, "/api/auth/") {
		if !authLimiter.allow(clientIP(r)) {
			writeErr(w, 429, "Rate limit exceeded, slow down")
			return
		}
	} else if strings.HasPrefix(u, "/api/") {
		if !apiLimiter.allow(clientIP(r)) {
			writeErr(w, 429, "Rate limit exceeded, slow down")
			return
		}
	}
	if (m == "GET" || m == "HEAD") && isCrawler(r) {
		q := r.URL.Query()
		if u == "/watch" {
			if id, rest, ok := parseID(q.Get("id")); ok && rest == "" {
				if tags := watchMeta(id); tags != "" {
					servePageWithMeta(w, r, "watch.html", tags)
					return
				}
			}
		}
		if u == "/channel" {
			if tags := channelMeta(q.Get("user")); tags != "" {
				servePageWithMeta(w, r, "channel.html", tags)
				return
			}
		}
	}
	cleanPages := map[string]string{
		"/watch": "watch.html",
		"/channel": "channel.html",
		"/wheels": "wheels.html",
		"/music": "music.html",
		"/upload": "upload.html",
		"/settings": "settings.html",
		"/forgot": "forgot.html",
		"/reset": "reset.html",
		"/verify": "verify.html",
		"/admin": "admin.html",
	}
	if u == "/shorts" || u == "/shorts.html" {
		dest := "/wheels"
		if r.URL.RawQuery != "" {
			dest += "?" + r.URL.RawQuery
		}
		http.Redirect(w, r, dest, http.StatusMovedPermanently)
		return
	}
	if f, ok := cleanPages[u]; ok && (m == "GET" || m == "HEAD") {
		serveMedia(w, r, filepath.Join(publicDir, f), f)
		return
	}
	if strings.HasSuffix(u, ".html") {
		if _, ok := cleanPages[strings.TrimSuffix(u, ".html")]; ok {
			dest := strings.TrimSuffix(u, ".html")
			if r.URL.RawQuery != "" {
				dest += "?" + r.URL.RawQuery
			}
			http.Redirect(w, r, dest, http.StatusMovedPermanently)
			return
		}
	}
	switch {
	case m == "GET" && u == "/health":
		writeJSON(w, 200, map[string]any{"ok": true})
		return
	case m == "GET" && u == "/api/me":
		handleMe(w, r)
		return
	case m == "POST" && u == "/api/auth/signup":
		handleSignup(w, r)
		return
	case m == "POST" && u == "/api/auth/login":
		handleLogin(w, r)
		return
	case m == "POST" && u == "/api/auth/logout":
		handleLogout(w, r)
		return
	case m == "GET" && u == "/api/auth/verify":
		handleVerify(w, r)
		return
	case m == "POST" && u == "/api/auth/resend":
		handleResend(w, r)
		return
	case m == "POST" && u == "/api/auth/forgot":
		handleForgot(w, r)
		return
	case m == "POST" && u == "/api/auth/reset":
		handleReset(w, r)
		return
	case m == "POST" && u == "/api/auth/change":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleChange(w, r, uid)
		return
	case m == "POST" && u == "/api/auth/username":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleUsername(w, r, uid)
		return
	case m == "POST" && u == "/api/settings/notifications":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleNotifSet(w, r, uid)
		return
	case m == "GET" && (u == "/api/videos" || u == "/api/videos/"):
		handleList(w, r)
		return
	case m == "GET" && u == "/api/wheels":
		handleWheels(w, r)
		return
	case m == "POST" && (u == "/api/videos" || u == "/api/videos/"):
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleUpload(w, r, uid)
		return
	case m == "POST" && u == "/api/pfp":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handlePfp(w, r, uid)
		return
	case m == "GET" && u == "/api/notifications":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleNotifications(w, r, uid)
		return
	case m == "POST" && u == "/api/notifications/read":
		uid, _, ok := authUser(r)
		if !ok {
			writeErr(w, 401, "Login required")
			return
		}
		handleNotifRead(w, r, uid)
		return
	case m == "GET" && u == "/api/admin/pending":
		uid, un, ok := requireAdmin(w, r)
		if !ok {
			return
		}
		_ = uid
		_ = un
		handleAdminPending(w, r)
		return
	case m == "POST" && u == "/api/admin/approve":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminApprove(w, r)
		return
	case m == "POST" && u == "/api/admin/reject":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminReject(w, r)
		return
	case m == "GET" && u == "/api/admin/users":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminUsers(w, r)
		return
	case m == "POST" && u == "/api/admin/ban":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminBan(w, r)
		return
	case m == "POST" && u == "/api/admin/unban":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminUnban(w, r)
		return
	case m == "POST" && u == "/api/admin/soft-delete":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminSoftDelete(w, r)
		return
	case m == "POST" && u == "/api/admin/restore":
		if _, _, ok := requireAdmin(w, r); !ok {
			return
		}
		handleAdminRestore(w, r)
		return
	}
	if strings.HasPrefix(u, "/api/videos/") {
		rest := u[len("/api/videos/"):]
		if id, leftover, ok := parseID(rest); ok {
			switch {
			case m == "GET" && leftover == "":
				handleGetVideo(w, r, id)
				return
			case m == "DELETE" && leftover == "":
				handleDeleteVideo(w, r, id)
				return
			case m == "POST" && leftover == "/like":
				handleLike(w, r, id)
				return
		case m == "POST" && leftover == "/comments":
			handleComment(w, r, id)
			return
		case m == "POST" && leftover == "/thumbnail":
			handleThumb(w, r, id)
			return
		case m == "POST" && leftover == "/edit":
			handleEditVideo(w, r, id)
			return
			}
		}
		writeErr(w, 404, "Not found")
		return
	}
	if strings.HasPrefix(u, "/api/follow/") {
		if id, leftover, ok := parseID(u[len("/api/follow/"):]); ok && leftover == "" && m == "POST" {
			uid, _, ok := authUser(r)
			if !ok {
				writeErr(w, 401, "Login required")
				return
			}
			handleFollow(w, r, uid, id)
			return
		}
		writeErr(w, 404, "Not found")
		return
	}
	if strings.HasPrefix(u, "/api/channel/") {
		if m == "GET" && len(u) > len("/api/channel/") {
			viewer := int64(-1)
			if id, _, ok := authUser(r); ok {
				viewer = id
			}
			handleChannel(w, r, u[len("/api/channel/"):], viewer)
			return
		}
		writeErr(w, 404, "Not found")
		return
	}
	if strings.HasPrefix(u, "/api/admin/users/") {
		if id, leftover, ok := parseID(u[len("/api/admin/users/"):]); ok && leftover == "" && m == "DELETE" {
			uid, _, ok := requireAdmin(w, r)
			if !ok {
				return
			}
			handleAdminDelUser(w, r, id, uid)
			return
		}
		writeErr(w, 404, "Not found")
		return
	}
	if m == "GET" || m == "HEAD" {
		serveStatic(w, r, u)
		return
	}
	writeErr(w, 404, "Not found")
}

func main() {
	port := envOr("PORT", "3000")
	dataDir = envOr("DATA_DIR", "data")
	publicDir = envOr("PUBLIC_DIR", "public")
	videosDir = dataDir + "/videos"
	thumbsDir = dataDir + "/thumbs"
	avatarsDir = dataDir + "/avatars"
	jwtSecret = envOr("JWT_SECRET", "CHANGE_ME_watchshark_secret")
	if os.Getenv("JWT_SECRET") == "" {
		fmt.Println("[watchshark] WARNING: JWT_SECRET not set, using default!")
	}
	maxBytes = envULL("MAX_UPLOAD_MB", 4096) * 1024 * 1024
	quotaBytes = envULL("QUOTA_GB", 50) * 1024 * 1024 * 1024
	adminUser = strings.ToLower(envOr("ADMIN_USER", ""))
	appURL = envOr("APP_URL", "https://watchshark.duckdns.org")
	smtpHost = envOr("SMTP_HOST", "")
	smtpPort = envOr("SMTP_PORT", "587")
	smtpUser = envOr("SMTP_USER", "")
	smtpPass = envOr("SMTP_PASS", "")
	smtpFrom = envOr("SMTP_FROM", "")
	if smtpFrom == "" {
		smtpFrom = smtpUser
	}
	dbPath = dataDir + "/db.sqlite"
	_ = mkdirs(dataDir)
	_ = mkdirs(videosDir)
	_ = mkdirs(thumbsDir)
	_ = mkdirs(avatarsDir)
	if err := openDB(); err != nil {
		fmt.Println("db open:", err)
		os.Exit(1)
	}
	writeMsmtprc()
	authLimiter = newRateLimiter(30, time.Hour)
	apiLimiter = newRateLimiter(120, time.Minute)
	recoverJobs()
	backfillOrientation()
	sweepOrphans()
	go backupLoop()
	srv := &http.Server{Addr: "0.0.0.0:" + port, Handler: withSecurity(route)}
	go func() {
		fmt.Println("[watchshark] (go) up on :" + port + " data=" + dataDir)
		if err := srv.ListenAndServe(); err != http.ErrServerClosed {
			fmt.Println("serve:", err)
		}
	}()
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGTERM, syscall.SIGINT)
	<-sig
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}
