package com.obddroid.features.vehiclehistory.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

/**
 * The report part of the vehicle history screen (activity_autocheck): hero score, score analysis, stats
 * grid, safety, recalls and the history timeline, bound from the layout and filled from one report. The
 * cards built in code (at-a-glance, odometer, owners) come from {@link AutoCheckExtraCards}.
 */
final class AutoCheckReportView {

    private final Context context;

    // Hero score
    private final TextView vehicleName;
    private final TextView vehicleVin;
    private final View scoreCircleBackground;
    private final TextView autoCheckScore;
    private final TextView scoreRange;
    private final TextView scoreInterpretation;
    private final LinearLayout quickStatusContainer;

    // Score analysis
    private final CardView scoreAnalysisCard;
    private final LinearLayout comparisonContainer;
    private final TextView vehicleComparison;
    private final LinearLayout outlookContainer;
    private final TextView vehicleOutlook;
    private final LinearLayout increasingFactorsContainer;
    private final LinearLayout increasingFactorsList;
    private final LinearLayout decreasingFactorsContainer;
    private final LinearLayout decreasingFactorsList;

    // Stats grid
    private final TextView statOwners;
    private final TextView statOdometer;
    private final TextView statServiceRecords;
    private final TextView statYear;

    // Safety
    private final TextView titleBrandValue;
    private final TextView accidentValue;
    private final TextView structuralValue;
    private final TextView airbagValue;
    private final TextView rollbackValue;

    // Recalls, details, timeline
    private final CardView recallsCard;
    private final LinearLayout recallsContainer;
    private final CardView detailsCard;
    private final CardView timelineCard;
    private final TextView timelineSummary;
    private final LinearLayout timelineContainer;

    private final AutoCheckExtraCards extraCards;

    AutoCheckReportView(Activity activity) {
        this.context = activity;

        vehicleName = activity.findViewById(R.id.vehicle_name);
        vehicleVin = activity.findViewById(R.id.vehicle_vin);
        scoreCircleBackground = activity.findViewById(R.id.score_circle_background);
        autoCheckScore = activity.findViewById(R.id.autocheck_score);
        scoreRange = activity.findViewById(R.id.score_range);
        scoreInterpretation = activity.findViewById(R.id.score_interpretation);
        quickStatusContainer = activity.findViewById(R.id.quick_status_container);

        scoreAnalysisCard = activity.findViewById(R.id.score_analysis_card);
        comparisonContainer = activity.findViewById(R.id.comparison_container);
        vehicleComparison = activity.findViewById(R.id.vehicle_comparison);
        outlookContainer = activity.findViewById(R.id.outlook_container);
        vehicleOutlook = activity.findViewById(R.id.vehicle_outlook);
        increasingFactorsContainer = activity.findViewById(R.id.increasing_factors_container);
        increasingFactorsList = activity.findViewById(R.id.increasing_factors_list);
        decreasingFactorsContainer = activity.findViewById(R.id.decreasing_factors_container);
        decreasingFactorsList = activity.findViewById(R.id.decreasing_factors_list);

        statOwners = activity.findViewById(R.id.stat_owners);
        statOdometer = activity.findViewById(R.id.stat_odometer);
        statServiceRecords = activity.findViewById(R.id.stat_service_records);
        statYear = activity.findViewById(R.id.stat_year);

        titleBrandValue = activity.findViewById(R.id.title_brand_value);
        accidentValue = activity.findViewById(R.id.accident_value);
        structuralValue = activity.findViewById(R.id.structural_value);
        airbagValue = activity.findViewById(R.id.airbag_value);
        rollbackValue = activity.findViewById(R.id.rollback_value);

        recallsCard = activity.findViewById(R.id.recalls_card);
        recallsContainer = activity.findViewById(R.id.recalls_container);
        detailsCard = activity.findViewById(R.id.details_card);
        timelineCard = activity.findViewById(R.id.timeline_card);
        timelineSummary = activity.findViewById(R.id.timeline_summary);
        timelineContainer = activity.findViewById(R.id.timeline_container);

        extraCards = new AutoCheckExtraCards(activity,
            activity.findViewById(R.id.report_container), timelineCard);
    }

    /** Fills every section from the report; cards built for an earlier report are removed first. */
    void show(AutoCheckReport report) {
        extraCards.removeAll();

        showHero(report);
        showScoreAnalysis(report);
        showStats(report);
        showSafety(report);
        showRecalls(report);
        // Vehicle details moved to the at-a-glance card
        detailsCard.setVisibility(View.GONE);
        extraCards.addAtAGlance(report);
        extraCards.addOdometerChecks(report);
        extraCards.addOwnerHistory(report);
        showTimeline(report);
    }

