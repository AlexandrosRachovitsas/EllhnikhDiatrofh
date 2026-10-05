package com.example.ellhnikhdiatrofh;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Ψάχνει πληροφορίες προϊόντος με τη σειρά - 100% ΔΩΡΕΑΝ, χωρίς κανένα AI/LLM:
 * 1. Τοπική cache (SharedPreferences) - instant, offline
 * 2. Open Food Facts API - δωρεάν, χωρίς key, πραγματική βάση δεδομένων
 * 3. UPCitemdb (trial endpoint) - δωρεάν, χωρίς key, 100 requests/ημέρα ανά IP
 * 4. Αν τίποτα δεν βρεθεί -> onNotFound(), ο χρήστης καταχωρεί χειροκίνητα
 *    και το UI καλεί confirmAndCache() για να αποθηκευτεί για πάντα τοπικά.
 */
public class ProductLookup {

    private static final String TAG = "ProductLookup";
    private static final String PREFS_NAME = "product_cache";

    private final OkHttpClient client = new OkHttpClient();
    private final SharedPreferences prefs;

    public interface LookupCallback {
        void onResult(ProductInfo info, Source source);
        void onNotFound(String barcode);
    }

    public enum Source { CACHE, OPEN_FOOD_FACTS, UPCITEMDB }

    public static class ProductInfo {
        public String quantity;
        public String type;
        public String brand;
        public String price;
        public String unit; // "ΤΟ ΚΙΛΟ" / "ΤΟ ΤΕΜΑΧΙΟ" / ""
        public String customDescription; // αν έχει επεξεργαστεί χειροκίνητα το κείμενο της ετικέτας
        public String customTitle; // αν έχει επεξεργαστεί το "SUPER TIMH" - κενό = προεπιλογή
        public String descriptionSize; // μέγεθος γραμμάτων περιγραφής σε sp (κενό = προεπιλογή)

        public ProductInfo(String quantity, String type, String brand) {
            this(quantity, type, brand, "", "", "");
        }

        public ProductInfo(String quantity, String type, String brand, String price) {
            this(quantity, type, brand, price, "", "");
        }

        public ProductInfo(String quantity, String type, String brand, String price, String unit) {
            this(quantity, type, brand, price, unit, "");
        }

        public ProductInfo(String quantity, String type, String brand, String price, String unit, String customDescription) {
            this(quantity, type, brand, price, unit, customDescription, "", "");
        }

        public ProductInfo(String quantity, String type, String brand, String price, String unit,
                            String customDescription, String customTitle, String descriptionSize) {
            this.quantity = quantity == null ? "" : quantity;
            this.type = type == null ? "" : type;
            this.brand = brand == null ? "" : brand;
            this.price = price == null ? "" : price;
            this.unit = unit == null ? "" : unit;
            this.customDescription = customDescription == null ? "" : customDescription;
            this.customTitle = customTitle == null ? "" : customTitle;
            this.descriptionSize = descriptionSize == null ? "" : descriptionSize;
        }
    }

    public ProductLookup(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void lookup(String barcode, LookupCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onNotFound(barcode);
            return;
        }

        // 1. Τοπική cache
        String cached = prefs.getString(barcode, null);
        if (cached != null) {
            ProductInfo info = fromJson(cached);
            if (info != null) {
                callback.onResult(info, Source.CACHE);
                return;
            }
        }

        // 2. Open Food Facts
        queryOpenFoodFacts(barcode, callback);
    }

    /** Ψάχνει ΜΟΝΟ στις δωρεάν βάσεις (Open Food Facts -> UPCitemdb), χωρίς έλεγχο cache.
     *  Χρησιμοποιείται ως fallback μετά το AI search. */
    public void lookupExternalDatabasesOnly(String barcode, LookupCallback callback) {
        queryOpenFoodFacts(barcode, callback);
    }

    private void queryOpenFoodFacts(String barcode, LookupCallback callback) {
        String url = "https://world.openfoodfacts.org/api/v2/product/" + barcode
                + ".json?fields=product_name,brands,quantity";

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Open Food Facts network error: " + e.getMessage());
                queryUpcItemDb(barcode, callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        queryUpcItemDb(barcode, callback);
                        return;
                    }

                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    int status = json.optInt("status", 0);

                    if (status == 1) {
                        JSONObject product = json.getJSONObject("product");
                        String name = product.optString("product_name", "");
                        String brands = product.optString("brands", "");
                        String quantity = product.optString("quantity", "");

                        if (name.isEmpty() && brands.isEmpty()) {
                            queryUpcItemDb(barcode, callback);
                            return;
                        }

                        ProductInfo info = new ProductInfo(quantity, name, brands);
                        saveToCache(barcode, info);
                        callback.onResult(info, Source.OPEN_FOOD_FACTS);
                    } else {
                        queryUpcItemDb(barcode, callback);
                    }
                } catch (IOException | JSONException e) {
                    Log.w(TAG, "Open Food Facts parse error: " + e.getMessage());
                    queryUpcItemDb(barcode, callback);
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Δωρεάν trial endpoint - ΔΕΝ χρειάζεται API key.
     * Όριο: 100 requests/ημέρα ανά IP διεύθυνση.
     */
    private void queryUpcItemDb(String barcode, LookupCallback callback) {
        String url = "https://api.upcitemdb.com/prod/trial/lookup?upc=" + barcode;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "UPCitemdb network error: " + e.getMessage());
                callback.onNotFound(barcode);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onNotFound(barcode);
                        return;
                    }

                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray items = json.optJSONArray("items");

