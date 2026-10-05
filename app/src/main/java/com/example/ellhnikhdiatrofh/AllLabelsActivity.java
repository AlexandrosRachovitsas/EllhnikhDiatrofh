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
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Δείχνει όλες τις αποθηκευμένες ετικέτες (προϊόντα με τιμή) σαν πραγματικές
 * μίνι-ετικέτες, σε πλέγμα 2 στηλών με όσες γραμμές χρειάζονται.
 * Πάτημα πάνω σε μια ετικέτα -> ανοίγει το LabelActivity για προβολή/επεξεργασία.
 * Κουμπί "Διαγραφή" κάτω από κάθε ετικέτα -> την σβήνει με fade-out, μετά από επιβεβαίωση.
 */
public class AllLabelsActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final String CHANNEL_ID = "labels_pdf_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int DEFAULT_PDF_ROWS = 5;

    private ProductLookup productLookup;
    private GridLayout grid;
    private int pendingPdfRows = DEFAULT_PDF_ROWS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_labels);

        productLookup = new ProductLookup(this);
        grid = findViewById(R.id.labelsGrid);
        grid.setColumnCount(2);

        Button exportPdfButton = findViewById(R.id.exportPdfButton);
        exportPdfButton.setOnClickListener(v -> showPdfLayoutChoiceDialog());

        Button deleteAllButton = findViewById(R.id.deleteAllButton);
        deleteAllButton.setOnClickListener(v -> confirmDeleteAll());

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Εξαγωγή Ετικετών", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Ειδοποιήσεις όταν αποθηκεύεται ένα PDF με ετικέτες.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLabels(); // ξαναφόρτωσε κάθε φορά (π.χ. μετά από επεξεργασία στο LabelActivity)
    }

    /** Φιλτράρει (μόνο προϊόντα με τιμή) και ταξινομεί αλφαβητικά ανά brand-είδος. */
    private List<Map.Entry<String, ProductLookup.ProductInfo>> getSortedLabelEntries() {
        Map<String, ProductLookup.ProductInfo> entries = productLookup.getAllEntries();
        List<Map.Entry<String, ProductLookup.ProductInfo>> withLabel = new ArrayList<>();
        for (Map.Entry<String, ProductLookup.ProductInfo> entry : entries.entrySet()) {
            if (entry.getValue().price != null && !entry.getValue().price.isEmpty()) {
                withLabel.add(entry);
            }
        }

        withLabel.sort((a, b) -> {
            String brandA = a.getValue().brand == null ? "" : a.getValue().brand;
            String brandB = b.getValue().brand == null ? "" : b.getValue().brand;
            int cmp = brandA.compareToIgnoreCase(brandB);
            if (cmp != 0) return cmp;
            String typeA = a.getValue().type == null ? "" : a.getValue().type;
            String typeB = b.getValue().type == null ? "" : b.getValue().type;
            return typeA.compareToIgnoreCase(typeB);
        });

        return withLabel;
    }

    /** Ζητά από τον χρήστη πόσες γραμμές ανά σελίδα θέλει στο PDF, πριν προχωρήσει. */
    private void showPdfLayoutChoiceDialog() {
        String[] options = {
                "2 στήλες x 5 γραμμές (10 ανά σελίδα, κανονικό μέγεθος)",
                "2 στήλες x 3 γραμμές (6 ανά σελίδα, μεγαλύτερες ετικέτες)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Διάταξη PDF")
                .setItems(options, (dialog, which) -> {
                    pendingPdfRows = (which == 1) ? 3 : 5;
                    exportToPdf();
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    private void exportToPdf() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        doExportToPdf();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doExportToPdf();
            } else {
                Toast.makeText(this, "Χρειάζεται άδεια αποθήκευσης για να δημιουργηθεί το PDF.", Toast.LENGTH_LONG).show();
            }
        }
        // Για NOTIFICATION_PERMISSION_REQUEST δεν χρειάζεται ενέργεια εδώ -
        // αν δεν δοθεί άδεια, απλά δεν θα εμφανιστεί ειδοποίηση αργότερα.
    }

    private void doExportToPdf() {
        List<Map.Entry<String, ProductLookup.ProductInfo>> entries = getSortedLabelEntries();
        LabelPdfExporter.export(this, entries, pendingPdfRows, new LabelPdfExporter.ExportCallback() {
            @Override
            public void onSuccess(String fileName, Uri fileUri) {
                runOnUiThread(() -> {
                    Toast.makeText(AllLabelsActivity.this,
                            "Αποθηκεύτηκε στα Downloads: " + fileName, Toast.LENGTH_LONG).show();
                    showShareNotification(fileName, fileUri);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(AllLabelsActivity.this,
                        message, Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Ειδοποίηση που, όταν πατηθεί, ανοίγει το μενού κοινοποίησης (email, εκτυπωτής, κ.λπ.) για το PDF. */
    private void showShareNotification(String fileName, Uri fileUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, "Κοινοποίηση ετικετών (PDF)");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, chooser, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Το PDF ετικετών αποθηκεύτηκε")
                .setContentText(fileName + " — πάτα για κοινοποίηση")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void loadLabels() {
        grid.removeAllViews();

        List<Map.Entry<String, ProductLookup.ProductInfo>> withLabel = getSortedLabelEntries();

        if (withLabel.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Δεν υπάρχουν ακόμα αποθηκευμένες ετικέτες.");
            empty.setPadding(dp(8), dp(24), dp(8), dp(8));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.columnSpec = GridLayout.spec(0, 2);
            empty.setLayoutParams(params);
            grid.addView(empty);
            return;
        }

        for (Map.Entry<String, ProductLookup.ProductInfo> entry : withLabel) {
            View card = buildLabelCard(entry.getKey(), entry.getValue());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            card.setLayoutParams(params);
            grid.addView(card);
        }
    }

    /** Χτίζει μία μίνι-ετικέτα (ίδιο στυλ με το LabelActivity) + κουμπί διαγραφής. */
    private View buildLabelCard(String barcode, ProductLookup.ProductInfo info) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);

        LinearLayout ticket = new LinearLayout(this);
        ticket.setOrientation(LinearLayout.VERTICAL);
        ticket.setGravity(Gravity.CENTER);
        ticket.setBackgroundResource(R.drawable.label_border);
        ticket.setClickable(true);
        ticket.setFocusable(true);

        LinearLayout.LayoutParams ticketParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ticket.setLayoutParams(ticketParams);
        ticket.setMinimumHeight(dp(170)); // ίδιο ελάχιστο ύψος σε όλες - μεγαλώνει μόνο αν δεν χωράει το κείμενο

        TextView title = new TextView(this);
        String titleText = (info.customTitle != null && !info.customTitle.trim().isEmpty())
                ? info.customTitle.trim() : "SUPER TIMH";
        title.setText(titleText);
        title.setAllCaps(true); // κεφαλαία by default
        title.setTextSize(14);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(android.graphics.Color.BLACK);
        title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        ticket.addView(title);

        TextView description = new TextView(this);
        String fullDescription;
        if (info.customDescription != null && !info.customDescription.trim().isEmpty()) {
            fullDescription = info.customDescription.trim();
        } else {
            String typePart = (info.type == null || info.type.isEmpty()) ? "Άγνωστο" : info.type;
            String brandPart = (info.brand == null || info.brand.isEmpty()) ? "" : info.brand;
            String quantityPart = (info.quantity == null || info.quantity.isEmpty()) ? "" : info.quantity;
            fullDescription = (typePart + " " + brandPart + " " + quantityPart).replaceAll("\\s+", " ").trim();
        }
        description.setText(fullDescription);
        description.setAllCaps(true); // κεφαλαία by default
        description.setTextSize(scaledMiniDescriptionSize(info.descriptionSize));
        description.setTypeface(null, Typeface.BOLD);
        description.setTextColor(android.graphics.Color.BLACK);
        description.setGravity(Gravity.CENTER);
        ticket.addView(description);

        // Μονάδα (ΤΟ ΚΙΛΟ/ΤΟ ΤΕΜΑΧΙΟ): ξεκινάει ακριβώς στη μέση του πλάτους, προς τα δεξιά
        LinearLayout unitRow = new LinearLayout(this);
        unitRow.setOrientation(LinearLayout.HORIZONTAL);
        unitRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View unitSpacer = new View(this);
        unitSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        unitRow.addView(unitSpacer);

        TextView unit = new TextView(this);
        unit.setText(info.unit == null ? "" : info.unit);
        unit.setTextSize(9);
        unit.setTypeface(null, Typeface.BOLD);
        unit.setTextColor(android.graphics.Color.BLACK);
        unit.setGravity(Gravity.START);
        unit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        unitRow.addView(unit);

        ticket.addView(unitRow);

        TextView price = new TextView(this);
        price.setText(formatPrice(info.price));
        price.setTextSize(20);
        price.setTypeface(null, Typeface.BOLD);
        price.setTextColor(android.graphics.Color.BLACK);
        price.setPaintFlags(price.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        priceParams.topMargin = dp(4);
        price.setLayoutParams(priceParams);
        ticket.addView(price);

        ticket.setOnClickListener(v -> {
            Intent intent = new Intent(AllLabelsActivity.this, LabelActivity.class);
            intent.putExtra(LabelActivity.EXTRA_BARCODE, barcode);
            intent.putExtra(LabelActivity.EXTRA_QUANTITY, info.quantity);
            intent.putExtra(LabelActivity.EXTRA_TYPE, info.type);
            intent.putExtra(LabelActivity.EXTRA_BRAND, info.brand);
            intent.putExtra(LabelActivity.EXTRA_PRICE, info.price);
            intent.putExtra(LabelActivity.EXTRA_UNIT, info.unit);
            intent.putExtra(LabelActivity.EXTRA_CUSTOM_DESCRIPTION, info.customDescription);
            intent.putExtra(LabelActivity.EXTRA_CUSTOM_TITLE, info.customTitle);
            intent.putExtra(LabelActivity.EXTRA_DESCRIPTION_SIZE, info.descriptionSize);
            startActivity(intent);
        });

        outer.addView(ticket);

        Button deleteButton = new Button(this);
        deleteButton.setText("Διαγραφή");
        deleteButton.setTextSize(10);
        deleteButton.setTextColor(android.graphics.Color.WHITE);
        deleteButton.setBackgroundResource(R.drawable.button_bg_delete);
        deleteButton.setAllCaps(false);
        deleteButton.setOnClickListener(v -> confirmDelete(barcode, outer));
        outer.addView(deleteButton);

        return outer;
    }

    /** Μέγεθος γραμμάτων περιγραφής στη μίνι-κάρτα, αναλογικά προς το προσαρμοσμένο μέγεθος στην ετικέτα. */
    private float scaledMiniDescriptionSize(String descriptionSize) {
        float baseFullSize = 18f;
        float baseMiniSize = 13f;
        if (descriptionSize == null || descriptionSize.trim().isEmpty()) {
            return baseMiniSize;
        }
        try {
            float fullSize = Float.parseFloat(descriptionSize.trim());
            return baseMiniSize * (fullSize / baseFullSize);
        } catch (NumberFormatException e) {
            return baseMiniSize;
        }
    }

    private String formatPrice(String rawPrice) {
        try {
            double p = Double.parseDouble(rawPrice.replace(",", "."));
            return String.format(Locale.forLanguageTag("el"), "%.2f€", p);
        } catch (Exception e) {
            return rawPrice + "€";
        }
    }

    /** Ζητά επιβεβαίωση, μετά κάνει fade-out στη συγκεκριμένη κάρτα πριν τη διαγράψει πραγματικά. */
    private void confirmDelete(String barcode, View cardView) {
        new AlertDialog.Builder(this)
                .setTitle("Διαγραφή ετικέτας")
                .setMessage("Σίγουρα θες να διαγράψεις αυτή την ετικέτα;")
                .setPositiveButton("Διαγραφή", (dialog, which) -> {
                    cardView.animate()
                            .alpha(0f)
                            .setDuration(350)
                            .withEndAction(() -> {
                                productLookup.clearLabelData(barcode);
                                Toast.makeText(this, "Η ετικέτα διαγράφηκε (το προϊόν παραμένει).", Toast.LENGTH_SHORT).show();
                                loadLabels();
                            })
                            .start();
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    /** Ζητά επιβεβαίωση, μετά καθαρίζει τα στοιχεία ΕΤΙΚΕΤΑΣ όλων των προϊόντων που φαίνονται
     *  εδώ (τιμή/μονάδα/κείμενο) - τα ίδια τα προϊόντα ΔΕΝ διαγράφονται, παραμένουν στο
     *  "Όλα τα Προϊόντα". */
    private void confirmDeleteAll() {
        List<Map.Entry<String, ProductLookup.ProductInfo>> withLabel = getSortedLabelEntries();
        if (withLabel.isEmpty()) {
            Toast.makeText(this, "Δεν υπάρχουν ετικέτες για διαγραφή.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Διαγραφή όλων των ετικετών")
                .setMessage("Θα διαγραφούν όλες οι ετικέτες (τιμές/μονάδες/κείμενα). "
                        + "Τα ίδια τα προϊόντα ΔΕΝ θα διαγραφούν, θα παραμείνουν στο \"Όλα τα Προϊόντα\". Συνέχεια;")
                .setPositiveButton("Διαγραφή όλων", (dialog, which) -> {
                    grid.animate()
                            .alpha(0f)
                            .setDuration(350)
                            .withEndAction(() -> {
                                for (Map.Entry<String, ProductLookup.ProductInfo> entry : withLabel) {
                                    productLookup.clearLabelData(entry.getKey());
                                }
                                grid.setAlpha(1f);
                                Toast.makeText(this, "Όλες οι ετικέτες διαγράφηκαν.", Toast.LENGTH_SHORT).show();
                                loadLabels();
                            })
                            .start();
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
