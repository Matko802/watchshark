import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiError implements Exception {
  final String message;
  ApiError(this.message);
  @override
  String toString() => message;
}

class Video {
  final int id;
  final String title;
  final String description;
  final String username;
  final int userId;
  final String src;
  final String? thumbnail;
  final int views;
  int likes;
  bool liked;
  final int comments;
  int followers;
  bool following;
  final String createdAt;
  final String? avatar;
  final String status;
  final String kind;
  final Map<String, String> renditions;

  Video({
    required this.id,
    required this.title,
    required this.description,
    required this.username,
    required this.userId,
    required this.src,
    required this.thumbnail,
    required this.views,
    required this.likes,
    required this.liked,
    required this.comments,
    required this.followers,
    required this.following,
    required this.createdAt,
    required this.avatar,
    required this.status,
    required this.kind,
    required this.renditions,
  });

  static int _i(dynamic v) => v is int ? v : int.tryParse('$v') ?? 0;
  static String _s(dynamic v) => v?.toString() ?? '';

  factory Video.fromJson(Map<String, dynamic> j) {
    final rend = <String, String>{};
    final r = j['renditions'];
    if (r is Map) {
      r.forEach((k, v) {
        if (v != null) rend['$k'] = '$v';
      });
    }
    return Video(
      id: _i(j['id']),
      title: _s(j['title']),
      description: _s(j['description']),
      username: _s(j['username']),
      userId: _i(j['user_id']),
      src: _s(j['src']),
      thumbnail: j['thumbnail']?.toString(),
      views: _i(j['views']),
      likes: _i(j['likes']),
      liked: j['liked'] == true,
      comments: _i(j['comments']),
      followers: _i(j['followers']),
      following: j['following'] == true,
      createdAt: _s(j['created_at']),
      avatar: j['avatar']?.toString(),
      status: _s(j['status']),
      kind: _s(j['kind']).isEmpty ? 'video' : _s(j['kind']),
      renditions: rend,
    );
  }
}

class ApiUser {
  final int id;
  final String username;
  final bool admin;
  final String? avatar;
  final bool notifyUploads;
  final String since;
  final bool banned;
  final double banDaysLeft;
  final String? banReason;
  final bool deleted;
  final String? deletedReason;

  ApiUser({
    required this.id,
    required this.username,
    required this.admin,
    required this.avatar,
    required this.notifyUploads,
    required this.since,
    required this.banned,
    required this.banDaysLeft,
    required this.banReason,
    required this.deleted,
    required this.deletedReason,
  });

  factory ApiUser.fromJson(Map<String, dynamic> j) {
    return ApiUser(
      id: Video._i(j['id']),
      username: Video._s(j['username']),
      admin: j['admin'] == true,
      avatar: j['avatar']?.toString(),
      notifyUploads: j['notify_uploads'] != false,
      since: Video._s(j['since']),
      banned: j['banned'] == true,
      banDaysLeft: (j['ban_days_left'] as num?)?.toDouble() ?? 0.0,
      banReason: j['ban_reason']?.toString(),
      deleted: j['deleted'] == true,
      deletedReason: j['deleted_reason']?.toString(),
    );
  }
}

class Comment {
  final int id;
  final String body;
  final String createdAt;
  final String username;
  final String? avatar;

  Comment({required this.id, required this.body, required this.createdAt, required this.username, required this.avatar});

  factory Comment.fromJson(Map<String, dynamic> j) => Comment(
        id: Video._i(j['id']),
        body: Video._s(j['body']),
        createdAt: Video._s(j['created_at']),
        username: Video._s(j['username']),
        avatar: j['avatar']?.toString(),
      );
}

class ChannelInfo {
  final int id;
  final String username;
  final String? avatar;
  final String createdAt;
  final int followers;
  final int videos;
  final int views;
  bool following;

  ChannelInfo({required this.id, required this.username, required this.avatar, required this.createdAt, required this.followers, required this.videos, required this.views, required this.following});

