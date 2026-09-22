import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import 'screens/channel.dart';
import 'screens/notifications.dart';
import 'screens/settings.dart';
import 'screens/auth.dart';
import 'screens/watch.dart';
import 'screens/wheels.dart';
import 'screens/music.dart';
import 'screens/admin.dart';

String timeAgo(String s) {
  final t = DateTime.tryParse(s.replaceAll(' ', 'T'))?.toLocal();
  if (t == null) return s;
  final sec = DateTime.now().difference(t).inSeconds.clamp(0, 1 << 31);
  if (sec < 60) return sec <= 1 ? '1 second ago' : '$sec seconds ago';
  final m = sec ~/ 60;
  if (m < 60) return m == 1 ? '1 minute ago' : '$m minutes ago';
  final h = m ~/ 60;
  if (h < 24) return h == 1 ? '1 hour ago' : '$h hours ago';
  final d = h ~/ 24;
  if (d < 30) return d == 1 ? '1 day ago' : '$d days ago';
  return '${(d / 30).floor()} months ago';
}

class TopBar extends StatelessWidget implements PreferredSizeWidget {
  final ApiUser? me;
  final ValueChanged<ApiUser?> onMeChanged;
  final VoidCallback? onSearchTap;

  const TopBar({super.key, required this.me, required this.onMeChanged, this.onSearchTap});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      backgroundColor: const Color(0xFF111111),
      titleSpacing: 8,
      title: InkWell(
        onTap: () {
          Navigator.of(context).popUntil((r) => r.isFirst);
        },
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Image.asset('assets/logo.webp', width: 32, height: 32),
            const SizedBox(width: 10),
            const Text('WatchShark', style: TextStyle(fontSize: 18)),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => const WheelsScreen())),
          child: const Text('Wheels'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => const MusicScreen())),
          child: const Text('Music'),
        ),
        if (me != null) ...[
          BellButton(me: me!, onMeChanged: onMeChanged),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            color: Colors.white,
            onPressed: () => Navigator.of(context)
                .push(MaterialPageRoute(
                    builder: (_) =>
                        SettingsScreen(me: me!, onMeChanged: onMeChanged)))
                .then((_) async => onMeChanged(await api.me())),
          ),
          if (me!.admin)
            TextButton(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => const AdminScreen())),
              child: const Text('Admin'),
            ),
          InkWell(
            onTap: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => ChannelScreen(username: me!.username))),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: UserAvatar(url: me!.avatar, radius: 14),
            ),
          ),
        ] else
          FilledButton(
            onPressed: () => showDialog(
              context: context,
              builder: (_) => AuthDialog(onMeChanged: onMeChanged),
            ).then((_) async => onMeChanged(await api.me())),
            child: const Text('Sign in'),
          ),
      ],
    );
  }
}

// Navigation targets live in screens/.

class BellButton extends StatefulWidget {
  final ApiUser me;
  final ValueChanged<ApiUser?> onMeChanged;
  const BellButton({super.key, required this.me, required this.onMeChanged});

  @override
  State<BellButton> createState() => _BellButtonState();
}

class _BellButtonState extends State<BellButton> {
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final n = await api.notifications();
      if (mounted) setState(() => _unread = n['unread'] as int);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: [
        IconButton(
          icon: Icon(
              _unread > 0 ? Icons.notifications_active : Icons.notifications_outlined,
              color: Colors.white),
          onPressed: () => Navigator.of(context)
              .push(MaterialPageRoute(
                  builder: (_) => const NotificationsScreen()))
              .then((_) => _load()),
        ),
        if (_unread > 0)
          Positioned(
            top: 6,
            right: 6,
            child: Container(
              constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
              padding: const EdgeInsets.symmetric(horizontal: 5),
              decoration: BoxDecoration(
                color: const Color(0xFFE0E0E0),
                borderRadius: BorderRadius.circular(9),
              ),
              child: Center(
                child: Text(_unread > 9 ? '9+' : '$_unread',
                    style: const TextStyle(
                        color: Colors.black,
                        fontSize: 11,
                        fontWeight: FontWeight.bold)),
              ),
            ),
          ),
      ],
    );
  }
}

class UserAvatar extends StatelessWidget {
  final String? url;
  final double radius;
  const UserAvatar({super.key, required this.url, this.radius = 14});

  @override
  Widget build(BuildContext context) {
    final full = api.full(url);
    if (full == null) {
      return Icon(Icons.account_circle,
          size: radius * 2, color: const Color(0xFFA8A8A8));
    }
    final lower = full.toLowerCase();
    if (lower.endsWith('.webm')) {
      return Icon(Icons.account_circle,
          size: radius * 2, color: const Color(0xFFA8A8A8));
    }
    return ClipOval(
      child: Image.network(full,
          width: radius * 2,
          height: radius * 2,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Icon(Icons.account_circle,
              size: radius * 2, color: const Color(0xFFA8A8A8))),
    );
  }
}

class VideoCard extends StatelessWidget {
  final Video video;
  const VideoCard({super.key, required this.video});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => WatchScreen(videoId: video.id))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: Container(
              color: Colors.black,
              child: video.thumbnail != null
                  ? Image.network(api.full(video.thumbnail)!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) =>
                          const Icon(Icons.movie, color: Colors.grey))
                  : const Center(
                      child: Icon(Icons.movie, color: Colors.grey)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 10, 4, 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                UserAvatar(url: video.avatar, radius: 14),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        (video.status != 'ready' ? '⏳ Processing… ' : '') +
                            (video.title.isEmpty ? 'Untitled' : video.title),
                        style: const TextStyle(
                            fontSize: 14, fontWeight: FontWeight.w500),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '@${video.username} • ${fmtNum(video.views)} views • ${timeAgo(video.createdAt)}',
                        style: const TextStyle(
                            color: Color(0xFFA8A8A8), fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
