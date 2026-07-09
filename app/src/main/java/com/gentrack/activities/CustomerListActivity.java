package com.gentrack.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gentrack.R;
import com.gentrack.adapters.CustomerAdapter;
import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.MonthlyReport;
import com.gentrack.network.StorageCallback;
import com.gentrack.network.StorageService;
import com.gentrack.network.VolleyService;
import com.gentrack.services.BillingService;
import com.gentrack.utils.Constants;
import com.gentrack.utils.DateUtils;
import com.gentrack.utils.SessionManager;
import com.gentrack.utils.ValidationUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerListActivity extends BaseActivity {

    private DatabaseHandler db;
    private CustomerAdapter adapter;
    private RecyclerView    recycler;
    private View            layoutEmpty;
    private ProgressBar     progressBar;
    private ChipGroup       chipGroup;
    private String          ownerUid;
    private List<Customer>  allCustomers = new ArrayList<>();
    private String          currentQuery = "";

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private Uri       pendingImageUri;
    private ImageView dialogPhotoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_list);
        setupToolbarWithBack(getString(R.string.title_customers));

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

        ownerUid    = SessionManager.getInstance(this).getUid();
        db          = DatabaseHandler.getInstance(this);
        recycler    = findViewById(R.id.recyclerCustomers);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressBar);
        chipGroup   = findViewById(R.id.chipGroupCustomers);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> applyFilters());

        FloatingActionButton fab = findViewById(R.id.fabAddCustomer);
        fab.setOnClickListener(v -> showCustomerDialog(null));

        MaterialButton btnAddFirst = findViewById(R.id.btnAddFirst);
        btnAddFirst.setOnClickListener(v -> showCustomerDialog(null));

        loadCustomers();
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCustomers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_customer_list, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem == null) return true;

        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView == null) return true;

        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getString(R.string.label_name));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                currentQuery = newText != null ? newText : "";
                applyFilters();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_batch_generate) {
            confirmBatchGenerate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ───── DATA ─────

    private void loadCustomers() {
        setLoading(true);
        db.getAllCustomers(ownerUid, new DbCallback<List<Customer>>() {
            @Override public void onResult(List<Customer> customers) {
                if (!isActive()) return;
                setLoading(false);
                bindList(customers);
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void bindList(List<Customer> customers) {
        allCustomers = customers != null ? new ArrayList<>(customers) : new ArrayList<>();
        bindFilteredList(filterCustomers());
    }

    private void applyFilters() {
        bindFilteredList(filterCustomers());
    }

    private List<Customer> filterCustomers() {
        String status = selectedStatus();
        String query = currentQuery.trim().toLowerCase(Locale.ROOT);
        List<Customer> filtered = new ArrayList<>();
        for (Customer c : allCustomers) {
            boolean statusMatch = status == null || status.equals(c.getStatus());
            boolean queryMatch = query.isEmpty()
                    || contains(c.getName(), query)
                    || contains(c.getPhone(), query)
                    || contains(c.getLocation(), query);
            if (statusMatch && queryMatch) filtered.add(c);
        }
        return filtered;
    }

    private String selectedStatus() {
        int checkedId = chipGroup != null ? chipGroup.getCheckedChipId() : View.NO_ID;
        if (checkedId == R.id.chipCustomerActive) return Constants.STATUS_ACTIVE;
        if (checkedId == R.id.chipCustomerUnpaid) return Constants.STATUS_UNPAID;
        if (checkedId == R.id.chipCustomerDisconnected) return Constants.STATUS_DISCONNECTED;
        return null;
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void bindFilteredList(List<Customer> customers) {
        if (customers == null || customers.isEmpty()) {
            recycler.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }
        recycler.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        if (adapter == null) {
            adapter = new CustomerAdapter(customers, new CustomerAdapter.ActionListener() {
                @Override public void onItemClick(Customer c) {
                    Intent intent = new Intent(CustomerListActivity.this,
                            CustomerDetailActivity.class);
                    intent.putExtra(CustomerDetailActivity.EXTRA_CUSTOMER_ID, c.getId());
                    startActivity(intent);
                }
                @Override public void onEdit(Customer c)   { showCustomerDialog(c); }
                @Override public void onDelete(Customer c) { confirmDelete(c); }
            });
            recycler.setAdapter(adapter);
        } else {
            adapter.updateList(customers);
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ───── ADD / EDIT DIALOG ─────

    private void showCustomerDialog(Customer existing) {
        boolean isEdit = (existing != null);

        View view = getLayoutInflater().inflate(R.layout.dialog_customer, null);

        com.google.android.material.textfield.TextInputLayout tilName =
                view.findViewById(R.id.tilName);
        TextInputLayout tilAmps = view.findViewById(R.id.tilAmps);

        TextInputEditText etName  = view.findViewById(R.id.etName);
        TextInputEditText etPhone = view.findViewById(R.id.etPhone);
        TextInputEditText etLoc   = view.findViewById(R.id.etLocation);
        TextInputEditText etAmps  = view.findViewById(R.id.etAmps);
        TextInputEditText etNotes = view.findViewById(R.id.etNotes);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        tvTitle.setText(isEdit
                ? getString(R.string.title_edit_customer)
                : getString(R.string.title_add_customer));

        if (isEdit) {
            etName.setText(existing.getName());
            etPhone.setText(existing.getPhone());
            etLoc.setText(existing.getLocation());
            etAmps.setText(String.valueOf(existing.getAmps()));
            etNotes.setText(existing.getNotes());
        }

        pendingImageUri = null;
        dialogPhotoView = view.findViewById(R.id.ivDialogPhoto);
        Glide.with(this)
                .load(isEdit ? existing.getImageUrl() : null)
                .placeholder(R.drawable.bg_avatar_placeholder)
                .error(R.drawable.bg_avatar_placeholder)
                .circleCrop()
                .into(dialogPhotoView);

        View layoutPhotoPicker = view.findViewById(R.id.layoutPhotoPicker);
        layoutPhotoPicker.setOnClickListener(v -> pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setOnDismissListener(d -> dialogPhotoView = null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        MaterialButton btnCancel  = view.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
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

            if (isEdit) {
                doEditCustomer(existing, name, phone, loc, Integer.parseInt(amps), notes, imageUri);
            } else {
                doAddCustomer(name, phone, loc, Integer.parseInt(amps), notes, imageUri);
            }
        });

        dialog.show();
    }

    // ───── CRUD OPERATIONS ─────

    private void doAddCustomer(String name, String phone, String loc, int amps, String notes,
                               Uri imageUri) {
        Customer c = new Customer();
        c.setOwnerUid(ownerUid);
        c.setName(name);
        c.setPhone(phone);
        c.setLocation(loc);
        c.setAmps(amps);
        c.setStatus(Constants.STATUS_ACTIVE);
        c.setNotes(notes);
        c.setCreatedAt(DateUtils.now());
        c.setUpdatedAt(DateUtils.now());

        setLoading(true);
        db.insertCustomer(c, new DbCallback<Long>() {
            @Override public void onResult(Long id) {
                if (!isActive()) return;
                if (id == null || id == -1L) {
                    setLoading(false);
                    showError(getString(R.string.title_error));
                    return;
                }
                c.setId(id.intValue());
                VolleyService.getInstance(CustomerListActivity.this).postCustomer(c, null);
                if (imageUri != null) uploadCustomerImage(c, imageUri);
                loadCustomers();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void doEditCustomer(Customer existing, String name, String phone,
                                String loc, int amps, String notes, Uri imageUri) {
        // Build an updated copy so the original is not mutated until DB confirms success
        Customer updated = new Customer();
        updated.setId(existing.getId());
        updated.setOwnerUid(existing.getOwnerUid());
        updated.setStatus(existing.getStatus());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setImageUrl(existing.getImageUrl());
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
                VolleyService.getInstance(CustomerListActivity.this).updateCustomer(updated, null);
                if (imageUri != null) uploadCustomerImage(updated, imageUri);
                loadCustomers();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
                loadCustomers(); // resync UI with DB after failed edit
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
                                VolleyService.getInstance(CustomerListActivity.this)
                                        .updateCustomer(customer, null);
                                loadCustomers();
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

    private void confirmDelete(Customer customer) {
        showConfirm(getString(R.string.confirm_delete_customer), () -> {
            db.hasBillsForCustomer(customer.getId(), ownerUid, new DbCallback<Boolean>() {
                @Override public void onResult(Boolean hasBills) {
                    if (!isActive()) return;
                    if (Boolean.TRUE.equals(hasBills)) {
                        showError(getString(R.string.error_delete_has_bills));
                    } else {
                        doDeleteCustomer(customer);
                    }
                }
                @Override public void onError(String e) {
                    if (!isActive()) return;
                    showError(e);
                }
            });
        });
    }

    private void doDeleteCustomer(Customer customer) {
        setLoading(true);
        db.deleteCustomer(customer.getId(), ownerUid, new DbCallback<Boolean>() {
            @Override public void onResult(Boolean deleted) {
                if (!isActive()) return;
                if (!Boolean.TRUE.equals(deleted)) {
                    // DatabaseHandler returned false — bills exist (double safety check)
                    setLoading(false);
                    showError(getString(R.string.error_delete_has_bills));
                    return;
                }
                VolleyService.getInstance(CustomerListActivity.this)
                        .deleteCustomer(customer.getId(), null);
                loadCustomers();
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    // ───── BATCH GENERATE ─────

    private void confirmBatchGenerate() {
        String month = DateUtils.currentMonth();
        String msg = getString(R.string.confirm_batch_generate) + "\n\n" + month;
        showConfirm(msg, this::doBatchGenerate);
    }

    private void doBatchGenerate() {
        String month = DateUtils.currentMonth();
        setLoading(true);
        db.getCustomersByStatus(Constants.STATUS_ACTIVE, ownerUid, new DbCallback<List<Customer>>() {
            @Override public void onResult(List<Customer> activeCustomers) {
                if (!isActive()) return;
                if (activeCustomers == null || activeCustomers.isEmpty()) {
                    setLoading(false);
                    showError(getString(R.string.error_no_active_customers));
                    return;
                }
                SessionManager batchSm = SessionManager.getInstance(CustomerListActivity.this);
                if (batchSm.getPrice5a() <= 0 || batchSm.getPrice10a() <= 0
                        || batchSm.getPrice15a() <= 0) {
                    setLoading(false);
                    showError(getString(R.string.error_pricing_not_configured));
                    return;
                }
                BillingService.getInstance(CustomerListActivity.this)
                        .generateBatchBills(activeCustomers, month, (count, totalRevenue) -> {
                            if (!isActive()) return;
                            setLoading(false);
                            if (count > 0) {
                                MonthlyReport report = new MonthlyReport(
                                        month, count, totalRevenue, ownerUid);
                                VolleyService.getInstance(CustomerListActivity.this)
                                        .postMonthlyReport(report, new com.gentrack.network.ApiCallback() {
                                            @Override public void onSuccess(org.json.JSONObject response) {
                                                Log.d("GenTrack_REPORT",
                                                        "Monthly report saved: " + response);
                                            }

                                            @Override public void onError(String message) {
                                                Log.e("GenTrack_REPORT",
                                                        "Monthly report save failed: " + message);
                                                if (isActive()) showError(message);
                                            }
                                        });
                            }
                            new AlertDialog.Builder(CustomerListActivity.this)
                                    .setTitle(getString(R.string.title_confirm))
                                    .setMessage(getString(R.string.msg_batch_result, count))
                                    .setPositiveButton(getString(R.string.action_ok), null)
                                    .show();
                            loadCustomers();
                        });
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }
}
