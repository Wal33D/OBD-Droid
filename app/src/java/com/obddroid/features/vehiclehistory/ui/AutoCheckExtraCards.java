package com.obddroid.features.vehiclehistory.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import java.util.List;

/**
 * The report cards the layout does not have, built in code and placed above the history timeline:
 * at-a-glance checks, odometer validation and ownership history. Each is tagged "dynamic_card_..." so a
 * new report can remove them.
 */
final class AutoCheckExtraCards {

    private static final String TAG_PREFIX = "dynamic_card_";

    private final Context context;
    private final Resources res;
    private final LinearLayout reportContainer;
    private final View timelineCard;

    AutoCheckExtraCards(Context context, LinearLayout reportContainer, View timelineCard) {
        this.context = context;
        this.res = context.getResources();
        this.reportContainer = reportContainer;
        this.timelineCard = timelineCard;
    }

    /** Removes the cards added for an earlier report. */
    void removeAll() {
        if (reportContainer == null) return;

        for (int i = reportContainer.getChildCount() - 1; i >= 0; i--) {
            Object tag = reportContainer.getChildAt(i).getTag();
            if (tag != null && tag.toString().startsWith(TAG_PREFIX)) {
                reportContainer.removeViewAt(i);
            }
        }
    }

    void addAtAGlance(AutoCheckReport report) {
        AutoCheckReport.AtAGlance atAGlance = report.getAtAGlance();
        if (atAGlance == null) {
            return;
        }

        LinearLayout container = cardContent("Vehicle Status At-A-Glance", 20);

        addGlanceCheck(container, "State Title Brand", atAGlance.stateTitleBrand);
        addGlanceCheck(container, "Auction Brand Issues", atAGlance.auctionBrandIssues);
        addGlanceCheck(container, "Accident/Damage", atAGlance.accidentDamage);
        addGlanceCheck(container, "Insurance Loss Transfer", atAGlance.insuranceLossTransfer);
        addGlanceCheck(container, "Odometer", atAGlance.odometerCheck);
        addGlanceCheck(container, "Certified Pre-Owned", atAGlance.certifiedPreOwned);
        addGlanceCheck(container, "Service/Repair Records", atAGlance.serviceRepair);
        addGlanceCheck(container, "Additional History", atAGlance.additionalHistory);

        String damage = report.getDamageMessage();
        if (damage != null && !damage.trim().isEmpty()) {
            addGlanceItem(container, AutoCheckReportFormat.glance(damage), "Damage Information", damage, null);
        }

        insertCard("ataglance", container);
    }

    void addOdometerChecks(AutoCheckReport report) {
        AutoCheckReport.OdometerSubChecks checks = report.getOdometerSubChecks();
        if (checks == null) return;

        LinearLayout container = cardContent("Odometer Validation", 18);

        if (checks.stateTitleOdometerCheck != null) {
            addOdometerCheck(container, "State Title Check", checks.stateTitleOdometerCheck);
        }
        if (checks.auctionOdometerCheck != null) {
            addOdometerCheck(container, "Auction Check", checks.auctionOdometerCheck);
        }
        if (checks.odometerCalculationCheck != null) {
            addOdometerCheck(container, "Calculation Check", checks.odometerCalculationCheck);
        }

        insertCard("odometer", container);
    }

    void addOwnerHistory(AutoCheckReport report) {
        List<AutoCheckReport.OwnerHistory> owners = report.getOwnerHistory();
        if (owners == null || owners.isEmpty()) return;

        LinearLayout container = cardContent(
            "Ownership History (" + owners.size() + " Owner" + (owners.size() > 1 ? "s" : "") + ")", 18);

        for (int i = 0; i < owners.size(); i++) {
            addOwner(container, owners.get(i), i == owners.size() - 1);
        }

        insertCard("owner", container);
    }

