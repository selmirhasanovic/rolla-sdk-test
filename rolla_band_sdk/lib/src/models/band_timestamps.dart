class BandTimestamps {
  final int? activityHrLastBlock;
  final int? activityHrLastEntry;
  final int? passiveHrLastTimestamp;
  final int? stepsLastBlock;
  final int? stepsLastEntry;
  final int? activeKcalLastTimestamp;
  final int? sleepLastBlock;
  final int? sleepLastEntry;
  final int? hrvLastTimestamp;

  const BandTimestamps({
    this.activityHrLastBlock,
    this.activityHrLastEntry,
    this.passiveHrLastTimestamp,
    this.stepsLastBlock,
    this.stepsLastEntry,
    this.activeKcalLastTimestamp,
    this.sleepLastBlock,
    this.sleepLastEntry,
    this.hrvLastTimestamp,
  });

  factory BandTimestamps.fromResponse(Map<String, dynamic> response) {
    final Map<String, dynamic>? ts = response['timestamps'] as Map<String, dynamic>?;
    if (ts == null) {
      return const BandTimestamps();
    }
    
    int? _toInt(dynamic v) {
      if (v == null) return null;
      if (v is int) return v;
      if (v is num) return v.toInt();
      if (v is String) return int.tryParse(v);
      return null;
    }

    // Backend returns timestamps in seconds, convert to milliseconds for band API
    return BandTimestamps(
      activityHrLastBlock: _toInt(ts['activity_hr_last_block']) != null ? _toInt(ts['activity_hr_last_block'])! * 1000 : null,
      activityHrLastEntry: _toInt(ts['activity_hr_last_entry']) != null ? _toInt(ts['activity_hr_last_entry'])! * 1000 : null,
      passiveHrLastTimestamp: _toInt(ts['passive_hr_last_timestamp']) != null ? _toInt(ts['passive_hr_last_timestamp'])! * 1000 : null,
      stepsLastBlock: _toInt(ts['steps_last_block']),
      stepsLastEntry: _toInt(ts['steps_last_entry']),
      activeKcalLastTimestamp: _toInt(ts['active_kcal_last_timestamp']),
      sleepLastBlock: _toInt(ts['sleep_last_block']),
      sleepLastEntry: _toInt(ts['sleep_last_entry']),
      hrvLastTimestamp: _toInt(ts['hrv_last_timestamp']),
    );
  }
}

