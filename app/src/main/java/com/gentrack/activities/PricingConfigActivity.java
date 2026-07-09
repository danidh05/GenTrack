package com.gentrack.activities;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.gentrack.R;
import com.gentrack.network.ApiCallback;
import com.gentrack.network.VolleyService;
import com.gentrack.utils.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class PricingConfigActivity extends BaseActivity {

    private TextInputLayout   tilPricePerKwh;
    private TextInputLayout   tilPrice5a;
    private TextInputLayout   tilPrice10a;
    private TextInputLayout   tilPrice15a;
    private TextInputLayout   tilBasePrice5a;
    private TextInputLayout   tilBasePrice10a;
    private TextInputLayout   tilBasePrice15a;
    private TextInputEditText etPricePerKwh;
    private TextInputEditText etPrice5a;
    private TextInputEditText etPrice10a;
    private TextInputEditText etPrice15a;
    private TextInputEditText etBasePrice5a;
    private TextInputEditText etBasePrice10a;
    private TextInputEditText etBasePrice15a;
    private AutoCompleteTextView spinnerCurrency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pricing_config);
        setupToolbarWithBack(getString(R.string.title_pricing_config));

        tilPricePerKwh  = findViewById(R.id.tilPricePerKwh);
        tilPrice5a      = findViewById(R.id.tilPrice5a);
        tilPrice10a     = findViewById(R.id.tilPrice10a);
        tilPrice15a     = findViewById(R.id.tilPrice15a);
        tilBasePrice5a  = findViewById(R.id.tilBasePrice5a);
        tilBasePrice10a = findViewById(R.id.tilBasePrice10a);
        tilBasePrice15a = findViewById(R.id.tilBasePrice15a);
        etPricePerKwh   = findViewById(R.id.etPricePerKwh);
        etPrice5a       = findViewById(R.id.etPrice5a);
        etPrice10a      = findViewById(R.id.etPrice10a);
        etPrice15a      = findViewById(R.id.etPrice15a);
        etBasePrice5a   = findViewById(R.id.etBasePrice5a);
        etBasePrice10a  = findViewById(R.id.etBasePrice10a);
        etBasePrice15a  = findViewById(R.id.etBasePrice15a);
        spinnerCurrency = findViewById(R.id.spinnerCurrency);

        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line,
                new String[]{"USD", "LBP"});
        spinnerCurrency.setAdapter(currencyAdapter);

        prefill();

        findViewById(R.id.btnSaveConfig).setOnClickListener(v -> saveConfig());
    }

    private void prefill() {
        SessionManager sm = SessionManager.getInstance(this);
        setIfPositive(etPricePerKwh,  sm.getPricePerKwh());
        setIfPositive(etPrice5a,      sm.getPrice5a());
        setIfPositive(etPrice10a,     sm.getPrice10a());
        setIfPositive(etPrice15a,     sm.getPrice15a());
        setIfPositive(etBasePrice5a,  sm.getBasePrice5a());
        setIfPositive(etBasePrice10a, sm.getBasePrice10a());
        setIfPositive(etBasePrice15a, sm.getBasePrice15a());
        spinnerCurrency.setText(sm.getCurrency(), false);
    }

    private void setIfPositive(TextInputEditText et, double value) {
        if (value > 0) et.setText(String.valueOf(value));
    }

    private void saveConfig() {
        clearErrors();

        double pkw  = parsePositive(etPricePerKwh,  tilPricePerKwh);
        double p5   = parsePositive(etPrice5a,       tilPrice5a);
        double p10  = parsePositive(etPrice10a,      tilPrice10a);
        double p15  = parsePositive(etPrice15a,      tilPrice15a);
        double bp5  = parsePositive(etBasePrice5a,   tilBasePrice5a);
        double bp10 = parsePositive(etBasePrice10a,  tilBasePrice10a);
        double bp15 = parsePositive(etBasePrice15a,  tilBasePrice15a);

        if (pkw < 0 || p5 < 0 || p10 < 0 || p15 < 0
                || bp5 < 0 || bp10 < 0 || bp15 < 0) return;

        String currency = spinnerCurrency.getText().toString().trim();
        if (currency.isEmpty()) currency = "USD";

        SessionManager sm = SessionManager.getInstance(this);
        sm.savePricePerKwh(pkw);
        sm.savePrice5a(p5);
        sm.savePrice10a(p10);
        sm.savePrice15a(p15);
        sm.saveBasePrice5a(bp5);
        sm.saveBasePrice10a(bp10);
        sm.saveBasePrice15a(bp15);
        sm.saveCurrency(currency);

        final String finalCurrency = currency;
        VolleyService.getInstance(this).updateRemoteConfig(
                p5, p10, p15, pkw, bp5, bp10, bp15, finalCurrency,
                new ApiCallback() {
                    @Override public void onSuccess(JSONObject r) {}
                    @Override public void onError(String e) {}
                });

        Toast.makeText(this, R.string.msg_config_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private double parsePositive(TextInputEditText et, TextInputLayout til) {
        String s = et.getText() != null ? et.getText().toString().trim() : "";
        if (s.isEmpty()) {
            til.setError(getString(R.string.error_invalid_price_config));
            return -1;
        }
        try {
            double v = Double.parseDouble(s);
            if (v <= 0) {
                til.setError(getString(R.string.error_invalid_price_config));
                return -1;
            }
            return v;
        } catch (NumberFormatException e) {
            til.setError(getString(R.string.error_invalid_price_config));
            return -1;
        }
    }

    private void clearErrors() {
        tilPricePerKwh.setError(null);
        tilPrice5a.setError(null);
        tilPrice10a.setError(null);
        tilPrice15a.setError(null);
        tilBasePrice5a.setError(null);
        tilBasePrice10a.setError(null);
        tilBasePrice15a.setError(null);
    }
}
