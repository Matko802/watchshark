import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import '../widgets.dart';
import 'upload.dart';

class HomeScreen extends StatefulWidget {
  final ApiUser? me;
  final ValueChanged<ApiUser?> onMeChanged;
  const HomeScreen({super.key, required this.me, required this.onMeChanged});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _page = 1;
  int _pages = 0;
  int _total = 0;
  String _sort = 'new';
  String _q = '';
  List<Video> _videos = [];
  bool _loading = true;
  final _searchCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _load(1);
    if (widget.me != null && (widget.me!.banned || widget.me!.deleted)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) showBanOverlay(context, widget.me!);
      });
    }
  }

  Future<void> _load(int p) async {
    if (p == 1) setState(() => _loading = true);
    try {
      final res = await api.videos(q: _q, sort: _sort, page: p, limit: 12);
      if (!mounted) return;
      setState(() {
        _page = p;
        _pages = (res['pages'] as int?) ?? 0;
        _total = (res['total'] as int?) ?? 0;
        final list = (res['videos'] as List).cast<Video>();
        _videos = p == 1 ? list : [..._videos, ...list];
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

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
      backgroundColor: Colors.black,
      appBar: TopBar(me: widget.me, onMeChanged: widget.onMeChanged),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: TextField(
              controller: _searchCtrl,
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Search',
                hintStyle: const TextStyle(color: Color(0xFFA8A8A8)),
                prefixIcon:
                    const Icon(Icons.search, color: Color(0xFFA8A8A8)),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24)),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              ),
              textInputAction: TextInputAction.search,
              onSubmitted: (v) {
                _q = v;
                _load(1);
              },
              onChanged: (v) {
                Future.delayed(const Duration(milliseconds: 500), () {
                  if (mounted && _searchCtrl.text == v && _q != v) {
                    _q = v;
                    _load(1);
                  }
                });
              },
            ),
          ),
          TabBar(
            tabs: const [Tab(text: 'Latest'), Tab(text: 'Trending')],
            labelColor: Colors.white,
            unselectedLabelColor: const Color(0xFFA8A8A8),
            indicatorColor: Colors.white,
            onTap: (i) {
              _sort = i == 1 ? 'popular' : 'new';
              _load(1);
            },
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('$_total videos',
                  style:
                      const TextStyle(color: Color(0xFFA8A8A8), fontSize: 12)),
            ),
          ),
          Expanded(
            child: _loading && _videos.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : RefreshIndicator(
                    onRefresh: () => _load(1),
                    child: GridView.builder(
                      padding: const EdgeInsets.fromLTRB(8, 4, 8, 96),
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 2,
                        childAspectRatio: 0.72,
                        crossAxisSpacing: 8,
                        mainAxisSpacing: 4,
                      ),
                      itemCount:
                          _videos.length + (_page < _pages ? 1 : 0),
                      itemBuilder: (ctx, i) {
                        if (i >= _videos.length) {
                          return Center(
                            child: TextButton(
                              onPressed: () => _load(_page + 1),
                              child: const Text('Load more'),
                            ),
                          );
                        }
                        return VideoCard(video: _videos[i]);
                      },
                    ),
                  ),
          ),
        ],
      ),
      floatingActionButton: widget.me != null
          ? FloatingActionButton(
              backgroundColor: const Color(0xFFF5F5F5),
              foregroundColor: Colors.black,
              onPressed: () => Navigator.of(context)
                  .push(MaterialPageRoute(
                      builder: (_) => UploadScreen(me: widget.me)))
                  .then((_) => _load(1)),
              child: const Icon(Icons.add),
            )
          : null,
      ),
    );
  }
}
