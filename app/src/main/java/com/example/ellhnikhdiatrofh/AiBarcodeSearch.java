package com.example.ellhnikhdiatrofh;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Αναζήτηση barcode μέσω πραγματικής αναζήτησης Google (Serper.dev) + Gemini
 * για εξαγωγή δομημένων στοιχείων προϊόντος (quantity/type/brand).
 *
 * Ροή: 1) (Προαιρετικά) ό,τι βρήκαν οι δωρεάν βάσεις περνάει ως ΥΠΟΔΕΙΞΗ
 *      2) Serper κάνει πραγματική αναζήτηση Google για το barcode
 *      3) Η υπόδειξη + τα αποτελέσματα Google περνάνε ως context στο Gemini
 *      4) Το Gemini βγάζει την ΤΕΛΙΚΗ απόφαση (JSON με 0, 1, ή περισσότερα προϊόντα)
 *
 * ΠΡΟΣΟΧΗ: Χρειάζεται δικά σου API keys στο local.properties:
 *   GEMINI_API_KEY=...   (δωρεάν από https://aistudio.google.com/apikey)
 *   SERPER_API_KEY=...   (δωρεάν 2.500 αναζητήσεις από https://serper.dev)
 */
public class AiBarcodeSearch {

    public interface Callback {
        void onNone();
        void onSingleCandidate(ProductLookup.ProductInfo candidate);
        void onMultipleCandidates(List<ProductLookup.ProductInfo> candidates);
        /** Δικτυακό/API σφάλμα (π.χ. δεν έχεις βάλει keys) - ο caller αποφασίζει τι να κάνει. */
        void onError(String message);
    }

    private final SerperSearchClient searchClient;
    private final GeminiClient geminiClient;

    public AiBarcodeSearch() {
        this.searchClient = new SerperSearchClient(BuildConfig.SERPER_API_KEY);
        this.geminiClient = new GeminiClient(BuildConfig.GEMINI_API_KEY);
    }

    /** Χωρίς υπόδειξη από βάση δεδομένων. */
    public void search(String barcode, Callback callback) {
        search(barcode, null, callback);
    }

    /**
     * @param databaseHint ό,τι (αν κάτι) βρήκαν οι δωρεάν βάσεις γι' αυτό το barcode -
     *                     δίνεται στο Gemini ως πρόσθετο στοιχείο, αλλά το Gemini αποφασίζει
     *                     την τελική απάντηση αφού δει και τα αποτελέσματα του Serper.
     */
    public void search(String barcode, ProductLookup.ProductInfo databaseHint, Callback callback) {
        String searchQuery =  barcode ;

        searchClient.search(searchQuery, new SerperSearchClient.SearchCallback() {
            @Override
            public void onSuccess(String searchResults) {
                String prompt = buildPrompt(barcode, searchResults, databaseHint);
                geminiClient.askGemini(prompt, new GeminiClient.GeminiCallback() {
                    @Override
                    public void onSuccess(String answer) {
                        parseAnswer(answer, callback);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        callback.onError(errorMessage);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    private String buildPrompt(String barcode, String searchResults, ProductLookup.ProductInfo databaseHint) {
        StringBuilder hintSection = new StringBuilder();
        if (databaseHint != null) {
            hintSection.append("\nΜΙΑ ΒΑΣΗ ΔΕΔΟΜΕΝΩΝ ΠΡΟΪΟΝΤΩΝ επέστρεψε ήδη αυτή την υποψήφια απάντηση "
                    + "γι' αυτό το barcode (μπορεί να είναι σωστή, ελλιπής, ή και λάθος - χρησιμοποίησέ "
                    + "την ΜΟΝΟ ως υπόδειξη, ΟΧΙ ως δεδομένη αλήθεια):\n")
                    .append("{\"type\": \"").append(databaseHint.type).append("\", ")
                    .append("\"brand\": \"").append(databaseHint.brand).append("\", ")
                    .append("\"quantity\": \"").append(databaseHint.quantity).append("\"}\n")
                    .append("Σύγκρινε αυτή την υπόδειξη με τα αποτελέσματα Google παρακάτω, και δώσε την "
                    + "ΤΕΛΙΚΗ, πιο ακριβή απάντηση - διόρθωσε την υπόδειξη αν τα αποτελέσματα Google δείχνουν κάτι διαφορετικό/πιο σωστό.\n");
        }

        return "Ο παρακάτω αριθμός είναι ΠΑΝΤΑ ένα barcode προϊόντος (EAN/UPC/GTIN), "
                + "ποτέ γενική ερώτηση: " + barcode + "\n"
                + hintSection
                + "\nΑποτελέσματα Google για \"" + barcode + " barcode\":\n\n"
                + searchResults
                + "\n\nΑΥΣΤΗΡΕΣ ΟΔΗΓΙΕΣ:\n"
                + "1. Δέξου ΜΟΝΟ αποτελέσματα όπου ο αριθμός " + barcode + " εμφανίζεται ως "
                + "ΑΥΤΟΤΕΛΗΣ κωδικός barcode/EAN/UPC/GTIN προϊόντος. ΑΓΝΟΗΣΕ αποτελέσματα όπου ο "
                + "αριθμός είναι απλά τμήμα μέσα σε άλλον, μεγαλύτερο κωδικό.\n"
                + "2. Απάντησε ΜΟΝΟ με JSON, χωρίς κείμενο πριν ή μετά, ΑΚΡΙΒΩΣ σε αυτή τη μορφή:\n"
                + "{\"products\": [{\"type\": \"...\", \"brand\": \"...\", \"quantity\": \"...\"}]}\n\n"
                + "ΟΡΙΣΜΟΣ ΤΩΝ ΠΕΔΙΩΝ (πολύ σημαντικό να μην τα μπερδέψεις):\n"
                + "- \"brand\": ΜΟΝΟ το εμπορικό όνομα/η εταιρεία που είναι τυπωμένη στο προϊόν "
                + "ως κύριο σήμα (π.χ. \"ΕΖΑ\", \"Mythos\", \"Fix\", \"Heineken\", \"Rizzla\"). "
                + "ΠΟΤΕ μην βάλεις εδώ λέξεις που περιγράφουν στυλ/κατηγορία/ποιότητα.\n"
                + "- \"type\": το είδος/κατηγορία προϊόντος, μπορεί να περιλαμβάνει και το στυλ "
                + "(π.χ. \"μπίρα pilsner\", \"μπίρα lager\", \"χαρτάκια στριφτού\"). Λέξεις όπως "
                + "\"Premium\", \"Pilsner\", \"Lager\", \"Ξανθιά\", \"Σκούρα\" ανήκουν ΕΔΩ, όχι στο brand.\n"
                + "- \"quantity\": μόνο ο όγκος/βάρος/αριθμός τεμαχίων συσκευασίας (π.χ. \"330ml\", "
                + "\"6x330ml\", \"50 φύλλα\", \"500g\").\n\n"
                + "ΠΑΡΑΔΕΙΓΜΑ: Αν ένα αποτέλεσμα λέει \"ΕΖΑ Premium Pilsner Μπύρα 330ml\", η σωστή "
                + "εξαγωγή είναι: {\"type\": \"μπίρα pilsner\", \"brand\": \"ΕΖΑ\", \"quantity\": \"330ml\"} "
                + "- ΟΧΙ brand=\"Premium Pilsner\".\n\n"
                + "Άφησε κενό string \"\" σε ό,τι πεδίο δεν ξέρεις με σιγουριά (καλύτερα κενό παρά λάθος).\n"
                + "3. Αν βρεις ΠΑΝΩ ΑΠΟ ΕΝΑ διαφορετικό πραγματικό προϊόν με ακριβώς αυτό το barcode "
                + "(σπάνιο αλλά συμβαίνει), βάλε τα ΟΛΑ μέσα στο ίδιο \"products\" array.\n"
                + "4. Αν δεν βρεις κανένα έγκυρο αποτέλεσμα προϊόντος (ούτε από την υπόδειξη, ούτε από "
                + "τα αποτελέσματα Google), απάντησε ΜΟΝΟ με {\"products\": []}.\n"
                + "Μην γράψεις ΤΙΠΟΤΑ άλλο εκτός από το JSON.";
    }

    private void parseAnswer(String answer, Callback callback) {
        try {
            String clean = answer.replace("```json", "").replace("```", "").trim();
            JSONObject json = new JSONObject(clean);
            JSONArray products = json.optJSONArray("products");

            if (products == null || products.length() == 0) {
                callback.onNone();
                return;
            }

            List<ProductLookup.ProductInfo> candidates = new ArrayList<>();
            for (int i = 0; i < products.length(); i++) {
                JSONObject p = products.getJSONObject(i);
                candidates.add(new ProductLookup.ProductInfo(
                        p.optString("quantity", ""),
                        p.optString("type", ""),
                        p.optString("brand", "")));
            }

            if (candidates.size() == 1) {
                callback.onSingleCandidate(candidates.get(0));
            } else {
                callback.onMultipleCandidates(candidates);
            }
        } catch (JSONException e) {
            // Αν το Gemini δεν επέστρεψε έγκυρο JSON, το θεωρούμε "δεν βρέθηκε"
            // αντί να σκάσει η εφαρμογή - ασφαλέστερο fallback.
            callback.onNone();
        }
    }
}
