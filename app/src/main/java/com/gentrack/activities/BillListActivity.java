package com.gentrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gentrack.R;
import com.gentrack.adapters.BillAdapter;
import com.gentrack.db.DatabaseHandler;
import com.gentrack.db.DbCallback;
import com.gentrack.models.Bill;
import com.gentrack.utils.Constants;
import com.gentrack.utils.SessionManager;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class BillListActivity extends BaseActivity {

    public static final String EXTRA_CUSTOMER_ID   = "extra_customer_id";
    public static final String EXTRA_CUSTOMER_NAME = "extra_customer_name";

    private DatabaseHandler db;
    private String          ownerUid;
    private int             customerId;

    private RecyclerView recyclerBills;
    private View         layoutEmpty;
    private ProgressBar  progressBar;
    private ChipGroup    chipGroup;
    private BillAdapter  billAdapter;

    private List<Bill> allBills = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_list);

        customerId = getIntent().getIntExtra(EXTRA_CUSTOMER_ID, -1);
        if (customerId == -1) { finish(); return; }

        String customerName = getIntent().getStringExtra(EXTRA_CUSTOMER_NAME);
        String title = getString(R.string.title_bills)
                + (customerName != null ? " — " + customerName : "");
        setupToolbarWithBack(title);

        ownerUid     = SessionManager.getInstance(this).getUid();
        db           = DatabaseHandler.getInstance(this);

        recyclerBills = findViewById(R.id.recyclerBills);
        layoutEmpty   = findViewById(R.id.layoutEmpty);
        progressBar   = findViewById(R.id.progressBar);
        chipGroup     = findViewById(R.id.chipGroup);

        recyclerBills.setLayoutManager(new LinearLayoutManager(this));

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> applyFilter(checkedId));

        loadBills();
    }

    private boolean isActive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBills();
    }

    // ───── DATA ─────

    private void loadBills() {
        setLoading(true);
        db.getAllBillsForCustomerWithPaidAmounts(customerId, ownerUid, new DbCallback<List<Bill>>() {
            @Override public void onResult(List<Bill> bills) {
                if (!isActive()) return;
                setLoading(false);
                allBills = bills != null ? bills : new ArrayList<>();
                applyFilter(chipGroup.getCheckedChipId());
            }
            @Override public void onError(String e) {
                if (!isActive()) return;
                setLoading(false);
                showError(e);
            }
        });
    }

    private void applyFilter(int checkedChipId) {
        List<Bill> filtered;
        if (checkedChipId == R.id.chipUnpaid) {
            filtered = filterByStatus(Constants.STATUS_BILL_UNPAID);
        } else if (checkedChipId == R.id.chipPartial) {
            filtered = filterByStatus(Constants.STATUS_PARTIAL);
        } else if (checkedChipId == R.id.chipPaid) {
            filtered = filterByStatus(Constants.STATUS_PAID);
        } else {
            filtered = new ArrayList<>(allBills);
        }
        bindBills(filtered);
    }

    private List<Bill> filterByStatus(String status) {
        List<Bill> result = new ArrayList<>();
        for (Bill b : allBills) {
            if (status.equals(b.getStatus())) result.add(b);
        }
        return result;
    }

    private void bindBills(List<Bill> bills) {
        if (bills.isEmpty()) {
            recyclerBills.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }
        recyclerBills.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        if (billAdapter == null) {
            billAdapter = new BillAdapter(bills, bill -> openBillDetail(bill));
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
}
