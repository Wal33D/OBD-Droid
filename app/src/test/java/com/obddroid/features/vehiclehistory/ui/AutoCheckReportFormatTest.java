package com.obddroid.features.vehiclehistory.ui;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.obddroid.R;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;
import com.obddroid.features.vehiclehistory.ui.AutoCheckReportFormat.Glance;

import org.junit.Test;

public class AutoCheckReportFormatTest {

    private static final String TEST_VIN = "1HGBH41JXMN109186";

    @Test
    public void scoreBandsStartAtEightySixtyAndForty() {
        assertEquals("Excellent Condition", AutoCheckReportFormat.scoreBand(80).label);
        assertEquals(R.drawable.score_circle_excellent, AutoCheckReportFormat.scoreBand(80).circleRes);
        assertEquals("Good Condition", AutoCheckReportFormat.scoreBand(79).label);
        assertEquals("Good Condition", AutoCheckReportFormat.scoreBand(60).label);
        assertEquals("Fair Condition", AutoCheckReportFormat.scoreBand(40).label);
        assertEquals("Poor Condition", AutoCheckReportFormat.scoreBand(39).label);
        assertEquals(AutoCheckReportFormat.RED, AutoCheckReportFormat.scoreBand(0).color);
    }

    @Test
    public void statsTexts() {
        assertEquals("1 Recall Detected", AutoCheckReportFormat.recallBadge(1));
        assertEquals("3 Recalls Detected", AutoCheckReportFormat.recallBadge(3));

        assertEquals("2", AutoCheckReportFormat.owners(2, null));
        assertEquals("2\n(Lease)", AutoCheckReportFormat.owners(2, "Lease"));
        assertEquals("unknown owners count as one", "1", AutoCheckReportFormat.owners(null, ""));
        assertEquals("1\n(Personal)", AutoCheckReportFormat.owners(0, "Personal"));

        assertEquals("1 yr", AutoCheckReportFormat.age(1, "2024"));
        assertEquals("7 yrs", AutoCheckReportFormat.age(7, null));
        assertEquals("2019", AutoCheckReportFormat.age(null, "2019"));
        assertEquals("N/A", AutoCheckReportFormat.age(null, null));

        assertEquals("03/2019 → 05/2022", AutoCheckReportFormat.ownedPeriod("03/2019", "05/2022"));
        assertEquals("03/2019", AutoCheckReportFormat.ownedPeriod("03/2019", null));
        assertEquals("05/2022", AutoCheckReportFormat.ownedPeriod(null, "05/2022"));
    }

    @Test
    public void glanceReadsPositivePhrasesBeforeProblems() {
        assertEquals(Glance.OK, AutoCheckReportFormat.glance("No Issue Reported"));
        assertEquals(Glance.OK, AutoCheckReportFormat.glance("Events Reported"));
        assertEquals(Glance.PROBLEM, AutoCheckReportFormat.glance("Issue Found"));
        assertEquals(Glance.PROBLEM, AutoCheckReportFormat.glance("Accident reported"));
        assertEquals(Glance.OPEN_RECALL, AutoCheckReportFormat.glance("1 open recall"));
        assertEquals(Glance.OK, AutoCheckReportFormat.glance("Unrecognised wording"));
    }

    @Test
    public void glanceCheckReadsTypeAndStatusTogether() {
        AutoCheckReport.GlanceCheck check = new AutoCheckReport.GlanceCheck();
        assertEquals("no status at all is OK", Glance.OK, AutoCheckReportFormat.glance(check));

        check.statusType = "Issue Found";
        assertEquals(Glance.PROBLEM, AutoCheckReportFormat.glance(check));

        check.status = "Checks out";
        assertEquals("a positive phrase in either part wins", Glance.OK, AutoCheckReportFormat.glance(check));
    }

    @Test
    public void exportFileName() {
        assertEquals("vehicle_history_" + TEST_VIN + "_20261117_101500.csv",
                AutoCheckReportFormat.exportFileName(TEST_VIN, "20261117_101500", "csv"));
    }

    @Test
    public void csvHasVehicleScoreSafetyAndQuotedEvents() {
        String json = "{\"vin\":\"" + TEST_VIN + "\",\"year\":\"2015\",\"make\":\"Honda\",\"model\":\"Civic\","
                + "\"score\":85,\"scoreRange\":{\"low\":80,\"high\":90},\"titleBrand\":\"Clean\","
                + "\"structuralDamage\":true,\"odometerRollback\":false,"
                + "\"historyEvents\":[{\"date\":\"01/2020\",\"location\":\"Kalamazoo, MI\",\"odometer\":\"12,000\","
                + "\"source\":\"Dealer\",\"details\":\"Oil \\\"synthetic\\\" change\"}]}";
        AutoCheckReport report = new Gson().fromJson(json, AutoCheckReport.class);

        String expected = "OBD-Droid Vehicle History Report,2026-11-17 10:15:00\n\n"
                + "Vehicle Information\n"
                + "VIN," + TEST_VIN + "\n"
                + "Year,2015\n"
                + "Make,Honda\n"
                + "Model,Civic\n"
                + "\nAutoCheck Score\n"
                + "Score,85\n"
                + "Range,80-90\n"
                + "\nSafety Information\n"
                + "Category,Value\n"
                + "Title Brand,Clean\n"
                + "Total Loss,No\n"
                + "Structural Damage,Yes\n"
                + "Airbag Deployed,No\n"
                + "Odometer Rollback,No\n"
                + "\nHistory Events\n"
                + "Date,Location,Odometer,Source,Details\n"
                + "\"01/2020\",\"Kalamazoo, MI\",\"12,000\",\"Dealer\",\"Oil \"\"synthetic\"\" change\"\n";
        assertEquals(expected, AutoCheckReportFormat.csv(report, "2026-11-17 10:15:00"));
    }

    @Test
    public void csvWithoutScoreOrEvents() {
        AutoCheckReport report = new AutoCheckReport(TEST_VIN);
        String csv = AutoCheckReportFormat.csv(report, "now");
        assertEquals("OBD-Droid Vehicle History Report,now\n\n"
                + "Vehicle Information\n"
                + "VIN," + TEST_VIN + "\n"
                + "\nAutoCheck Score\n"
                + "\nSafety Information\n"
                + "Category,Value\n"
                + "Total Loss,No\n"
                + "Structural Damage,No\n"
                + "Airbag Deployed,No\n"
                + "Odometer Rollback,No\n", csv);
    }
}