  factory ChannelInfo.fromJson(Map<String, dynamic> j) => ChannelInfo(
        id: Video._i(j['id']),
        username: Video._s(j['username']),
        avatar: j['avatar']?.toString(),
        createdAt: Video._s(j['created_at']),
        followers: Video._i(j['followers']),
        videos: Video._i(j['videos']),
        views: Video._i(j['views']),
        following: j['following'] == true,
      );
}

class Notif {
  final int id;
  final int? videoId;
  final String createdAt;
  final bool read;
  final String title;
  final String username;
  final String kind;
  final String text;

  Notif({required this.id, required this.videoId, required this.createdAt, required this.read, required this.title, required this.username, required this.kind, required this.text});

  factory Notif.fromJson(Map<String, dynamic> j) {
    final v = j['video_id'];
    return Notif(
      id: Video._i(j['id']),
      videoId: v == null ? null : Video._i(v),
      createdAt: Video._s(j['created_at']),
      read: j['read'] == true,
      title: Video._s(j['title']),
      username: Video._s(j['username']),
      kind: Video._s(j['kind']),
      text: Video._s(j['text']),
    );
  }
}

class AdminUser {
  final int id;
  final String username;
  final bool verified;
  final String role;
  final String createdAt;
  final bool banned;
  final double banDaysLeft;
  final String banReason;
  final bool deleted;
  final String deletedReason;

  AdminUser({required this.id, required this.username, required this.verified, required this.role, required this.createdAt, required this.banned, required this.banDaysLeft, required this.banReason, required this.deleted, required this.deletedReason});

  factory AdminUser.fromJson(Map<String, dynamic> j) => AdminUser(
        id: Video._i(j['id']),
        username: Video._s(j['username']),
        verified: j['verified'] == true,
        role: Video._s(j['role']).isEmpty ? 'user' : Video._s(j['role']),
        createdAt: Video._s(j['created_at']),
        banned: j['banned'] == true,
        banDaysLeft: (j['ban_days_left'] as num?)?.toDouble() ?? 0.0,
        banReason: Video._s(j['ban_reason']),
        deleted: j['deleted'] == true,
        deletedReason: Video._s(j['deleted_reason']),
      );
}

String fmtNum(int n) {
  if (n < 1000) return '$n';
  if (n >= 1000000000) return '${_trim(n / 1000000000)}B';
  if (n >= 1000000) return '${_trim(n / 1000000)}M';
  return '${_trim(n / 1000)}K';
}

String _trim(double x) {
  if (x >= 100) return x.round().toString();
  var s = x.toStringAsFixed(1);
  if (s.endsWith('.0')) s = s.substring(0, s.length - 2);
  return s;
}

String fmtDur(int totalSec) {
  final m = totalSec ~/ 60;
  final s = (totalSec % 60).toString().padLeft(2, '0');
  return '$m:$s';
}

class Api {
  static const base = 'https://watchshark.duckdns.org';
  String? token;

  Map<String, String> get _headers =>
      token != null ? {'Cookie': 'ws_token=$token'} : {};

  dynamic _decode(http.Response r) {
    dynamic body;
    try {
      body = json.decode(r.body);
    } catch (_) {
      throw ApiError('Server error (${r.statusCode})');
    }
    if (body is Map && body['error'] != null) {
      throw ApiError('${body['error']}');
    }
    if (r.statusCode < 200 || r.statusCode >= 300) {
      throw ApiError('Request failed (${r.statusCode})');
    }
    return body;
  }

  Future<dynamic> get(String path, [Map<String, String>? query]) async {
    final uri = Uri.parse('$base$path').replace(queryParameters: query);
    final r = await http.get(uri, headers: _headers);
    return _decode(r);
  }

  Future<dynamic> postJson(String path, Map<String, dynamic> body) async {
    final r = await http.post(Uri.parse('$base$path'),
        headers: {..._headers, 'Content-Type': 'application/json'},
        body: json.encode(body));
    return _decode(r);
  }

  void _saveSession(http.BaseResponse r) {
    final setCookie = r.headers['set-cookie'];
    if (setCookie == null) return;
    final m = RegExp(r'ws_token=([^;]+)').firstMatch(setCookie);
    if (m != null) {
      token = m.group(1);
      SharedPreferences.getInstance()
          .then((p) => p.setString('ws_token', token!));
    }
  }

