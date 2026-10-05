package com.example.ellhnikhdiatrofh;

import androidx.core.content.FileProvider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Δημιουργεί ένα PDF με όλες τις ετικέτες, στην ίδια μορφή με το "Όλες οι Ετικέτες":
 * ίδιο μέγεθος/ύψος ανά ετικέτα, πλέγμα 2 στηλών x Ν γραμμών, γεμίζοντας ολόκληρη
 * τη σελίδα. Πάνω από ΟΡΙΟ ετικέτες -> νέα σελίδα. Το Ν (5 ή 3) το διαλέγει ο χρήστης -
 * λιγότερες γραμμές σημαίνει μεγαλύτερες ετικέτες (τα γράμματα κλιμακώνονται ανάλογα).
 */
public class LabelPdfExporter {

    private static final int COLUMNS = 2;
    private static final int BASELINE_ROWS = 5; // σε αυτό αντιστοιχούν τα "κανονικά" μεγέθη γραμμάτων παρακάτω

    // A4 σε points (72 dpi)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int PAGE_MARGIN = 20;
    private static final int CELL_PADDING = 8;

    public interface ExportCallback {
        void onSuccess(String fileName, Uri fileUri);
        void onError(String message);
    }

    public static void export(Context context,
                               List<Map.Entry<String, ProductLookup.ProductInfo>> entries,
                               int rows,
                               ExportCallback callback) {
        if (entries.isEmpty()) {
            callback.onError("Δεν υπάρχουν ετικέτες για αποθήκευση.");
            return;
        }

        int rowsToUse = rows > 0 ? rows : BASELINE_ROWS;
        int perPage = COLUMNS * rowsToUse;

        PdfDocument document = new PdfDocument();

        int usableWidth = PAGE_WIDTH - (2 * PAGE_MARGIN);
        int usableHeight = PAGE_HEIGHT - (2 * PAGE_MARGIN);
        int cellWidth = usableWidth / COLUMNS;
        int cellHeight = usableHeight / rowsToUse;

        // Όσο λιγότερες οι γραμμές, τόσο πιο ψηλό το κελί -> μεγαλύτερα γράμματα, αναλογικά
        float baselineCellHeight = usableHeight / (float) BASELINE_ROWS;
        float fontScale = cellHeight / baselineCellHeight;

        int pageNumber = 1;
        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
        Canvas canvas = page.getCanvas();

        int indexOnPage = 0;

        for (Map.Entry<String, ProductLookup.ProductInfo> entry : entries) {
            if (indexOnPage == perPage) {
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(
                        new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
                canvas = page.getCanvas();
                indexOnPage = 0;
            }

            int col = indexOnPage % COLUMNS;
            int row = indexOnPage / COLUMNS;

            float left = PAGE_MARGIN + col * cellWidth;
            float top = PAGE_MARGIN + row * cellHeight;
            RectF cellRect = new RectF(left, top, left + cellWidth, top + cellHeight);

            drawTicket(canvas, cellRect, entry.getValue(), fontScale);
            indexOnPage++;
        }

        document.finishPage(page);

        String fileName = "Etiketes.pdf";

        try {
            Uri fileUri = saveDocument(context, document, fileName);
            document.close();
            callback.onSuccess(fileName, fileUri);
        } catch (IOException e) {
            document.close();
            callback.onError("Σφάλμα αποθήκευσης: " + e.getMessage());
        }
    }

    private static void drawTicket(Canvas canvas, RectF cellRect, ProductLookup.ProductInfo info, float scale) {
        RectF borderRect = new RectF(
                cellRect.left + CELL_PADDING,
                cellRect.top + CELL_PADDING,
                cellRect.right - CELL_PADDING,
                cellRect.bottom - CELL_PADDING);

        // Διακεκομμένο πλαίσιο (ίδιο στυλ με το label_border.xml)
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{6f, 4f}, 0));
        canvas.drawRect(borderRect, borderPaint);

        float contentLeft = borderRect.left + 10;
        float contentRight = borderRect.right - 10;
        float contentWidth = contentRight - contentLeft;
        float centerX = (contentLeft + contentRight) / 2f;

        // Αναλογία μεγέθους περιγραφής βάσει του τι έχει επιλέξει ο χρήστης στην ετικέτα (Α-/Α+)
        float descriptionSizeRatio = 1f;
        if (info.descriptionSize != null && !info.descriptionSize.trim().isEmpty()) {
            try {
                float userSize = Float.parseFloat(info.descriptionSize.trim());
                descriptionSizeRatio = userSize / 18f;
            } catch (NumberFormatException ignored) {
                descriptionSizeRatio = 1f;
            }
        }

        // --- Paints (μαύρα, έντονα) - μεγέθη κλιμακωμένα ανά τη διάταξη σελίδας ---
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(28f * scale);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFlags(titlePaint.getFlags() | Paint.UNDERLINE_TEXT_FLAG);

        Paint descPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        descPaint.setColor(Color.BLACK);
        descPaint.setFakeBoldText(true);
        descPaint.setTextSize(16f * scale * descriptionSizeRatio);
        descPaint.setTextAlign(Paint.Align.CENTER);

        Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitPaint.setColor(Color.BLACK);
        unitPaint.setFakeBoldText(true);
        unitPaint.setTextSize(11f * scale);
        unitPaint.setTextAlign(Paint.Align.LEFT);

