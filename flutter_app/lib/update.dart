import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:open_filex/open_filex.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

class _Update {
  final String version;
  final String notes;
  final String url;
  final int size;
  _Update(this.version, this.notes, this.url, this.size);
}

List<int> _parseVer(String v) {
  final m = RegExp(r'(\d+)\.(\d+)\.(\d+)').firstMatch(v);
  if (m == null) return [0, 0, 0];
  return [int.parse(m.group(1)!), int.parse(m.group(2)!), int.parse(m.group(3)!)];
}

int _cmpVer(List<int> a, List<int> b) {
  for (var i = 0; i < 3; i++) {
    if (a[i] != b[i]) return a[i].compareTo(b[i]);
  }
  return 0;
}

String _fmtSize(int n) {
  if (n <= 0) return '';
  if (n >= 1048576) return '${(n / 1048576).toStringAsFixed(0)} MB';
  return '${(n / 1024).toStringAsFixed(0)} KB';
}

/// Checks GitHub releases for a newer Dart (`flutter-v*` + `dart` APK) build.
Future<void> checkForUpdate(BuildContext context,
    {bool manual = false}) async {
  try {
    final info = await PackageInfo.fromPlatform();
    final current = _parseVer(info.version);
    final res = await http
        .get(Uri.parse(
            'https://api.github.com/repos/Matko802/watchshark/releases?per_page=30'))
        .timeout(const Duration(seconds: 10));
    if (res.statusCode != 200) throw Exception('bad status');
    final releases = (json.decode(res.body) as List).cast<Map>();
    _Update? best;
    for (final r in releases) {
      final tag = '${r['tag_name'] ?? ''}';
      if (!tag.startsWith('flutter-v')) continue;
      final ver = _parseVer(tag);
      if (_cmpVer(ver, current) <= 0) continue;
      final assets = ((r['assets'] as List?) ?? []).cast<Map>();
      Map? apk;
      for (final a in assets) {
        final name = '${a['name'] ?? ''}';
        if (!name.endsWith('.apk') || !name.contains('arm64')) continue;
        if (!name.contains('dart')) continue;
        apk = a;
        break;
      }
      if (apk == null) continue;
      final cand = _Update(tag, '${r['body'] ?? ''}',
          '${apk['browser_download_url'] ?? ''}', (apk['size'] as int?) ?? 0);
      if (cand.url.isEmpty) continue;
      if (best == null ||
          _cmpVer(_parseVer(tag), _parseVer(best.version)) > 0) {
        best = cand;
      }
    }
    if (!context.mounted) return;
    if (best == null) {
      if (manual) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Already on the latest version')),
        );
      }
      return;
    }
    final update = best;
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        title: Text('Update available (${update.version})'),
        content: Text(
          '${update.notes}\n\nSize: ${_fmtSize(update.size)}',
          style: const TextStyle(color: Color(0xFFA8A8A8), fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Later'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Update'),
          ),
        ],
      ),
    );
    if (go != true || !context.mounted) return;
    await _downloadAndInstall(context, update);
  } on TimeoutException {
    if (manual && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Update check timed out')),
      );
    }
  } catch (_) {
    if (manual && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not check for updates')),
      );
    }
  }
}
Future<void> _downloadAndInstall(BuildContext context, _Update update) {
  return showDialog(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => _DownloadDialog(update: update),
  );
}

class _DownloadDialog extends StatefulWidget {
  final _Update update;
  const _DownloadDialog({required this.update});

  @override
  State<_DownloadDialog> createState() => _DownloadDialogState();
}

class _DownloadDialogState extends State<_DownloadDialog> {
  double _progress = 0;
  int _received = 0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    try {
      final client = http.Client();
      try {
        final req = http.Request('GET', Uri.parse(widget.update.url));
        final resp =
            await client.send(req).timeout(const Duration(seconds: 15));
        if (resp.statusCode != 200) {
          throw Exception('HTTP ${resp.statusCode}');
        }
        final total = resp.contentLength ?? widget.update.size;
        final dir = await getTemporaryDirectory();
        final file = File('${dir.path}/watchshark-update.apk');
        if (await file.exists()) await file.delete();
        final sink = file.openWrite();
        try {
          await for (final chunk in resp.stream.timeout(
              const Duration(seconds: 30),
              onTimeout: (EventSink<List<int>> sink) {
            sink.close();
            throw TimeoutException('stalled download');
          })) {
            _received += chunk.length;
            sink.add(chunk);
            if (total > 0 && mounted) {
              setState(() => _progress = _received / total);
            }
          }
        } finally {
          await sink.close();
        }
        final result = await OpenFilex.open(
          file.path,
          type: 'application/vnd.android.package-archive',
        );
        if (result.type != ResultType.done) {
          throw Exception('Could not open installer (${result.message})');
        }
        if (mounted) Navigator.of(context).pop();
      } finally {
        client.close();
      }
    } on TimeoutException {
      if (mounted) {
        setState(() => _error = 'Download timed out');
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = e.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF1E1E1E),
      title: const Text('Downloading update'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          LinearProgressIndicator(
              value: _error != null
                  ? 0
                  : (_progress == 0 ? null : _progress)),
          const SizedBox(height: 8),
          Text(_error ??
              '${(_progress * 100).round()}% • ${_fmtSize(_received)} / ${_fmtSize(widget.update.size)}'),
        ],
      ),
      actions: _error != null
          ? [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Close'),
              ),
            ]
          : null,
    );
  }
}
