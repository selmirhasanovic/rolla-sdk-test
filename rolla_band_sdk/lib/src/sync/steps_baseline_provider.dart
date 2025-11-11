import 'package:meta/meta.dart';

@immutable
class StepsTodayBaselines {
  final int steps;
  final int activeCalories;

  const StepsTodayBaselines({required this.steps, required this.activeCalories});
}

abstract class StepsBaselineProvider {
  Future<StepsTodayBaselines> getTodayBaselines();
}

