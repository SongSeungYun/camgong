package com.camgong.groupbuy.check;

import java.util.List;

public record CheckSheet(CheckSummary summary, List<CheckRow> rows) {
}
