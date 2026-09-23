import 'package:flutter/material.dart';
import '../api.dart';
import '../main.dart';

class ForgotScreen extends StatefulWidget {
  const ForgotScreen({super.key});

  @override
  State<ForgotScreen> createState() => _ForgotScreenState();
}

class _ForgotScreenState extends State<ForgotScreen> {
  final _emailCtrl = TextEditingController();
  String _msg = '';
  bool _busy = false;

  Future<void> _go() async {
    setState(() {
      _busy = true;
      _msg = '';
    });
    try {
      await api.forgot(_emailCtrl.text.trim());
      if (!mounted) return;
      setState(() {
        _msg = 'If that email has an account, a reset link is on its way.';
        _busy = false;
      });
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
      appBar: AppBar(
          backgroundColor: const Color(0xFF111111),
          title: const Text('Trouble signing in?')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Enter your account email. We send a reset link, or a fresh confirmation link if you never confirmed.',
              style: TextStyle(color: Color(0xFFA8A8A8)),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _emailCtrl,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                  labelText: 'Email', border: OutlineInputBorder()),
              keyboardType: TextInputType.emailAddress,
              onSubmitted: (_) => _go(),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _busy ? null : _go,
              child: const Text('Send link'),
            ),
            const SizedBox(height: 8),
            Text(_msg, style: const TextStyle(color: Color(0xFFA8A8A8))),
          ],
        ),
      ),
    );
  }
}
