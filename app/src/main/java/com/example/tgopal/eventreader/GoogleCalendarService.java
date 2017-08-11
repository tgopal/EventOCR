package com.example.tgopal.eventreader;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static android.app.Activity.RESULT_OK;

/**
 * Created by tgopal on 8/11/17.
 */

public class GoogleCalendarService extends IntentService {

    GoogleAccountCredential mCredential;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    private static final String[] SCOPES = { CalendarScopes.CALENDAR };

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private String googleAcctName;
    private String month, day, year, location, people, categories, keywords;

    public GoogleCalendarService() {
        super("GoogleCalendarService");
    }
    @Override
    protected void onHandleIntent(Intent workIntent) {

        googleAcctName = workIntent.getExtras().getString("acctName");
        month = workIntent.getExtras().getString("month");
        day = workIntent.getExtras().getString("day");
        year = workIntent.getExtras().getString("year");
        location = workIntent.getExtras().getString("loc");
        people = workIntent.getExtras().getString("people");
        categories = workIntent.getExtras().getString("categories");
        keywords = workIntent.getExtras().getString("keywords");

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        getResultsFromApi();
    }

    private void getResultsFromApi() {
        System.out.println("Reached getResultsFromApi");
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (isDeviceOnline()) {
            new MakeRequestTask(mCredential).execute();
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        System.out.println("Reached chooseAccount");
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            String accountName = preferences.getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
            } else {
                System.out.println("Choose account: " + googleAcctName);
                mCredential.setSelectedAccountName(googleAcctName);
            }
            getResultsFromApi();

        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {

        }
    }

    private class MakeRequestTask extends AsyncTask<Void, Void, String> {
        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Event Reader")
                    .build();
            System.out.println(mService);
        }

        /**
         * Background task to call Google Calendar API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected String doInBackground(Void... params) {
            try {
                System.out.println("Gathering data");
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return "";
            }
        }

        /**
         * Fetch a list of the next 10 events from the primary calendar.
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        private String getDataFromApi() throws IOException {
            // List the next 10 events from the primary calendar.
            DateTime now = new DateTime(System.currentTimeMillis());
            //List<String> eventStrings = new ArrayList<String>();
            //System.out.println("Reached getDataFromApi() 1");
            StringBuilder desc = new StringBuilder();
            desc.append("People: " + people + "\n");
            desc.append("Relates to: " + categories + "\n");
            desc.append("Key phrases: " + keywords + "\n");

            Event event = new Event()
                    .setSummary("Event Created by EventReader")
                    .setLocation(location)
                    .setDescription(desc.toString());

            /*System.out.println("Reached getDataFromApi() 2");
            DateTime startDateTime = new DateTime("2017-08-12T09:00:00-07:00");
            EventDateTime start = new EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone("America/Los_Angeles");
            event.setStart(start);

            System.out.println("Reached getDataFromApi() 3");
            DateTime endDateTime = new DateTime("2017-08-12T17:00:00-07:00");
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("America/Los_Angeles");
            event.setEnd(end);

            System.out.println("Reached getDataFromApi() 4");
            EventReminder[] reminderOverrides = new EventReminder[] {
                    new EventReminder().setMethod("email").setMinutes(24 * 60),
                    new EventReminder().setMethod("popup").setMinutes(10),
            };

            Event.Reminders reminders = new Event.Reminders()
                    .setUseDefault(false)
                    .setOverrides(Arrays.asList(reminderOverrides));
            event.setReminders(reminders);

            String calendarId = "primary";
            System.out.println("Reached getDataFromApi() 5");
            Event eventCreated; */
            try {
                mService.events().insert("primary", event).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("Reached getDataFromApi() 6");

            //return "Success";

            /*Events events = mService.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();

            for (Event event : items) {
                DateTime start = event.getStart().getDateTime();
                if (start == null) {
                    // All-day events don't have start times, so just use
                    // the start date.
                    start = event.getStart().getDate();
                }
                eventStrings.add(
                        String.format("%s (%s)", event.getSummary(), start));
            }
            System.out.println(eventStrings); */
            return "Success";
            //return eventStrings;
        }

        private String convertDate(String date) {
            StringBuilder converted = new StringBuilder();
            Map<String, String> months = new HashMap<>();
            months.put("Jan", "01");
            months.put("January", "01");
            months.put("Feb", "02");
            months.put("February", "02");
            months.put("Mar", "03");
            months.put("March", "03");
            months.put("Apr", "04");
            months.put("April", "04");
            months.put("May", "05");
            months.put("Jun", "06");
            months.put("June", "06");
            months.put("Jul", "07");
            months.put("July", "07");
            months.put("Aug", "08");
            months.put("August", "08");
            months.put("Sept", "09");
            months.put("September", "09");
            months.put("Oct", "10");
            months.put("October", "10");
            months.put("Nov", "11");
            months.put("November", "11");
            months.put("Dec", "12");
            months.put("December", "12");

            converted.append(year);

            return converted.toString();
        }


        @Override
        protected void onPreExecute() {
            Log.d("GoogleCalendarService", "Starting request to Google Calendar API!");
        }

        @Override
        protected void onPostExecute(String output) {
            Log.d("GoogleCalendarService", "Finished creating event.");
            System.out.println(output);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    Log.d("GoogleCalendarService", "GooglePlayServiceAvailabilityIOException was thrown.");
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    Log.d("GoogleCalendarService", "UserRecoverableAuthIOException was thrown.");
                } else {
                    Log.d("GoogleCalendarService", "The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                Log.d("GoogleCalendarService", "Request cancelled.");
            }
        }
    }
}
