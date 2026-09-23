import 'package:flutter/foundation.dart';

/// App-wide selected tab owned by the shell. Tab bodies listen to pause
/// media when hidden.
final shellTab = ValueNotifier<int>(0);
