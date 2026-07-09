package com.gentrack.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.gentrack.R;
import com.gentrack.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout    tilEmail;
    private TextInputLayout    tilPassword;
    private TextInputEditText  etEmail;
    private TextInputEditText  etPassword;
    private MaterialButton     btnLogin;
    private ProgressBar        progressBar;
    private FirebaseAuth       auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // Skip login screen if a session is already active
        if (auth.getCurrentUser() != null) {
            goToDashboard();
            return;
        }

        tilEmail    = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> attemptLogin());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_email));
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_password));
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    String uid = result.getUser() != null ? result.getUser().getUid() : "";
                    if (uid.isEmpty()) {
                        showError(getString(R.string.error_login_failed));
                        return;
                    }
                    SessionManager.getInstance(this).saveSession(uid, email);
                    goToDashboard();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getString(R.string.error_login_failed));
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);

    }

    private void showError(String message) {
        Snackbar.make(btnLogin, message, Snackbar.LENGTH_LONG).show();
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
