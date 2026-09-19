import '../../domain/interfaces/playlist_strategy.dart';
import '../../domain/models/photo_entry.dart';

/// Shows photos in chronological order (oldest to newest), looping back
/// to the start once the end of the collection is reached.
class SequentialStrategy implements PlaylistStrategy {
  PhotoEntry? _lastShown;

  @override
  String get id => 'sequential';

  @override
  String get name => 'Sequential Order';

  @override
  PhotoEntry? nextPhoto(List<PhotoEntry> availablePhotos) {
    if (availablePhotos.isEmpty) return null;

    final sorted = [...availablePhotos]..sort((a, b) => a.date.compareTo(b.date));

    final lastShown = _lastShown;
    final lastIndex = lastShown == null
        ? -1
        : sorted.indexWhere((p) => p.file.path == lastShown.file.path);
    final nextIndex = lastIndex == -1 ? 0 : (lastIndex + 1) % sorted.length;

    final photo = sorted[nextIndex];
    _lastShown = photo;
    return photo;
  }
}