        Paint pricePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pricePaint.setColor(Color.BLACK);
        pricePaint.setFakeBoldText(true);
        pricePaint.setTextSize(32f * scale);
        pricePaint.setTextAlign(Paint.Align.CENTER);
        pricePaint.setFlags(pricePaint.getFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Τίτλος: "SUPER TIMH" εκτός αν έχει επεξεργαστεί χειροκίνητα - πάντα ΚΕΦΑΛΑΙΑ
        String titleText = (info.customTitle != null && !info.customTitle.trim().isEmpty())
                ? info.customTitle.trim() : "SUPER TIMH";
        titleText = titleText.toUpperCase(Locale.forLanguageTag("el"));

        // Σειρά: Είδος - Brand - Ποσότητα (π.χ. "μπίρα ΕΖΑ 6x330"), εκτός αν έχει επεξεργαστεί χειροκίνητα
        // Πάντα ΚΕΦΑΛΑΙΑ by default
        String description;
        if (info.customDescription != null && !info.customDescription.trim().isEmpty()) {
            description = info.customDescription.trim();
        } else {
            String typePart = (info.type == null || info.type.isEmpty()) ? "Άγνωστο" : info.type;
            String brandPart = info.brand == null ? "" : info.brand;
            String quantityPart = info.quantity == null ? "" : info.quantity;
            description = (typePart + " " + brandPart + " " + quantityPart).replaceAll("\\s+", " ").trim();
        }
        description = description.toUpperCase(Locale.forLanguageTag("el"));
        List<String> descLines = wrapText(description, descPaint, contentWidth);

        String unitText = info.unit == null ? "" : info.unit;
        String priceText = formatPrice(info.price);

        float descLineHeight = textHeight(descPaint);
        float unitHeight = unitText.isEmpty() ? 0 : textHeight(unitPaint);

        float spacing = 6f * scale;
        float topPadding = 16f * scale;    // κενό ανάμεσα στο "ταβάνι" του πλαισίου και το SUPER TIMH
        float bottomPadding = 10f * scale; // κενό ανάμεσα στην τιμή και το "πάτωμα" του πλαισίου

        // Το SUPER TIMH ξεκινάει με σταθερό κενό από πάνω (όχι κολλητά στο πλαίσιο)
        float y = borderRect.top + topPadding - titlePaint.getFontMetrics().ascent;

        canvas.drawText(titleText, centerX, y, titlePaint);
        y += spacing + descLineHeight;

        for (int i = 0; i < descLines.size(); i++) {
            canvas.drawText(descLines.get(i), centerX, y, descPaint);
            if (i < descLines.size() - 1) {
                y += descLineHeight;
            }
        }

        if (!unitText.isEmpty()) {
            y += spacing + unitHeight;
            // Ξεκινάει ακριβώς στη μέση του πλάτους (centerX), προς τα δεξιά
            canvas.drawText(unitText, centerX, y, unitPaint);
        }

        // Η τιμή έχει σταθερό κενό από κάτω (όχι κολλητά στο πλαίσιο), ανεξάρτητα
        // από το πόσο χώρο πήρε το παραπάνω περιεχόμενο.
        float priceY = borderRect.bottom - bottomPadding - pricePaint.getFontMetrics().descent;
        canvas.drawText(priceText, centerX, priceY, pricePaint);
    }

    private static float textHeight(Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    private static List<String> wrapText(String text, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (paint.measureText(candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static String formatPrice(String rawPrice) {
        try {
            double p = Double.parseDouble(rawPrice.replace(",", "."));
            return String.format(Locale.forLanguageTag("el"), "%.2f€", p);
        } catch (Exception e) {
            return (rawPrice == null ? "" : rawPrice) + "€";
        }
    }

    /** Αποθηκεύει το PDF στον φάκελο Downloads της συσκευής, και επιστρέφει Uri για κοινοποίηση.
     *  Αν υπάρχει ήδη αρχείο με το ίδιο όνομα, το διαγράφει πρώτα (αντικατάσταση, όχι σώρευση). */
    private static Uri saveDocument(Context context, PdfDocument document, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            deleteExistingIfPresent(resolver, fileName);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri itemUri = resolver.insert(collection, values);
            if (itemUri == null) {
                throw new IOException("Αποτυχία δημιουργίας αρχείου στα Downloads.");
            }

            try (OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) throw new IOException("Αποτυχία ανοίγματος output stream.");
                document.writeTo(out);
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);

            return itemUri;

        } else {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                downloadsDir.mkdirs();
            }
            File outFile = new File(downloadsDir, fileName);
            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                document.writeTo(out);
            }

            return FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", outFile);
        }
    }

    /** Ψάχνει και διαγράφει τυχόν προηγούμενο αρχείο με το ίδιο όνομα στα Downloads (MediaStore). */
    private static void deleteExistingIfPresent(ContentResolver resolver, String fileName) {
        Uri collection = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] selectionArgs = {fileName};

        try (Cursor cursor = resolver.query(
                collection, new String[]{MediaStore.Downloads._ID}, selection, selectionArgs, null)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri deleteUri = ContentUris.withAppendedId(collection, id);
                    resolver.delete(deleteUri, null, null);
                }
            }
        }
    }
}
