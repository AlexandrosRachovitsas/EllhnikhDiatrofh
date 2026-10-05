package com.example.ellhnikhdiatrofh;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Αποθηκεύει τοπικά τις παραγγελίες (Πωλητής/Εταιρία, Υπεύθυνος, γραμμές
 * προϊόντων-ποσοτήτων, παρατηρήσεις) σαν λίστα JSON στο SharedPreferences.
 */
public class OrderStorage {

    private static final String PREFS_NAME = "orders_data";
    private static final String KEY_ORDERS = "orders_list";

    private final SharedPreferences prefs;

    public static class OrderItem {
        public String description;
        public String quantity;

        public OrderItem(String description, String quantity) {
            this.description = description == null ? "" : description;
            this.quantity = quantity == null ? "" : quantity;
        }
    }

    public static class Order {
        public String seller = "";
        public String responsible = "";
        public String notes = "";
        public long timestamp;
        public List<OrderItem> items = new ArrayList<>();
    }

    public OrderStorage(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Αποθηκεύει μια νέα παραγγελία (προστίθεται στη λίστα, δεν αντικαθιστά τις προηγούμενες). */
    public void saveOrder(Order order) {
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_ORDERS, "[]"));

            JSONObject obj = new JSONObject();
            obj.put("seller", order.seller);
            obj.put("responsible", order.responsible);
            obj.put("notes", order.notes);
            obj.put("timestamp", order.timestamp);

            JSONArray itemsArray = new JSONArray();
            for (OrderItem item : order.items) {
                JSONObject itemObj = new JSONObject();
                itemObj.put("description", item.description);
                itemObj.put("quantity", item.quantity);
                itemsArray.put(itemObj);
            }
            obj.put("items", itemsArray);

            array.put(obj);
            prefs.edit().putString(KEY_ORDERS, array.toString()).apply();
        } catch (JSONException ignored) {
            // δεν πρέπει να ρίξει την εφαρμογή αν κάτι πάει στραβά στην αποθήκευση
        }
    }
}