  Future<ApiUser> login(String login, String password) async {
    final uri = Uri.parse('$base/api/auth/login');
    final r = await http.post(uri,
        headers: {'Content-Type': 'application/json'},
        body: json.encode({'login': login, 'password': password}));
    final body = _decode(r);
    _saveSession(r);
    final u = (body as Map)['user'] as Map<String, dynamic>;
    return ApiUser.fromJson(u);
  }

  Future<ApiUser> signup(String username, String email, String password) async {
    final uri = Uri.parse('$base/api/auth/signup');
    final r = await http.post(uri,
        headers: {'Content-Type': 'application/json'},
        body: json.encode(
            {'username': username, 'email': email, 'password': password}));
    final body = _decode(r);
    _saveSession(r);
    final u = (body as Map)['user'] as Map<String, dynamic>;
    return ApiUser.fromJson(u);
  }

  Future<void> forgot(String email) async {
    await postJson('/api/auth/forgot', {'email': email});
  }

  Future<void> logout() async {    try {
      await postJson('/api/auth/logout', {});
    } catch (_) {}
    token = null;
    final p = await SharedPreferences.getInstance();
    await p.remove('ws_token');
  }

  Future<ApiUser?> me() async {
    try {
      final body = await get('/api/me') as Map<String, dynamic>;
      final u = body['user'];
      if (u == null) return null;
      return ApiUser.fromJson((u as Map).cast<String, dynamic>());
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, dynamic>> videos({
    String q = '',
    String sort = 'new',
    int page = 1,
    int limit = 12,
    String kind = 'video',
  }) async {
    final query = <String, String>{
      'sort': sort,
      'page': '$page',
      'limit': '$limit',
      'kind': kind,
    };
    if (q.isNotEmpty) {
      query['q'] = q;
    }
    final body = await get('/api/videos', query) as Map<String, dynamic>;
    final list = (body['videos'] as List? ?? [])
        .whereType<Map>()
        .map((e) => Video.fromJson(e.cast<String, dynamic>()))
        .toList();
    return {
      'videos': list,
      'page': body['page'] ?? page,
      'pages': body['pages'] ?? 0,
      'total': body['total'] ?? 0,
    };
  }

  Future<Map<String, dynamic>> videoDetail(int id) async {
    final body = await get('/api/videos/$id') as Map<String, dynamic>;
    final v = Video.fromJson((body['video'] as Map).cast<String, dynamic>());
    final comments = ((body['comments'] as List?) ?? [])
        .whereType<Map>()
        .map((e) => Comment.fromJson(e.cast<String, dynamic>()))
        .toList();
    return {'video': v, 'comments': comments};
  }

  Future<Map<String, dynamic>> like(int id) async {
    final body = await postJson('/api/videos/$id/like', {}) as Map<String, dynamic>;
    return {'liked': body['liked'] == true, 'likes': Video._i(body['likes'])};
  }

  Future<void> comment(int id, String text) async {
    await postJson('/api/videos/$id/comments', {'body': text});
  }

  Future<Map<String, dynamic>> follow(int userId) async {
    final body = await postJson('/api/follow/$userId', {}) as Map<String, dynamic>;
    return {
      'following': body['following'] == true,
      'followers': Video._i(body['followers'])
    };
  }

  Future<Map<String, dynamic>> channel(String name) async {
    final body = await get('/api/channel/$name') as Map<String, dynamic>;
    final u = ChannelInfo.fromJson((body['user'] as Map).cast<String, dynamic>());
    final list = ((body['videos'] as List?) ?? [])
        .whereType<Map>()
        .map((e) => Video.fromJson(e.cast<String, dynamic>()))
        .toList();
    return {'user': u, 'videos': list};
  }

  Future<Video> wheels([List<int> seen = const []]) async {
    final q = <String, String>{};
    if (seen.isNotEmpty) {
      q['seen'] = seen.take(128).join(',');
    }
    final body = await get('/api/wheels', q) as Map<String, dynamic>;
    return Video.fromJson((body['video'] as Map).cast<String, dynamic>());
  }

  Future<Map<String, dynamic>> notifications() async {
    final body = await get('/api/notifications') as Map<String, dynamic>;
    final list = ((body['notifications'] as List?) ?? [])
        .whereType<Map>()
        .map((e) => Notif.fromJson(e.cast<String, dynamic>()))
        .toList();
    return {'items': list, 'unread': Video._i(body['unread'])};
  }

  Future<void> notifRead([int? id]) async {
    await postJson('/api/notifications/read', id == null ? {} : {'id': id});
  }

  Future<void> pfpUpload(String path) async {
    final mime = path.toLowerCase().endsWith('.png')
        ? 'image/png'
        : path.toLowerCase().endsWith('.gif')
            ? 'image/gif'
            : path.toLowerCase().endsWith('.webp')
                ? 'image/webp'
                : 'image/jpeg';
    final file = File(path);
    final r = await http.post(Uri.parse('$base/api/pfp'),
        headers: {..._headers, 'Content-Type': mime},
        body: await file.readAsBytes());
    _decode(r);
  }

  Future<void> rename(String username) async {    await postJson('/api/auth/username', {'username': username});
  }

  Future<void> changePw(String current, String password) async {
    await postJson('/api/auth/change', {'current': current, 'password': password});
  }

  Future<void> notifSet(bool uploads) async {
    await postJson('/api/settings/notifications', {'uploads': uploads});
  }

  Future<List<AdminUser>> adminUsers() async {
    final body = await get('/api/admin/users') as Map<String, dynamic>;
    return ((body['users'] as List?) ?? [])
        .whereType<Map>()
        .map((e) => AdminUser.fromJson(e.cast<String, dynamic>()))
        .toList();
  }

  Future<void> adminBan(int id,
      {double days = 0, double hours = 0, String reason = '', bool permanent = false}) async {
    await postJson('/api/admin/ban', {
      'id': id,
      'days': days,
      'hours': hours,
      'reason': reason,
      'permanent': permanent,
    });
  }

  Future<void> adminUnban(int id) async {
    await postJson('/api/admin/unban', {'id': id});
  }

  Future<void> adminDelete(int id, String reason) async {
    await postJson('/api/admin/soft-delete', {'id': id, 'reason': reason});
  }

  Future<void> adminRestore(int id) async {
    await postJson('/api/admin/restore', {'id': id});
  }

  Future<void> adminRemove(int id) async {
    final uri = Uri.parse('$base/api/admin/users/$id');
    final r = await http.delete(uri, headers: _headers);
    _decode(r);
  }

  Future<void> adminApprove(int id) async {
    await postJson('/api/admin/approve', {'id': id});
  }

  Future<Map<String, dynamic>> upload({
    required String title,
    required String description,
    required String kind,
    required String filePath,
    required String mime,
    String? thumbPath,
    String? thumbMime,
  }) async {
    final req = http.MultipartRequest('POST', Uri.parse('$base/api/videos'));
    if (token != null) req.headers['Cookie'] = 'ws_token=$token';
    req.fields['title'] = title;
    req.fields['description'] = description;
    req.fields['kind'] = kind;
    req.files.add(await http.MultipartFile.fromPath('file', filePath));
    if (thumbPath != null) {
      req.files.add(await http.MultipartFile.fromPath('thumb', thumbPath));
    }
    final resp = await req.send();
    final text = await resp.stream.bytesToString();
    dynamic body;
    try {
      body = json.decode(text);
    } catch (_) {
      throw ApiError('Upload failed (${resp.statusCode})');
    }
    if (body is Map && body['error'] != null) throw ApiError('${body['error']}');
    if (resp.statusCode < 200 || resp.statusCode >= 300) {
      throw ApiError('Upload failed (${resp.statusCode})');
    }
    return (body as Map).cast<String, dynamic>();
  }

  String? full(String? path) {
    if (path == null || path.isEmpty) return null;
    if (path.startsWith('http')) return path;
    return '$base$path';
  }
}
