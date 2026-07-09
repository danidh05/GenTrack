package com.gentrack.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.adapters.PaymentAdapter;
import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.Payment;
import com.gentrack.services.BillingService;
import com.gentrack.services.PaymentService;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.PdfGenerator;
import com.gentrack.utils.SessionManager;
import com.gentrack.utils.StatusHelper;
import com.gentrack.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BillDetailActivity extends BaseActivity {

    public static final String EXTRA_BILL_ID = "extra_bill_id";

    private DatabaseHandler db;
    private String          ownerUid;
    private int             billId;
    private Bill            currentBill;
    private double          computedRemainingBalance;
    private List<Payment>   currentPayments = new ArrayList<>();

    private TextView              tvBillMonth;
    private TextView              tvBillStatus;
    private TextView              tvBillAmpsRate;
    private TextView              tvBillBaseFee;
    private TextView              tvBillConsumptionCharge;
    private TextView              tvBillTotal;
    private TextView              tvBillPrevBalance;
    private TextView              tvBillFinalTotal;
    private TextView              tvRemainingBalance;
    private TextView              tvEmptyPayments;
    private RecyclerView          recyclerPayments;
    private ProgressBar           progressBar;
    private FloatingActionButton  fabAddPayment;
    private PaymentAdapter        paymentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_detail);

        billId = getIntent().getIntExtra(EXTRA_BILL_ID, -1);
        if (billId == -1) { finish(); return; }

        ownerUid = SessionManager.getInstance(this).getUid();
        db       = DatabaseHandler.getInstance(this);

        setupToolbarWithBack(getString(R.string.title_bills));

        tvBillMonth             = findViewById(R.id.tvBillMonth);
        tvBillStatus            = findViewById(R.id.tvBillStatus);
        tvBillAmpsRate          = findViewById(R.id.tvBillAmpsRate);
        tvBillBaseFee           = findViewById(R.id.tvBillBaseFee);
        tvBillConsumptionCharge = findViewById(R.id.tvBillConsumptionCharge);
        tvBillTotal             = findViewById(R.id.tvBillTotal);
        tvBillPrevBalance       = findViewById(R.id.tvBillPrevBalance);
        tvBillFinalTotal        = findViewById(R.id.tvBillFinalTotal);
        tvRemainingBalance      = findViewById(R.id.tvRemainingBalance);
        tvEmptyPayments         = findViewById(R.id.tvEmptyPayments);
        recyclerPayments        = findViewById(R.id.recyclerPayments);
        progressBar             = findViewById(R.id.progressBar);
        fabAddPayment           = findViewById(R.id.fabAddPayment);

        recyclerPayments.setLayoutManager(new LinearLayoutManager(this));
        recyclerPayments.setNestedScrollingEnabled(false);

        fabAddPayment.setOnClickListener(v -> {
            if (currentBill != null) showAddPaymentDialog();
        });

        loadBill();
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    // ───── MENU ─────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bill_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareBillPdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ───── DATA ─────

    private void loadBill() {
        setLoading(true);
        db.getBillById(billId, ownerUid, new DbCallback<Bill>() {
            @Override public void onResult(Bill bill) {
                if (!isActive()) return;
                if (bill == null) { finish(); return; }
                currentBill = bill;
                bindBill(bill);
                loadPayments();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void loadPayments() {
        db.getAllPaymentsForBill(billId, ownerUid, new DbCallback<List<Payment>>() {
            @Override public void onResult(List<Payment> payments) {
                if (!isActive()) return;
                setLoading(false);
                bindPayments(payments);
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void bindBill(Bill bill) {
        setupToolbarWithBack("Bill — " + bill.getMonth());

        tvBillMonth.setText(bill.getMonth());
        StatusHelper.applyStatusChip(tvBillStatus, bill.getStatus());

        String model = bill.getBillingModel();
        if (Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(model)) {
            double consumptionCharge = bill.getConsumption() * bill.getPricePerAmp();
            double baseFee           = bill.getTotal() - consumptionCharge;
            tvBillAmpsRate.setText(bill.getAmps() + " A  —  base + consumption");
            tvBillBaseFee.setVisibility(View.VISIBLE);
            tvBillConsumptionCharge.setVisibility(View.VISIBLE);
            tvBillBaseFee.setText(String.format("Base fee:  $%.2f", baseFee));
            tvBillConsumptionCharge.setText(String.format("%.2f kWh  ×  $%.4f/kWh  =  $%.2f",
                    bill.getConsumption(), bill.getPricePerAmp(), consumptionCharge));
        } else if (Constants.BILLING_MODEL_CONSUMPTION.equals(model)) {
            tvBillAmpsRate.setText(String.format("%.2f kWh  ×  $%.2f/kWh",
                    bill.getConsumption(), bill.getPricePerAmp()));
            tvBillBaseFee.setVisibility(View.GONE);
            tvBillConsumptionCharge.setVisibility(View.GONE);
        } else {
            tvBillAmpsRate.setText(bill.getAmps() + " A  —  flat $"
                    + String.format("%.2f", bill.getPricePerAmp()));
            tvBillBaseFee.setVisibility(View.GONE);
            tvBillConsumptionCharge.setVisibility(View.GONE);
        }

        tvBillTotal.setText(String.format("$%.2f", bill.getTotal()));
        tvBillPrevBalance.setText(String.format("$%.2f", bill.getPreviousBalance()));
        tvBillFinalTotal.setText(String.format("$%.2f", bill.getFinalTotal()));

        if (Constants.STATUS_PAID.equals(bill.getStatus())) {
            fabAddPayment.hide();
        } else {
            fabAddPayment.show();
        }
    }

    private void bindPayments(List<Payment> payments) {
        currentPayments = payments != null ? payments : new ArrayList<>();
        double totalPaid = 0;
        for (Payment p : currentPayments) totalPaid += p.getAmountPaid();
        computedRemainingBalance = Math.max(0, currentBill.getFinalTotal() - totalPaid);

        tvRemainingBalance.setText(String.format("$%.2f", computedRemainingBalance));
        int color = computedRemainingBalance <= 0
                ? getResources().getColor(R.color.statusPaid, getTheme())
                : getResources().getColor(R.color.statusUnpaid, getTheme());
        tvRemainingBalance.setTextColor(color);

        if (payments == null || payments.isEmpty()) {
            recyclerPayments.setVisibility(View.GONE);
            tvEmptyPayments.setVisibility(View.VISIBLE);
            return;
        }
        recyclerPayments.setVisibility(View.VISIBLE);
        tvEmptyPayments.setVisibility(View.GONE);

        if (paymentAdapter == null) {
            paymentAdapter = new PaymentAdapter(payments);
            recyclerPayments.setAdapter(paymentAdapter);
        } else {
            paymentAdapter.updateList(payments);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ───── PDF SHARE ─────

    private void shareBillPdf() {
        if (currentBill == null) return;
        db.getCustomerById(currentBill.getCustomerId(), ownerUid, new DbCallback<Customer>() {
            @Override public void onResult(Customer customer) {
                if (!isActive()) return;
                new Thread(() -> {
                    File pdf = PdfGenerator.generateBillPdf(
                            BillDetailActivity.this, currentBill, customer, currentPayments);
                    runOnUiThread(() -> {
                        if (!isActive()) return;
                        if (pdf == null) {
                            Toast.makeText(BillDetailActivity.this,
                                    R.string.error_pdf_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Uri uri = FileProvider.getUriForFile(BillDetailActivity.this,
                                    Constants.FILE_PROVIDER_AUTHORITY, pdf);
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("application/pdf");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent,
                                    getString(R.string.title_share_bill)));
                        } catch (Exception e) {
                            Toast.makeText(BillDetailActivity.this,
                                    R.string.error_pdf_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                Toast.makeText(BillDetailActivity.this,
                        R.string.error_pdf_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ───── ADD PAYMENT DIALOG ─────

    private void showAddPaymentDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_payment, null);

        TextInputLayout   tilAmount = view.findViewById(R.id.tilAmount);
        TextInputEditText etAmount  = view.findViewById(R.id.etAmount);
        TextInputEditText etDate    = view.findViewById(R.id.etDate);
        TextView          tvPreview = view.findViewById(R.id.tvPreviewRemaining);

        etDate.setText(DateUtils.today());
        etDate.setOnClickListener(v -> showDatePicker(etDate));

        updatePaymentPreview(tvPreview, 0.0);

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                updatePaymentPreview(tvPreview, parseDouble(etAmount));
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            tilAmount.setError(null);

            String amountStr = etAmount.getText() != null
                    ? etAmount.getText().toString().trim() : "";
            String err = ValidationUtils.validatePaymentAmount(amountStr, computedRemainingBalance);
            if (err != null) {
                tilAmount.setError(err);
                return;
            }

            String date = etDate.getText() != null
                    ? etDate.getText().toString().trim() : DateUtils.today();

            dialog.dismiss();
            doRecordPayment(Double.parseDouble(amountStr), date);
        });

        dialog.show();
    }

    private void showDatePicker(TextInputEditText etDate) {
        Calendar cal = Calendar.getInstance();
        String existing = etDate.getText() != null ? etDate.getText().toString() : "";
        if (existing.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                cal.set(Integer.parseInt(existing.substring(0, 4)),
                        Integer.parseInt(existing.substring(5, 7)) - 1,
                        Integer.parseInt(existing.substring(8, 10)));
            } catch (NumberFormatException ignored) {}
        }
        new DatePickerDialog(this,
                (picker, year, month, day) ->
                        etDate.setText(String.format("%04d-%02d-%02d", year, month + 1, day)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updatePaymentPreview(TextView tvPreview, double amount) {
        double remaining = Math.max(0, computedRemainingBalance - amount);
        tvPreview.setText(String.format("$%.2f", remaining));
        int color = remaining <= 0
                ? getResources().getColor(R.color.statusPaid, getTheme())
                : getResources().getColor(R.color.colorAccent, getTheme());
        tvPreview.setTextColor(color);
    }

    private double parseDouble(TextInputEditText et) {
        try {
            String s = et.getText() != null ? et.getText().toString().trim() : "";
            return s.isEmpty() ? 0.0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ───── RECORD PAYMENT ─────

    private void doRecordPayment(double amount, String date) {
        setLoading(true);
        PaymentService.getInstance(this).recordPayment(billId, amount, ownerUid, date,
                new DbCallback<Payment>() {
                    @Override public void onResult(Payment payment) {
                        if (!isActive()) return;
                        // Refresh bill status + payment list in place — no navigation
                        loadBill();
                    }
                    @Override public void onError(String e) {
                        if (!isActive()) return;
                        setLoading(false);
                        showError(e);
                    }
                });
    }
}
