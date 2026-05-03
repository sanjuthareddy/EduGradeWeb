package com.edugrade.interfaces;

public interface Reportable {
    String buildReportHTML();
    String buildSummaryRow(int index);
}
