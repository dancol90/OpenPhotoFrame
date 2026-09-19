import '../../domain/interfaces/config_provider.dart';
import '../../domain/interfaces/playlist_strategy.dart';
import '../../domain/models/photo_entry.dart';

/// Delegates to whichever [PlaylistStrategy] is currently selected in
/// [ConfigProvider.playlistStrategyId], so the strategy can be switched
/// from settings without restarting the app.
class SelectablePlaylistStrategy implements PlaylistStrategy {
  final ConfigProvider _configProvider;
  final Map<String, PlaylistStrategy> _strategiesById;
  final PlaylistStrategy _defaultStrategy;

  SelectablePlaylistStrategy({
    required ConfigProvider configProvider,
    required List<PlaylistStrategy> strategies,
  })  : _configProvider = configProvider,
        _strategiesById = {for (final s in strategies) s.id: s},
        _defaultStrategy = strategies.first;

  PlaylistStrategy get _active =>
      _strategiesById[_configProvider.playlistStrategyId] ?? _defaultStrategy;

  @override
  String get id => _active.id;

  @override
  String get name => _active.name;

  @override
  PhotoEntry? nextPhoto(List<PhotoEntry> availablePhotos) =>
      _active.nextPhoto(availablePhotos);
}
