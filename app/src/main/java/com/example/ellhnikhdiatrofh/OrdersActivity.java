package com.example.ellhnikhdiatrofh;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Φόρμα παραγγελίας: Πωλητής/Εταιρία, Υπεύθυνος, γραμμές
 * (περιγραφή προϊόντος + ποσότητα - είτε χειροκίνητα είτε επιλογή από τα
 * ήδη καταχωρημένα προϊόντα), και παρατηρήσεις. Αποθηκεύεται ΜΟΝΟ ως PDF.
 */
public class OrdersActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_REQUEST = 1003;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1004;
    private static final String CHANNEL_ID = "orders_pdf_channel";
    private static final int NOTIFICATION_ID = 3001;

    private LinearLayout rowsContainer;
    private EditText sellerInput;
    private EditText responsibleInput;
    private EditText notesInput;

    private ProductLookup productLookup;

    private final List<EditText[]> rows = new ArrayList<>(); // [0]=περιγραφή, [1]=ποσότητα ανά γραμμή

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        rowsContainer = findViewById(R.id.rowsContainer);
        sellerInput = findViewById(R.id.sellerInput);
        responsibleInput = findViewById(R.id.responsibleInput);
        notesInput = findViewById(R.id.notesInput);
        Button addRowButton = findViewById(R.id.addRowButton);
        Button saveOrderButton = findViewById(R.id.saveOrderButton);

        productLookup = new ProductLookup(this);

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }

        // Ξεκίνα με μερικές άδειες γραμμές, όπως το χάρτινο πρότυπο
        for (int i = 0; i < 3; i++) {
            addRow();
        }

        addRowButton.setOnClickListener(v -> addRow());
        saveOrderButton.setOnClickListener(v -> saveOrder());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Εξαγωγή Παραγγελιών", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Ειδοποιήσεις όταν αποθηκεύεται ένα PDF παραγγελίας.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Προσθέτει μία νέα γραμμή (περιγραφή + κουμπί επιλογής + ποσότητα). */
    private void addRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("Πληκτρολόγησε ή επίλεξε");
        descriptionInput.setTextSize(13);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f);
        descriptionInput.setLayoutParams(descParams);
        row.addView(descriptionInput);

        Button pickButton = new Button(this);
        pickButton.setText("☰");
        pickButton.setTextSize(14);
        pickButton.setAllCaps(false);
        pickButton.setBackgroundResource(R.drawable.button_selector);
        pickButton.setTextColor(getResources().getColor(R.color.brand_blue_dark));
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        pickParams.setMargins(dp(4), 0, dp(4), 0);
        pickButton.setLayoutParams(pickParams);
        pickButton.setOnClickListener(v -> showProductPickerDialog(descriptionInput));
        row.addView(pickButton);

        EditText quantityInput = new EditText(this);
        quantityInput.setHint("π.χ. 6");
        quantityInput.setTextSize(13);
        LinearLayout.LayoutParams qtyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.30f);
        quantityInput.setLayoutParams(qtyParams);
        row.addView(quantityInput);

        rowsContainer.addView(row);
        rows.add(new EditText[]{descriptionInput, quantityInput});
    }

    /** Λίστα με αναζήτηση πάνω από τα ήδη καταχωρημένα προϊόντα - επιλογή γεμίζει τη γραμμή. */
    private void showProductPickerDialog(EditText targetDescriptionInput) {
        Map<String, ProductLookup.ProductInfo> entries = productLookup.getAllEntries();

        if (entries.isEmpty()) {
            Toast.makeText(this, "Δεν υπάρχουν ακόμα καταχωρημένα προϊόντα - γράψε χειροκίνητα.", Toast.LENGTH_LONG).show();
            return;
        }

        List<String[]> allProducts = new ArrayList<>(); // [0]=barcode, [1]=label
        for (Map.Entry<String, ProductLookup.ProductInfo> entry : entries.entrySet()) {
            ProductLookup.ProductInfo info = entry.getValue();
            String typePart = (info.type == null || info.type.isEmpty()) ? "" : info.type;
            String brandPart = (info.brand == null || info.brand.isEmpty()) ? "" : info.brand;
            String quantityPart = (info.quantity == null || info.quantity.isEmpty()) ? "" : info.quantity;
            String label = (typePart + " " + brandPart + " " + quantityPart).replaceAll("\\s+", " ").trim();
            if (!label.isEmpty()) {
                allProducts.add(new String[]{entry.getKey(), label});
            }
        }
        allProducts.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));

        List<String> labels = new ArrayList<>();
        for (String[] p : allProducts) {
            labels.add(p[1]);
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        container.setPadding(pad, pad, pad, 0);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Αναζήτηση...");
        container.addView(searchInput);

        ListView listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(380)));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels);
        listView.setAdapter(adapter);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Επιλογή προϊόντος")
                .setView(container)
                .setNegativeButton("Άκυρο", null)
                .create();

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                adapter.clear();
                if (query.isEmpty()) {
                    for (String[] p : allProducts) adapter.add(p[1]);
                } else {
                    for (String[] p : allProducts) {
                        if (p[1].toLowerCase().contains(query) || p[0].contains(query)) {
                            adapter.add(p[1]);
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            targetDescriptionInput.setText(selected);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Μαζεύει όλα τα στοιχεία της φόρμας και τα αποθηκεύει τοπικά + ως PDF. */
    private void saveOrder() {
        String seller = sellerInput.getText().toString().trim();
        if (seller.isEmpty()) {
            Toast.makeText(this, "Συμπλήρωσε τον Πωλητή/Εταιρία.", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderStorage.Order order = new OrderStorage.Order();
        order.seller = seller;
        order.responsible = responsibleInput.getText().toString().trim();
        order.notes = notesInput.getText().toString().trim();
        order.timestamp = System.currentTimeMillis();

        for (EditText[] row : rows) {
            String description = row[0].getText().toString().trim();
            String quantity = row[1].getText().toString().trim();
            if (!description.isEmpty() || !quantity.isEmpty()) {
                order.items.add(new OrderStorage.OrderItem(description, quantity));
            }
        }

        if (order.items.isEmpty()) {
            Toast.makeText(this, "Πρόσθεσε τουλάχιστον ένα προϊόν στην παραγγελία.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingOrder = order;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }

        exportOrderToPdf(order);
    }

    private OrderStorage.Order pendingOrder;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingOrder != null) {
                exportOrderToPdf(pendingOrder);
            } else {
                Toast.makeText(this, "Χρειάζεται άδεια αποθήκευσης για να δημιουργηθεί το PDF.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void exportOrderToPdf(OrderStorage.Order order) {
        OrderPdfExporter.export(this, order, new OrderPdfExporter.ExportCallback() {
            @Override
            public void onSuccess(String fileName, Uri fileUri) {
                runOnUiThread(() -> {
                    Toast.makeText(OrdersActivity.this,
                            "Η παραγγελία αποθηκεύτηκε: " + fileName, Toast.LENGTH_LONG).show();
                    showShareNotification(fileName, fileUri);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(OrdersActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Ειδοποίηση που, όταν πατηθεί, ανοίγει το μενού κοινοποίησης (email, εκτυπωτής, κ.λπ.) για το PDF. */
    private void showShareNotification(String fileName, Uri fileUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, "Κοινοποίηση παραγγελίας (PDF)");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, chooser, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Η παραγγελία αποθηκεύτηκε")
                .setContentText(fileName + " — πάτα για κοινοποίηση")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
