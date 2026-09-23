import 'package:flutter/material.dart';
import '../api.dart';
import '../tab_index.dart';
import '../widgets.dart';
import 'home.dart';
import 'wheels.dart';
import 'music.dart';

class MainShell extends StatefulWidget {
  final ApiUser? me;
  final ValueChanged<ApiUser?> onMeChanged;
  const MainShell({super.key, required this.me, required this.onMeChanged});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  @override
  void initState() {
    super.initState();
    shellTab.addListener(_onTab);
  }

  @override
  void dispose() {
    shellTab.removeListener(_onTab);
    super.dispose();
  }

  void _onTab() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: TopBar(me: widget.me, onMeChanged: widget.onMeChanged),
      body: IndexedStack(
        index: shellTab.value.clamp(0, 2),
        children: [
          HomeTab(me: widget.me, onMeChanged: widget.onMeChanged),
          const WheelsTab(),
          const MusicTab(),
        ],
      ),
      bottomNavigationBar: BottomNav(
        current: shellTab.value,
        onTab: (i) => shellTab.value = i,
        onMeChanged: widget.onMeChanged,
      ),
    );
  }
}
