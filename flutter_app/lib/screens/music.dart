import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:just_audio/just_audio.dart';
import '../api.dart';
import '../main.dart';
import 'channel.dart';
import '../tab_index.dart';
import '../widgets.dart';

class MusicTab extends StatefulWidget {
  const MusicTab({super.key});

  @override
  State<MusicTab> createState() => _MusicTabState();
}

class _MusicTabState extends State<MusicTab>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;
  List<Video> _tracks = [];
  int _ti = -1;
  String _sort = 'new';
  bool _loading = true;
  bool _sheetOpen = false;
  late AudioPlayer _player;
  Duration _pos = Duration.zero;
  Duration _dur = Duration.zero;
  bool _playing = false;

  @override
  void initState() {
    super.initState();
    shellTab.addListener(_onTabHidden);
    _tabs = TabController(length: 2, vsync: this);
    _tabs.addListener(() {
      if (_tabs.indexIsChanging) return;
      _sort = _tabs.index == 1 ? 'pop' : 'new';
      _load();
    });
    _player = AudioPlayer();
    _player.positionStream.listen((p) {
      if (mounted) setState(() => _pos = p);
    });
    _player.durationStream.listen((d) {
      if (mounted && d != null) setState(() => _dur = d);
    });
    _player.playerStateStream.listen((s) {
      if (mounted) {
        setState(() => _playing = s.playing);
        if (s.processingState == ProcessingState.completed) {
          _play((_ti + 1) % _tracks.length);
        }
      }
    });
    _load();
  }

  @override
  void dispose() {
    shellTab.removeListener(_onTabHidden);
    _player.dispose();
    _tabs.dispose();
    super.dispose();
  }

  void _onTabHidden() {
    if (shellTab.value != 2) _player.pause();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final res = await api.videos(
          sort: _sort == 'pop' ? 'popular' : 'new',
          page: 1,
          limit: 48,
          kind: 'music');
      if (!mounted) return;
      setState(() {
        _tracks = (res['videos'] as List)
            .cast<Video>()
            .where((v) => v.status == 'ready')
            .toList();
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

  Future<void> _play(int i) async {
    if (_tracks.isEmpty) return;
    final idx = (i + _tracks.length) % _tracks.length;
    setState(() => _ti = idx);
    try {
      await _player.setUrl(api.full(_tracks[idx].src)!);
      await _player.play();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    }
  }

  Future<void> _toggle() async {
    if (_player.playing) {
      await _player.pause();
    } else if (_ti >= 0) {
      await _player.play();
    }
  }

  @override
  Widget build(BuildContext context) {
    final shown = <int>{};
    final quick = <Video>[];
    for (final t in _tracks) {
      if (quick.length >= 12) break;
      shown.add(t.id);
      quick.add(t);
    }
    final fresh = _tracks
        .where((t) => !shown.contains(t.id))
        .toList()
      ..sort((a, b) => b.id.compareTo(a.id));
    final top = _tracks
        .where((t) => !shown.contains(t.id))
        .toList()
      ..sort((a, b) => b.views.compareTo(a.views));
    final fresh10 = fresh.take(10).toList();
    final top10 = top.take(10).toList();

    return Column(
      children: [
        TabBar(
          controller: _tabs,
          labelColor: Colors.white,
          unselectedLabelColor: const Color(0xFFA8A8A8),
          indicatorColor: Colors.white,
          tabs: const [Tab(text: 'Newest'), Tab(text: 'Popular')],
        ),
        Expanded(child: _bodyContent(quick, fresh10, top10)),
        if (_ti >= 0 && _tracks.isNotEmpty) _miniBar(),
        if (_sheetOpen && _ti >= 0) _sheet(),
      ],
    );
  }

  Widget _bodyContent(
      List<Video> quick, List<Video> fresh10, List<Video> top10) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_tracks.isEmpty) return const Center(child: Text('No music yet'));
    return ListView(
      padding: const EdgeInsets.only(bottom: 16),
      children: [
        _sectionHead('Quick picks', true),
        ...quick.map(_row),
        if (_sort == 'new' && fresh10.isNotEmpty) ...[
          _sectionHead('New uploads', false),
          _carousel(fresh10),
        ],
        if (_sort == 'pop' && top10.isNotEmpty) ...[
          _sectionHead('Top hits', false),
          _carousel(top10),
        ],
      ],
    );
  }

  Widget _sectionHead(String title, bool withActions) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 8, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(title,
                style: const TextStyle(
                    fontSize: 20, fontWeight: FontWeight.bold)),
          ),
          if (withActions) ...[
            FilledButton(
                onPressed: () => _play(0), child: const Text('Play all')),
            IconButton(
              icon: const Icon(Symbols.shuffle_sharp, color: Colors.white),
              onPressed: () {
                if (_tracks.isEmpty) return;
                _play(DateTime.now().millisecond % _tracks.length);
              },
            ),
          ],
        ],
      ),
    );
  }

  Widget _row(Video t) {
    final idx = _tracks.indexOf(t);
    final cur = idx == _ti;
    return ListTile(
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: SizedBox(
          width: 48,
          height: 48,
          child: t.thumbnail != null
              ? Image.network(api.full(t.thumbnail)!,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) =>
                      const Icon(Symbols.music_note_sharp, color: Colors.grey))
              : const Icon(Symbols.music_note_sharp, color: Colors.grey),
        ),
      ),
      title: Text(t.title.isEmpty ? 'Untitled' : t.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
              fontWeight: cur ? FontWeight.bold : FontWeight.normal)),
      subtitle: Text('@${t.username} • ${fmtNum(t.views)} plays',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: Color(0xFFA8A8A8), fontSize: 12)),
      trailing: cur && _playing
          ? const Icon(Symbols.bar_chart_sharp, color: Colors.white)
          : null,
      onTap: () {
        if (idx == _ti) {
          _toggle();
        } else {
          _play(idx);
        }
      },
    );
  }

  Widget _carousel(List<Video> list) {
    return SizedBox(
      height: 210,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: list.length,
        itemBuilder: (ctx, i) {
          final t = list[i];
          return InkWell(
            onTap: () {
              final idx = _tracks.indexOf(t);
              if (idx == _ti) {
                _toggle();
              } else {
                _play(idx);
              }
            },
            child: Container(
              width: 148,
              margin: const EdgeInsets.symmetric(horizontal: 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AspectRatio(
                    aspectRatio: 1,
                    child: Container(
                      decoration: BoxDecoration(
                          color: const Color(0xFF161616),
                          borderRadius: BorderRadius.circular(8)),
                      clipBehavior: Clip.antiAlias,
                      child: t.thumbnail != null
                          ? Image.network(api.full(t.thumbnail)!,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => const Icon(
                                  Symbols.music_note_sharp,
                                  color: Colors.grey,
                                  size: 48))
                          : const Icon(Symbols.music_note_sharp,
                              color: Colors.grey, size: 48),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(t.title.isEmpty ? 'Untitled' : t.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w500)),
                  Text('@${t.username}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: Color(0xFFA8A8A8), fontSize: 12)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _miniBar() {
    final t = _tracks[_ti];
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Color.fromRGBO(0, 0, 0, 217)],
        ),
      ),
      padding: const EdgeInsets.fromLTRB(8, 12, 8, 8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Slider(
            value: _dur.inMilliseconds > 0
                ? (_pos.inMilliseconds / _dur.inMilliseconds * 1000)
                    .clamp(0, 1000)
                    .toDouble()
                : 0,
            min: 0,
            max: 1000,
            activeColor: Colors.white,
            inactiveColor: Colors.white24,
            onChanged: (x) {
              if (_dur.inMilliseconds > 0) {
                _player.seek(Duration(
                    milliseconds:
                        (x / 1000 * _dur.inMilliseconds).round()));
              }
            },
          ),
          Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: SizedBox(
                  width: 44,
                  height: 44,
                  child: t.thumbnail != null
                      ? Image.network(api.full(t.thumbnail)!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => const Icon(
                              Symbols.music_note_sharp,
                              color: Colors.grey))
                      : const Icon(Symbols.music_note_sharp, color: Colors.grey),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: InkWell(
                  onTap: () => setState(() => _sheetOpen = true),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(t.title.isEmpty ? 'Untitled' : t.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 14)),
                      Text('@${t.username}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: Color(0xFFA8A8A8), fontSize: 12)),
                    ],
                  ),
                ),
              ),
              _pillBtn(Symbols.skip_previous_sharp,
                  () => _play((_ti - 1 + _tracks.length) % _tracks.length)),
              _pillBtn(_playing ? Symbols.pause_sharp : Symbols.play_arrow_sharp, _toggle,
                  white: true, big: true),
              _pillBtn(Symbols.skip_next_sharp,
                  () => _play((_ti + 1) % _tracks.length)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _sheet() {
    final t = _tracks[_ti];
    return Container(
      color: Colors.black,
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              _pillBtn(Symbols.expand_more_sharp,
                  () => setState(() => _sheetOpen = false)),
              const Expanded(
                child: Text('Now playing',
                    textAlign: TextAlign.center,
                    style:
                        TextStyle(color: Color(0xFFA8A8A8), fontSize: 13)),
              ),
              const SizedBox(width: 48),
            ],
          ),
          const SizedBox(height: 8),
          SizedBox(
            width: 200,
            height: 200,
            child: Container(
              decoration: BoxDecoration(
                  color: const Color(0xFF222222),
                  borderRadius: BorderRadius.circular(8)),
              clipBehavior: Clip.antiAlias,
              child: t.thumbnail != null
                  ? Image.network(api.full(t.thumbnail)!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const Icon(
                          Symbols.music_note_sharp,
                          color: Colors.grey,
                          size: 64))
                  : const Icon(Symbols.music_note_sharp,
                      color: Colors.grey, size: 64),
            ),
          ),
          const SizedBox(height: 12),
          Text(t.title.isEmpty ? 'Untitled' : t.title,
              style:
                  const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              maxLines: 1,
              overflow: TextOverflow.ellipsis),
          Text('@${t.username}',
              style:
                  const TextStyle(color: Color(0xFFA8A8A8), fontSize: 14)),
          Slider(
            value: _dur.inMilliseconds > 0
                ? (_pos.inMilliseconds / _dur.inMilliseconds * 1000)
                    .clamp(0, 1000)
                    .toDouble()
                : 0,
            min: 0,
            max: 1000,
            activeColor: Colors.white,
            inactiveColor: Colors.white24,
            onChanged: (x) {
              if (_dur.inMilliseconds > 0) {
                _player.seek(Duration(
                    milliseconds:
                        (x / 1000 * _dur.inMilliseconds).round()));
              }
            },
          ),
          Row(
            children: [
              Text(_fmt(_pos),
                  style: const TextStyle(
                      color: Color(0xFFA8A8A8), fontSize: 12)),
              const Spacer(),
              Text(_fmt(_dur),
                  style: const TextStyle(
                      color: Color(0xFFA8A8A8), fontSize: 12)),
            ],
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _pillBtn(Symbols.skip_previous_sharp,
                  () => _play((_ti - 1 + _tracks.length) % _tracks.length)),
              _pillBtn(_playing ? Symbols.pause_sharp : Symbols.play_arrow_sharp, _toggle,
                  white: true, big: true),
              _pillBtn(Symbols.skip_next_sharp,
                  () => _play((_ti + 1) % _tracks.length)),
            ],
          ),
          const Align(
            alignment: Alignment.centerLeft,
            child: Padding(
              padding: EdgeInsets.only(top: 8, bottom: 4),
              child: Text('Up next',
                  style:
                      TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            ),
          ),
          SizedBox(
            height: 180,
            child: ListView.builder(
              itemCount: _tracks.length,
              itemBuilder: (ctx, i) {
                final q = _tracks[i];
                return ListTile(
                  dense: true,
                  leading: ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: SizedBox(
                      width: 40,
                      height: 40,
                      child: q.thumbnail != null
                          ? Image.network(api.full(q.thumbnail)!,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => const Icon(
                                  Symbols.music_note_sharp,
                                  color: Colors.grey))
                          : const Icon(Symbols.music_note_sharp, color: Colors.grey),
                    ),
                  ),
                  title: Text(q.title.isEmpty ? 'Untitled' : q.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 13)),
                  subtitle: Text('@${q.username}',
                      maxLines: 1,
                      style: const TextStyle(
                          color: Color(0xFFA8A8A8), fontSize: 12)),
                  trailing: i == _ti && _playing
                      ? const Icon(Symbols.bar_chart_sharp,
                          color: Colors.white, size: 18)
                      : null,
                  onTap: () {
                    if (i == _ti) {
                      _toggle();
                    } else {
                      _play(i);
                    }
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  String _fmt(Duration d) {
    final m = d.inMinutes;
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  Widget _pillBtn(IconData icon, VoidCallback onTap,
      {bool white = false, bool big = false}) {
    final s = big ? 56.0 : 44.0;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 2),
      child: Material(
        color: white ? Colors.white : const Color(0xFF2B2B2B),
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(
            width: s,
            height: s,
            child: Icon(icon,
                color: white ? Colors.black : Colors.white,
                size: big ? 30 : 22),
          ),
        ),
      ),
    );
  }
}
