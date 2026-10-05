package com.example.ellhnikhdiatrofh;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView textView;       // δείχνει το τελευταίο σαρωμένο barcode
    private TextView resultTextView; // δείχνει το αποτέλεσμα του lookup
    private Button button;
    private Button menuButton;
    private Button cameraScanButton;
    private String barcodeBuffer = "";
    private String lastScannedBarcode = "";

    private  Button createLabelButton;

    /**
     * Τα πιο πρόσφατα στοιχεία που έχουν εμφανιστεί για το lastScannedBarcode -
     * ακόμα κι αν δεν έχουν αποθηκευτεί ακόμα (π.χ. πρόταση AI που περιμένει επιβεβαίωση).
     * Το κουμπί "Ετικέτα" τα χρησιμοποιεί ΑΜΕΣΩΣ, ώστε να μη δείχνει ποτέ κενή ετικέτα
     * ενώ κάτι έχει ήδη βρεθεί/προταθεί στην οθόνη.
     */
    private ProductLookup.ProductInfo lastScannedInfo = null;

    private ProductLookup productLookup;
    private final AiBarcodeSearch aiBarcodeSearch = new AiBarcodeSearch();

    private final ActivityResultLauncher<Intent> cameraScanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedBarcode = result.getData()
                            .getStringExtra(CameraScanActivity.EXTRA_SCANNED_BARCODE);
                    if (scannedBarcode != null && !scannedBarcode.trim().isEmpty()) {
                        textView.setText(scannedBarcode.trim());
                        runLookup(scannedBarcode.trim());
                    }
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    cameraScanLauncher.launch(new Intent(MainActivity.this, CameraScanActivity.class));
                } else {
                    Toast.makeText(this, "Χρειάζεται άδεια κάμερας για να σαρώσεις.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createLabelButton = findViewById(R.id.addProduct);
        textView = findViewById(R.id.textView);
        resultTextView = findViewById(R.id.resultTextView);
        button = findViewById(R.id.button);
        menuButton = findViewById(R.id.menuButton);
        cameraScanButton = findViewById(R.id.cameraScanButton);

        productLookup = new ProductLookup(this);

        menuButton.setOnClickListener(v -> showMainMenu());

        createLabelButton.setOnClickListener(v -> showCreateNewLabelDialog());


        cameraScanButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraScanLauncher.launch(new Intent(MainActivity.this, CameraScanActivity.class));
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        button.setOnClickListener(v -> {
            String barcode = textView.getText().toString().trim();
            if (barcode.isEmpty()) {
                resultTextView.setText("Σάρωσε πρώτα ένα barcode.");
                return;
            }
            runLookup(barcode);
        });
    }

    /** Δημιουργεί εντελώς νέο προϊόν/ετικέτα από την αρχή (χωρίς scan) - όλα τα πεδία μαζί. */
    private void showCreateNewLabelDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText barcodeInput = new EditText(this);
        barcodeInput.setHint("Barcode / κωδικός (μοναδικός)");
        layout.addView(barcodeInput);

        EditText typeInput = new EditText(this);
        typeInput.setHint("Είδος (π.χ. μπίρα)");
        layout.addView(typeInput);

        EditText brandInput = new EditText(this);
        brandInput.setHint("Brand (π.χ. ΕΖΑ)");
        layout.addView(brandInput);

        EditText quantityInput = new EditText(this);
        quantityInput.setHint("Ποσότητα (π.χ. 6x330ml)");
        layout.addView(quantityInput);

        EditText priceInput = new EditText(this);
        priceInput.setHint("Τιμή (π.χ. 1.49)");
        layout.addView(priceInput);

        // Μονάδα: δύο κουμπιά toggle, ίδια λογική με το LabelActivity
        final String[] selectedUnit = {""}; // "" / "ΤΟ ΚΙΛΟ" / "ΤΟ ΤΕΜΑΧΙΟ"

        LinearLayout unitRow = new LinearLayout(this);
        unitRow.setOrientation(LinearLayout.HORIZONTAL);

        Button kiloButton = new Button(this);
        kiloButton.setText("ΤΟ ΚΙΛΟ");
        unitRow.addView(kiloButton);

        Button temaxioButton = new Button(this);
        temaxioButton.setText("ΤΟ ΤΕΜΑΧΙΟ");
        unitRow.addView(temaxioButton);

        kiloButton.setOnClickListener(v -> {
            selectedUnit[0] = selectedUnit[0].equals("ΤΟ ΚΙΛΟ") ? "" : "ΤΟ ΚΙΛΟ";
            Toast.makeText(this, "Μονάδα: " + (selectedUnit[0].isEmpty() ? "καμία" : selectedUnit[0]), Toast.LENGTH_SHORT).show();
        });
        temaxioButton.setOnClickListener(v -> {
            selectedUnit[0] = selectedUnit[0].equals("ΤΟ ΤΕΜΑΧΙΟ") ? "" : "ΤΟ ΤΕΜΑΧΙΟ";
            Toast.makeText(this, "Μονάδα: " + (selectedUnit[0].isEmpty() ? "καμία" : selectedUnit[0]), Toast.LENGTH_SHORT).show();
        });

        layout.addView(unitRow);

        new AlertDialog.Builder(this)
                .setTitle("Νέα Ετικέτα")
                .setView(layout)
                .setPositiveButton("Αποθήκευση", (dialog, which) -> {
                    String barcode = barcodeInput.getText().toString().trim();
                    if (barcode.isEmpty()) {
                        Toast.makeText(this, "Χρειάζεται barcode/κωδικός.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ProductLookup.ProductInfo info = new ProductLookup.ProductInfo(
                            quantityInput.getText().toString().trim(),
                            typeInput.getText().toString().trim(),
                            brandInput.getText().toString().trim(),
                            priceInput.getText().toString().trim(),
                            selectedUnit[0]);

                    productLookup.confirmAndCache(barcode, info);
                    lastScannedBarcode = barcode;
                    lastScannedInfo = info;

                    Toast.makeText(this, "Το προϊόν αποθηκεύτηκε.", Toast.LENGTH_SHORT).show();
                    openLabelActivity(barcode, info);
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }
    /** Ανοίγει το μενού με τις 4 ενέργειες που πριν ήταν σκόρπια κουμπιά. */
    private void showMainMenu() {
        PopupMenu popup = new PopupMenu(this, menuButton);
        popup.getMenu().add(0, 1, 0, "Επεξεργασία");
        popup.getMenu().add(0, 2, 1, "Ετικέτα");
        popup.getMenu().add(0, 3, 2, "Όλα τα Προϊόντα");
        popup.getMenu().add(0, 4, 3, "Όλες οι Ετικέτες");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (lastScannedBarcode.isEmpty()) {
                        Toast.makeText(this, "Δεν έχεις σαρώσει ακόμα κάτι.", Toast.LENGTH_SHORT).show();
                    } else {
                        showEditEntryDialog(lastScannedBarcode, currentInfoOrEmpty());
                    }
                    return true;
                case 2:
                    if (lastScannedBarcode.isEmpty()) {
                        Toast.makeText(this, "Δεν έχεις σαρώσει ακόμα κάτι.", Toast.LENGTH_SHORT).show();
                    } else {
                        openLabelActivity(lastScannedBarcode, currentInfoOrEmpty());
                    }
                    return true;
                case 3:
                    showProductListDialog();
                    return true;
                case 4:
                    startActivity(new Intent(MainActivity.this, AllLabelsActivity.class));
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    /** Το πιο πρόσφατο γνωστό ProductInfo για το lastScannedBarcode (cache αν δεν έχουμε κάτι πιο φρέσκο). */
    private ProductLookup.ProductInfo currentInfoOrEmpty() {
        if (lastScannedInfo != null) {
            return lastScannedInfo;
        }
        ProductLookup.ProductInfo cached = productLookup.getEntry(lastScannedBarcode);
        return cached != null ? cached : new ProductLookup.ProductInfo("", "", "");
    }

    /** Ανοίγει το LabelActivity για ένα συγκεκριμένο barcode/προϊόν. */
    private void openLabelActivity(String barcode, ProductLookup.ProductInfo info) {
        Intent intent = new Intent(MainActivity.this, LabelActivity.class);
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
    }

    /**
     * Σειρά αναζήτησης:
     * 1. Τοπική cache (άμεσο - αν το barcode είναι ήδη γνωστό, δεν ξαναρωτάμε τίποτα)
     * 2. Open Food Facts / UPCitemdb (δωρεάν βάσεις) - ό,τι βρεθεί γίνεται ΥΠΟΔΕΙΞΗ
     * 3. AI (Serper πραγματική αναζήτηση Google + Gemini) - τρέχει ΠΑΝΤΑ μετά τις βάσεις,
     *    ακόμα κι αν βρέθηκε κάτι εκεί, και το Gemini βγάζει την ΤΕΛΙΚΗ απόφαση
     * 4. Χειροκίνητη καταχώρηση (αν ούτε το AI βρει τίποτα αξιόπιστο)
     */
    private void runLookup(String barcode) {
        lastScannedBarcode = barcode;
        lastScannedInfo = null; // καθάρισμα από τυχόν προηγούμενο barcode

        // Εμφάνισε τα (αν ήταν κρυμμένα πριν το πρώτο scan) - ασφαλές να καλείται σε κάθε scan
        textView.setVisibility(android.view.View.VISIBLE);
        resultTextView.setVisibility(android.view.View.VISIBLE);
        button.setVisibility(android.view.View.VISIBLE);

        resultTextView.setText("Αναζήτηση...");

        ProductLookup.ProductInfo cached = productLookup.getEntry(barcode);
        if (cached != null) {
            lastScannedInfo = cached;
            showResult(cached, "📦 Τοπική βάση");
            return;
        }

        lookupExternalDatabases(barcode);
    }

    private void lookupExternalDatabases(String barcode) {
        resultTextView.setText("Αναζήτηση στις δωρεάν βάσεις (Open Food Facts / UPCitemdb)...");

        productLookup.lookupExternalDatabasesOnly(barcode, new ProductLookup.LookupCallback() {
            @Override
            public void onResult(ProductLookup.ProductInfo info, ProductLookup.Source source) {
                // Βρέθηκε κάτι στις βάσεις - ΔΕΝ σταματάμε εδώ, το περνάμε ως υπόδειξη
                // στο AI, που θα βγάλει την τελική απόφαση αφού ψάξει και στο Google.
                runOnUiThread(() -> {
                    String sourceLabel = source == ProductLookup.Source.OPEN_FOOD_FACTS
                            ? "Open Food Facts" : "UPCitemdb";
                    resultTextView.setText("Βρέθηκε στο " + sourceLabel + " - επιβεβαίωση με AI (Google + Gemini)...");
                    runAiSearch(barcode, info);
                });
            }

            @Override
            public void onNotFound(String barcode) {
                // Δεν βρέθηκε τίποτα στις βάσεις - προχώρα στο AI χωρίς υπόδειξη
                runOnUiThread(() -> runAiSearch(barcode, null));
            }
        });
    }

    /** AI (τελική απόφαση): πραγματική αναζήτηση Google (Serper) -> Gemini συνδυάζει με την τυχόν υπόδειξη από τις βάσεις. */
    private void runAiSearch(String barcode, ProductLookup.ProductInfo databaseHint) {
        if (databaseHint == null) {
            resultTextView.setText("Δεν βρέθηκε στις βάσεις. Αναζήτηση με AI (Google + Gemini)...");
        }

        aiBarcodeSearch.search(barcode, databaseHint, new AiBarcodeSearch.Callback() {
            @Override
            public void onSingleCandidate(ProductLookup.ProductInfo candidate) {
                runOnUiThread(() -> showManualEntryDialog(barcode, candidate,
                        "⚠️ Τελική πρόταση AI (Google + Gemini) - έλεγξε πριν αποθηκεύσεις"));
            }

            @Override
            public void onMultipleCandidates(List<ProductLookup.ProductInfo> candidates) {
                runOnUiThread(() -> showCandidatesChoiceDialog(barcode, candidates));
            }

            @Override
            public void onNone() {
                runOnUiThread(() -> {
                    if (databaseHint != null) {
                        // Το AI δεν επιβεβαίωσε τίποτα καλύτερο - χρησιμοποίησε την υπόδειξη της βάσης ως έχει
                        showManualEntryDialog(barcode, databaseHint,
                                "⚠️ Βρέθηκε στη βάση, αλλά το AI δεν το επιβεβαίωσε - έλεγξε πριν αποθηκεύσεις");
                    } else {
                        resultTextView.setText("Δεν βρέθηκε πουθενά αυτόματα.");
                        showManualEntryDialog(barcode, null, null);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (databaseHint != null) {
                        // Το AI βήμα απέτυχε (π.χ. δικτυακό σφάλμα) - χρησιμοποίησε ό,τι βρήκε η βάση
                        showManualEntryDialog(barcode, databaseHint,
                                "⚠️ Βρέθηκε στη βάση (το AI δεν ήταν διαθέσιμο) - έλεγξε πριν αποθηκεύσεις");
                    } else {
                        resultTextView.setText("Δεν βρέθηκε πουθενά αυτόματα.");
                        showManualEntryDialog(barcode, null, null);
                    }
                });
            }
        });
    }

    private void showResult(ProductLookup.ProductInfo info, String sourceLabel) {
        String text = sourceLabel + "\n"
                + "Ποσότητα: " + info.quantity + "\n"
                + "Είδος: " + info.type + "\n"
                + "Brand: " + info.brand;
        resultTextView.setText(text);
    }

    /**
     * Το AI βρήκε ΠΑΝΩ ΑΠΟ ΕΝΑ διαφορετικά πιθανά προϊόντα για το ίδιο barcode -
     * ζήτα από τον χρήστη να διαλέξει ποιο είναι, ή να φτιάξει το δικό του από την αρχή.
     */
    private void showCandidatesChoiceDialog(String barcode, List<ProductLookup.ProductInfo> candidates) {
        String[] labels = new String[candidates.size() + 1];
        for (int i = 0; i < candidates.size(); i++) {
            ProductLookup.ProductInfo c = candidates.get(i);
            String brandPart = (c.brand == null || c.brand.isEmpty()) ? "" : " " + c.brand;
            String qtyPart = (c.quantity == null || c.quantity.isEmpty()) ? "" : " " + c.quantity;
            labels[i] = ((c.type == null || c.type.isEmpty()) ? "Άγνωστο" : c.type) + brandPart + qtyPart;
        }
        labels[candidates.size()] = "➕ Κανένα από αυτά - φτιάξε το μόνος σου";

        new AlertDialog.Builder(this)
                .setTitle("Βρέθηκαν περισσότερα από ένα προϊόντα για αυτό το barcode")
                .setItems(labels, (dialog, which) -> {
                    if (which == candidates.size()) {
                        showManualEntryDialog(barcode, null, null);
                    } else {
                        showManualEntryDialog(barcode, candidates.get(which),
                                "⚠️ Πρόταση AI (Google + Gemini) - έλεγξε πριν αποθηκεύσεις");
                    }
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    /**
     * Ενιαία φόρμα καταχώρησης/επιβεβαίωσης προϊόντος.
     * @param prefill      στοιχεία προς προσυμπλήρωση, ή null για εντελώς κενή φόρμα
     *                     (δηλαδή "φτιάξε το μόνος σου")
     * @param sourceNotice προειδοποιητικό μήνυμα πηγής (π.χ. "Πρόταση AI"), ή null αν δεν χρειάζεται
     */
    private void showManualEntryDialog(String barcode, ProductLookup.ProductInfo prefill, String sourceNotice) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText quantityInput = new EditText(this);
        quantityInput.setHint("Ποσότητα (π.χ. 6x330ml)");
        if (prefill != null) quantityInput.setText(prefill.quantity);
        layout.addView(quantityInput);

        EditText typeInput = new EditText(this);
        typeInput.setHint("Είδος (π.χ. μπίρα)");
        if (prefill != null) typeInput.setText(prefill.type);
        layout.addView(typeInput);

        EditText brandInput = new EditText(this);
        brandInput.setHint("Brand (π.χ. ΕΖΑ)");
        if (prefill != null) brandInput.setText(prefill.brand);
        layout.addView(brandInput);

        String dialogMessage = (sourceNotice != null && !sourceNotice.isEmpty())
                ? sourceNotice
                : "Συμπλήρωσε τα στοιχεία του προϊόντος.";

        new AlertDialog.Builder(this)
                .setTitle("Καταχώρηση προϊόντος: " + barcode)
                .setMessage(dialogMessage)
                .setView(layout)
                .setPositiveButton("Αποθήκευση", (dialog, which) -> {
                    ProductLookup.ProductInfo info = new ProductLookup.ProductInfo(
                            quantityInput.getText().toString().trim(),
                            typeInput.getText().toString().trim(),
                            brandInput.getText().toString().trim());
                    productLookup.confirmAndCache(barcode, info);
                    if (barcode.equals(lastScannedBarcode)) {
                        lastScannedInfo = info; // ώστε το κουμπί "Ετικέτα" να το βλέπει αμέσως
                    }
                    showResult(info, "💾 Καταχωρήθηκε");
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    /** Λίστα όλων των καταχωρημένων προϊόντων, αλφαβητικά ανά brand (brand - είδος). */
    private void showProductListDialog() {
        Map<String, ProductLookup.ProductInfo> entries = productLookup.getAllEntries();

        if (entries.isEmpty()) {
            Toast.makeText(this, "Δεν υπάρχουν ακόμα καταχωρημένα προϊόντα.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map.Entry<String, ProductLookup.ProductInfo>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort((a, b) -> {
            String brandA = a.getValue().brand == null ? "" : a.getValue().brand;
            String brandB = b.getValue().brand == null ? "" : b.getValue().brand;
            int cmp = brandA.compareToIgnoreCase(brandB);
            if (cmp != 0) return cmp;
            String typeA = a.getValue().type == null ? "" : a.getValue().type;
            String typeB = b.getValue().type == null ? "" : b.getValue().type;
            return typeA.compareToIgnoreCase(typeB);
        });

        String[] barcodes = new String[sorted.size()];
        ProductListEntry[] entryItems = new ProductListEntry[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, ProductLookup.ProductInfo> entry = sorted.get(i);
            barcodes[i] = entry.getKey();
            ProductLookup.ProductInfo info = entry.getValue();
            String brandPart = (info.brand == null || info.brand.isEmpty()) ? "—" : info.brand;
            String typePart = (info.type == null || info.type.isEmpty()) ? "—" : info.type;
            String label = brandPart + " - " + typePart;
            entryItems[i] = new ProductListEntry(barcodes[i], info, label);
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Αναζήτηση (brand ή είδος)...");
        container.addView(searchInput);

        ListView listView = new ListView(this);
        int listHeightPx = (int) (380 * getResources().getDisplayMetrics().density);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeightPx));

        List<ProductListEntry> allEntries = new ArrayList<>(java.util.Arrays.asList(entryItems));
        ArrayAdapter<ProductListEntry> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>(allEntries));
        listView.setAdapter(adapter);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Όλα τα προϊόντα (αλφαβητικά ανά brand)")
                .setView(container)
                .setNegativeButton("Κλείσιμο", null)
                .create();

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                adapter.clear();
                if (query.isEmpty()) {
                    adapter.addAll(allEntries);
                } else {
                    for (ProductListEntry entry : allEntries) {
                        if (entry.label.toLowerCase().contains(query) || entry.barcode.contains(query)) {
                            adapter.add(entry);
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ProductListEntry selected = (ProductListEntry) parent.getItemAtPosition(position);
            dialog.dismiss();
            showProductInfoDialog(selected.barcode, selected.info);
        });

        dialog.show();
    }

    /** Μικρή βοηθητική κλάση για τη λίστα με αναζήτηση - toString() = ό,τι φιλτράρεται/εμφανίζεται. */
    private static class ProductListEntry {
        final String barcode;
        final ProductLookup.ProductInfo info;
        final String label;

        ProductListEntry(String barcode, ProductLookup.ProductInfo info, String label) {
            this.barcode = barcode;
            this.info = info;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Εμφανίζει τα στοιχεία ενός προϊόντος, με κουμπί για να ανοίξει την επεξεργασία. */
    private void showProductInfoDialog(String barcode, ProductLookup.ProductInfo info) {
        String message = "Barcode: " + barcode + "\n"
                + "Brand: " + (info.brand.isEmpty() ? "—" : info.brand) + "\n"
                + "Είδος: " + (info.type.isEmpty() ? "—" : info.type) + "\n"
                + "Ποσότητα: " + (info.quantity.isEmpty() ? "—" : info.quantity) + "\n"
                + "Μονάδα: " + (info.unit.isEmpty() ? "—" : info.unit) + "\n"
                + "Τιμή: " + (info.price.isEmpty() ? "—" : info.price + "€");

        new AlertDialog.Builder(this)
                .setTitle("Στοιχεία προϊόντος")
                .setMessage(message)
                .setPositiveButton("Επεξεργασία", (dialog, which) -> showEditEntryDialog(barcode, info))
                .setNeutralButton("Ετικέτα", (dialog, which) -> openLabelActivity(barcode, info))
                .setNegativeButton("Κλείσιμο", null)
                .show();
    }

    /** Φόρμα επεξεργασίας ενός συγκεκριμένου barcode, με κουμπιά Αποθήκευση/Διαγραφή. */
    private void showEditEntryDialog(String barcode, ProductLookup.ProductInfo info) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText quantityInput = new EditText(this);
        quantityInput.setHint("Ποσότητα");
        quantityInput.setText(info.quantity);
        layout.addView(quantityInput);

        EditText typeInput = new EditText(this);
        typeInput.setHint("Είδος");
        typeInput.setText(info.type);
        layout.addView(typeInput);

        EditText brandInput = new EditText(this);
        brandInput.setHint("Brand");
        brandInput.setText(info.brand);
        layout.addView(brandInput);

        EditText priceInput = new EditText(this);
        priceInput.setHint("Τιμή (π.χ. 1.49)");
        priceInput.setText(info.price);
        layout.addView(priceInput);

        new AlertDialog.Builder(this)
                .setTitle("Barcode: " + barcode)
                .setView(layout)
                .setPositiveButton("Αποθήκευση", (dialog, which) -> {
                    ProductLookup.ProductInfo updated = new ProductLookup.ProductInfo(
                            quantityInput.getText().toString().trim(),
                            typeInput.getText().toString().trim(),
                            brandInput.getText().toString().trim(),
                            priceInput.getText().toString().trim(),
                            info.unit, // διατηρούμε το unit όπως ήταν, δεν το επεξεργαζόμαστε εδώ
                            info.customDescription); // διατηρούμε και το επεξεργασμένο κείμενο ετικέτας, αν υπάρχει
                    productLookup.confirmAndCache(barcode, updated);
                    if (barcode.equals(lastScannedBarcode)) {
                        lastScannedInfo = updated;
                    }
                    Toast.makeText(this, "Αποθηκεύτηκε.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Διαγραφή", (dialog, which) -> {
                    productLookup.deleteEntry(barcode);
                    if (barcode.equals(lastScannedBarcode)) {
                        lastScannedInfo = null;
                    }
                    Toast.makeText(this, "Διαγράφηκε.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Άκυρο", null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // Έλεγχος για Enter (είτε το κανονικό είτε του NumPad)
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (!barcodeBuffer.isEmpty()) {
                    textView.setText(barcodeBuffer);
                    String scannedBarcode = barcodeBuffer;
                    barcodeBuffer = "";
                    runLookup(scannedBarcode); // αυτόματο lookup με το σάρωμα, χωρίς να χρειάζεται το κουμπί
                }
                return true;
            }

            // Hardware τρόπος για να πάρουμε τον χαρακτήρα χωρίς να χαθεί τίποτα
            char pressedKey = (char) event.getMatch(new char[]{'0','1','2','3','4','5','6','7','8','9'});

            if (Character.isDigit(pressedKey)) {
                barcodeBuffer += pressedKey;
                return true; // Καταναλώνουμε το event για να μην πάει αλλού
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
