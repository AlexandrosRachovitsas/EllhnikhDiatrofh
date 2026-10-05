package com.example.ellhnikhdiatrofh;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Κλάση για πραγματική αναζήτηση στο Google μέσω του Serper.dev API.
 * Το Serper επιστρέφει πραγματικά αποτελέσματα Google (ίδια με αυτά που θα έβλεπες
 * στον browser), σε αντίθεση με το Gemini που απλά "μαντεύει" από τη μνήμη του.
 *
 * Δωρεάν tier: 2.500 αναζητήσεις, χωρίς να χρειάζεται κάρτα/billing.
 *
 * Χρήση:
 *
 *   SerperSearchClient searchClient = new SerperSearchClient("ΤΟ_SERPER_KEY_ΣΟΥ");
 *   searchClient.search("barcode 5010175810538", new SerperSearchClient.SearchCallback() {
 *       @Override
 *       public void onSuccess(String results) {
 *           // Κείμενο με τους τίτλους/περιγραφές των πρώτων αποτελεσμάτων Google
 *       }
 *
 *       @Override
 *       public void onError(String errorMessage) {
 *           // Χειρισμός σφάλματος
 *       }
 *   });
 */
public class SerperSearchClient {

    private static final String SEARCH_URL = "https://google.serper.dev/search";

    private final String apiKey;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface SearchCallback {
        /**
         * @param resultsText Καθαρό κείμενο με τα πρώτα αποτελέσματα Google
         *                     (τίτλος + περιγραφή για κάθε αποτέλεσμα), έτοιμο
         *                     είτε να εμφανιστεί απευθείας, είτε να περαστεί σαν
         *                     context σε ένα ερώτημα προς το Gemini.
         */
        void onSuccess(String resultsText);
        void onError(String errorMessage);
    }

    public SerperSearchClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Κάνει πραγματική αναζήτηση Google για το δοσμένο query.
     *
     * @param query   το ερώτημα αναζήτησης (π.χ. "barcode 5010175810538")
     * @param callback το callback που θα λάβει τα αποτελέσματα ή το σφάλμα
     */
    public void search(String query, SearchCallback callback) {
        try {
            JSONObject requestBodyJson = new JSONObject()
                    .put("q", query)
                    .put("num", 10)
                    .put("gl", "gr")   // Χώρα αναζήτησης: Ελλάδα
                    .put("hl", "el");  // Γλώσσα αποτελεσμάτων: Ελληνικά

            RequestBody body = RequestBody.create(
                    requestBodyJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(SEARCH_URL)
                    .header("X-API-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Σφάλμα σύνδεσης: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        callback.onError("Σφάλμα Serper API (" + response.code() + "): " + responseBody);
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseBody);
                        StringBuilder resultsText = new StringBuilder();

                        // Αν υπάρχει "answer box" (άμεση απάντηση Google), το βάζουμε πρώτο
                        if (json.has("answerBox")) {
                            JSONObject answerBox = json.getJSONObject("answerBox");
                            if (answerBox.has("answer")) {
                                resultsText.append("Άμεση απάντηση: ")
                                        .append(answerBox.getString("answer"))
                                        .append("\n\n");
                            } else if (answerBox.has("snippet")) {
                                resultsText.append("Άμεση απάντηση: ")
                                        .append(answerBox.getString("snippet"))
                                        .append("\n\n");
                            }
                        }

                        // Αν υπάρχει "knowledge graph" (π.χ. πληροφορίες προϊόντος/οντότητας)
                        if (json.has("knowledgeGraph")) {
                            JSONObject kg = json.getJSONObject("knowledgeGraph");
                            if (kg.has("title")) {
                                resultsText.append("Τίτλος: ").append(kg.getString("title"));
                                if (kg.has("type")) {
                                    resultsText.append(" (").append(kg.getString("type")).append(")");
                                }
                                resultsText.append("\n");
                            }
                            if (kg.has("description")) {
                                resultsText.append("Περιγραφή: ").append(kg.getString("description")).append("\n\n");
                            }
                        }

                        // Τα κανονικά οργανικά αποτελέσματα αναζήτησης
                        if (json.has("organic")) {
                            JSONArray organic = json.getJSONArray("organic");
                            int limit = Math.min(organic.length(), 8); // πρώτα 8 αποτελέσματα

                            for (int i = 0; i < limit; i++) {
                                JSONObject item = organic.getJSONObject(i);
                                String title = item.optString("title", "");
                                String snippet = item.optString("snippet", "");
                                String link = item.optString("link", "");

                                resultsText.append(i + 1).append(". ").append(title).append("\n");
                                if (!snippet.isEmpty()) {
                                    resultsText.append("   ").append(snippet).append("\n");
                                }
                                if (!link.isEmpty()) {
                                    resultsText.append("   (").append(link).append(")\n");
                                }
                                resultsText.append("\n");
                            }
                        }

                        if (resultsText.length() == 0) {
                            callback.onError("Δεν βρέθηκαν αποτελέσματα.");
                        } else {
                            callback.onSuccess(resultsText.toString());
                        }

                    } catch (Exception e) {
                        callback.onError("Σφάλμα ανάλυσης απάντησης: " + responseBody);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Σφάλμα δημιουργίας request: " + e.getMessage());
        }
    }
}
