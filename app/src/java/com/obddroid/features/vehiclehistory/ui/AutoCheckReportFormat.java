package com.obddroid.features.vehiclehistory.ui;

import com.obddroid.R;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

/**
 * The text rules of the vehicle history screen: score bands, the stats grid texts, the at-a-glance icon
 * for a status, export file names and the CSV export. Plain Java, no views, so they can be unit tested.
 */
final class AutoCheckReportFormat {

    static final String GREEN = "#4CAF50";
    static final String AMBER = "#FFC107";
    static final String RED = "#F44336";
    static final String GREY = "#757575";

    private AutoCheckReportFormat() {
    }

    /** The score circle and its words for one score band. */
    static final class ScoreBand {
        final int circleRes;
        final String label;
        final String color;

        ScoreBand(int circleRes, String label, String color) {
            this.circleRes = circleRes;
            this.label = label;
            this.color = color;
        }
    }

    /** 80 and up excellent, 60 good, 40 fair, below that poor. */
    static ScoreBand scoreBand(int score) {
        if (score >= 80) {
            return new ScoreBand(R.drawable.score_circle_excellent, "Excellent Condition", GREEN);
        } else if (score >= 60) {
            return new ScoreBand(R.drawable.score_circle_good, "Good Condition", AMBER);
        } else if (score >= 40) {
            return new ScoreBand(R.drawable.score_circle_fair, "Fair Condition", "#FF9800");
        }
        return new ScoreBand(R.drawable.score_circle_poor, "Poor Condition", RED);
    }

    /** "3 Recalls Detected" (count above zero). */
    static String recallBadge(int openRecalls) {
        return openRecalls + " Recall" + (openRecalls > 1 ? "s" : "") + " Detected";
    }

    /** Owner count (1 when unknown) with the usage on a second line, e.g. "1\n(Lease)". */
    static String owners(Integer owners, String usage) {
        String count = (owners != null && owners > 0) ? String.valueOf(owners) : "1";
        if (usage != null && !usage.isEmpty()) {
            return count + "\n(" + usage + ")";
        }
        return count;
    }

    /** "7 yrs", else the model year, else "N/A". */
    static String age(Integer age, String year) {
        if (age != null) {
            return age + " " + (age == 1 ? "yr" : "yrs");
        }
        return year != null ? year : "N/A";
    }

    /** "from → to", either end alone when the other is missing. */
    static String ownedPeriod(String from, String to) {
        String period = "";
        if (from != null) period += from;
        if (to != null) {
            if (!period.isEmpty()) period += " → ";
            period += to;
        }
        return period;
    }

    /** The at-a-glance verdict for a status text. */
    enum Glance {
        OK("✓", GREEN),
        PROBLEM("✗", RED),
        OPEN_RECALL("!", AMBER);

        final String icon;
        final String color;

        Glance(String icon, String color) {
            this.icon = icon;
            this.color = color;
        }
    }

    /**
     * Reads a status text. Positive phrases win over "issue" ("No Issue"), then problems, then open
     * recalls; anything else is OK.
     */
    static Glance glance(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("no issue") || lower.contains("checks out") ||
            lower.contains("clean") || lower.contains("not reported") ||
            lower.contains("no records") || lower.contains("events reported")) {
            return Glance.OK;
        }
        if (lower.contains("issue") || lower.contains("found") ||
            lower.contains("problem") || lower.contains("damage") ||
            lower.contains("accident")) {
            return Glance.PROBLEM;
        }
        if (lower.contains("open recall")) {
            return Glance.OPEN_RECALL;
        }
        return Glance.OK;
    }

    /** An at-a-glance check: its status type and status read together. */
    static Glance glance(AutoCheckReport.GlanceCheck check) {
        return glance((check.statusType != null ? check.statusType : "") + " "
            + (check.status != null ? check.status : ""));
    }

    /** "vehicle_history_<vin>_<yyyyMMdd_HHmmss>.<extension>" */
    static String exportFileName(String vin, String timestamp, String extension) {
        return "vehicle_history_" + vin + "_" + timestamp + "." + extension;
    }

    /** The CSV export: vehicle, score, safety and the history events. */
    static String csv(AutoCheckReport report, String displayTimestamp) {
        StringBuilder csv = new StringBuilder();
        csv.append("OBD-Droid Vehicle History Report,").append(displayTimestamp).append("\n\n");

        csv.append("Vehicle Information\n");
        csv.append("VIN,").append(report.getVin()).append("\n");
        if (report.getYear() != null) csv.append("Year,").append(report.getYear()).append("\n");
        if (report.getMake() != null) csv.append("Make,").append(report.getMake()).append("\n");
        if (report.getModel() != null) csv.append("Model,").append(report.getModel()).append("\n");
        if (report.getStyle() != null) csv.append("Style,").append(report.getStyle()).append("\n");

        csv.append("\nAutoCheck Score\n");
        if (report.getScore() != null) {
            csv.append("Score,").append(report.getScore()).append("\n");
            if (report.getScoreRange() != null) {
                csv.append("Range,").append(report.getScoreRange().low).append("-")
                    .append(report.getScoreRange().high).append("\n");
            }
        }

        csv.append("\nSafety Information\n");
        csv.append("Category,Value\n");
        if (report.getTitleBrand() != null) csv.append("Title Brand,").append(report.getTitleBrand()).append("\n");
        if (report.getAccidentDamage() != null) {
            csv.append("Accident Damage,").append(report.getAccidentDamage()).append("\n");
        }
        csv.append("Total Loss,").append(yesNo(report.getTotalLoss())).append("\n");
        csv.append("Structural Damage,").append(yesNo(report.getStructuralDamage())).append("\n");
        csv.append("Airbag Deployed,").append(yesNo(report.getAirbagDeployed())).append("\n");
        csv.append("Odometer Rollback,").append(yesNo(report.getOdometerRollback())).append("\n");

        if (report.getHistoryEvents() != null && !report.getHistoryEvents().isEmpty()) {
            csv.append("\nHistory Events\n");
            csv.append("Date,Location,Odometer,Source,Details\n");
            for (AutoCheckReport.HistoryEvent event : report.getHistoryEvents()) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    event.date != null ? event.date : "",
                    event.location != null ? event.location.replace("\"", "\"\"") : "",
                    event.odometer != null ? event.odometer : "",
                    event.source != null ? event.source.replace("\"", "\"\"") : "",
                    event.details != null ? event.details.replace("\"", "\"\"") : ""
                ));
            }
        }
        return csv.toString();
    }

    private static String yesNo(Boolean value) {
        return value != null && value ? "Yes" : "No";
    }
}
