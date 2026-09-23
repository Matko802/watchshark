import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import '../widgets.dart';

class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  List<AdminUser> _users = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final users = await api.adminUsers();
      if (!mounted) return;
      setState(() {
        _users = users;
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

  Future<void> _run(Future<void> Function() fn) async {
    try {
      await fn();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    }
    _load();
  }

  Future<void> _banDialog(AdminUser u) async {
    final daysCtrl = TextEditingController(text: '7');
    final hoursCtrl = TextEditingController(text: '0');
    final reasonCtrl = TextEditingController();
    bool perm = false;
    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          backgroundColor: const Color(0xFF1E1E1E),
          title: Text('Manage @${u.username}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: daysCtrl,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                      labelText: 'Ban days',
                      border: OutlineInputBorder()),
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: hoursCtrl,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                      labelText: 'Ban hours',
                      border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: reasonCtrl,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                      labelText: 'Reason', border: OutlineInputBorder()),
                ),
                Row(
                  children: [
                    Checkbox(
                      value: perm,
                      onChanged: (v) => setD(() => perm = v ?? false),
                    ),
                    const Text('Permanent ban'),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            if (!u.deleted && !u.banned)
              FilledButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  _run(() => api.adminBan(u.id,
                      days: double.tryParse(daysCtrl.text) ?? 0,
                      hours: double.tryParse(hoursCtrl.text) ?? 0,
                      reason: reasonCtrl.text,
                      permanent: perm));
                },
                child: const Text('Ban'),
              ),
            if (u.banned && !u.deleted)
              TextButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  _run(() => api.adminUnban(u.id));
                },
                child: const Text('Unban'),
              ),
            if (u.deleted)
              TextButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  _run(() => api.adminRestore(u.id));
                },
                child: const Text('Restore'),
              ),
            if (!u.deleted)
              TextButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  _run(() => api.adminDelete(u.id, 'Removed by admin'));
                },
                child: const Text('Delete'),
              ),
            TextButton(
              onPressed: () {
                Navigator.of(ctx).pop();
                _run(() => api.adminRemove(u.id));
              },
              child: const Text('Remove'),
            ),
            if (!u.verified && !u.deleted)
              FilledButton.tonal(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  _run(() => api.adminApprove(u.id));
                },
                child: const Text('Approve'),
              ),
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Close'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      bottomNavigationBar: BottomNav(current: '', onMeChanged: (_) {}),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111111),
        title: Text('All accounts (${_users.length})'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView.builder(
              itemCount: _users.length,
              itemBuilder: (ctx, i) {
                final u = _users[i];
                final status = u.deleted
                    ? 'deleted'
                    : u.banned
                        ? 'banned'
                        : u.verified
                            ? 'active'
                            : 'pending';
                var extra = '';
                if (u.deleted && u.deletedReason.isNotEmpty) {
                  extra = ' • ${u.deletedReason}';
                } else if (u.banned) {
                  final dl = u.banDaysLeft < 0
                      ? 'permanent'
                      : '${u.banDaysLeft.toStringAsFixed(1)}d left';
                  extra =
                      ' • $dl${u.banReason.isNotEmpty ? ' • ${u.banReason}' : ''}';
                }
                return ListTile(
                  title: Text('${u.username}${u.role == 'admin' ? ' ⭐' : ''}'),
                  subtitle: Text('$status • ${u.createdAt}$extra',
                      style: const TextStyle(
                          color: Color(0xFFA8A8A8), fontSize: 12)),
                  trailing: u.role == 'admin'
                      ? null
                      : IconButton(
                          icon: const Icon(Icons.settings,
                              color: Colors.white),
                          onPressed: () => _banDialog(u),
                        ),
                );
              },
            ),
    );
  }
}