    private void showHero(AutoCheckReport report) {
        vehicleName.setText(report.getVehicleName());
        vehicleVin.setText(report.getVin());

        if (report.getScore() != null) {
            int score = report.getScore();
            autoCheckScore.setText(String.valueOf(score));

            AutoCheckReportFormat.ScoreBand band = AutoCheckReportFormat.scoreBand(score);
            scoreCircleBackground.setBackgroundResource(band.circleRes);
            scoreInterpretation.setText(band.label);
            scoreInterpretation.setTextColor(Color.parseColor(band.color));

            if (report.getScoreRange() != null) {
                scoreRange.setText(String.format("Range: %d - %d",
                    report.getScoreRange().low, report.getScoreRange().high));
            } else {
                scoreRange.setText("Score: " + score);
            }
        } else {
            autoCheckScore.setText("N/A");
            scoreRange.setVisibility(View.GONE);
            scoreInterpretation.setText("Score Not Available");
            scoreInterpretation.setTextColor(Color.parseColor(AutoCheckReportFormat.GREY));
        }

        // Recall count badge
        quickStatusContainer.removeAllViews();
        if (report.getOpenRecalls() != null && report.getOpenRecalls() > 0) {
            addStatusBadge(AutoCheckReportFormat.recallBadge(report.getOpenRecalls()), R.drawable.status_badge_warning);
        } else if (report.getOpenRecalls() != null && report.getOpenRecalls() == 0) {
            addStatusBadge("No Open Recalls", R.drawable.status_badge_success);
        }
    }

    private void addStatusBadge(String text, int backgroundResource) {
        TextView badge = new TextView(context);
        badge.setText(text);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(12);
        badge.setPadding(24, 12, 24, 12);
        badge.setBackgroundResource(backgroundResource);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);
        badge.setLayoutParams(params);

