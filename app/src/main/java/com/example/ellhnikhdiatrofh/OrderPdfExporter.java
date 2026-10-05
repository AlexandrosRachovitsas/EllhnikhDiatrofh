package com.example.ellhnikhdiatrofh;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Δημιουργεί ένα PDF με τη φόρμα παραγγελίας (Πωλητής/Εταιρία, Υπεύθυνος,
 * πίνακας προϊόντων-ποσοτήτων, παρατηρήσεις), στην ίδια λογική με το χάρτινο
 * πρότυπο. Αποθηκεύεται στα Downloads, ίδια λογική με τις ετικέτες.
 */
public class OrderPdfExporter {

    // A4 σε points (72 dpi)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_ROW_HEIGHT = 26;

    public interface ExportCallback {
        void onSuccess(String fileName, Uri fileUri);
        void onError(String message);
    }

    public static void export(Context context, OrderStorage.Order order, ExportCallback callback) {
        if (order.items.isEmpty()) {
            callback.onError("Δεν υπάρχουν προϊόντα στην παραγγελία.");
            return;
        }

        PdfDocument document = new PdfDocument();

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(18f);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.BLACK);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextSize(11f);

        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.BLACK);
        valuePaint.setTextSize(11f);

        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.BLACK);
        headerPaint.setFakeBoldText(true);
        headerPaint.setTextSize(11f);

        Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellPaint.setColor(Color.BLACK);
        cellPaint.setTextSize(11f);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1f);

        int usableWidth = PAGE_WIDTH - (2 * MARGIN);
        float descColumnWidth = usableWidth * 0.65f;
        float qtyColumnWidth = usableWidth * 0.35f;

        int rowsPerPage = (PAGE_HEIGHT - MARGIN - MARGIN - 160) / ROW_HEIGHT; // χώρος μετά τον πίνακα για notes
        if (rowsPerPage < 5) rowsPerPage = 5;

        int pageNumber = 1;
        int itemIndex = 0;

        while (itemIndex < order.items.size()) {
            PdfDocument.Page page = document.startPage(
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
            Canvas canvas = page.getCanvas();

            float y = MARGIN;

            if (pageNumber == 1) {
                canvas.drawText("ΦΟΡΜΑ ΠΑΡΑΓΓΕΛΙΩΝ", PAGE_WIDTH / 2f, y + 14, titlePaint);
                y += 40;

                canvas.drawText("Πωλητής/Εταιρία:", MARGIN, y, labelPaint);
                canvas.drawText(order.seller, MARGIN + 120, y, valuePaint);
                y += 20;

                canvas.drawText("Υπεύθυνος παραγγελίας:", MARGIN, y, labelPaint);
                canvas.drawText(order.responsible, MARGIN + 160, y, valuePaint);
                y += 28;
            } else {
                canvas.drawText("ΦΟΡΜΑ ΠΑΡΑΓΓΕΛΙΩΝ (συνέχεια)", PAGE_WIDTH / 2f, y + 14, titlePaint);
                y += 40;
            }

            float col1X = MARGIN;
            float col2X = MARGIN + descColumnWidth;
            float tableRight = MARGIN + descColumnWidth + qtyColumnWidth;

            // Κεφαλίδα πίνακα
            canvas.drawRect(col1X, y, tableRight, y + HEADER_ROW_HEIGHT, linePaint);
            canvas.drawLine(col2X, y, col2X, y + HEADER_ROW_HEIGHT, linePaint);
            canvas.drawText("Περιγραφή Προϊόντος", col1X + 6, y + 17, headerPaint);
            canvas.drawText("Ποσότ. (τεμ)", col2X + 6, y + 17, headerPaint);
            y += HEADER_ROW_HEIGHT;

            int rowsOnThisPage = 0;
            while (itemIndex < order.items.size() && rowsOnThisPage < rowsPerPage) {
                OrderStorage.OrderItem item = order.items.get(itemIndex);

                canvas.drawRect(col1X, y, tableRight, y + ROW_HEIGHT, linePaint);
                canvas.drawLine(col2X, y, col2X, y + ROW_HEIGHT, linePaint);
                canvas.drawText(truncate(item.description.toUpperCase(new java.util.Locale("el")), cellPaint, descColumnWidth - 12), col1X + 6, y + 16, cellPaint);
                canvas.drawText(item.quantity, col2X + 6, y + 16, cellPaint);

                y += ROW_HEIGHT;
                itemIndex++;
                rowsOnThisPage++;
            }

            // Σημειώσεις μόνο στην τελευταία σελίδα, μετά τον πίνακα
            if (itemIndex >= order.items.size() && order.notes != null && !order.notes.trim().isEmpty()) {
                y += 24;
                canvas.drawText("Παρατηρήσεις/Σχόλια:", MARGIN, y, labelPaint);
                y += 16;
                for (String line : wrapText(order.notes.trim(), valuePaint, usableWidth)) {
                    canvas.drawText(line, MARGIN, y, valuePaint);
                    y += 14;
                }
            }

            document.finishPage(page);
            pageNumber++;
        }

        String fileName = "Paraggelia_" + System.currentTimeMillis() + ".pdf";

        try {
            Uri fileUri = saveDocument(context, document, fileName);
            document.close();
            callback.onSuccess(fileName, fileUri);
        } catch (IOException e) {
            document.close();
            callback.onError("Σφάλμα αποθήκευσης: " + e.getMessage());
        }
    }

    private static String truncate(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) return text;
        String truncated = text;
        while (truncated.length() > 1 && paint.measureText(truncated + "…") > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "…";
    }

    private static List<String> wrapText(String text, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (paint.measureText(candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /** Αποθηκεύει το PDF στον φάκελο Downloads της συσκευής, και επιστρέφει Uri για κοινοποίηση. */
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
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                document.writeTo(out);
            }

            return FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", outFile);
        }
    }

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
