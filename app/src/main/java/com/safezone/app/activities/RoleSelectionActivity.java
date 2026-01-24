package com.safezone.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.safezone.app.R;

public class RoleSelectionActivity extends AppCompatActivity {
    private MaterialCardView cardParent, cardChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        cardParent = findViewById(R.id.cardParent);
        cardChild = findViewById(R.id.cardChild);

        // When a role is selected, go to LoginActivity and pass the role
        cardParent.setOnClickListener(v -> navigateToLogin("parent"));
        cardChild.setOnClickListener(v -> navigateToLogin("child"));
    }

    private void navigateToLogin(String role) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("role", role);
        startActivity(intent);
        // Do not finish here so user can press back if needed
    }
}