        quickStatusContainer.addView(badge);
    }

    private void showScoreAnalysis(AutoCheckReport report) {
        boolean hasComparison = report.getVehicleComparison() != null && !report.getVehicleComparison().isEmpty();
        boolean hasOutlook = report.getVehicleOutlook() != null && !report.getVehicleOutlook().isEmpty();
        boolean hasIncreasing = report.getIncreasingFactors() != null && !report.getIncreasingFactors().isEmpty();
        boolean hasDecreasing = report.getDecreasingFactors() != null && !report.getDecreasingFactors().isEmpty();

        if (!hasComparison && !hasOutlook && !hasIncreasing && !hasDecreasing) {
            scoreAnalysisCard.setVisibility(View.GONE);
            return;
        }
        scoreAnalysisCard.setVisibility(View.VISIBLE);

        showText(comparisonContainer, vehicleComparison, hasComparison ? report.getVehicleComparison() : null);
        showText(outlookContainer, vehicleOutlook, hasOutlook ? report.getVehicleOutlook() : null);
        showFactors(increasingFactorsContainer, increasingFactorsList,
            hasIncreasing ? report.getIncreasingFactors() : null);
        showFactors(decreasingFactorsContainer, decreasingFactorsList,
            hasDecreasing ? report.getDecreasingFactors() : null);
    }

    /** Shows the text in its container, or hides the container when the text is null. */
    private static void showText(View container, TextView textView, String text) {
        if (text != null) {
            container.setVisibility(View.VISIBLE);
            textView.setText(text);
        } else {
            container.setVisibility(View.GONE);
        }
    }

    /** A bullet list of score factors, or the container hidden when there are none. */
    private void showFactors(View container, LinearLayout list, Iterable<String> factors) {
        if (factors == null) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        list.removeAllViews();
        for (String factor : factors) {
            TextView factorView = new TextView(context);
            factorView.setText("• " + factor);
            factorView.setTextSize(14);
            factorView.setTextColor(Color.parseColor("#FFFFFF"));
            factorView.setPadding(0, 0, 0, dpToPx(8));
            factorView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            list.addView(factorView);
        }
    }

    private void showStats(AutoCheckReport report) {
        statOwners.setText(AutoCheckReportFormat.owners(report.getOwners(), report.getUsage()));

        if (report.getLastOdometer() != null) {
            statOdometer.setText(String.format("%,d", report.getLastOdometer()));
        } else {
            statOdometer.setText("N/A");
        }

        if (report.getServiceRecords() != null) {
            statServiceRecords.setText(String.valueOf(report.getServiceRecords()));
        } else {
            statServiceRecords.setText("0");
        }

        statYear.setText(AutoCheckReportFormat.age(report.getVehicleAge(), report.getYear()));
    }

    private void showSafety(AutoCheckReport report) {
        if (report.getTitleBrand() != null) {
            titleBrandValue.setText(report.getTitleBrand().toUpperCase());
            titleBrandValue.setBackgroundResource(report.hasCleanTitle()
                ? R.drawable.status_badge_success : R.drawable.status_badge_error);
        } else {
            titleBrandValue.setText("UNKNOWN");
            titleBrandValue.setBackgroundResource(R.drawable.status_badge_warning);
        }

        if (report.getAccidentDamage() != null) {
            accidentValue.setText(report.getAccidentDamage());
            accidentValue.setTextColor(Color.parseColor(report.hasAccidents()
                ? AutoCheckReportFormat.RED : AutoCheckReportFormat.GREEN));
        } else {
            accidentValue.setText("Unknown");
            accidentValue.setTextColor(Color.parseColor(AutoCheckReportFormat.GREY));
        }

        showYesIsBad(structuralValue, report.getStructuralDamage());
        showYesIsBad(airbagValue, report.getAirbagDeployed());

        // No rollback unless AutoCheck says so
        if (Boolean.TRUE.equals(report.getOdometerRollback())) {
            rollbackValue.setText("✗ Rollback Detected");
            rollbackValue.setTextColor(Color.parseColor(AutoCheckReportFormat.RED));
        } else {
            rollbackValue.setText("✓ No Rollback");
            rollbackValue.setTextColor(Color.parseColor(AutoCheckReportFormat.GREEN));
        }
    }

    /** "✗ Yes" in red, "✓ No" in green, "Unknown" in grey. */
    private static void showYesIsBad(TextView textView, Boolean value) {
        if (value == null) {
            textView.setText("Unknown");
            textView.setTextColor(Color.parseColor(AutoCheckReportFormat.GREY));
        } else if (value) {
            textView.setText("✗ Yes");
            textView.setTextColor(Color.parseColor(AutoCheckReportFormat.RED));
        } else {
            textView.setText("✓ No");
            textView.setTextColor(Color.parseColor(AutoCheckReportFormat.GREEN));
        }
    }

    private void showRecalls(AutoCheckReport report) {
        if (report.getRecallDetails() == null || report.getRecallDetails().isEmpty()) {
            recallsCard.setVisibility(View.GONE);
            return;
        }

        recallsCard.setVisibility(View.VISIBLE);
        recallsContainer.removeAllViews();
        for (AutoCheckReport.RecallDetail recall : report.getRecallDetails()) {
            recallsContainer.addView(recallItem(recall));
        }
    }

    private View recallItem(AutoCheckReport.RecallDetail recall) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setBackgroundResource(R.drawable.stat_card_background);
        itemLayout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 0, 0, dpToPx(8));
        itemLayout.setLayoutParams(layoutParams);

        // Date and type
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView dateText = new TextView(context);
        dateText.setText(recall.recallDate != null ? recall.recallDate : "Unknown Date");
        dateText.setTextSize(13);
        dateText.setTextColor(Color.parseColor(AutoCheckReportFormat.AMBER));
        dateText.setTypeface(null, Typeface.BOLD);
        dateText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        headerRow.addView(dateText);

        TextView typeText = new TextView(context);
        typeText.setText(recall.recallType != null ? recall.recallType : "");
        typeText.setTextSize(12);
        typeText.setTextColor(Color.parseColor("#B0BEC5"));
        typeText.setGravity(Gravity.END);
        headerRow.addView(typeText);

        itemLayout.addView(headerRow);

        // NHTSA / OEM recall numbers
        if (recall.combinedRecallNo != null && !recall.combinedRecallNo.isEmpty()) {
            TextView recallNoText = new TextView(context);
            recallNoText.setText(recall.combinedRecallNo);
            recallNoText.setTextSize(11);
            recallNoText.setTextColor(Color.parseColor("#90CAF9"));
            recallNoText.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams recallNoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            recallNoParams.setMargins(0, dpToPx(4), 0, dpToPx(6));
            recallNoText.setLayoutParams(recallNoParams);
            itemLayout.addView(recallNoText);
        }

        TextView descriptionText = new TextView(context);
        descriptionText.setText(recall.campaignDescription != null ? recall.campaignDescription : "No description available");
        descriptionText.setTextSize(12);
        descriptionText.setTextColor(Color.parseColor("#FFFFFF"));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, dpToPx(4), 0, 0);
        descriptionText.setLayoutParams(descParams);
        itemLayout.addView(descriptionText);

        if (recall.status != null && !recall.status.isEmpty()) {
            TextView statusText = new TextView(context);
            statusText.setText("Status: " + recall.status);
            statusText.setTextSize(11);
            statusText.setTextColor(Color.parseColor(AutoCheckReportFormat.AMBER));
            statusText.setTypeface(null, Typeface.ITALIC);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            statusParams.setMargins(0, dpToPx(6), 0, 0);
            statusText.setLayoutParams(statusParams);
            itemLayout.addView(statusText);
        }

        return itemLayout;
    }

    /** The 15 most recent history events. */
    private void showTimeline(AutoCheckReport report) {
        if (report.getHistoryEvents() == null || report.getHistoryEvents().isEmpty()) {
            timelineCard.setVisibility(View.GONE);
            return;
        }

        timelineCard.setVisibility(View.VISIBLE);
        timelineContainer.removeAllViews();

        int totalEvents = report.getHistoryEvents().size();
        int displayCount = Math.min(15, totalEvents);
        timelineSummary.setText(String.format("%d total events (showing %d most recent)",
            totalEvents, displayCount));

        for (int i = 0; i < displayCount; i++) {
            addTimelineEvent(report.getHistoryEvents().get(i), i == displayCount - 1);
        }
    }

    private void addTimelineEvent(AutoCheckReport.HistoryEvent event, boolean isLast) {
        LinearLayout eventLayout = new LinearLayout(context);
        eventLayout.setOrientation(LinearLayout.HORIZONTAL);
        eventLayout.setPadding(0, 0, 0, isLast ? 0 : 16);

        // Dot, and a line down to the next event
        LinearLayout timelineIndicator = new LinearLayout(context);
        timelineIndicator.setOrientation(LinearLayout.VERTICAL);
        timelineIndicator.setGravity(Gravity.CENTER_HORIZONTAL);
        timelineIndicator.setPadding(0, 6, 20, 0);

        View dot = new View(context);
        dot.setBackgroundResource(R.drawable.timeline_dot);
        dot.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
        timelineIndicator.addView(dot);

        if (!isLast) {
            View line = new View(context);
            line.setBackgroundResource(R.drawable.timeline_line);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(3,
                ViewGroup.LayoutParams.MATCH_PARENT);
            lineParams.topMargin = 6;
            line.setLayoutParams(lineParams);
            timelineIndicator.addView(line);
        }

        timelineIndicator.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        eventLayout.addView(timelineIndicator);

        LinearLayout eventContent = new LinearLayout(context);
        eventContent.setOrientation(LinearLayout.VERTICAL);
        eventContent.setPadding(16, 14, 16, 14);
        eventContent.setBackgroundColor(Color.TRANSPARENT);

        TextView dateText = new TextView(context);
        dateText.setText(event.date != null ? event.date : "Unknown Date");
        dateText.setTextSize(15);
        dateText.setTextColor(Color.parseColor("#00ACC1"));
        dateText.setTypeface(null, Typeface.BOLD);
        dateText.setLetterSpacing(0.02f);
        eventContent.addView(dateText);

        TextView detailsText = new TextView(context);
        detailsText.setText(event.details != null ? event.details : "No details");
        detailsText.setTextSize(15);
        detailsText.setTextColor(Color.parseColor("#E0E0E0"));
        detailsText.setPadding(0, 8, 0, 0);
        detailsText.setLineSpacing(4, 1.0f);
        eventContent.addView(detailsText);

        if (event.location != null && !event.location.isEmpty()) {
            addEventLine(eventContent, "📍 " + event.location, 13, "#D32F2F", 8);
        }
        if (event.odometer != null && !event.odometer.isEmpty()) {
            addEventLine(eventContent, "🛣 " + event.odometer + " miles", 13, "#66BB6A", 4);
        }
        if (event.source != null && !event.source.isEmpty()) {
            addEventLine(eventContent, "Source: " + event.source, 12, "#B0BEC5", 8);
        }

        eventContent.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        eventLayout.addView(eventContent);

        timelineContainer.addView(eventLayout);
    }

    private void addEventLine(LinearLayout parent, String text, int sizeSp, String color, int paddingTop) {
        TextView line = new TextView(context);
        line.setText(text);
        line.setTextSize(sizeSp);
        line.setTextColor(Color.parseColor(color));
        line.setPadding(0, paddingTop, 0, 0);
        parent.addView(line);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
