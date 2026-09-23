import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import '../widgets.dart';

class ChannelScreen extends StatefulWidget {
  final String username;
  const ChannelScreen({super.key, required this.username});

  @override
  State<ChannelScreen> createState() => _ChannelScreenState();
}

class _ChannelScreenState extends State<ChannelScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;
  ChannelInfo? _user;
  List<Video> _videos = [];
  bool _loading = true;
  String? _error;
  ApiUser? _me;
  final _kinds = ['video', 'wheel', 'music'];

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    _tabs.addListener(() {
      if (!_tabs.indexIsChanging) setState(() {});
    });
    _load();
  }

  Future<void> _load() async {
    try {
      final res = await api.channel(widget.username);
      final me = await api.me();
      if (!mounted) return;
      setState(() {
        _user = res['user'] as ChannelInfo;
        _videos = (res['videos'] as List).cast<Video>();
        _me = me;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _toggleFollow() async {
    final u = _user;
    if (u == null) return;
    try {
      final res = await api.follow(u.id);
      if (!mounted) return;
      setState(() {
        u.following = res['following'] as bool;
        // refresh counts
        _load();
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final u = _user;
    return Scaffold(
      backgroundColor: Colors.black,
      bottomNavigationBar: BottomNav(current: '', onMeChanged: (_) {}),
      appBar: AppBar(
          backgroundColor: const Color(0xFF111111),
          title: Text(u == null ? 'Channel' : '@${u.username}')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          UserAvatar(url: u!.avatar, radius: 36),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('@${u.username}',
                                    style: const TextStyle(
                                        fontSize: 20,
                                        fontWeight: FontWeight.bold)),
                                Text(
                                  '${fmtNum(u.followers)} followers • ${fmtNum(u.videos)} videos • ${fmtNum(u.views)} views',
                                  style: const TextStyle(
                                      color: Color(0xFFA8A8A8),
                                      fontSize: 13),
                                ),
                              ],
                            ),
                          ),
                          if (_me != null && _me!.id != u.id)
                            OutlinedButton(
                              onPressed: _toggleFollow,
                              child: Text(u.following
                                  ? 'Following'
                                  : 'Follow'),
                            ),
                        ],
                      ),
                    ),
                    TabBar(
                      controller: _tabs,
                      labelColor: Colors.white,
                      unselectedLabelColor: const Color(0xFFA8A8A8),
                      indicatorColor: Colors.white,
                      tabs: const [
                        Tab(text: 'Videos'),
                        Tab(text: 'Wheels'),
                        Tab(text: 'Music')
                      ],
                    ),
                    Expanded(
                      child: GridView.builder(
                        padding:
                            const EdgeInsets.fromLTRB(8, 4, 8, 96),
                        gridDelegate:
                            const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          childAspectRatio: 0.72,
                          crossAxisSpacing: 8,
                          mainAxisSpacing: 4,
                        ),
                        itemCount: _videos
                            .where((v) => (v.kind.isEmpty
                                    ? 'video'
                                    : v.kind) ==
                                _kinds[_tabs.index])
                            .length,
                        itemBuilder: (ctx, i) {
                          final list = _videos
                              .where((v) => (v.kind.isEmpty
                                      ? 'video'
                                      : v.kind) ==
                                  _kinds[_tabs.index])
                              .toList();
                          return VideoCard(video: list[i]);
                        },
                      ),
                    ),
                  ],
                ),
    );
  }
}
