package com.example.ellhnikhdiatrofh;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Απλή κλάση για επικοινωνία με το Gemini API.
 * Χρήση:
 *
 *   GeminiClient client = new GeminiClient("ΤΟ_API_KEY_ΣΟΥ");
 *   client.askGemini("Ποια είναι η πρωτεύουσα της Ελλάδας;", new GeminiClient.GeminiCallback() {
 *       @Override
 *       public void onSuccess(String answer) {
 *           // Εδώ παίρνεις την απάντηση (τρέχει σε background thread)
 *       }
 *
 *       @Override
 *       public void onError(String errorMessage) {
 *           // Εδώ χειρίζεσαι το σφάλμα
 *       }
 *   });
 */
public class GeminiClient {

    // Κύριο μοντέλο - χρησιμοποιείται πρώτα
    private static final String PRIMARY_MODEL = "gemini-3.1-flash-lite";
    // Εφεδρικό μοντέλο - χρησιμοποιείται αν το κύριο χτυπήσει το ημερήσιο όριο (429)
    private static final String FALLBACK_MODEL = "gemini-3.5-flash";

    private static final String URL_PREFIX = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String URL_SUFFIX = ":generateContent?key=";

    // Πόσες φορές θα ξαναπροσπαθήσουμε πριν παραδοθούμε
    private static final int MAX_RETRIES = 3;
    // Αρχική καθυστέρηση πριν το πρώτο retry (θα διπλασιάζεται κάθε φορά: 2s, 4s, 8s...)
    private static final long INITIAL_RETRY_DELAY_MS = 2000;

    private final String apiKey;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Callback interface για την απάντηση του Gemini.
     * Τα onSuccess/onError καλούνται σε background thread, όχι στο UI thread.
     * Αν θες να ενημερώσεις UI μέσα σε αυτά, χρησιμοποίησε runOnUiThread(...).
     */
    public interface GeminiCallback {
        void onSuccess(String answer);
        void onError(String errorMessage);
    }

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Στέλνει μια ερώτηση στο Gemini και επιστρέφει την απάντηση μέσω callback.
     * Αν η απάντηση αποτύχει με προσωρινό σφάλμα (503 overload, 429 rate limit),
     * ξαναπροσπαθεί αυτόματα έως MAX_RETRIES φορές με αυξανόμενη καθυστέρηση.
     *
     * @param question η ερώτηση/prompt που θέλεις να στείλεις
     * @param callback το callback που θα λάβει την απάντηση ή το σφάλμα
     */
    public void askGemini(String question, GeminiCallback callback) {
        askGeminiWithRetry(question, callback, 0, PRIMARY_MODEL, false);
    }

    private void askGeminiWithRetry(String question, GeminiCallback callback, int attempt,
                                     String model, boolean alreadyFellBack) {
        try {
            JSONObject part = new JSONObject().put("text", question);
            JSONArray partsArray = new JSONArray().put(part);
            JSONObject content = new JSONObject().put("parts", partsArray);
            JSONArray contentsArray = new JSONArray().put(content);
            JSONObject requestBodyJson = new JSONObject().put("contents", contentsArray);

            RequestBody body = RequestBody.create(
                    requestBodyJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(URL_PREFIX + model + URL_SUFFIX + apiKey)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Σφάλμα δικτύου (π.χ. δεν υπάρχει σύνδεση) - επίσης άξιο retry
                    retryOrFail(question, callback, attempt, model, alreadyFellBack,
                            "Σφάλμα σύνδεσης: " + e.getMessage(), false);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        int code = response.code();

                        if (code == 429) {
                            // Το 429 στο free tier είναι συνήθως ημερήσιο όριο -
                            // δεν βοηθάει το retry στο ίδιο μοντέλο, καλύτερα να
                            // κάνουμε αμέσως fallback σε άλλο μοντέλο (αν δεν το έχουμε ήδη κάνει)
                            retryOrFail(question, callback, attempt, model, alreadyFellBack,
                                    "Σφάλμα API (" + code + "): " + responseBody, true);
                        } else if (code == 503) {
                            // Προσωρινό overload - κανονικό retry στο ίδιο μοντέλο
                            retryOrFail(question, callback, attempt, model, alreadyFellBack,
                                    "Σφάλμα API (" + code + "): " + responseBody, false);
                        } else {
                            // Μόνιμα σφάλματα (π.χ. 404 λάθος μοντέλο, 400 λάθος request,
                            // 403 λάθος key) - δεν έχει νόημα retry ούτε fallback
                            callback.onError("Σφάλμα API (" + code + "): " + responseBody);
                        }
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseBody);
                        String answer = json
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        callback.onSuccess(answer);
                    } catch (Exception e) {
                        callback.onError("Σφάλμα ανάλυσης απάντησης: " + responseBody);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Σφάλμα δημιουργίας request: " + e.getMessage());
        }
    }

    private void retryOrFail(String question, GeminiCallback callback, int attempt,
                              String model, boolean alreadyFellBack, String lastError,
                              boolean isQuotaError) {

        // Αν είναι σφάλμα ημερήσιου ορίου (429) και δεν έχουμε ήδη κάνει fallback,
        // δοκιμάζουμε ΑΜΕΣΩΣ το εφεδρικό μοντέλο (χωρίς καθυστέρηση - δεν πρόκειται
        // να λυθεί περιμένοντας λίγα δευτερόλεπτα σε ημερήσιο όριο)
        if (isQuotaError && !alreadyFellBack && model.equals(PRIMARY_MODEL)) {
            askGeminiWithRetry(question, callback, 0, FALLBACK_MODEL, true);
            return;
        }

        if (attempt >= MAX_RETRIES) {
            callback.onError(lastError + "\n(Απέτυχε μετά από " + (attempt + 1) + " προσπάθειες, μοντέλο: " + model + ")");
            return;
        }

        // Exponential backoff: 2s, 4s, 8s...
        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt);

        scheduler.schedule(
                () -> askGeminiWithRetry(question, callback, attempt + 1, model, alreadyFellBack),
                delay,
                TimeUnit.MILLISECONDS
        );
    }
}
