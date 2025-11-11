import 'package:rolla_band_sdk/src/sync/steps_baseline_provider.dart';

class StepsBaselineProviderImpl implements StepsBaselineProvider {
  @override
  Future<StepsTodayBaselines> getTodayBaselines() async {
    return const StepsTodayBaselines(steps: 0, activeCalories: 0);
  }
}

