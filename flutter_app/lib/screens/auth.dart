import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';
import '../screens/forgot.dart';

class AuthDialog extends StatefulWidget {
  final ValueChanged<ApiUser?> onMeChanged;
  const AuthDialog({super.key, required this.onMeChanged});

  @override
  State<AuthDialog> createState() => _AuthDialogState();
}

class _AuthDialogState extends State<AuthDialog> {
  bool _login = true;
  bool _busy = false;
  String? _err;
  final _loginCtrl = TextEditingController();
  final _pwCtrl = TextEditingController();
  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();

  void _toggle() => setState(() {
        _login = !_login;
        _err = null;
      });

  Future<void> _go() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _err = null;
    });
    try {
      ApiUser me;
      if (_login) {
        me = await api.login(_loginCtrl.text.trim(), _pwCtrl.text);
      } else {
        me = await api.signup(_nameCtrl.text.trim(), _emailCtrl.text.trim(),
            _pwCtrl.text);
      }
      if (!mounted) return;
      Navigator.of(context).pop();
      widget.onMeChanged(me);
      if (me.banned || me.deleted) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) showBanOverlay(context, me);
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _err = e.toString();
        _busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: const Color(0xFF1E1E1E),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Welcome to WatchShark',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center),
            const SizedBox(height: 16),
            if (_login) ...[
              TextField(
                controller: _loginCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                    labelText: 'Email', border: OutlineInputBorder()),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _pwCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                    labelText: 'Password', border: OutlineInputBorder()),
                obscureText: true,
                onSubmitted: (_) => _go(),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(
                          builder: (_) => const ForgotScreen())),
                  child: const Text('Forgot password?'),
                ),
              ),
            ] else ...[
              TextField(
                controller: _nameCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                    labelText: 'Handle (a-z, 0-9, _)',
                    border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _emailCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                    labelText: 'Email', border: OutlineInputBorder()),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _pwCtrl,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                    labelText: 'Password (6+ chars)',
                    border: OutlineInputBorder()),
                obscureText: true,
                onSubmitted: (_) => _go(),
              ),
            ],
            if (_err != null) ...[
              const SizedBox(height: 8),
              Text(_err!,
                  style:
                      const TextStyle(color: Color(0xFFFF5252), fontSize: 13)),
            ],
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: _toggle,
                  child: Text(_login ? 'Create account' : 'Log in'),
                ),
                const SizedBox(width: 4),
                FilledButton(
                  onPressed: _busy ? null : _go,
                  child: _busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2))
                      : Text(_login ? 'Log in' : 'Create account'),
                ),
                const SizedBox(width: 4),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Close'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
