import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api.dart';
import 'screens/home.dart';
import 'screens/shell.dart';
import 'screens/auth.dart';

final api = Api();

void main() {
  runApp(const WatchSharkApp());
}

class WatchSharkApp extends StatefulWidget {
  const WatchSharkApp({super.key});

  @override
  State<WatchSharkApp> createState() => _WatchSharkAppState();
}

class _WatchSharkAppState extends State<WatchSharkApp> {
  bool _ready = false;
  ApiUser? _me;

  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    final p = await SharedPreferences.getInstance();
    api.token = p.getString('ws_token');
    final me = await api.me();
    if (!mounted) return;
    setState(() {
      _me = me;
      _ready = true;
    });
  }

  void _refreshMe(ApiUser? me) => setState(() => _me = me);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WatchShark',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFF5F5F5),
          onPrimary: Colors.black,
          surface: Color(0xFF111111),
          onSurface: Color(0xFFF2F2F2),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF111111),
          foregroundColor: Color(0xFFF2F2F2),
        ),
      ),
      home: !_ready
          ? const Scaffold(
              backgroundColor: Colors.black,
              body: Center(child: CircularProgressIndicator()))
          : MainShell(me: _me, onMeChanged: _refreshMe),
    );
  }
}

String banMessage(ApiUser u) {
  if (u.deleted) {
    var t = 'Your account has been deleted.';
    if (u.deletedReason != null && u.deletedReason!.isNotEmpty) {
      t += '\nReason: ${u.deletedReason}';
    }
    return t;
  }
  if (u.banned) {
    final r = (u.banReason != null && u.banReason!.isNotEmpty) ? '\nReason: ${u.banReason}' : '';
    if (u.banDaysLeft < 0) return 'You have been banned permanently.$r';
    return 'You have been banned for ${u.banDaysLeft.toStringAsFixed(1)} days.$r';
  }
  return '';
}

Future<void> showBanOverlay(BuildContext context, ApiUser u) {
  final msg = banMessage(u);
  if (msg.isEmpty) return Future.value();
  return showDialog(
    context: context,
    barrierDismissible: true,
    barrierColor: Colors.black87,
    builder: (ctx) => Dialog(
      backgroundColor: const Color(0xFF171717),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Symbols.block_sharp, color: Color(0xFFFF5252), size: 48),
            const SizedBox(height: 12),
            const Text('Account restricted',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Text(msg, textAlign: TextAlign.center, style: const TextStyle(fontSize: 16)),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        ),
      ),
    ),
  );
}
