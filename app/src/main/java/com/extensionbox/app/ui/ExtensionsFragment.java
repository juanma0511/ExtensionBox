package com.extensionbox.app.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class ExtensionsFragment extends Fragment {

    private LinearLayout container;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_extensions, parent, false);
        container = v.findViewById(R.id.extContainer);
        buildList();
        return v;
    }

    private void buildList() {
        container.removeAllViews();
        for (int i = 0; i < ModuleRegistry.count(); i++) {
            container.addView(buildModuleCard(i));
        }
    }

    private View buildModuleCard(int idx) {
        String key = ModuleRegistry.keyAt(idx);

        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(6);
        card.setLayoutParams(clp);
        card.setContentPadding(dp(16), dp(12), dp(16), dp(12));
        card.setCardElevation(0);
        card.setStrokeWidth(0);

        LinearLayout outer = new LinearLayout(requireContext());
        outer.setOrientation(LinearLayout.VERTICAL);

        // Row 1: Title + Switch
        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(requireContext());
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(requireContext());
        title.setText(ModuleRegistry.emojiAt(idx) + "  " + ModuleRegistry.nameAt(idx));
        title.setTextSize(16);
        titleCol.addView(title);

        TextView desc = new TextView(requireContext());
        desc.setText(ModuleRegistry.descAt(idx));
        desc.setTextSize(12);
        desc.setAlpha(0.6f);
        titleCol.addView(desc);

        row1.addView(titleCol);

        MaterialSwitch sw = new MaterialSwitch(requireContext());
        sw.setChecked(Prefs.isModuleEnabled(requireContext(), key, ModuleRegistry.defAt(idx)));
        row1.addView(sw);
        outer.addView(row1);

        // Settings panel (collapsed)
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(12), 0, 0);
        panel.setVisibility(View.GONE);
        buildSettingsPanel(panel, key);

        // Settings toggle button
        TextView btnSettings = new TextView(requireContext());
        btnSettings.setText("⚙ Settings");
        btnSettings.setTextSize(13);
        btnSettings.setPadding(0, dp(8), 0, 0);
        btnSettings.setAlpha(0.7f);
        btnSettings.setOnClickListener(v2 ->
                panel.setVisibility(panel.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        outer.addView(btnSettings);
        outer.addView(panel);

        // Switch listener
        sw.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setModuleEnabled(requireContext(), key, checked);
            btnSettings.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) panel.setVisibility(View.GONE);
        });

        boolean enabled = sw.isChecked();
        btnSettings.setVisibility(enabled ? View.VISIBLE : View.GONE);

        card.addView(outer);
        return card;
    }

    private void buildSettingsPanel(LinearLayout panel, String key) {
        switch (key) {
            case "battery":
                addSpinnerWithCustom(panel, "bat_interval", "Refresh",
                        new String[]{"5s","10s","30s","60s"}, new int[]{5000,10000,30000,60000}, 10000);
                addSwitch(panel, "bat_low_alert", "Low Battery Alert", true);
                addSpinnerWithCustom(panel, "bat_low_thresh", "Alert Threshold",
                        new String[]{"5%","10%","15%","20%","25%","30%"}, new int[]{5,10,15,20,25,30}, 15);
                addSwitch(panel, "bat_temp_alert", "High Temp Alert", true);
                addSpinnerWithCustom(panel, "bat_temp_thresh", "Temp Threshold",
                        new String[]{"38°C","40°C","42°C","45°C"}, new int[]{38,40,42,45}, 42);
                break;
            case "cpu_ram":
                addSpinnerWithCustom(panel, "cpu_interval", "Refresh",
                        new String[]{"3s","5s","10s","30s"}, new int[]{3000,5000,10000,30000}, 5000);
                addSwitch(panel, "cpu_ram_alert", "High RAM Alert", false);
                addSpinnerWithCustom(panel, "cpu_ram_thresh", "RAM Threshold",
                        new String[]{"80%","85%","90%","95%"}, new int[]{80,85,90,95}, 90);
                break;
            case "screen":
                addSpinnerWithCustom(panel, "scr_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "scr_time_limit", "Screen Time Limit",
                        new String[]{"Off","1h","2h","3h","4h","6h","8h"}, new int[]{0,60,120,180,240,360,480}, 0);
                addSwitch(panel, "scr_show_drain", "Show Drain Info", true);
                addSwitch(panel, "scr_show_yesterday", "Show Yesterday", true);
                break;
            case "sleep":
                addSpinnerWithCustom(panel, "slp_interval", "Refresh",
                        new String[]{"10s","30s","60s"}, new int[]{10000,30000,60000}, 30000);
                break;
            case "network":
                addSpinnerWithCustom(panel, "net_interval", "Refresh",
                        new String[]{"1s","3s","5s","10s"}, new int[]{1000,3000,5000,10000}, 3000);
                break;
            case "data":
                addSpinnerWithCustom(panel, "dat_interval", "Refresh",
                        new String[]{"30s","60s","5min"}, new int[]{30000,60000,300000}, 60000);
                addSwitch(panel, "dat_show_breakdown", "WiFi/Mobile Split", true);
                addSpinnerWithCustom(panel, "dat_plan_limit", "Monthly Plan",
                        new String[]{"Off","1GB","2GB","5GB","10GB","20GB","50GB"}, new int[]{0,1024,2048,5120,10240,20480,51200}, 0);
                addSpinnerWithCustom(panel, "dat_plan_alert_pct", "Plan Alert At",
                        new String[]{"80%","90%","95%"}, new int[]{80,90,95}, 90);
                addSpinnerWithCustom(panel, "dat_billing_day", "Billing Day",
                        new String[]{"1","5","10","15","20","25"}, new int[]{1,5,10,15,20,25}, 1);
                break;
            case "unlock":
                addSpinnerWithCustom(panel, "ulk_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "ulk_daily_limit", "Daily Limit",
                        new String[]{"Off","20","30","50","75","100"}, new int[]{0,20,30,50,75,100}, 0);
                addSwitch(panel, "ulk_limit_alert", "Alert on Limit", true);
                addSpinnerWithCustom(panel, "ulk_debounce", "Debounce",
                        new String[]{"3s","5s","10s"}, new int[]{3000,5000,10000}, 5000);
                break;
            case "storage":
                addSpinnerWithCustom(panel, "sto_interval", "Refresh",
                        new String[]{"1min","5min","15min"}, new int[]{60000,300000,900000}, 300000);
                addSwitch(panel, "sto_low_alert", "Low Storage Alert", true);
                addSpinnerWithCustom(panel, "sto_low_thresh_mb", "Low Threshold",
                        new String[]{"500MB","1GB","2GB","5GB"}, new int[]{500,1000,2000,5000}, 1000);
                break;
            case "connection":
                addSpinnerWithCustom(panel, "con_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                break;
            case "uptime":
                addSpinnerWithCustom(panel, "upt_interval", "Refresh",
                        new String[]{"30s","1min","5min"}, new int[]{30000,60000,300000}, 60000);
                break;
            case "steps":
                addSpinnerWithCustom(panel, "stp_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "stp_goal", "Daily Goal",
                        new String[]{"Off","5000","8000","10000","15000"}, new int[]{0,5000,8000,10000,15000}, 10000);
                addSpinnerWithCustom(panel, "stp_stride_cm", "Step Length",
                        new String[]{"60cm","65cm","70cm","75cm","80cm","85cm"}, new int[]{60,65,70,75,80,85}, 75);
                addSwitch(panel, "stp_show_distance", "Show Distance", true);
                addSwitch(panel, "stp_show_yesterday", "Show Yesterday", true);
                addSwitch(panel, "stp_show_goal", "Show Goal", true);
                break;
            case "speedtest":
                addSpinnerWithCustom(panel, "spd_interval", "Display Refresh",
                        new String[]{"10s","30s","60s"}, new int[]{10000,30000,60000}, 30000);
                addSwitch(panel, "spd_auto_test", "Auto Test", true);
                addSpinnerWithCustom(panel, "spd_test_freq", "Test Frequency",
                        new String[]{"15min","30min","1h","2h"}, new int[]{15,30,60,120}, 60);
                addSwitch(panel, "spd_wifi_only", "WiFi Only", true);
                addSwitch(panel, "spd_show_ping", "Show Ping", true);
                addSpinnerWithCustom(panel, "spd_daily_limit", "Daily Test Limit",
                        new String[]{"5","10","20","∞"}, new int[]{5,10,20,9999}, 10);
                break;
            case "fap":
                addSpinnerWithCustom(panel, "fap_interval", "Refresh",
                        new String[]{"30s","1min","5min"}, new int[]{30000,60000,300000}, 60000);
                addSpinnerWithCustom(panel, "fap_daily_limit", "Daily Limit Warning",
                        new String[]{"Off","1","2","3","5"}, new int[]{0,1,2,3,5}, 0);
                addSwitch(panel, "fap_show_streak", "Show Streak", true);
                addSwitch(panel, "fap_show_yesterday", "Show Yesterday", true);
                addSwitch(panel, "fap_show_alltime", "Show All-Time Total", true);
                break;
        }
    }

    private void addSwitch(LinearLayout parent, String prefKey, String label, boolean def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        MaterialSwitch sw = new MaterialSwitch(requireContext());
        sw.setChecked(Prefs.getBool(requireContext(), prefKey, def));
        sw.setOnCheckedChangeListener((b, c) -> Prefs.setBool(requireContext(), prefKey, c));

        row.addView(tv);
        row.addView(sw);
        parent.addView(row);
    }

    /**
     * Spinner with a "Custom" option at the end.
     * Selecting "Custom" shows an EditText dialog for custom numeric input.
     */
    private void addSpinnerWithCustom(LinearLayout parent, String prefKey, String label,
                                      String[] options, int[] values, int def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Add "Custom" option
        String[] extOptions = new String[options.length + 1];
        int[] extValues = new int[values.length + 1];
        System.arraycopy(options, 0, extOptions, 0, options.length);
        System.arraycopy(values, 0, extValues, 0, values.length);
        extOptions[options.length] = "Custom…";
        extValues[values.length] = -1; // sentinel for custom

        Spinner sp = new Spinner(requireContext());
        sp.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, extOptions));

        // Find current selection
        int current = Prefs.getInt(requireContext(), prefKey, def);
        boolean found = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                sp.setSelection(i);
                found = true;
                break;
            }
        }
        // If current value is custom (not in options), show it selected on "Custom"
        if (!found) {
            sp.setSelection(extOptions.length - 1);
        }

        final boolean[] suppressFirst = {true}; // suppress initial onItemSelected
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppressFirst[0]) { suppressFirst[0] = false; return; }

                if (extValues[pos] == -1) {
                    // Show custom input dialog
                    showCustomInputDialog(prefKey, label, sp, extValues);
                } else {
                    Prefs.setInt(requireContext(), prefKey, extValues[pos]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        row.addView(tv);
        row.addView(sp);
        parent.addView(row);
    }

    private void showCustomInputDialog(String prefKey, String label, Spinner sp, int[] values) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter value");
        int currentVal = Prefs.getInt(requireContext(), prefKey, 0);
        if (currentVal > 0) input.setText(String.valueOf(currentVal));

        int padding = dp(24);
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setPadding(padding, dp(8), padding, 0);
        wrapper.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Custom: " + label)
                .setView(wrapper)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val > 0) {
                            Prefs.setInt(requireContext(), prefKey, val);
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    // Revert spinner to the current saved value
                    int saved = Prefs.getInt(requireContext(), prefKey, values[0]);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == saved) { sp.setSelection(i); break; }
                    }
                })
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
