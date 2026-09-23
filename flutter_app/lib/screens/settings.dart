import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import '../update.dart';
import '../widgets.dart';

class SettingsScreen extends StatefulWidget {
  final ApiUser me;
  final ValueChanged<ApiUser?> onMeChanged;
  const SettingsScreen(
      {super.key, required this.me, required this.onMeChanged});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _nameCtrl = TextEditingController();
  final _curCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  bool _notif = true;
  String _msg = '';
  bool _uploadingPfp = false;

  @override
  void initState() {
    super.initState();
    _notif = widget.me.notifyUploads;
  }

  void _say(String s) => setState(() => _msg = s);

  Future<void> _rename() async {
    try {
      await api.rename(_nameCtrl.text.trim());
      if (!mounted) return;
      widget.onMeChanged(await api.me());
      _say('Name saved!');
    } catch (e) {
      if (mounted) _say(e.toString());
    }
  }

  Future<void> _changePw() async {
    try {
      await api.changePw(_curCtrl.text, _newCtrl.text);
      if (!mounted) return;
      _say('Password changed.');
    } catch (e) {
      if (mounted) _say(e.toString());
    }
  }

  Future<void> _saveNotif(bool on) async {
    setState(() => _notif = on);
    try {
      await api.notifSet(on);
      if (mounted) _say('Saved!');
    } catch (e) {
      if (mounted) _say(e.toString());
    }
  }

  Future<void> _uploadPfp() async {
    final res = await FilePicker.platform.pickFiles(type: FileType.image);
    final path = res?.files.single.path;
    if (path == null) return;
    setState(() => _uploadingPfp = true);
    try {
      // Reuse upload endpoint? No — dedicated pfp endpoint via raw client.
      // Implemented in api as pfpUpload.
      await api.pfpUpload(path);
      if (!mounted) return;
      widget.onMeChanged(await api.me());
      _say('Saved!');
    } catch (e) {
      if (mounted) _say(e.toString());
    } finally {
      if (mounted) setState(() => _uploadingPfp = false);
    }
  }

  Future<void> _signOut() async {
    await api.logout();
    if (!mounted) return;
    widget.onMeChanged(null);
    Navigator.of(context).popUntil((r) => r.isFirst);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      bottomNavigationBar: BottomNav(current: -1, onTab: (_) {}, onMeChanged: widget.onMeChanged),
      appBar: AppBar(
          backgroundColor: const Color(0xFF111111),
          title: Text('@${widget.me.username}')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Center(
              child: UserAvatar(url: widget.me.avatar, radius: 64)),
          Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('Member since ${widget.me.since}',
                  style: const TextStyle(color: Color(0xFFA8A8A8))),
            ),
          ),
          const SizedBox(height: 20),
          const Text('Profile picture',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const Text('Square picture, max 10MB.',
              style: TextStyle(color: Color(0xFFA8A8A8), fontSize: 13)),
          const SizedBox(height: 8),
          Row(
            children: [
              FilledButton.tonal(
                  onPressed: _uploadingPfp ? null : _uploadPfp,
                  child: const Text('Upload')),
              if (_uploadingPfp) ...[
                const SizedBox(width: 12),
                const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2)),
              ],
            ],
          ),
          const SizedBox(height: 20),
          const Text('Handle',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const Text('Letters, numbers and _ only, 3-30 characters.',
              style: TextStyle(color: Color(0xFFA8A8A8), fontSize: 13)),
          const SizedBox(height: 8),
          TextField(
            controller: _nameCtrl,
            style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(
                labelText: 'New handle',
                border: OutlineInputBorder()),
          ),
          const SizedBox(height: 8),
          FilledButton(onPressed: _rename, child: const Text('Save name')),
          const SizedBox(height: 20),
          const Text('Change password',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          TextField(
            controller: _curCtrl,
            style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(
                labelText: 'Current password',
                border: OutlineInputBorder()),
            obscureText: true,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _newCtrl,
            style: const TextStyle(color: Colors.white),
            decoration: const InputDecoration(
                labelText: 'New password (6+ chars)',
                border: OutlineInputBorder()),
            obscureText: true,
          ),
          const SizedBox(height: 8),
          FilledButton(
              onPressed: _changePw, child: const Text('Save new password')),
          const SizedBox(height: 20),
          const Text('Upload alerts',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          Row(
            children: [
              Switch(value: _notif, onChanged: _saveNotif),
              const Expanded(
                  child: Text('Notify me when channels I follow upload')),
            ],
          ),
          const SizedBox(height: 12),
          OutlinedButton(
              onPressed: _signOut, child: const Text('Sign out')),
          const SizedBox(height: 12),
          OutlinedButton(
              onPressed: () {
                checkForUpdate(context, manual: true);
              },
              child: const Text('Check for updates')),
          Text(_msg,
              style: const TextStyle(color: Color(0xFFA8A8A8))),
        ],
      ),
    );
  }
}
