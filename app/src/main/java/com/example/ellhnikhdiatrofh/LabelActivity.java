package com.example.ellhnikhdiatrofh;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class LabelActivity extends AppCompatActivity {

    public static final String EXTRA_BARCODE = "extra_barcode";
    public static final String EXTRA_QUANTITY = "extra_quantity";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_BRAND = "extra_brand";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_UNIT = "extra_unit";
    public static final String EXTRA_CUSTOM_DESCRIPTION = "extra_custom_description";
    public static final String EXTRA_CUSTOM_TITLE = "extra_custom_title";
    public static final String EXTRA_DESCRIPTION_SIZE = "extra_description_size";

    private static final String UNIT_KILO = "ΤΟ ΚΙΛΟ";
    private static final String UNIT_TEMAXIO = "ΤΟ ΤΕΜΑΧΙΟ";
    private static final float DEFAULT_DESCRIPTION_SIZE_SP = 18f;
    private static final float MIN_DESCRIPTION_SIZE_SP = 10f;
    private static final float MAX_DESCRIPTION_SIZE_SP = 34f;

    private EditText labelTitle;
    private EditText labelDescription;
    private TextView labelUnit;
    private TextView labelPrice;
    private EditText priceInput;

    private ProductLookup productLookup;
    private String barcode;
    private String quantity;
    private String type;
    private String brand;
    private String currentUnit = "";
    private float currentDescriptionSizeSp = DEFAULT_DESCRIPTION_SIZE_SP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_label);

        labelTitle = findViewById(R.id.labelTitle);
        labelDescription = findViewById(R.id.labelDescription);
        labelUnit = findViewById(R.id.labelUnit);
        labelPrice = findViewById(R.id.labelPrice);
        priceInput = findViewById(R.id.priceInput);
        Button kiloButton = findViewById(R.id.kiloButton);
        Button temaxioButton = findViewById(R.id.temaxioButton);
        Button setPriceButton = findViewById(R.id.setPriceButton);
        Button saveLabelButton = findViewById(R.id.saveLabelButton);
        Button decreaseFontButton = findViewById(R.id.decreaseFontButton);
        Button increaseFontButton = findViewById(R.id.increaseFontButton);

        // Υπογράμμιση τίτλου (SUPER TIMH) και τιμής, όπως στο πρότυπο ετικέτας
        labelTitle.setPaintFlags(labelTitle.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        labelPrice.setPaintFlags(labelPrice.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        productLookup = new ProductLookup(this);

        barcode = getIntent().getStringExtra(EXTRA_BARCODE);
        quantity = getIntent().getStringExtra(EXTRA_QUANTITY);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        brand = getIntent().getStringExtra(EXTRA_BRAND);
        String price = getIntent().getStringExtra(EXTRA_PRICE);
        String unit = getIntent().getStringExtra(EXTRA_UNIT);
        String customDescription = getIntent().getStringExtra(EXTRA_CUSTOM_DESCRIPTION);
        String customTitle = getIntent().getStringExtra(EXTRA_CUSTOM_TITLE);
        String descriptionSize = getIntent().getStringExtra(EXTRA_DESCRIPTION_SIZE);

        // "SUPER TIMH" - επεξεργάσιμο, με προεπιλογή αν δεν έχει αλλαχτεί ποτέ
        if (customTitle != null && !customTitle.trim().isEmpty()) {
            labelTitle.setText(customTitle.trim());
        } else {
            labelTitle.setText("SUPER TIMH");
        }

        // Μέγεθος γραμμάτων περιγραφής - προσαρμοσμένο ή προεπιλογή
        if (descriptionSize != null && !descriptionSize.trim().isEmpty()) {
            try {
                currentDescriptionSizeSp = Float.parseFloat(descriptionSize.trim());
            } catch (NumberFormatException ignored) {
                currentDescriptionSizeSp = DEFAULT_DESCRIPTION_SIZE_SP;
            }
        }
        labelDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentDescriptionSizeSp);

        if (customDescription != null && !customDescription.trim().isEmpty()) {
            // Ο χρήστης είχε ήδη επεξεργαστεί το κείμενο πριν - το δείχνουμε ως έχει
            labelDescription.setText(customDescription.trim());
        } else {
            // Σειρά: Είδος - Brand - Ποσότητα (π.χ. "μπίρα ΕΖΑ 6x330")
            StringBuilder description = new StringBuilder();
            if (type != null && !type.trim().isEmpty()) {
                description.append(type.trim());
            }
            if (brand != null && !brand.trim().isEmpty()) {
                if (description.length() > 0) description.append(" ");
                description.append(brand.trim());
            }
            if (quantity != null && !quantity.trim().isEmpty()) {
                if (description.length() > 0) description.append(" ");
                description.append(quantity.trim());
            }
            labelDescription.setText(description.length() > 0 ? description.toString() : "Άγνωστο προϊόν");
        }

        // Αν υπάρχει ήδη αποθηκευμένη μονάδα (ΤΟ ΚΙΛΟ / ΤΟ ΤΕΜΑΧΙΟ), την προσυμπληρώνουμε
        if (unit != null && !unit.trim().isEmpty()) {
            currentUnit = unit.trim();
            labelUnit.setText(currentUnit);
        }

        // Αν υπάρχει ήδη αποθηκευμένη τιμή γι' αυτό το barcode, την προσυμπληρώνουμε
        if (price != null && !price.trim().isEmpty()) {
            priceInput.setText(price.trim());
            displayPrice(price.trim());
        }

        kiloButton.setOnClickListener(v -> toggleUnit(UNIT_KILO));
        temaxioButton.setOnClickListener(v -> toggleUnit(UNIT_TEMAXIO));

        setPriceButton.setOnClickListener(v -> updatePriceDisplay());

        decreaseFontButton.setOnClickListener(v -> changeDescriptionSize(-2f));
        increaseFontButton.setOnClickListener(v -> changeDescriptionSize(2f));

        saveLabelButton.setOnClickListener(v -> saveLabel());
    }

    /** Μεγαλώνει/μικραίνει το μέγεθος γραμμάτων της περιγραφής, μέσα σε λογικά όρια. */
    private void changeDescriptionSize(float delta) {
        float newSize = currentDescriptionSizeSp + delta;
        if (newSize < MIN_DESCRIPTION_SIZE_SP) newSize = MIN_DESCRIPTION_SIZE_SP;
        if (newSize > MAX_DESCRIPTION_SIZE_SP) newSize = MAX_DESCRIPTION_SIZE_SP;
        currentDescriptionSizeSp = newSize;
        labelDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentDescriptionSizeSp);
    }

    /** Αν η μονάδα είναι ήδη επιλεγμένη, τη σβήνει· αλλιώς τη βάζει (και αντικαθιστά την άλλη). */
    private void toggleUnit(String unitLabel) {
        if (currentUnit.equals(unitLabel)) {
            currentUnit = "";
        } else {
            currentUnit = unitLabel;
        }
        labelUnit.setText(currentUnit);
    }

    /** Ενημερώνει μόνο την προβολή τιμής στην ετικέτα (χωρίς αποθήκευση). */
    private void updatePriceDisplay() {
        String priceText = priceInput.getText().toString().trim().replace(",", ".");

        if (priceText.isEmpty()) {
            Toast.makeText(this, "Βάλε μια τιμή.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double parsed = Double.parseDouble(priceText);
            displayPrice(String.valueOf(parsed));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Μη έγκυρη τιμή.", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayPrice(String rawPrice) {
        try {
            double price = Double.parseDouble(rawPrice.replace(",", "."));
            labelPrice.setText(String.format(Locale.forLanguageTag("el"), "%.2f€", price));
        } catch (NumberFormatException ignored) {
            // αν είναι ήδη μορφοποιημένο κείμενο, το αφήνουμε ως έχει
        }
    }

    /** Αποθηκεύει μόνιμα την ετικέτα (ποσότητα/είδος/brand/τιμή/μονάδα/τίτλο/μέγεθος) στην τοπική βάση. */
    private void saveLabel() {
        if (barcode == null || barcode.isEmpty()) {
            Toast.makeText(this, "Δεν υπάρχει barcode για αποθήκευση.", Toast.LENGTH_SHORT).show();
            return;
        }

        String priceText = priceInput.getText().toString().trim().replace(",", ".");
        if (priceText.isEmpty()) {
            Toast.makeText(this, "Βάλε μια τιμή πριν αποθηκεύσεις.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double parsedPrice = Double.parseDouble(priceText);
            String normalizedPrice = String.valueOf(parsedPrice);

            ProductLookup.ProductInfo info = new ProductLookup.ProductInfo(
                    quantity != null ? quantity : "",
                    type != null ? type : "",
                    brand != null ? brand : "",
                    normalizedPrice,
                    currentUnit,
                    labelDescription.getText().toString().trim(),
                    labelTitle.getText().toString().trim(),
                    String.valueOf(currentDescriptionSizeSp));

            productLookup.confirmAndCache(barcode, info);
            displayPrice(normalizedPrice);

            Toast.makeText(this, "Η ετικέτα αποθηκεύτηκε.", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Μη έγκυρη τιμή.", Toast.LENGTH_SHORT).show();
        }
    }
}
