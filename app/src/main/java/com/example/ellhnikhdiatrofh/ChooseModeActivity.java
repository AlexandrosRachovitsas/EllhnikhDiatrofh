package com.example.ellhnikhdiatrofh;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

/**
 * Οθόνη επιλογής μετά το splash: "Ετικέτες" (πάει στο MainActivity - ό,τι είχαμε
 * φτιάξει ήδη) ή "Παραγγελίες" (πάει σε ξεχωριστό activity, προς ολοκλήρωση).
 */
public class ChooseModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_mode);

        Button labelsModeButton = findViewById(R.id.labelsModeButton);
        Button ordersModeButton = findViewById(R.id.ordersModeButton);

        labelsModeButton.setOnClickListener(v ->
                startActivity(new Intent(ChooseModeActivity.this, MainActivity.class)));

        ordersModeButton.setOnClickListener(v ->
                startActivity(new Intent(ChooseModeActivity.this, OrdersActivity.class)));
    }
}
