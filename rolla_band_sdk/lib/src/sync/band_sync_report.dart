import 'package:meta/meta.dart';

@immutable
class BandSyncReport {
  final Set<String> _localDates = <String>{};

  void reset() {
    _localDates.clear();
  }

  void addUtcTimestamps(Iterable<int> utcMillis) {
    for (final int ts in utcMillis) {
      if (ts <= 0) continue;
      final DateTime local = DateTime.fromMillisecondsSinceEpoch(ts, isUtc: true).toLocal();
      final String y = local.year.toString().padLeft(4, '0');
      final String m = local.month.toString().padLeft(2, '0');
      final String d = local.day.toString().padLeft(2, '0');
      _localDates.add('$y-$m-$d');
    }
  }

  void addUtcRange(int startUtcMillis, int endUtcMillis) {
    if (endUtcMillis < startUtcMillis) {
      final int t = startUtcMillis;
      startUtcMillis = endUtcMillis;
      endUtcMillis = t;
    }
    DateTime cursor = DateTime.fromMillisecondsSinceEpoch(startUtcMillis, isUtc: true).toLocal();
    final DateTime endLocal = DateTime.fromMillisecondsSinceEpoch(endUtcMillis, isUtc: true).toLocal();
    while (true) {
      final String y = cursor.year.toString().padLeft(4, '0');
      final String m = cursor.month.toString().padLeft(2, '0');
      final String d = cursor.day.toString().padLeft(2, '0');
      _localDates.add('$y-$m-$d');
      if (cursor.year == endLocal.year && cursor.month == endLocal.month && cursor.day == endLocal.day) break;
      cursor = DateTime(cursor.year, cursor.month, cursor.day + 1);
    }
  }

  Set<String> consumeLocalDates() {
    final Set<String> out = Set<String>.from(_localDates);
    _localDates.clear();
    return out;
  }
}

