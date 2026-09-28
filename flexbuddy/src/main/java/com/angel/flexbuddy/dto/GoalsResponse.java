package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.model.GoalBasis;

/** The driver's weekly and monthly goal progress; a goal that is not set is null. */
public record GoalsResponse(GoalBasis basis, GoalProgress week, GoalProgress month) {
}