    /** The padded content of a new card, starting with its title. */
    private LinearLayout cardContent(String titleText, int titleSizeSp) {
        int padding = (int) res.getDimension(R.dimen.card_padding_standard);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(titleSizeSp);
        title.setTextColor(Color.parseColor("#FFFFFF"));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, (int) res.getDimension(R.dimen.spacing_medium));
        container.addView(title);
        return container;
    }

    /** Wraps the content in a tagged card and puts it just above the timeline card. */
    private void insertCard(String name, LinearLayout content) {
        CardView card = new CardView(context);
        card.setTag(TAG_PREFIX + name);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, (int) res.getDimension(R.dimen.spacing_large));
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(res.getColor(R.color.background_secondary));
        card.setRadius(res.getDimension(R.dimen.card_corner_radius));
        card.setCardElevation(res.getDimension(R.dimen.elevation_content_card));
        card.addView(content);

        if (reportContainer != null && timelineCard != null) {
            int timelineIndex = reportContainer.indexOfChild(timelineCard);
            if (timelineIndex >= 0) {
                reportContainer.addView(card, timelineIndex);
            }
        }
    }

    private void addGlanceCheck(LinearLayout parent, String label, AutoCheckReport.GlanceCheck check) {
        if (check == null) return;
        addGlanceItem(parent, AutoCheckReportFormat.glance(check), label, check.status, check.description);
    }

    /** An icon, a label, and the status and description lines that are present. */
    private void addGlanceItem(LinearLayout parent, AutoCheckReportFormat.Glance glance, String label,
                               String status, String description) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 12, 0, 12);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView icon = new TextView(context);
        icon.setText(glance.icon);
        icon.setTextSize(18);
        icon.setTextColor(Color.parseColor(glance.color));
        icon.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconParams.setMargins(0, 0, 20, 0);
        icon.setLayoutParams(iconParams);
        itemLayout.addView(icon);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView labelText = new TextView(context);
        labelText.setText(label);
        labelText.setTextSize(15);
        labelText.setTextColor(Color.parseColor("#FFFFFF"));
        labelText.setTypeface(null, Typeface.BOLD);
        contentLayout.addView(labelText);

        if (status != null) {
            addLine(contentLayout, status, 14, "#B0BEC5");
        }
        if (description != null) {
            addLine(contentLayout, description, 13, "#90A4AE");
        }

        itemLayout.addView(contentLayout);
        parent.addView(itemLayout);
    }

    private void addLine(LinearLayout parent, String text, int sizeSp, String color) {
        TextView line = new TextView(context);
        line.setText(text);
        line.setTextSize(sizeSp);
        line.setTextColor(Color.parseColor(color));
        line.setPadding(0, 4, 0, 0);
        parent.addView(line);
    }

    private void addOdometerCheck(LinearLayout parent, String label, String status) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 8, 0, 8);

        TextView labelText = new TextView(context);
        labelText.setText(label + ": ");
        labelText.setTextSize(14);
        labelText.setTextColor(Color.parseColor("#B0BEC5"));
        itemLayout.addView(labelText);

        TextView statusText = new TextView(context);
        statusText.setText(status);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.parseColor("#FFFFFF"));
        statusText.setTypeface(null, Typeface.BOLD);
        itemLayout.addView(statusText);

        parent.addView(itemLayout);
    }

    private void addOwner(LinearLayout parent, AutoCheckReport.OwnerHistory owner, boolean isLast) {
        int spacingMedium = (int) res.getDimension(R.dimen.spacing_medium);

        LinearLayout ownerLayout = new LinearLayout(context);
        ownerLayout.setOrientation(LinearLayout.VERTICAL);
        ownerLayout.setPadding(0, 0, 0, isLast ? 0 : spacingMedium);

        TextView header = new TextView(context);
        header.setText("Owner #" + owner.ownerNumber);
        header.setTextSize(16);
        header.setTextColor(Color.parseColor("#00ACC1"));
        header.setTypeface(null, Typeface.BOLD);
        ownerLayout.addView(header);

        if (owner.location != null) {
            addLine(ownerLayout, "📍 " + owner.location, 14, "#B0BEC5");
        }
        if (owner.ownedFrom != null || owner.ownedTo != null) {
            addLine(ownerLayout, "📅 " + AutoCheckReportFormat.ownedPeriod(owner.ownedFrom, owner.ownedTo),
                14, "#B0BEC5");
        }
        if (owner.usage != null) {
            addLine(ownerLayout, "🚗 " + owner.usage, 14, "#B0BEC5");
        }

        if (owner.events != null && !owner.events.isEmpty()) {
            TextView eventsText = new TextView(context);
            eventsText.setText("• " + owner.events.size() + " event" + (owner.events.size() > 1 ? "s" : "") + " during ownership");
            eventsText.setTextSize(13);
            eventsText.setTextColor(Color.parseColor("#90A4AE"));
            eventsText.setPadding(0, 8, 0, 0);
            eventsText.setTypeface(null, Typeface.ITALIC);
            ownerLayout.addView(eventsText);
        }

        parent.addView(ownerLayout);

        if (!isLast) {
            View separator = new View(context);
            separator.setBackgroundColor(Color.parseColor("#37474F"));
            LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2
            );
            sepParams.setMargins(0, spacingMedium, 0, spacingMedium);
            separator.setLayoutParams(sepParams);
            parent.addView(separator);
        }
    }
}
