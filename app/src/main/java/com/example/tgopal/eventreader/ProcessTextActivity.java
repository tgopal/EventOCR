package com.example.tgopal.eventreader;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.CategoriesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.RelationsOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProcessTextActivity extends AppCompatActivity {

    private EditText editText;
    private Button analyzeButton;

    private class AskWatsonTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... params) {
            NaturalLanguageUnderstanding service = new NaturalLanguageUnderstanding(
                    NaturalLanguageUnderstanding.VERSION_DATE_2017_02_27,
                    "9a55cea6-e90e-4461-acf9-9ed5c31c9289",
                    "Gfybl1Y08OOZ"
            );

            String text1 = ("Tejas's Farewell party\nCome join me to celebrate the end of my internship! The whole" +
                    " S1 Android team is invited. There will be food and non-alcoholic drinks provided.\n" +
                    "When: August 18th, 2017\tWhere: 185 Channel St., San Francisco CA");

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
                    .text(text1)
                    .features(features)
                    .build();

            AnalysisResults response = service.analyze(parameters).execute();
            Log.d("IBMResults: ", response.toString());
            return response.toString();
        }

        @Override
        protected void onPostExecute(String res) {
            System.out.println(res);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process_text);

        editText = (EditText) findViewById(R.id.edit_text);
        analyzeButton = (Button) findViewById(R.id.watson_btn);

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Analyzing text...", Toast.LENGTH_SHORT).show();

                new AskWatsonTask().execute();

                String text2 = "My event taking place on 08 Oct 2007";
                String[] moDayYear = new String[3];
                moDayYear = extractDate(text2);
                String res = moDayYear[0] + " " + moDayYear[1] + " " + moDayYear[2];
                Toast.makeText(getApplicationContext(), res, Toast.LENGTH_SHORT).show();
            }
        });
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