                    if (items == null || items.length() == 0) {
                        callback.onNotFound(barcode);
                        return;
                    }

                    JSONObject item = items.getJSONObject(0);
                    String title = item.optString("title", "");
                    String brand = item.optString("brand", "");
                    String size = item.optString("size", ""); // π.χ. "6 x 330 ml"

                    ProductInfo info = new ProductInfo(size, title, brand);
                    saveToCache(barcode, info);
                    callback.onResult(info, Source.UPCITEMDB);

                } catch (IOException | JSONException e) {
                    Log.w(TAG, "UPCitemdb parse error: " + e.getMessage());
                    callback.onNotFound(barcode);
                } finally {
                    response.close();
                }
            }
        });
    }

    /** Καλέστε το αφού ο χρήστης καταχωρήσει χειροκίνητα ένα προϊόν, ώστε να μπει στην cache. */
    public void confirmAndCache(String barcode, ProductInfo info) {
        saveToCache(barcode, info);
    }

    /** Επιστρέφει τα στοιχεία ενός barcode από την τοπική βάση, ή null αν δεν υπάρχει ακόμα. */
    public ProductInfo getEntry(String barcode) {
        String cached = prefs.getString(barcode, null);
        return cached != null ? fromJson(cached) : null;
    }

    /** Επιστρέφει όλα τα καταχωρημένα προϊόντα (barcode -> στοιχεία), για την οθόνη επεξεργασίας. */
    public Map<String, ProductInfo> getAllEntries() {
        Map<String, ProductInfo> result = new LinkedHashMap<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                ProductInfo info = fromJson((String) entry.getValue());
                if (info != null) {
                    result.put(entry.getKey(), info);
                }
            }
        }
        return result;
    }

    /** Διαγράφει ένα καταχωρημένο barcode από την τοπική βάση. */
    public void deleteEntry(String barcode) {
        prefs.edit().remove(barcode).apply();
    }

    /**
     * "Διαγράφει" μόνο την ΕΤΙΚΕΤΑ ενός προϊόντος (τιμή, μονάδα, custom κείμενο/μέγεθος) -
     * ΔΕΝ διαγράφει το ίδιο το προϊόν (quantity/type/brand παραμένουν), οπότε συνεχίζει
     * να εμφανίζεται κανονικά στο "Όλα τα Προϊόντα". Μετά από αυτό, το προϊόν απλά δεν
     * θα εμφανίζεται πια στο "Όλες οι Ετικέτες" (αφού δεν έχει πια τιμή).
     */
    public void clearLabelData(String barcode) {
        ProductInfo existing = getEntry(barcode);
        if (existing == null) return;

        ProductInfo cleared = new ProductInfo(
                existing.quantity,
                existing.type,
                existing.brand,
                "",   // price
                "",   // unit
                "",   // customDescription
                "",   // customTitle
                "");  // descriptionSize
        saveToCache(barcode, cleared);
    }

    private void saveToCache(String barcode, ProductInfo info) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("quantity", info.quantity);
            obj.put("type", info.type);
            obj.put("brand", info.brand);
            obj.put("price", info.price);
            obj.put("unit", info.unit);
            obj.put("customDescription", info.customDescription);
            obj.put("customTitle", info.customTitle);
            obj.put("descriptionSize", info.descriptionSize);
            prefs.edit().putString(barcode, obj.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Cache save failed: " + e.getMessage());
        }
    }

    private ProductInfo fromJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            return new ProductInfo(
                    obj.optString("quantity", ""),
                    obj.optString("type", ""),
                    obj.optString("brand", ""),
                    obj.optString("price", ""),
                    obj.optString("unit", ""),
                    obj.optString("customDescription", ""),
                    obj.optString("customTitle", ""),
                    obj.optString("descriptionSize", ""));
        } catch (JSONException e) {
            return null;
        }
    }
}
