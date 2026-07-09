package com.gentrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.gentrack.R;
import com.gentrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            SessionManager.getInstance(this).clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    protected void setupToolbar(String title) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    protected void setupToolbarWithBack(String title) {
        setupToolbar(title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    protected void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_error)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    protected void showConfirm(String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_confirm)
                .setMessage(message)
                .setPositiveButton(R.string.action_yes, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (id == R.id.action_logout) {
            showConfirm(getString(R.string.confirm_logout), () -> {
                FirebaseAuth.getInstance().signOut();
                SessionManager.getInstance(this).clearSession();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
            return true;
        }
        if (id == R.id.action_announcements) {
            startActivity(new Intent(this, AnnouncementsActivity.class));
            return true;
        }
        if (id == R.id.action_pricing_config) {
            startActivity(new Intent(this, PricingConfigActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
