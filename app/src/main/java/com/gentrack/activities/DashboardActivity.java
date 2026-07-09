package com.gentrack.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.gentrack.R;
import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Announcement;
import com.gentrack.models.MonthlyReport;
import com.gentrack.network.VolleyService;
import com.gentrack.services.AnnouncementService;
import com.gentrack.services.ReportService;
import com.gentrack.utils.Constants;
import com.gentrack.utils.SessionManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardActivity extends BaseActivity {

    private DatabaseHandler db;
    private String          ownerUid;

    private TextView tvActiveCount;
    private TextView tvUnpaidCount;
    private TextView tvOutstanding;
    private PieChart pieChart;
    private BarChart barChart;
    private CardView cardAnnouncement;
    private TextView tvAnnouncementTitle;
    private TextView tvAnnouncementBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        setupToolbar(getString(R.string.title_dashboard));

        ownerUid = SessionManager.getInstance(this).getUid();
        db       = DatabaseHandler.getInstance(this);

        tvActiveCount       = findViewById(R.id.tvActiveCount);
        tvUnpaidCount       = findViewById(R.id.tvUnpaidCount);
        tvOutstanding       = findViewById(R.id.tvOutstanding);
        pieChart            = findViewById(R.id.pieChart);
        barChart            = findViewById(R.id.barChart);
        cardAnnouncement    = findViewById(R.id.cardAnnouncement);
        tvAnnouncementTitle = findViewById(R.id.tvAnnouncementTitle);
        tvAnnouncementBody  = findViewById(R.id.tvAnnouncementBody);

        findViewById(R.id.btnManageCustomers).setOnClickListener(v ->
                startActivity(new Intent(this, CustomerListActivity.class)));

        cardAnnouncement.setOnClickListener(v ->
                startActivity(new Intent(this, AnnouncementsActivity.class)));

        setupPieChart();
        setupBarChart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        if (ownerUid == null || ownerUid.isEmpty()) return;
        loadStats();
        loadBarChart();
        loadLatestAnnouncement();
        fetchAndSaveRemoteConfig();
        VolleyService.getInstance(this).retrySyncQueue();
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    // ───── STAT CARDS ─────

    private void loadStats() {
        db.getCustomerStatusCounts(ownerUid, new DbCallback<Map<String, Integer>>() {
            @Override public void onResult(Map<String, Integer> counts) {
                if (!isActive()) return;
                int active = counts.containsKey(Constants.STATUS_ACTIVE)
                        ? counts.get(Constants.STATUS_ACTIVE) : 0;
                int unpaid = counts.containsKey(Constants.STATUS_UNPAID)
                        ? counts.get(Constants.STATUS_UNPAID) : 0;
                tvActiveCount.setText(String.valueOf(active));
                tvUnpaidCount.setText(String.valueOf(unpaid));
                bindPieChart(counts);
            }
            @Override public void onError(String e) {}
        });

        db.getTotalOutstanding(ownerUid, new DbCallback<Double>() {
            @Override public void onResult(Double total) {
                if (!isActive()) return;
                tvOutstanding.setText(String.format("$%.0f", total != null ? total : 0.0));
            }
            @Override public void onError(String e) {}
        });
    }

    // ───── PIE CHART ─────

    private void setupPieChart() {
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(44f);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setDrawEntryLabels(false);
        pieChart.getDescription().setEnabled(false);
        pieChart.setNoDataText(getString(R.string.label_no_data));
        pieChart.setNoDataTextColor(Color.parseColor("#6C757D"));

        Legend legend = pieChart.getLegend();
        legend.setTextColor(Color.parseColor("#1A1A2E"));
        legend.setFormSize(10f);
        legend.setTextSize(11f);
    }

    private void bindPieChart(Map<String, Integer> counts) {
        int[]    colors = {
                Color.parseColor("#2ECC71"),
                Color.parseColor("#F39C12"),
                Color.parseColor("#E74C3C")
        };
        String[] labels = {
                Constants.STATUS_ACTIVE,
                Constants.STATUS_UNPAID,
                Constants.STATUS_DISCONNECTED
        };

        List<PieEntry>  entries    = new ArrayList<>();
        List<Integer>   usedColors = new ArrayList<>();

        for (int i = 0; i < labels.length; i++) {
            int count = counts.containsKey(labels[i]) ? counts.get(labels[i]) : 0;
            if (count > 0) {
                entries.add(new PieEntry(count, labels[i]));
                usedColors.add(colors[i]);
            }
        }

        if (entries.isEmpty()) {
            pieChart.clear();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(usedColors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.animateY(600);
        pieChart.invalidate();
    }

    // ───── BAR CHART ─────

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setNoDataText(getString(R.string.label_no_data));
        barChart.setNoDataTextColor(Color.parseColor("#6C757D"));

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#1A1A2E"));
        xAxis.setDrawGridLines(false);

        barChart.getAxisLeft().setTextColor(Color.parseColor("#1A1A2E"));
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(false);
    }

    private void loadBarChart() {
        ReportService.getInstance(this)
                .getMonthlyRevenueReports(ownerUid, 3, new DbCallback<List<MonthlyReport>>() {
                    @Override public void onResult(List<MonthlyReport> reports) {
                        if (!isActive()) return;
                        bindBarChart(reports);
                    }

                    @Override public void onError(String e) {
                        if (isActive()) barChart.clear();
                    }
                });
    }

    private void bindBarChart(List<MonthlyReport> reports) {
        if (reports == null || reports.isEmpty()) {
            barChart.clear();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < reports.size(); i++) {
            MonthlyReport report = reports.get(i);
            String month = report.getMonth();
            if (month == null || month.isEmpty()) continue;
            entries.add(new BarEntry(i, (float) report.getTotalExpectedRevenue()));
            // shorten label: "2025-03" → "25-03"
            labels.add(month.length() >= 7 ? month.substring(2) : month);
        }

        if (entries.isEmpty()) {
            barChart.clear();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(Color.parseColor("#E94560"));
        dataSet.setValueTextColor(Color.parseColor("#1A1A2E"));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return "$" + (int) value;
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        final List<String> finalLabels = labels;
        barChart.setData(data);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(finalLabels));
        barChart.getXAxis().setLabelCount(finalLabels.size());
        barChart.animateY(600);
        barChart.invalidate();
    }

    // ───── ANNOUNCEMENT ─────

    private void loadLatestAnnouncement() {
        AnnouncementService.getInstance()
                .getLatest(ownerUid, new DbCallback<Announcement>() {
                    @Override public void onResult(Announcement announcement) {
                    if (!isActive()) return;
                    if (announcement == null) {
                        cardAnnouncement.setVisibility(View.GONE);
                        return;
                    }
                    String title = announcement.getTitle();
                    String body = announcement.getMessage();
                    tvAnnouncementTitle.setText(title != null ? title : "");
                    tvAnnouncementBody.setText(body != null ? body : "");
                    cardAnnouncement.setVisibility(View.VISIBLE);
                    }

                    @Override public void onError(String e) {
                        if (isActive()) cardAnnouncement.setVisibility(View.GONE);
                    }
                });
    }

    // ───── REMOTE CONFIG ─────

    private void fetchAndSaveRemoteConfig() {
        // Parsing and SessionManager writes happen inside VolleyService.fetchRemoteConfig()
        VolleyService.getInstance(this).fetchRemoteConfig(null);
    }
}
