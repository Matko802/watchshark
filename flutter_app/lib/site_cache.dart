import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:path_provider/path_provider.dart';

/// Offline mirror of the site's static files.
///
/// Every HTML/CSS/JS/image of watchshark.duckdns.org is fetched from the
/// internet on first view and stored on disk. If the network fails later,
/// the stored copy is served instead, so the app shell still opens offline.
/// Videos, thumbnails, avatars and API calls always go to the network.
class SiteCache {
  static const _host = 'watchshark.duckdns.org';
  static const _maxAge = Duration(days: 30);

  Directory? _dir;

  Future<Directory> _dirFuture() async {
    final d = _dir;
    if (d != null) return d;
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/site');
    if (!await dir.exists()) await dir.create(recursive: true);
    _dir = dir;
    return dir;
  }

  bool _cacheable(String path) {
    if (path.startsWith('/api/') ||
        path.startsWith('/v/') ||
        path.startsWith('/t/') ||
        path.startsWith('/a/')) {
      return false;
    }
    return true;
  }

  String _key(String path, String query) {
    var name = path;
    if (name.isEmpty || name == '/') return 'index.html';
    name = name.startsWith('/') ? name.substring(1) : name;
    name = name.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_');
    if (query.isNotEmpty) {
      final q = query.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_');
      name = '${name}__$q';
    }
    if (!name.contains('.')) name = '$name.html';
    return name;
  }

  String _mime(String key) {
    if (key.endsWith('.html')) return 'text/html';
    if (key.endsWith('.css')) return 'text/css';
    if (key.endsWith('.js')) return 'application/javascript';
    if (key.endsWith('.png')) return 'image/png';
    if (key.endsWith('.webp')) return 'image/webp';
    if (key.endsWith('.ico')) return 'image/x-icon';
    if (key.endsWith('.svg')) return 'image/svg+xml';
    if (key.endsWith('.json')) return 'application/json';
    return 'text/plain';
  }

  bool _textual(String key) {
    return key.endsWith('.html') ||
        key.endsWith('.css') ||
        key.endsWith('.js') ||
        key.endsWith('.json') ||
        key.endsWith('.svg');
  }

  Future<WebResourceResponse?> handle(WebUri? url, String? method) async {
    try {
      if (method != null && method != 'GET') return null;
      if (url == null || url.host != _host) return null;
      final path = url.path.isEmpty ? '/' : url.path;
      if (!_cacheable(path)) return null;
      final key = _key(path, url.query);
      final dir = await _dirFuture();
      final file = File('${dir.path}/$key');
      try {
        final req = await HttpClient()
            .getUrl(Uri.parse(url.toString()))
            .timeout(const Duration(seconds: 15));
        final resp = await req.close().timeout(const Duration(seconds: 15));
        if (resp.statusCode == 200) {
          final bytes = await resp.fold<List<int>>(
              [], (all, chunk) => all..addAll(chunk));
          final data = Uint8List.fromList(bytes);
          try {
            await file.writeAsBytes(data, flush: true);
          } catch (_) {}
          return WebResourceResponse(
            data: data,
            statusCode: 200,
            reasonPhrase: 'OK',
            contentType: _mime(key),
            contentEncoding: _textual(key) ? 'utf-8' : null,
          );
        }
      } catch (_) {}
      if (await file.exists()) {
        try {
          await file.setLastAccessed(DateTime.now());
        } catch (_) {}
        final data = await file.readAsBytes();
        return WebResourceResponse(
          data: data,
          statusCode: 200,
          reasonPhrase: 'OK',
          contentType: _mime(key),
          contentEncoding: _textual(key) ? 'utf-8' : null,
        );
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<void> prune() async {
    try {
      final dir = await _dirFuture();
      final cutoff = DateTime.now().subtract(_maxAge);
      await for (final e in dir.list()) {
        if (e is File) {
          try {
            final stat = await e.stat();
            if (stat.accessed.isBefore(cutoff)) await e.delete();
          } catch (_) {}
        }
      }
    } catch (_) {}
  }
}
