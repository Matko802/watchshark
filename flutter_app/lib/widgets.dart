import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:video_player/video_player.dart';
import '../api.dart';
import '../main.dart';
import '../tab_index.dart';
import 'screens/channel.dart';
import 'screens/notifications.dart';
import 'screens/settings.dart';
import 'screens/auth.dart';
import 'screens/upload.dart';
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
        if (me != null) ...[
          BellButton(me: me!, onMeChanged: onMeChanged),
          IconButton(
            icon: const Icon(Symbols.settings_sharp),
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
              _unread > 0 ? Symbols.notifications_active_sharp : Symbols.notifications_sharp,
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
      return Icon(Symbols.account_circle_sharp,
          size: radius * 2, color: const Color(0xFFA8A8A8));
    }
    final lower = full.toLowerCase();
    if (lower.endsWith('.webm')) {
      return ClipOval(child: _AvatarVideo(url: full, radius: radius));
    }
    return ClipOval(
      child: Image.network(full,
          width: radius * 2,
          height: radius * 2,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Icon(Symbols.account_circle_sharp,
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
                          const Icon(Symbols.movie_sharp, color: Colors.grey))
                  : const Center(
                      child: Icon(Symbols.movie_sharp, color: Colors.grey)),
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

class BottomNav extends StatefulWidget {
  /// 0 = home, 1 = wheels, 2 = music, anything else = none highlighted.
  final int current;
  final ValueChanged<int> onTab;
  final ValueChanged<ApiUser?> onMeChanged;
  const BottomNav(
      {super.key,
      required this.current,
      required this.onTab,
      required this.onMeChanged});

  @override
  State<BottomNav> createState() => _BottomNavState();
}

class _BottomNavState extends State<BottomNav> {
  ApiUser? _me;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final me = await api.me();
    if (mounted) setState(() => _me = me);
  }

  void _goTab(int i) {
    Navigator.of(context).popUntil((r) => r.isFirst);
    widget.onTab(i);
    shellTab.value = i;
  }

  void _accountTap() {
    final me = _me;
    if (me == null) {
      showDialog(
        context: context,
        builder: (_) => AuthDialog(onMeChanged: (u) {
          widget.onMeChanged(u);
          _load();
        }),
      ).then((_) => _load());
    } else {
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ChannelScreen(username: me.username)));
    }
  }

  @override
  Widget build(BuildContext context) {
    const active = Colors.white;
    const idle = Color(0xFFA8A8A8);
    Widget item({
      required int tab,
      required Widget icon,
      required String label,
      required VoidCallback onTap,
    }) {
      final sel = widget.current == tab;
      return Expanded(
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 9),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconTheme(
                  data: IconThemeData(
                      color: sel ? active : idle, size: 24),
                  child: icon,
                ),
                const SizedBox(height: 3),
                Text(label,
                    style: TextStyle(
                        color: sel ? active : idle,
                        fontSize: 11,
                        fontWeight: FontWeight.w500)),
              ],
            ),
          ),
        ),
      );
    }

    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF111111),
        border: Border(top: BorderSide(color: Color(0xFF242424))),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            item(
              tab: 0,
              icon: const Icon(Symbols.home_sharp),
              label: 'Home',
              onTap: () => _goTab(0),
            ),
            item(
              tab: 1,
              icon: const Icon(Symbols.movie_sharp),
              label: 'Wheels',
              onTap: () => _goTab(1),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6),
              child: Material(
                color: const Color(0xFFF5F5F5),
                shape: const CircleBorder(),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: () => Navigator.of(context).push(
                      MaterialPageRoute(
                          builder: (_) => UploadScreen(me: _me))),
                  child: const Padding(
                    padding: EdgeInsets.all(12),
                    child: Icon(Symbols.add_sharp,
                        color: Colors.black, size: 24),
                  ),
                ),
              ),
            ),
            item(
              tab: 2,
              icon: const Icon(Symbols.music_note_sharp),
              label: 'Music',
              onTap: () => _goTab(2),
            ),
            _me == null
                ? item(
                    tab: -1,
                    icon: const Icon(Symbols.person_sharp),
                    label: 'You',
                    onTap: _accountTap,
                  )
                : Expanded(
                    child: InkWell(
                      onTap: _accountTap,
                      child: Padding(
                        padding:
                            const EdgeInsets.symmetric(vertical: 9),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            UserAvatar(
                                url: _me!.avatar, radius: 12),
                            const SizedBox(height: 3),
                            const Text('You',
                                style: TextStyle(
                                    color: idle,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w500)),
                          ],
                        ),
                      ),
                    ),
                  ),
          ],
        ),
      ),
    );
  }
}

class _AvatarVideo extends StatefulWidget {
  final String url;
  final double radius;
  const _AvatarVideo({required this.url, required this.radius});

  @override
  State<_AvatarVideo> createState() => _AvatarVideoState();
}

class _AvatarVideoState extends State<_AvatarVideo> {
  VideoPlayerController? _ctrl;

  @override
  void initState() {
    super.initState();
    final c = VideoPlayerController.networkUrl(Uri.parse(widget.url));
    _ctrl = c;
    c.initialize().then((_) async {
      if (!mounted) return;
      await c.setLooping(true);
      await c.setVolume(0);
      await c.play();
      if (mounted) setState(() {});
    }).catchError((_) {});
  }

  @override
  void dispose() {
    _ctrl?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = _ctrl;
    if (c == null || !c.value.isInitialized) {
      return Icon(Symbols.account_circle_sharp,
          size: widget.radius * 2, color: const Color(0xFFA8A8A8));
    }
    return SizedBox(
      width: widget.radius * 2,
      height: widget.radius * 2,
      child: FittedBox(
        fit: BoxFit.cover,
        clipBehavior: Clip.hardEdge,
        child: SizedBox(
          width: c.value.size.width == 0
              ? widget.radius * 2
              : c.value.size.width,
          height: c.value.size.height == 0
              ? widget.radius * 2
              : c.value.size.height,
          child: VideoPlayer(c),
        ),
      ),
    );
  }
}
