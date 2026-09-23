import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import '../api.dart';
import '../main.dart';
import '../widgets.dart';
import 'channel.dart';

class WatchScreen extends StatefulWidget {
  final int videoId;
  const WatchScreen({super.key, required this.videoId});

  @override
  State<WatchScreen> createState() => _WatchScreenState();
}

class _WatchScreenState extends State<WatchScreen> {
  VideoPlayerController? _ctrl;
  Video? _video;
  List<Comment> _comments = [];
  bool _loading = true;
  String? _error;
  bool _controls = true;
  bool _muted = false;
  bool _fullscreen = false;
  double _speed = 1.0;
  String _quality = 'Auto';
  ApiUser? _me;
  final _commentCtrl = TextEditingController();

  final _speeds = [1.0, 1.25, 1.5, 2.0, 0.5];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final res = await api.videoDetail(widget.videoId);
      final me = await api.me();
      if (!mounted) return;
      setState(() {
        _video = res['video'] as Video;
        _comments = (res['comments'] as List).cast<Comment>();
        _me = me;
        _loading = false;
      });
      _initPlayer();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  String _srcFor(Video v, String label) {
    if (label == 'Auto' || label == 'Source') {
      return api.full(v.src)!;
    }
    return api.full(v.renditions[label]) ?? api.full(v.src)!;
  }

  Future<void> _initPlayer() async {
    final v = _video;
    if (v == null) return;
    final old = _ctrl;
    final pos = await old?.position;
    final playing = old?.value.isPlaying ?? false;
    await old?.dispose();
    final c = VideoPlayerController.networkUrl(
      Uri.parse(_srcFor(v, _quality)),
      httpHeaders: api.token != null ? {'Cookie': 'ws_token=${api.token}'} : {},
    );
    setState(() => _ctrl = c);
    await c.initialize();
    await c.setPlaybackSpeed(_speed);
    if (_muted) await c.setVolume(0);
    if (pos != null) await c.seekTo(pos);
    if (playing || pos == null) await c.play();
    c.addListener(() {
      if (mounted) setState(() {});
    });
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _ctrl?.dispose();
    _commentCtrl.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
    super.dispose();
  }

  Future<void> _setFullscreen(bool on) async {
    setState(() => _fullscreen = on);
    if (on) {
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    } else {
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      await SystemChrome.setPreferredOrientations(
          [DeviceOrientation.portraitUp]);
    }
  }

  Future<void> _toggleLike() async {
    try {
      final res = await api.like(widget.videoId);
      if (!mounted) return;
      setState(() {
        _video!.liked = res['liked'] as bool;
        _video!.likes = res['likes'] as int;
      });
    } catch (e) {
      if (!mounted) return;
      if (e.toString().contains('Login')) {
        showDialog(
            context: context, builder: (_) => const _LoginNeeded());
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> _toggleFollow() async {
    final v = _video;
    if (v == null) return;
    try {
      final res = await api.follow(v.userId);
      if (!mounted) return;
      setState(() {
        v.following = res['following'] as bool;
        v.followers = res['followers'] as int;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  Future<void> _sendComment() async {
    final text = _commentCtrl.text.trim();
    if (text.isEmpty) return;
    try {
      await api.comment(widget.videoId, text);
      if (!mounted) return;
      _commentCtrl.clear();
      final res = await api.videoDetail(widget.videoId);
      if (!mounted) return;
      setState(() => _comments = (res['comments'] as List).cast<Comment>());
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  String _fmt(int secs) {
    final m = secs ~/ 60;
    final s = (secs % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final c = _ctrl;
    final v = _video;
    if (_loading || v == null) {
      return Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
            backgroundColor: const Color(0xFF111111),
            title: const Text('Watch')),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : Center(child: Text(_error ?? 'Error')),
      );
    }
    final video = v;
    if (_fullscreen) {
      return PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _setFullscreen(false);
        },
        child: Scaffold(
          backgroundColor: Colors.black,
          body: Center(child: _playerBox()),
        ),
      );
    }
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
          backgroundColor: const Color(0xFF111111),
          title: const Text('Watch')),
      body: ListView(
                  children: [                    _playerBox(),
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text(video.title.isEmpty ? 'Untitled' : video.title,
                          style: const TextStyle(
                              fontSize: 17, fontWeight: FontWeight.bold)),
                    ),
                    Padding(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 12),
                      child: Row(
                        children: [
                          InkWell(
                            onTap: () => Navigator.of(context).push(
                                MaterialPageRoute(
                                    builder: (_) => ChannelScreen(
                                        username: video.username))),
                            child: Row(
                              children: [
                                UserAvatar(url: video.avatar, radius: 14),
                                const SizedBox(width: 8),
                                Text('@${video.username}'),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          if (_me != null && _me!.id != video.userId)
                            OutlinedButton(
                              onPressed: _toggleFollow,
                              child: Text(video.following
                                  ? 'Following'
                                  : 'Follow'),
                            ),
                          const SizedBox(width: 8),
                          FilledButton.tonal(
                            onPressed: _toggleLike,
                            child: Text(
                                '${video.liked ? '♥ ' : ''}${fmtNum(video.likes)}'),
                          ),
                        ],
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text(
                        '${fmtNum(video.views)} views • ${timeAgo(video.createdAt)}',
                        style: const TextStyle(
                            color: Color(0xFFA8A8A8), fontSize: 12),
                      ),
                    ),
                    if (video.description.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text(video.description,
                            style: const TextStyle(
                                color: Color(0xFFDDDDDD), fontSize: 13)),
                      ),
                    const Padding(
                      padding: EdgeInsets.fromLTRB(12, 16, 12, 4),
                      child: Text('Comments',
                          style: TextStyle(
                              fontSize: 16, fontWeight: FontWeight.bold)),
                    ),
                    Padding(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 12),
                      child: Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _commentCtrl,
                              style:
                                  const TextStyle(color: Colors.white),
                              decoration: const InputDecoration(
                                  hintText: 'Add a comment...',
                                  border: OutlineInputBorder()),
                            ),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                              onPressed: _sendComment,
                              child: const Text('Post')),
                        ],
                      ),
                    ),
                    ..._comments.map((cm) => ListTile(
                          leading: UserAvatar(
                              url: cm.avatar, radius: 14),
                          title: Text('@${cm.username}',
                              style: const TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.bold)),
                          subtitle: Column(
                            crossAxisAlignment:
                                CrossAxisAlignment.start,
                            children: [
                              Text(cm.body,
                                  style: const TextStyle(fontSize: 14)),
                              Text(timeAgo(cm.createdAt),
                                  style: const TextStyle(
                                      color: Color(0xFF888888),
                                      fontSize: 11)),
                            ],
                          ),
                        )),
                    const SizedBox(height: 24),
                  ],
                ),
    );
  }

