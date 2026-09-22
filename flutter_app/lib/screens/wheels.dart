import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../api.dart';
import '../main.dart';
import 'channel.dart';
import 'watch.dart';

class WheelsScreen extends StatefulWidget {
  const WheelsScreen({super.key});

  @override
  State<WheelsScreen> createState() => _WheelsScreenState();
}

class _Reel {
  final Video video;
  VideoPlayerController? ctrl;
  bool ready = false;
  _Reel(this.video);
}

class _WheelsScreenState extends State<WheelsScreen> {
  final List<_Reel> _reels = [];
  final List<int> _seen = [];
  final _pageCtrl = PageController();
  int _page = 0;
  bool _loading = true;
  bool _exhausted = false;
  bool _muted = true;
  ApiUser? _me;

  @override
  void initState() {
    super.initState();
    _loadSeen();
    _boot();
  }

  Future<void> _boot() async {
    _me = await api.me();
    await _loadBatch();
    if (!mounted) return;
    setState(() => _loading = false);
    if (_reels.isNotEmpty) _playAt(0);
  }

  Future<void> _loadSeen() async {}

  Future<void> _loadBatch() async {
    if (_exhausted) return;
    int added = 0;
    for (var i = 0; i < 5; i++) {
      try {
        final v = await api.wheels(_seen);
        if (_seen.contains(v.id)) continue;
        _seen.add(v.id);
        if (_seen.length > 400) _seen.removeAt(0);
        _reels.add(_Reel(v));
        added++;
      } catch (_) {}
    }
    if (added == 0) _exhausted = true;
    if (mounted) setState(() {});
  }

  Future<void> _playAt(int i) async {
    for (var j = 0; j < _reels.length; j++) {
      final r = _reels[j];
      if (j == i) {
        if (r.ctrl == null) {
          final c = VideoPlayerController.networkUrl(
            Uri.parse(api.full(r.video.src)!),
            httpHeaders:
                api.token != null ? {'Cookie': 'ws_token=${api.token}'} : {},
          );
          r.ctrl = c;
          await c.initialize();
          await c.setLooping(true);
          if (_muted) await c.setVolume(0);
        }
        await r.ctrl!.play();
        setState(() => r.ready = true);
      } else {
        await r.ctrl?.pause();
      }
    }
    if (i >= _reels.length - 3) {
      await _loadBatch();
    }
    const keep = 9;
    if (_reels.length > keep + 4) {
      for (var j = 0; j < _reels.length - keep; j++) {
        await _reels[j].ctrl?.dispose();
        _reels[j].ctrl = null;
        _reels[j].ready = false;
      }
    }
  }

  @override
  void dispose() {
    for (final r in _reels) {
      r.ctrl?.dispose();
    }
    _pageCtrl.dispose();
    super.dispose();
  }

  Future<void> _toggleLike(_Reel r) async {
    try {
      final res = await api.like(r.video.id);
      if (!mounted) return;
      setState(() {
        r.video.liked = res['liked'] as bool;
        r.video.likes = res['likes'] as int;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: const Color(0xFF111111),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Image.asset('assets/logo.webp', width: 28, height: 28),
            const SizedBox(width: 8),
            const Text('Wheels'),
          ],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _reels.isEmpty
              ? const Center(child: Text('No videos yet'))
              : PageView.builder(
                  controller: _pageCtrl,
                  scrollDirection: Axis.vertical,
                  itemCount: _reels.length,
                  onPageChanged: (i) {
                    _page = i;
                    _playAt(i);
                  },
                  itemBuilder: (ctx, i) => _reelPage(_reels[i]),
                ),
    );
  }

  Widget _reelPage(_Reel r) {
    final v = r.video;
    return Stack(
      fit: StackFit.expand,
      children: [
        GestureDetector(
          onTap: () {
            final c = r.ctrl;
            if (c == null) return;
            if (c.value.isPlaying) {
              c.pause();
            } else {
              c.play();
            }
            setState(() {});
          },
          child: Container(
            color: Colors.black,
            child: (r.ctrl != null && r.ctrl!.value.isInitialized)
                ? VideoPlayer(r.ctrl!)
                : v.thumbnail != null
                    ? Image.network(api.full(v.thumbnail)!,
                        fit: BoxFit.contain)
                    : const SizedBox(),
          ),
        ),
        if (r.ctrl != null && r.ctrl!.value.isBuffering)
          const Center(child: CircularProgressIndicator()),
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Colors.transparent, Color.fromRGBO(0, 0, 0, 200)],
              ),
            ),
            padding: const EdgeInsets.fromLTRB(14, 30, 90, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(v.title.isEmpty ? 'Untitled' : v.title,
                    style: const TextStyle(
                        fontSize: 15, fontWeight: FontWeight.w500),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis),
                const SizedBox(height: 6),
                InkWell(
                  onTap: () => Navigator.of(context).push(
                      MaterialPageRoute(
                          builder: (_) =>
                              ChannelScreen(username: v.username))),
                  child: Text('@${v.username} • ${fmtNum(v.views)} views',
                      style: const TextStyle(
                          color: Color(0xFFDDDDDD), fontSize: 12)),
                ),
              ],
            ),
          ),
        ),
        Positioned(
          right: 8,
          bottom: 110,
          child: Column(
            children: [
              _railBtn(
                  v.liked ? Icons.favorite : Icons.favorite_border,
                  v.liked ? Colors.red : Colors.white, () => _toggleLike(r)),
              const SizedBox(height: 14),
              _railBtn(
                  _muted ? Icons.volume_off : Icons.volume_up,
                  Colors.white, () async {
                setState(() => _muted = !_muted);
                for (final x in _reels) {
                  await x.ctrl?.setVolume(_muted ? 0 : 1);
                }
              }),
              const SizedBox(height: 14),
              _railBtn(Icons.open_in_new, Colors.white, () {
                r.ctrl?.pause();
                Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => WatchScreen(videoId: v.id)));
              }),
              const SizedBox(height: 14),
              PopupMenuButton<String>(
                icon: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: const BoxDecoration(
                      color: Color(0xFF2B2B2B), shape: BoxShape.circle),
                  child: const Icon(Icons.settings,
                      color: Colors.white, size: 24),
                ),
                onSelected: (q) async {
                  final c = r.ctrl;
                  if (c == null) return;
                  final pos = await c.position ?? Duration.zero;
                  final playing = c.value.isPlaying;
                  final url = q == 'Source'
                      ? api.full(v.src)!
                      : api.full(v.renditions[q])!;
                  await c.pause();
                  await r.ctrl?.dispose();
                  final nc = VideoPlayerController.networkUrl(
                    Uri.parse(url),
                    httpHeaders: api.token != null
                        ? {'Cookie': 'ws_token=${api.token}'}
                        : {},
                  );
                  r.ctrl = nc;
                  await nc.initialize();
                  await nc.setLooping(true);
                  if (_muted) await nc.setVolume(0);
                  await nc.seekTo(pos);
                  if (playing) await nc.play();
                  setState(() {});
                },
                itemBuilder: (_) {
                  final items = ['Auto', ...v.renditions.keys, 'Source'];
                  return items
                      .map((q) =>
                          PopupMenuItem(value: q, child: Text(q)))
                      .toList();
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _railBtn(IconData icon, Color color, VoidCallback onTap) {
    return Material(
      color: const Color(0xFF2B2B2B),
      shape: const CircleBorder(),
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Icon(icon, color: color, size: 24),
        ),
      ),
    );
  }
}
