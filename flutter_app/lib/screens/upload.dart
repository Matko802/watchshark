import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import 'watch.dart';
import '../widgets.dart';

class UploadScreen extends StatefulWidget {
  final ApiUser? me;
  const UploadScreen({super.key, required this.me});

  @override
  State<UploadScreen> createState() => _UploadScreenState();
}

class _UploadScreenState extends State<UploadScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;
  String? _filePath;
  String? _fileName;
  String? _thumbPath;
  final _titleCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  bool _busy = false;
  String _msg = '';
  final _kinds = ['video', 'wheel', 'music'];

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    if (widget.me != null && (widget.me!.banned || widget.me!.deleted)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) showBanOverlay(context, widget.me!);
      });
    }
  }

  bool get _blocked =>
      widget.me != null && (widget.me!.banned || widget.me!.deleted);

  Future<void> _pickFile() async {
    final kind = _kinds[_tabs.index];
    final res = await FilePicker.platform.pickFiles(
      type: kind == 'music' ? FileType.audio : FileType.video,
    );
    if (res != null && res.files.single.path != null) {
      setState(() {
        _filePath = res.files.single.path;
        _fileName = res.files.single.name;
      });
    }
  }

  Future<void> _pickThumb() async {
    final res = await FilePicker.platform.pickFiles(type: FileType.image);
    if (res != null && res.files.single.path != null) {
      setState(() => _thumbPath = res.files.single.path);
    }
  }

  Future<void> _up() async {
    if (_busy) return;
    if (_blocked) {
      if (mounted) showBanOverlay(context, widget.me!);
      return;
    }
    if (_filePath == null) {
      setState(() => _msg = 'Choose a file first');
      return;
    }
    if (_titleCtrl.text.trim().isEmpty) {
      setState(() => _msg = 'Title is required');
      return;
    }
    setState(() {
      _busy = true;
      _msg = 'Uploading...';
    });
    try {
      final kind = _kinds[_tabs.index];
      String mime;
      if (kind == 'music') {
        mime = 'audio/mpeg';
      } else {
        mime = 'video/mp4';
      }
      final res = await api.upload(
        title: _titleCtrl.text.trim(),
        description: _descCtrl.text,
        kind: kind,
        filePath: _filePath!,
        mime: mime,
        thumbPath: _thumbPath,
      );
      if (!mounted) return;
      final id = (res['id'] as int?) ?? 0;
      if (kind == 'music') {
        Navigator.of(context).pop();
      } else {
        Navigator.of(context).pushReplacement(MaterialPageRoute(
            builder: (_) => WatchScreen(videoId: id)));
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _msg = e.toString();
        _busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      bottomNavigationBar: BottomNav(current: '', onMeChanged: (_) {}),
      appBar: AppBar(
          backgroundColor: const Color(0xFF111111),
          title: const Text('Upload')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
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
            const SizedBox(height: 16),
            if (_blocked)
              Text(banMessage(widget.me!),
                  style: const TextStyle(color: Color(0xFFFF5252))),
            OutlinedButton.icon(
              onPressed: _pickFile,
              icon: const Icon(Icons.folder_open),
              label:
                  Text(_fileName ?? 'Drag & drop is desktop-only — tap to choose'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _pickThumb,
              icon: const Icon(Icons.image),
              label: Text(_thumbPath == null
                  ? 'Choose thumbnail'
                  : 'Thumbnail selected'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _titleCtrl,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                  labelText: 'Title (required)',
                  border: OutlineInputBorder()),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _descCtrl,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                  labelText: 'Description...',
                  border: OutlineInputBorder()),
              maxLines: 3,
            ),
            const SizedBox(height: 12),
            if (_busy) const LinearProgressIndicator(),
            FilledButton.icon(
              onPressed: _busy ? null : _up,
              icon: const Icon(Icons.cloud_upload),
              label: const Text('Upload'),
            ),
            Text(_msg,
                style: const TextStyle(color: Color(0xFFA8A8A8))),
          ],
        ),
      ),
    );
  }
}
