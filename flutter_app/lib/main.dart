import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

const appUrl = 'https://watchshark.duckdns.org';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    systemNavigationBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarIconBrightness: Brightness.light,
  ));
  runApp(const WatchSharkApp());
}

class WatchSharkApp extends StatelessWidget {
  const WatchSharkApp({super.key});

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
        ),
      ),
      home: const WebShell(),
    );
  }
}

class WebShell extends StatefulWidget {
  const WebShell({super.key});

  @override
  State<WebShell> createState() => _WebShellState();
}

class _WebShellState extends State<WebShell> {
  InAppWebViewController? _ctrl;
  bool _loading = true;
  bool _error = false;
  bool _fullscreen = false;
  double _progress = 0;

  Future<bool> _goBack() async {
    if (_fullscreen) {
      await _exitFullscreen();
      return false;
    }
    if (_ctrl != null && await _ctrl!.canGoBack()) {
      await _ctrl!.goBack();
      return false;
    }
    return true;
  }

  Future<void> _enterFullscreen() async {
    setState(() => _fullscreen = true);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  }

  Future<void> _exitFullscreen() async {
    setState(() => _fullscreen = false);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  }

  Future<void> _insetSite() async {
    final c = _ctrl;
    if (c == null || !mounted) return;
    final pad = MediaQuery.of(context).padding;
    final top = pad.top.round();
    final bottom = pad.bottom.round();
    await c.evaluateJavascript(source:
        "(function(){var st=document.getElementById('ws-insets');"
        "if(!st){st=document.createElement('style');st.id='ws-insets';document.head.appendChild(st);}"
        "st.textContent='*{ -webkit-tap-highlight-color:transparent !important;}"
        ".topbar{padding-top:${top}px!important;}"
        "#bottomnav{padding-bottom:${bottom}px!important;}"
        "#ytmbar{padding-bottom:calc(10px + ${bottom}px)!important;}"
        ".reel{height:calc(100dvh - 84px)!important;}"
        ".reel-meta{padding-bottom:calc(30px + ${bottom}px)!important;}"
        "#rseek{bottom:${bottom}px!important;}';})();");
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        if (await _goBack() && mounted) {
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
              InAppWebView(
                initialUrlRequest:
                    URLRequest(url: WebUri(appUrl)),
                initialSettings: InAppWebViewSettings(
                  javaScriptEnabled: true,
                  domStorageEnabled: true,
                  databaseEnabled: true,
                  mediaPlaybackRequiresUserGesture: false,
                  allowFileAccess: true,
                  cacheEnabled: true,
                  useHybridComposition: true,
                  builtInZoomControls: false,
                  displayZoomControls: false,
                  thirdPartyCookiesEnabled: true,
                ),
                onWebViewCreated: (c) => _ctrl = c,
                onLoadStart: (_, __) => setState(() {
                  _loading = true;
                  _error = false;
                }),
                onLoadStop: (_, __) {
                  setState(() => _loading = false);
                  _insetSite();
                },
                onProgressChanged: (_, p) =>
                    setState(() => _progress = p / 100),
                onReceivedError: (c, req, err) {
                  if (req.isForMainFrame == true) {
                    setState(() {
                      _error = true;
                      _loading = false;
                    });
                  }
                },
                androidOnPermissionRequest:
                    (c, origin, resources) async {
                  return PermissionRequestResponse(
                    resources: resources,
                    action: PermissionRequestResponseAction.GRANT,
                  );
                },
                onEnterFullscreen: (c) => _enterFullscreen(),
                onExitFullscreen: (c) => _exitFullscreen(),
              ),
              if (_loading && !_error)
                LinearProgressIndicator(
                  value: _progress == 0 ? null : _progress,
                  backgroundColor: Colors.transparent,
                  color: Colors.white,
                ),
              if (_error)
                Container(
                  color: Colors.black,
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text('No connection',
                            style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold)),
                        const SizedBox(height: 8),
                        const Text(
                          'Could not reach the WatchShark server.',
                          style: TextStyle(color: Colors.grey),
                        ),
                        const SizedBox(height: 16),
                        FilledButton(
                          onPressed: () => _ctrl?.reload(),
                          child: const Text('Retry'),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
        ),
      ),
    );
  }
}
