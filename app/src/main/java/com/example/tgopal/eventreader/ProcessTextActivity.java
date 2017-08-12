package com.example.tgopal.eventreader;

import android.accounts.AccountManager;
import android.app.IntentService;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.library.bubbleview.BubbleTextView;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.CategoriesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.RelationsOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProcessTextActivity extends AppCompatActivity {

    private ProgressDialog mDialog;
    private String textToAnalyze;
    private String dateOfEventRes;
    private String month, day, year;
    private String categoriesRes;
    private String peopleRes;
    private String locRes;
    private String keywordsRes;
    private static final int REQ_CHOOSE_ACCOUNT = 1;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private GoogleAccountCredential mCredential;
    private String googleAcct;
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };

    private class AskWatsonTask extends AsyncTask<Void, Void, String> {
        @Override
        protected void onPreExecute() {
            mDialog = new ProgressDialog(ProcessTextActivity.this);
            mDialog.setMessage("Obtaining insights from text...");
            mDialog.setCancelable(false);
            mDialog.show();
        }
        @Override
        protected String doInBackground(Void... params) {
            NaturalLanguageUnderstanding service = new NaturalLanguageUnderstanding(
                    NaturalLanguageUnderstanding.VERSION_DATE_2017_02_27,
                    "9a55cea6-e90e-4461-acf9-9ed5c31c9289",
                    "Gfybl1Y08OOZ"
            );

            EntitiesOptions entities =  new EntitiesOptions.Builder()
                    .sentiment(true)
                    .build();
            CategoriesOptions categories = new CategoriesOptions();

            KeywordsOptions keywords = new KeywordsOptions.Builder()
                    .sentiment(true)
                    .emotion(true)
                    .build();

            RelationsOptions relations = new RelationsOptions.Builder()
                    .build();

            Features features = new Features.Builder()
                    .entities(entities)
                    .relations(relations)
                    .categories(categories)
                    .keywords(keywords)
                    .build();

            AnalyzeOptions parameters = new AnalyzeOptions.Builder()
                    .text(textToAnalyze)
                    .features(features)
                    .build();

            AnalysisResults response = service.analyze(parameters).execute();
            Log.d("IBMResults: ", response.toString());
            return response.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            processData(res);
            mDialog.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process_text);

        textToAnalyze = getIntent().getStringExtra("ocr_data");

        new AskWatsonTask().execute();

        String[] moDayYear;
        moDayYear = extractDate(textToAnalyze);
        StringBuilder sb = new StringBuilder();
        if (moDayYear[0] != null) sb.append(moDayYear[0] + " ");
        if (moDayYear[1] != null) sb.append(moDayYear[1] + " ");
        if (moDayYear[2] != null) sb.append(moDayYear[2] + " ");

        month = moDayYear[0];
        day = moDayYear[1];
        year = moDayYear[2];

        BubbleTextView date = (BubbleTextView) findViewById(R.id.bubble_date);
        date.setText(sb.toString());
        dateOfEventRes = sb.toString();


    }

    public void sendEventToCalendar(View v) {

        Intent auth = new Intent(this, AuthActivity.class);
        auth.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        auth.putExtra("month", month);
        auth.putExtra("day", day);
        auth.putExtra("year", year);
        auth.putExtra("loc", locRes);
        auth.putExtra("people", peopleRes);
        auth.putExtra("keywords", keywordsRes);
        auth.putExtra("categories", categoriesRes);
        startActivity(auth);
    }

    @Override
    protected void onActivityResult(final int reqCode, final int resCode, final Intent data) {
        if (resCode == RESULT_OK && data != null &&
                data.getExtras() != null) {
            String accountName =
                    data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (accountName != null) {
                SharedPreferences settings =
                        getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_ACCOUNT_NAME, accountName);
                editor.apply();
                setGoogleAccountName(accountName);
            }
        }
    }

    public String getGoogleAccountName() {
        return googleAcct;
    }

    public void setGoogleAccountName(String acct) {
        googleAcct = acct;
    }

    public void processData(String result) {
        try {
            JSONObject data = new JSONObject(result);
            JSONArray categories = data.optJSONArray("categories");
            StringBuilder sbCategory = new StringBuilder();
            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.getJSONObject(i);
                sbCategory.append(category.optString("label").toString());
                sbCategory.append("\n");
            }
            BubbleTextView bubble_categories = (BubbleTextView) findViewById(R.id.bubble_categories);
            bubble_categories.setText(sbCategory.toString());
            categoriesRes = sbCategory.toString();


            JSONArray entities = data.optJSONArray("entities");
            StringBuilder sbPerson = new StringBuilder();
            StringBuilder sbLoc = new StringBuilder();
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                String info = entity.opt("type").toString();
                if (info.equals("Person")) {
                    sbPerson.append(entity.optString("text").toString());
                    sbPerson.append("\n");
                } else if (info.equals("Location")) {
                    sbLoc.append(entity.optString("text").toString());
                    sbLoc.append("\n");
                }
            }
            BubbleTextView bubble_loc = (BubbleTextView) findViewById(R.id.bubble_location);
            bubble_loc.setText(sbLoc.toString());
            locRes = sbLoc.toString();
            BubbleTextView bubble_ppl = (BubbleTextView) findViewById(R.id.bubble_who);
            bubble_ppl.setText(sbPerson.toString());
            peopleRes = sbPerson.toString();


            JSONArray keywords = data.optJSONArray("keywords");
            StringBuilder sbPhrases = new StringBuilder();
            for(int i = 0; i < keywords.length(); i++) {
                JSONObject keyPhrase = keywords.getJSONObject(i);
                sbPhrases.append(keyPhrase.optString("text") + "   \t,  RELEVANCE: " + keyPhrase.optString("relevance"));
                sbPhrases.append("\n");
            }
            BubbleTextView bubble_keywords = (BubbleTextView) findViewById(R.id.bubble_keywords);
            bubble_keywords.setText(sbPhrases.toString());
            keywordsRes = sbPhrases.toString();

        } catch(JSONException e) {
            e.printStackTrace();
        }

    }

    private String[] extractDate(String data) {
        String[] moDayYear = new String[3];
        Pattern pattern = Pattern.compile("\\b(?:Jan(?:uary)?|Feb(?:ruary)|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sept(?:ember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)");
        Matcher matcher = pattern.matcher(data);
        if(matcher.find()) {
            moDayYear[0] = matcher.group(0);
        }

        pattern = Pattern.compile("\\b(0?[1-9]|[1-2][0-9]|30|31)(?:st|nd|rd|th)");
        matcher = pattern.matcher(data);
        if(matcher.find()) {
            moDayYear[1] = matcher.group(0);
        } else {
            pattern = Pattern.compile("\\b(0?[1-9]|[1-2][0-9]|30|31)");
            matcher = pattern.matcher(data);
            if(matcher.find()) {
                moDayYear[1] = matcher.group(0);
            }
        }

        pattern = Pattern.compile("\\b(?:19[7-9]\\d|2\\d{3})(?=\\D|$)");
        matcher = pattern.matcher(data);
        if (matcher.find()) {
            moDayYear[2] = matcher.group(0);
        }
        return moDayYear;
    }
}
