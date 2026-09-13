import 'dart:io';
import 'package:flutter/services.dart';
import 'package:logging/logging.dart';

/// Opens Android's file manager, falling back to its document browser.
class NativeFileManagerService {
  static const _channel = MethodChannel(
    'io.github.micw.openphotoframe/file_manager',
  );
  static final _log = Logger('NativeFileManagerService');

  static Future<bool> openFileManager() async {
    if (!Platform.isAndroid) return false;
    try {
      return await _channel.invokeMethod<bool>('openFileManager') ?? false;
    } catch (e) {
      _log.warning('openFileManager failed', e);
      return false;
    }
  }
}
