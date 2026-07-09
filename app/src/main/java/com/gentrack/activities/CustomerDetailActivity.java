package com.gentrack.activities;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gentrack.R;
import com.gentrack.adapters.BillAdapter;
import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.network.StorageCallback;
import com.gentrack.network.StorageService;
import com.gentrack.network.VolleyService;
import com.gentrack.services.BillingService;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.PhoneUtils;
import com.gentrack.utils.SessionManager;
import com.gentrack.utils.StatusHelper;
import com.gentrack.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CustomerDetailActivity extends BaseActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";

    private DatabaseHandler db;
    private String          ownerUid;
    private int             customerId;
    private Customer        currentCustomer;

    private ImageView    ivAvatar;
    private TextView     tvCustomerName;
    private TextView     tvCustomerPhone;
    private TextView     tvCustomerLocation;
    private TextView     tvCustomerAmps;
    private TextView     tvCustomerStatus;
    private TextView     tvCustomerNotes;
    private TextView     tvEmptyBills;
    private RecyclerView recyclerBills;
    private ProgressBar  progressBar;
    private ChipGroup    chipGroupBills;
    private BillAdapter  billAdapter;
    private List<Bill>   allBills = new ArrayList<>();

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private Uri       pendingImageUri;
    private ImageView dialogPhotoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);
        setupToolbarWithBack(getString(R.string.title_customers));

        customerId = getIntent().getIntExtra(EXTRA_CUSTOMER_ID, -1);
        if (customerId == -1) { finish(); return; }

        ownerUid = SessionManager.getInstance(this).getUid();
        db       = DatabaseHandler.getInstance(this);

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri == null) return;
                    pendingImageUri = uri;
                    if (dialogPhotoView != null) {
                        Glide.with(this)
                                .load(uri)
                                .placeholder(R.drawable.bg_avatar_placeholder)
                                .circleCrop()
                                .into(dialogPhotoView);
                    }
                });

        ivAvatar           = findViewById(R.id.ivAvatar);
        tvCustomerName     = findViewById(R.id.tvCustomerName);
        tvCustomerPhone    = findViewById(R.id.tvCustomerPhone);
        tvCustomerLocation = findViewById(R.id.tvCustomerLocation);
        tvCustomerAmps     = findViewById(R.id.tvCustomerAmps);
        tvCustomerStatus   = findViewById(R.id.tvCustomerStatus);
        tvCustomerNotes    = findViewById(R.id.tvCustomerNotes);
        tvEmptyBills       = findViewById(R.id.tvEmptyBills);
        recyclerBills      = findViewById(R.id.recyclerBills);
        progressBar        = findViewById(R.id.progressBar);
        chipGroupBills     = findViewById(R.id.chipGroupBills);

        recyclerBills.setLayoutManager(new LinearLayoutManager(this));
        recyclerBills.setNestedScrollingEnabled(false);
        chipGroupBills.setOnCheckedChangeListener((group, checkedId) -> applyBillFilter());

        MaterialButton btnGenerateBill = findViewById(R.id.btnGenerateBill);
        btnGenerateBill.setOnClickListener(v -> showGenerateBillDialog());

        loadCustomer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (customerId != -1) {
            loadCustomer();
        }
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    // ───── MENU ─────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_customer_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            if (currentCustomer != null) showEditCustomerDialog();
            return true;
        }
        if (id == R.id.action_delete) {
            if (currentCustomer != null) confirmDeleteCustomer();
            return true;
        }
        if (id == R.id.action_sms) {
            String phone = currentCustomer != null ? currentCustomer.getPhone() : "";
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, R.string.error_no_phone, Toast.LENGTH_SHORT).show();
                return true;
            }
            try {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.error_sms_not_available, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_whatsapp) {
            String phone = currentCustomer != null ? currentCustomer.getPhone() : "";
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, R.string.error_no_phone, Toast.LENGTH_SHORT).show();
                return true;
            }
            try {
                // wa.me requires international format without leading +
                String waPhone = PhoneUtils.toInternational(phone).replace("+", "");
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + waPhone)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.error_whatsapp_not_installed, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ───── DATA ─────

    private void loadCustomer() {
        setLoading(true);
        db.getCustomerById(customerId, ownerUid, new DbCallback<Customer>() {
            @Override public void onResult(Customer customer) {
                if (!isActive()) return;
                if (customer == null) { finish(); return; }
                currentCustomer = customer;
                bindCustomer(customer);
                loadBills();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void loadBills() {
        db.getAllBillsForCustomerWithPaidAmounts(customerId, ownerUid, new DbCallback<List<Bill>>() {
            @Override public void onResult(List<Bill> bills) {
                if (!isActive()) return;
                setLoading(false);
                allBills = bills != null ? new ArrayList<>(bills) : new ArrayList<>();
                applyBillFilter();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void bindCustomer(Customer c) {
        Glide.with(this)
                .load(c.getImageUrl())
                .placeholder(R.drawable.bg_avatar_placeholder)
                .error(R.drawable.bg_avatar_placeholder)
                .circleCrop()
                .into(ivAvatar);
        setupToolbarWithBack(c.getName());

        tvCustomerName.setText(c.getName());
        tvCustomerPhone.setText(TextUtils.isEmpty(c.getPhone())
                ? getString(R.string.label_no_phone) : c.getPhone());
        tvCustomerLocation.setText(TextUtils.isEmpty(c.getLocation())
                ? getString(R.string.label_no_location) : c.getLocation());
        tvCustomerAmps.setText(c.getAmps() + " A");

        StatusHelper.applyStatusChip(tvCustomerStatus, c.getStatus());

        if (!TextUtils.isEmpty(c.getNotes())) {
            tvCustomerNotes.setVisibility(View.VISIBLE);
            tvCustomerNotes.setText(c.getNotes());
        } else {
            tvCustomerNotes.setVisibility(View.GONE);
        }
    }

    private void applyBillFilter() {
        int checkedId = chipGroupBills != null
                ? chipGroupBills.getCheckedChipId() : View.NO_ID;
        List<Bill> filtered = new ArrayList<>();
        for (Bill bill : allBills) {
            if (checkedId == R.id.chipBillUnpaid
                    && !Constants.STATUS_BILL_UNPAID.equals(bill.getStatus())) continue;
            if (checkedId == R.id.chipBillPartial
                    && !Constants.STATUS_PARTIAL.equals(bill.getStatus())) continue;
            if (checkedId == R.id.chipBillPaid
                    && !Constants.STATUS_PAID.equals(bill.getStatus())) continue;
            filtered.add(bill);
        }
        bindBills(filtered);
    }

    private void bindBills(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) {
            recyclerBills.setVisibility(View.GONE);
            tvEmptyBills.setVisibility(View.VISIBLE);
            return;
        }
        recyclerBills.setVisibility(View.VISIBLE);
        tvEmptyBills.setVisibility(View.GONE);

        if (billAdapter == null) {
            billAdapter = new BillAdapter(bills, this::openBillDetail);
            recyclerBills.setAdapter(billAdapter);
        } else {
            billAdapter.updateList(bills);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ───── NAVIGATION ─────

    private void openBillDetail(Bill bill) {
        Intent intent = new Intent(this, BillDetailActivity.class);
        intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.getId());
        startActivity(intent);
    }

    // ───── EDIT CUSTOMER DIALOG ─────

    private void showEditCustomerDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_customer, null);

        TextInputLayout tilName = view.findViewById(R.id.tilName);
        TextInputLayout tilAmps = view.findViewById(R.id.tilAmps);
        TextInputEditText etName  = view.findViewById(R.id.etName);
        TextInputEditText etPhone = view.findViewById(R.id.etPhone);
        TextInputEditText etLoc   = view.findViewById(R.id.etLocation);
        TextInputEditText etAmps  = view.findViewById(R.id.etAmps);
        TextInputEditText etNotes = view.findViewById(R.id.etNotes);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(getString(R.string.title_edit_customer));

        etName.setText(currentCustomer.getName());
        etPhone.setText(currentCustomer.getPhone());
        etLoc.setText(currentCustomer.getLocation());
        etAmps.setText(String.valueOf(currentCustomer.getAmps()));
        etNotes.setText(currentCustomer.getNotes());

        pendingImageUri = null;
        dialogPhotoView = view.findViewById(R.id.ivDialogPhoto);
        Glide.with(this)
                .load(currentCustomer.getImageUrl())
                .placeholder(R.drawable.bg_avatar_placeholder)
                .error(R.drawable.bg_avatar_placeholder)
                .circleCrop()
                .into(dialogPhotoView);

        view.findViewById(R.id.layoutPhotoPicker).setOnClickListener(v ->
                pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setOnDismissListener(d -> dialogPhotoView = null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            tilName.setError(null);
            tilAmps.setError(null);

            String name  = etName.getText()  != null ? etName.getText().toString().trim()  : "";
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim()  : "";
            String loc   = etLoc.getText()   != null ? etLoc.getText().toString().trim()    : "";
            String amps  = etAmps.getText()  != null ? etAmps.getText().toString().trim()   : "";
            String notes = etNotes.getText() != null ? etNotes.getText().toString().trim()  : "";

            String nameErr = ValidationUtils.validateCustomerName(name);
            if (nameErr != null) { tilName.setError(nameErr); return; }

            String ampsErr = ValidationUtils.validateAmps(amps);
            if (ampsErr != null) { tilAmps.setError(ampsErr); return; }

            Uri imageUri = pendingImageUri;
            dialog.dismiss();
            doEditCustomer(name, phone, loc, Integer.parseInt(amps), notes, imageUri);
        });

        dialog.show();
    }

    private void doEditCustomer(String name, String phone, String loc, int amps, String notes,
                                Uri imageUri) {
        Customer updated = new Customer();
        updated.setId(currentCustomer.getId());
        updated.setOwnerUid(currentCustomer.getOwnerUid());
        updated.setStatus(currentCustomer.getStatus());
        updated.setCreatedAt(currentCustomer.getCreatedAt());
        updated.setImageUrl(currentCustomer.getImageUrl());
        updated.setName(name);
        updated.setPhone(phone);
        updated.setLocation(loc);
        updated.setAmps(amps);
        updated.setNotes(notes);
        updated.setUpdatedAt(DateUtils.now());

        setLoading(true);
        db.updateCustomer(updated, new DbCallback<Integer>() {
            @Override public void onResult(Integer rows) {
                if (!isActive()) return;
                VolleyService.getInstance(CustomerDetailActivity.this).updateCustomer(updated, null);
                if (imageUri != null) uploadCustomerImage(updated, imageUri);
                loadCustomer();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void uploadCustomerImage(Customer customer, Uri imageUri) {
        StorageService.getInstance().uploadCustomerImage(
                customer.getOwnerUid(), customer.getId(), imageUri, new StorageCallback() {
                    @Override public void onSuccess(String downloadUrl) {
                        if (!isActive()) return;
                        customer.setImageUrl(downloadUrl);
                        db.updateCustomer(customer, new DbCallback<Integer>() {
                            @Override public void onResult(Integer rows) {
                                if (!isActive()) return;
                                VolleyService.getInstance(CustomerDetailActivity.this)
                                        .updateCustomer(customer, null);
                                loadCustomer();
                            }
                            @Override public void onError(String e) {
                                if (!isActive()) return;
                                showError(e);
                            }
                        });
                    }
                    @Override public void onError(String message) {
                        if (!isActive()) return;
                        showError(message);
                    }
                });
    }

    // ───── GENERATE BILL DIALOG ─────

    private void showGenerateBillDialog() {
        if (currentCustomer == null) return;

        View view = getLayoutInflater().inflate(R.layout.dialog_generate_bill, null);

        RadioGroup        radioModelGroup    = view.findViewById(R.id.radioModelGroup);
        View              readingsContainer  = view.findViewById(R.id.readingsContainer);
        TextInputLayout   tilMonth           = view.findViewById(R.id.tilMonth);
        TextInputLayout   tilCurrentReading  = view.findViewById(R.id.tilCurrentReading);
        TextInputLayout   tilPreviousReading = view.findViewById(R.id.tilPreviousReading);
        TextInputEditText etMonth            = view.findViewById(R.id.etMonth);
        TextInputEditText etCurrentReading   = view.findViewById(R.id.etCurrentReading);
        TextInputEditText etPreviousReading  = view.findViewById(R.id.etPreviousReading);

        final String[] dialogMonth   = { DateUtils.currentMonth() };
        etMonth.setText(dialogMonth[0]);

        final double[] prevBalance   = { 0.0 };
        final String[] selectedModel = { Constants.BILLING_MODEL_FLAT };

        db.getPreviousBalance(customerId, ownerUid, dialogMonth[0], new DbCallback<Double>() {
            @Override public void onResult(Double prev) {
                prevBalance[0] = (prev != null) ? prev : 0.0;
                updatePreview(view, currentCustomer, selectedModel[0],
                        parseDouble(etCurrentReading), parseDouble(etPreviousReading),
                        prevBalance[0]);
            }
            @Override public void onError(String e) {}
        });

        radioModelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isBase = (checkedId == R.id.radioBaseConsumption);
            selectedModel[0] = isBase
                    ? Constants.BILLING_MODEL_BASE_CONSUMPTION
                    : Constants.BILLING_MODEL_FLAT;
            readingsContainer.setVisibility(isBase ? View.VISIBLE : View.GONE);
            if (!isBase) {
                if (etCurrentReading.getText()  != null) etCurrentReading.getText().clear();
                if (etPreviousReading.getText() != null) etPreviousReading.getText().clear();
            }
            updatePreview(view, currentCustomer, selectedModel[0],
                    parseDouble(etCurrentReading), parseDouble(etPreviousReading),
                    prevBalance[0]);
        });

        etMonth.setOnClickListener(v -> showMonthPicker(etMonth));

        TextWatcher readingWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updatePreview(view, currentCustomer, selectedModel[0],
                        parseDouble(etCurrentReading), parseDouble(etPreviousReading),
                        prevBalance[0]);
            }
        };
        etCurrentReading.addTextChangedListener(readingWatcher);
        etPreviousReading.addTextChangedListener(readingWatcher);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            tilMonth.setError(null);
            tilCurrentReading.setError(null);
            tilPreviousReading.setError(null);

            String month = etMonth.getText() != null ? etMonth.getText().toString().trim() : "";
            if (month.isEmpty() || !month.matches("\\d{4}-\\d{2}")) {
                tilMonth.setError(getString(R.string.error_invalid_month));
                return;
            }

            boolean isBase = Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(selectedModel[0]);
            double curR  = parseDouble(etCurrentReading);
            double prevR = parseDouble(etPreviousReading);

            if (isBase && (curR <= 0 || curR <= prevR)) {
                tilCurrentReading.setError(getString(R.string.error_invalid_readings));
                return;
            }

            SessionManager billSm = SessionManager.getInstance(CustomerDetailActivity.this);
            if (isBase) {
                double baseFee = getBaseTierPricePreview(billSm, currentCustomer.getAmps());
                double kwhRate = billSm.getPricePerKwh();
                if (baseFee <= 0 || kwhRate <= 0) {
                    showError(getString(R.string.error_pricing_not_configured));
                    return;
                }
            } else {
                double tierPrice = getTierPricePreview(billSm, currentCustomer.getAmps());
                if (tierPrice <= 0) {
                    showError(getString(R.string.error_pricing_not_configured));
                    return;
                }
            }

            dialog.dismiss();
            doGenerateBill(month, selectedModel[0], curR, prevR);
        });

        dialog.show();
    }

    private void showMonthPicker(TextInputEditText etMonth) {
        Calendar cal = Calendar.getInstance();
        String existing = etMonth.getText() != null ? etMonth.getText().toString() : "";
        if (existing.matches("\\d{4}-\\d{2}")) {
            try {
                cal.set(Integer.parseInt(existing.substring(0, 4)),
                        Integer.parseInt(existing.substring(5, 7)) - 1, 1);
            } catch (NumberFormatException ignored) {}
        }
        new DatePickerDialog(this, (picker, year, month, day) ->
                etMonth.setText(String.format("%04d-%02d", year, month + 1)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1).show();
    }

    private void updatePreview(View dialogView, Customer customer, String billingModel,
                               double curR, double prevR, double prevBalance) {
        SessionManager sm = SessionManager.getInstance(this);

        View     rowBaseFee     = dialogView.findViewById(R.id.rowPreviewBaseFee);
        View     rowConsumption = dialogView.findViewById(R.id.rowPreviewConsumption);
        TextView tvBaseFee      = dialogView.findViewById(R.id.tvPreviewBaseFee);
        TextView tvConsumption  = dialogView.findViewById(R.id.tvPreviewConsumption);

        double total;
        String modelLabel;

        if (Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(billingModel)) {
            double baseFee          = getBaseTierPricePreview(sm, customer.getAmps());
            double kwhRate          = sm.getPricePerKwh();
            double consumption      = Math.max(0, curR - prevR);
            double consumptionCharge = consumption * kwhRate;
            total      = baseFee + consumptionCharge;
            modelLabel = String.format("Base $%.2f + %.2f kWh × $%.4f/kWh",
                    baseFee, consumption, kwhRate);
            rowBaseFee.setVisibility(View.VISIBLE);
            rowConsumption.setVisibility(View.VISIBLE);
            tvBaseFee.setText(String.format("$%.2f", baseFee));
            tvConsumption.setText(String.format("$%.2f", consumptionCharge));
        } else {
            double tierPrice = getTierPricePreview(sm, customer.getAmps());
            total      = tierPrice;
            modelLabel = getString(R.string.label_flat_model, customer.getAmps());
            rowBaseFee.setVisibility(View.GONE);
            rowConsumption.setVisibility(View.GONE);
        }

        double finalTotal = total + prevBalance;
        ((TextView) dialogView.findViewById(R.id.tvPreviewModel)).setText(modelLabel);
        ((TextView) dialogView.findViewById(R.id.tvPreviewTotal))
                .setText(String.format("$%.2f", total));
        ((TextView) dialogView.findViewById(R.id.tvPreviewPrevBalance))
                .setText(String.format("$%.2f", prevBalance));
        ((TextView) dialogView.findViewById(R.id.tvPreviewFinalTotal))
                .setText(String.format("$%.2f", finalTotal));
    }

    private double getTierPricePreview(SessionManager sm, int amps) {
        if (amps <= 5)  return sm.getPrice5a();
        if (amps <= 10) return sm.getPrice10a();
        return sm.getPrice15a();
    }

    private double getBaseTierPricePreview(SessionManager sm, int amps) {
        if (amps <= 5)  return sm.getBasePrice5a();
        if (amps <= 10) return sm.getBasePrice10a();
        return sm.getBasePrice15a();
    }

    private double parseDouble(TextInputEditText et) {
        try {
            String s = et.getText() != null ? et.getText().toString().trim() : "";
            return s.isEmpty() ? 0.0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ───── GENERATE BILL (SINGLE) ─────

    private void doGenerateBill(String month, String billingModel,
                                double currentReading, double previousReading) {
        setLoading(true);
        BillingService.getInstance(this).generateBill(currentCustomer, month, billingModel,
                currentReading, previousReading,
                new DbCallback<Bill>() {
                    @Override public void onResult(Bill bill) {
                        if (!isActive()) return;
                        db.insertBill(bill, new DbCallback<Long>() {
                            @Override public void onResult(Long id) {
                                if (!isActive()) return;
                                if (id != null && id == -2L) {
                                    setLoading(false);
                                    showError(getString(R.string.error_bill_month_exists));
                                    return;
                                }
                                if (id == null || id == -1L) {
                                    setLoading(false);
                                    showError(getString(R.string.title_error));
                                    return;
                                }
                                bill.setId(id.intValue());
                                VolleyService.getInstance(CustomerDetailActivity.this)
                                        .postBill(bill, null);
                                // Fix: guard isActive() before touching views in the runnable
                                BillingService.getInstance(CustomerDetailActivity.this)
                                        .recalculateCustomerStatus(customerId, ownerUid, () -> {
                                            if (isActive()) loadCustomer();
                                        });
                            }
                            @Override public void onError(String e) {
                                if (!isActive()) return;
                                setLoading(false);
                                showError(e);
                            }
                        });
                    }
                    @Override public void onError(String e) {
                        if (!isActive()) return;
                        setLoading(false);
                        showError(e);
                    }
                });
    }

    // ───── DELETE CUSTOMER ─────

    private void confirmDeleteCustomer() {
        showConfirm(getString(R.string.confirm_delete_customer), () -> {
            db.hasBillsForCustomer(customerId, ownerUid, new DbCallback<Boolean>() {
                @Override public void onResult(Boolean hasBills) {
                    if (!isActive()) return;
                    if (Boolean.TRUE.equals(hasBills)) {
                        showError(getString(R.string.error_delete_has_bills));
                    } else {
                        doDeleteCustomer();
                    }
                }
                @Override public void onError(String e) {
                    if (!isActive()) return;
                    showError(e);
                }
            });
        });
    }

    private void doDeleteCustomer() {
        setLoading(true);
        db.deleteCustomer(customerId, ownerUid, new DbCallback<Boolean>() {
            @Override public void onResult(Boolean deleted) {
                if (!isActive()) return;
                if (!Boolean.TRUE.equals(deleted)) {
                    setLoading(false);
                    showError(getString(R.string.error_delete_has_bills));
                    return;
                }
                VolleyService.getInstance(CustomerDetailActivity.this)
                        .deleteCustomer(customerId, null);
                finish();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }
}