  Widget _playerBox() {
    final c = _ctrl;
    final video = _video;
    if (c == null || video == null) return const SizedBox();
    final pos = c.value.position;
    final dur = c.value.duration;
    final playing = c.value.isPlaying;
    return GestureDetector(
      onTap: () => setState(() => _controls = !_controls),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Container(
          color: const Color(0xFF333333),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (c.value.isInitialized) ClipRect(child: VideoPlayer(c)),
              if (c.value.isBuffering && !c.value.isPlaying)
                const Center(child: CircularProgressIndicator()),
              if (_controls)
                Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.transparent,
                        Color.fromRGBO(0, 0, 0, 200)
                      ],
                    ),
                  ),
                  padding: const EdgeInsets.fromLTRB(10, 2, 10, 8),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Slider(
                        value: dur.inMilliseconds > 0
                            ? pos.inMilliseconds /
                                dur.inMilliseconds *
                                1000
                            : 0,
                        min: 0,
                        max: 1000,
                        activeColor: Colors.white,
                        inactiveColor: Colors.white24,
                        onChanged: (x) {
                          c.seekTo(Duration(
                              milliseconds: (x / 1000 *
                                      dur.inMilliseconds)
                                  .round()));
                        },
                      ),
                      Row(
                        children: [
                          _pillBtn(
                              playing
                                  ? Symbols.pause_sharp
                                  : Symbols.play_arrow_sharp,
                              () => playing ? c.pause() : c.play()),
                          _pillBtn(
                              _muted
                                  ? Symbols.volume_off_sharp
                                  : Symbols.volume_up_sharp, () async {
                            setState(() => _muted = !_muted);
                            await c.setVolume(_muted ? 0 : 1);
                          }),
                          Expanded(
                            child: Text(
                              '${_fmt(pos.inSeconds)} / ${_fmt(dur.inSeconds)}',
                              style: const TextStyle(
                                  color: Colors.white, fontSize: 13),
                            ),
                          ),
                          TextButton(
                            onPressed: () {
                              setState(() {
                                _speed = _speeds[
                                    (_speeds.indexOf(_speed) + 1) %
                                        _speeds.length];
                              });
                              c.setPlaybackSpeed(_speed);
                            },
                            child: Text(
                                '${_speed == _speed.truncateToDouble() ? _speed.toInt() : _speed}x',
                                style:
                                    const TextStyle(color: Colors.white)),
                          ),
                          PopupMenuButton<String>(
                            child: Padding(
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 10),
                              child: Text(_quality,
                                  style:
                                      const TextStyle(color: Colors.white)),
                            ),
                            onSelected: (q) {
                              setState(() => _quality = q);
                              _initPlayer();
                            },
                            itemBuilder: (_) {
                              final items = [
                                'Auto',
                                ...video.renditions.keys,
                                'Source'
                              ];
                              return items
                                  .map((q) => PopupMenuItem(
                                      value: q, child: Text(q)))
                                  .toList();
                            },
                          ),
                          _pillBtn(
                              _fullscreen
                                  ? Symbols.fullscreen_exit_sharp
                                  : Symbols.fullscreen_sharp,
                              () => _setFullscreen(!_fullscreen)),
                        ],
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
  Widget _pillBtn(IconData icon, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(right: 2),
      child: Material(
        color: const Color(0xFF2B2B2B),
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Icon(icon, color: Colors.white, size: 22),
          ),
        ),
      ),
    );
  }
}

class _LoginNeeded extends StatelessWidget {
  const _LoginNeeded();
  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF1E1E1E),
      title: const Text('Login required'),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close')),
      ],
    );
  }
}
