import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import 'watch.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  List<Notif> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final res = await api.notifications();
      if (!mounted) return;
      setState(() {
        _items = (res['items'] as List).cast<Notif>();
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    }
  }

  Future<void> _open(Notif n) async {
    try {
      await api.notifRead(n.id);
    } catch (_) {}
    if (!mounted) return;
    if (n.videoId != null && n.videoId! > 0) {
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => WatchScreen(videoId: n.videoId!)));
    } else {
      _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: const Color(0xFF111111),
        title: const Text('Notifications'),
        actions: [
          TextButton(
            onPressed: () async {
              try {
                await api.notifRead();
              } catch (_) {}
              _load();
            },
            child: const Text('Mark all read'),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _items.isEmpty
              ? const Center(
                  child: Text(
                      'No notifications yet. Follow channels to get upload alerts.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Color(0xFFA8A8A8))))
              : ListView.builder(
                  itemCount: _items.length,
                  itemBuilder: (ctx, i) {
                    final n = _items[i];
                    final title = n.kind == 'delete'
                        ? 'Video removed: ${n.title}'
                        : '@${n.username} uploaded: ${n.title}';
                    final sub = n.kind == 'delete'
                        ? n.text
                        : n.createdAt;
                    return ListTile(
                      tileColor: n.read ? null : const Color(0xFF1F1F1F),
                      title: Text(title,
                          style: const TextStyle(fontSize: 14)),
                      subtitle: Text(sub,
                          style: const TextStyle(
                              color: Color(0xFFA8A8A8), fontSize: 12)),
                      onTap: () => _open(n),
                    );
                  },
                ),
    );
  }
}
