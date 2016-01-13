package io.ona.collect.android.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.ona.collect.android.R;
import io.ona.collect.android.adapters.FormListAdapter;
import io.ona.collect.android.application.Collect;
import io.ona.collect.android.listeners.FormDownloaderListener;
import io.ona.collect.android.listeners.FormListDownloaderListener;
import io.ona.collect.android.logic.FormDetails;
import io.ona.collect.android.preferences.PreferencesActivity;
import io.ona.collect.android.provider.FormsProviderAPI;
import io.ona.collect.android.tasks.DownloadFormListTask;
import io.ona.collect.android.tasks.DownloadFormsTask;
import io.ona.collect.android.tasks.FileStorageTask;
import io.ona.collect.android.utilities.CompatibilityUtils;
import io.ona.collect.android.utilities.WebUtils;

/**
 * Created by onamacuser on 17/12/2015.
 */
public class NewFormDownloadList extends ListActivity implements FormListDownloaderListener,
        FormDownloaderListener, AdapterView.OnItemSelectedListener {
    private static final String t = "RemoveFileManageList";

    private static final int PROGRESS_DIALOG = 1;
    private static final int AUTH_DIALOG = 2;
    private static final int MENU_PREFERENCES = Menu.FIRST;

    private static final int SEARCH_FORM_NAME = 0;
    private static final int SEARCH_PROJECT = 1;
    private static final int SEARCH_ORGANIZATION = 2;

    private static final String BUNDLE_TOGGLED_KEY = "toggled";
    private static final String BUNDLE_SELECTED_COUNT = "selectedcount";
    private static final String BUNDLE_FORM_MAP = "formmap";
    private static final String DIALOG_TITLE = "dialogtitle";
    private static final String DIALOG_MSG = "dialogmsg";
    private static final String DIALOG_SHOWING = "dialogshowing";
    private static final String FORMLIST = "formlist";

    public static final String LIST_URL = "listurl";

    public static final String FORMNAME = "formname";
    public static final String PROJECT = "project";
    public static final String ORGANIZATION = "organization";
    public static final String FORMDETAIL_KEY = "formdetailkey";
    public static final String FORMID_DISPLAY = "formiddisplay";

    public static final String KEY_NEW_FORMS = "newforms";

    public static final String FORM_ID_KEY = "formid";
    public static final String FORM_VERSION_KEY = "formversion";
    public static final String FORM_MD5_HASH = "formmd5hash";

    private String mAlertMsg;
    private boolean mAlertShowing = false;
    private String mAlertTitle;

    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private Button mDownloadButton;

    private DownloadFormListTask mDownloadFormListTask;
    private DownloadFormsTask mDownloadFormsTask;
    private Button mToggleButton;
    private Button mRefreshButton;
    private Button mDismissButton;
    private EditText searchFormField;

    private HashMap<String, FormDetails> mFormNamesAndURLs = new HashMap<String,FormDetails>();
    private FormListAdapter mFormListAdapter;
    private ArrayList<HashMap<String, String>> mFormList;

    private boolean mToggled = false;
    private int mSelectedCount = 0;

    private static final boolean EXIT = true;
    private static final boolean DO_NOT_EXIT = false;
    private boolean mShouldExit;
    private static final String SHOULD_EXIT = "shouldexit";

    public static HashSet<HashMap<String, String>> filesToDismiss;
    public static HashMap<HashMap<String, String>, Date> dateOfDownload;

    private int searchCategory = SEARCH_FORM_NAME;
    private String searchAttribute = FORMNAME;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remote_file_manage_list_new);
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.get_forms));
        mAlertMsg = getString(R.string.please_wait);

        FileStorageTask task = new FileStorageTask(this);
        filesToDismiss = task.getDismissedForms();
        dateOfDownload = task.getDatesOfDownload();

        // need white background before load
        getListView().setBackgroundColor(Color.WHITE);

        mDownloadButton = (Button) findViewById(R.id.add_button);
        mDownloadButton.setEnabled(selectedItemCount() > 0);
        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // this is callled in downloadSelectedFiles():
                //    Collect.getInstance().getActivityLogger().logAction(this, "downloadSelectedFiles", ...);
                downloadSelectedFiles();
                mToggled = false;
                clearChoices();
            }
        });

        mToggleButton = (Button) findViewById(R.id.toggle_button);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // toggle selections of items to all or none
                ListView ls = getListView();
                mToggled = !mToggled;

                Collect.getInstance().getActivityLogger().logAction(this, "toggleFormCheckbox", Boolean.toString(mToggled));

                for (int pos = 0; pos < ls.getCount(); pos++) {
                    ls.setItemChecked(pos, mToggled);
                }

                mDownloadButton.setEnabled(!(selectedItemCount() == 0));
                mDismissButton.setEnabled(!(selectedItemCount() == 0));
            }
        });

        mRefreshButton = (Button) findViewById(R.id.refresh_button);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger().logAction(this, "refreshForms", "");

                mToggled = false;
                downloadFormList();
                NewFormDownloadList.this.getListView().clearChoices();
                clearChoices();
            }
        });

        mDismissButton = (Button) findViewById(R.id.dismiss_button);
        mDismissButton.setEnabled(selectedItemCount() > 0);
        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger().logAction(this, "dismissForms", "");

                mToggled = false;
                dismissFormList();
                NewFormDownloadList.this.getListView().clearChoices();
                clearChoices();
            }
        });

        searchFormField = (EditText) findViewById(R.id.search_form);
        searchFormField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                displayFilteredFormList(s.toString(), searchAttribute);
            }
        });

        Spinner spinner = (Spinner) findViewById(R.id.search_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.search_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        if (savedInstanceState != null) {
            // If the screen has rotated, the hashmap with the form ids and urls is passed here.
            if (savedInstanceState.containsKey(BUNDLE_FORM_MAP)) {
                mFormNamesAndURLs =
                        (HashMap<String, FormDetails>) savedInstanceState
                                .getSerializable(BUNDLE_FORM_MAP);
            }

            // indicating whether or not select-all is on or off.
            if (savedInstanceState.containsKey(BUNDLE_TOGGLED_KEY)) {
                mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
            }

            // how many items we've selected
            // Android should keep track of this, but broken on rotate...
            if (savedInstanceState.containsKey(BUNDLE_SELECTED_COUNT)) {
                mSelectedCount = savedInstanceState.getInt(BUNDLE_SELECTED_COUNT);
                mDownloadButton.setEnabled(!(mSelectedCount == 0));
                mDismissButton.setEnabled(!(mSelectedCount == 0));
            }

            // to restore alert dialog.
            if (savedInstanceState.containsKey(DIALOG_TITLE)) {
                mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
            }
            if (savedInstanceState.containsKey(DIALOG_MSG)) {
                mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
            }
            if (savedInstanceState.containsKey(DIALOG_SHOWING)) {
                mAlertShowing = savedInstanceState.getBoolean(DIALOG_SHOWING);
            }
            if (savedInstanceState.containsKey(SHOULD_EXIT)) {
                mShouldExit = savedInstanceState.getBoolean(SHOULD_EXIT);
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(FORMLIST)) {
            mFormList =
                    (ArrayList<HashMap<String, String>>) savedInstanceState.getSerializable(FORMLIST);
        } else {
            mFormList = new ArrayList<HashMap<String, String>>();
        }

        if (getLastNonConfigurationInstance() instanceof DownloadFormListTask) {
            mDownloadFormListTask = (DownloadFormListTask) getLastNonConfigurationInstance();
            if (mDownloadFormListTask.getStatus() == AsyncTask.Status.FINISHED) {
                try {
                    dismissDialog(PROGRESS_DIALOG);
                } catch (IllegalArgumentException e) {
                    Log.i(t, "Attempting to close a dialog that was not previously opened");
                }
                mDownloadFormsTask = null;
            }
        } else if (getLastNonConfigurationInstance() instanceof DownloadFormsTask) {
            mDownloadFormsTask = (DownloadFormsTask) getLastNonConfigurationInstance();
            if (mDownloadFormsTask.getStatus() == AsyncTask.Status.FINISHED) {
                try {
                    dismissDialog(PROGRESS_DIALOG);
                } catch (IllegalArgumentException e) {
                    Log.i(t, "Attempting to close a dialog that was not previously opened");
                }
                mDownloadFormsTask = null;
            }
        } else if (getLastNonConfigurationInstance() == null) {
            // first time, so get the formlist
            downloadFormList();
        }

        String[] data = new String[] {
                FORMNAME, FORMID_DISPLAY, FORMDETAIL_KEY
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2
        };

        /**mFormListAdapter =
                new SimpleAdapter(this, mFormList, R.layout.two_item_multiple_choice_new, data, view);**/
        mFormListAdapter =
                new FormListAdapter(this, R.layout.two_item_multiple_choice_new, mFormList);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(false);
        setListAdapter(mFormListAdapter);
    }

    private void dismissFormList() {
        SparseBooleanArray sba = getListView().getCheckedItemPositions();
        for (int i = 0; i < getListView().getCount(); i++) {
            if (sba.get(i, false)) {
                HashMap<String, String> item =
                        (HashMap<String, String>) getListAdapter().getItem(i);
                filesToDismiss.add(item);
            }
        }
        FileStorageTask task = new FileStorageTask(this);
        task.saveDismissedForms(filesToDismiss);
        int totalCount = filesToDismiss.size();
        Collect.getInstance().getActivityLogger().logAction(this, "dismissSelectedFiles", Integer.toString(totalCount));
    }


    @Override
    protected void onStart() {
        super.onStart();
        Collect.getInstance().getActivityLogger().logOnStart(this);
    }

    @Override
    protected void onStop() {
        Collect.getInstance().getActivityLogger().logOnStop(this);
        super.onStop();
    }

    private void clearChoices() {
        NewFormDownloadList.this.getListView().clearChoices();
        mDownloadButton.setEnabled(false);
        mDismissButton.setEnabled(false);
    }

    private void displayFilteredFormList(String s, String att) {
        ArrayList<HashMap<String, String>> filteredFormList = new ArrayList<HashMap<String, String>>();
        for (HashMap<String, String> formItem : mFormList) {
            String value = formItem.get(att);
            if (value != null && value.toLowerCase().contains(s.toLowerCase())) {
                filteredFormList.add(formItem);
            }
        }
        populateListView(filteredFormList);
    }

    public void populateListView(ArrayList<HashMap<String, String>> mFormList) {
        String[] data = new String[] {
                FORMNAME, FORMID_DISPLAY, FORMDETAIL_KEY
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2
        };

        mFormListAdapter =
                new FormListAdapter(this, R.layout.two_item_multiple_choice, mFormList);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(false);
        setListAdapter(mFormListAdapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mDownloadButton.setEnabled(!(selectedItemCount() == 0));
        mDismissButton.setEnabled(!(selectedItemCount() == 0));

        Object o = getListAdapter().getItem(position);
        @SuppressWarnings("unchecked")
        HashMap<String, String> item = (HashMap<String, String>) o;
        FormDetails detail = mFormNamesAndURLs.get(item.get(FORMDETAIL_KEY));

        if ( detail != null ) {
            Collect.getInstance().getActivityLogger().logAction(this, "onListItemClick", detail.downloadUrl);
        } else {
            Collect.getInstance().getActivityLogger().logAction(this, "onListItemClick", "<missing form detail>");
        }
    }


    /**
     * Starts the download task and shows the progress dialog.
     */
    private void downloadFormList() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

        if (ni == null || !ni.isConnected()) {
            Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } else {

            mFormNamesAndURLs = new HashMap<String, FormDetails>();
            if (mProgressDialog != null) {
                // This is needed because onPrepareDialog() is broken in 1.6.
                mProgressDialog.setMessage(getString(R.string.please_wait));
            }
            showDialog(PROGRESS_DIALOG);

            if (mDownloadFormListTask != null &&
                    mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
                return; // we are already doing the download!!!
            } else if (mDownloadFormListTask != null) {
                mDownloadFormListTask.setDownloaderListener(null);
                mDownloadFormListTask.cancel(true);
                mDownloadFormListTask = null;
            }

            mDownloadFormListTask = new DownloadFormListTask();
            mDownloadFormListTask.setDownloaderListener(this);
            mDownloadFormListTask.execute();
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
        outState.putInt(BUNDLE_SELECTED_COUNT, selectedItemCount());
        outState.putSerializable(BUNDLE_FORM_MAP, mFormNamesAndURLs);
        outState.putString(DIALOG_TITLE, mAlertTitle);
        outState.putString(DIALOG_MSG, mAlertMsg);
        outState.putBoolean(DIALOG_SHOWING, mAlertShowing);
        outState.putBoolean(SHOULD_EXIT, mShouldExit);
        outState.putSerializable(FORMLIST, mFormList);
    }


    /**
     * returns the number of items currently selected in the list.
     *
     * @return
     */
    private int selectedItemCount() {
        ListView lv = getListView();
        int count = 0;
        SparseBooleanArray sba = lv.getCheckedItemPositions();
        for (int i = 0; i < lv.getCount(); i++) {
            if (sba.get(i, false)) {
                count++;
            }
        }
        return count;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Collect.getInstance().getActivityLogger().logAction(this, "onCreateOptionsMenu", "show");
        super.onCreateOptionsMenu(menu);

        CompatibilityUtils.setShowAsAction(
                menu.add(0, MENU_PREFERENCES, 0, R.string.general_preferences)
                        .setIcon(R.drawable.ic_menu_preferences),
                MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                Collect.getInstance().getActivityLogger().logAction(this, "onMenuItemSelected", "MENU_PREFERENCES");
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.PROGRESS_DIALOG", "show");
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.PROGRESS_DIALOG", "OK");
                                dialog.dismiss();
                                // we use the same progress dialog for both
                                // so whatever isn't null is running
                                if (mDownloadFormListTask != null) {
                                    mDownloadFormListTask.setDownloaderListener(null);
                                    mDownloadFormListTask.cancel(true);
                                    mDownloadFormListTask = null;
                                }
                                if (mDownloadFormsTask != null) {
                                    mDownloadFormsTask.setDownloaderListener(null);
                                    mDownloadFormsTask.cancel(true);
                                    mDownloadFormsTask = null;
                                }
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mAlertMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case AUTH_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "show");
                AlertDialog.Builder b = new AlertDialog.Builder(this);

                LayoutInflater factory = LayoutInflater.from(this);
                final View dialogView = factory.inflate(R.layout.server_auth_dialog, null);

                // Get the server, username, and password from the settings
                SharedPreferences settings =
                        PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                String server =
                        settings.getString(PreferencesActivity.KEY_SERVER_URL,
                                getString(R.string.default_server_url));

                String formListUrl = getString(R.string.default_odk_formlist);
                final String url =
                        server + settings.getString(PreferencesActivity.KEY_FORMLIST_URL, formListUrl);
                Log.i(t, "Trying to get formList from: " + url);

                EditText username = (EditText) dialogView.findViewById(R.id.username_edit);
                String storedUsername = settings.getString(PreferencesActivity.KEY_USERNAME, null);
                username.setText(storedUsername);

                EditText password = (EditText) dialogView.findViewById(R.id.password_edit);
                String storedPassword = settings.getString(PreferencesActivity.KEY_PASSWORD, null);
                password.setText(storedPassword);

                b.setTitle(getString(R.string.server_requires_auth));
                b.setMessage(getString(R.string.server_auth_credentials, url));
                b.setView(dialogView);
                b.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "OK");

                        EditText username = (EditText) dialogView.findViewById(R.id.username_edit);
                        EditText password = (EditText) dialogView.findViewById(R.id.password_edit);

                        Uri u = Uri.parse(url);

                        WebUtils.addCredentials(username.getText().toString(), password.getText()
                                .toString(), u.getHost());
                        downloadFormList();
                    }
                });
                b.setNegativeButton(getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this, "onCreateDialog.AUTH_DIALOG", "Cancel");
                                finish();
                            }
                        });

                b.setCancelable(false);
                mAlertShowing = false;
                return b.create();
        }
        return null;
    }


    /**
     * starts the task to download the selected forms, also shows progress dialog
     */
    @SuppressWarnings("unchecked")
    private void downloadSelectedFiles() {
        int totalCount = 0;
        ArrayList<FormDetails> filesToDownload = new ArrayList<FormDetails>();

        SparseBooleanArray sba = getListView().getCheckedItemPositions();
        for (int i = 0; i < getListView().getCount(); i++) {
            if (sba.get(i, false)) {
                HashMap<String, String> item =
                        (HashMap<String, String>) getListAdapter().getItem(i);
                filesToDownload.add(mFormNamesAndURLs.get(item.get(FORMDETAIL_KEY)));
            }
        }
        totalCount = filesToDownload.size();

        Collect.getInstance().getActivityLogger().logAction(this, "downloadSelectedFiles", Integer.toString(totalCount));

        if (totalCount > 0) {
            // show dialog box
            showDialog(PROGRESS_DIALOG);

            mDownloadFormsTask = new DownloadFormsTask();
            mDownloadFormsTask.setDownloaderListener(this);
            mDownloadFormsTask.execute(filesToDownload);
        } else {
            Toast.makeText(getApplicationContext(), R.string.noselect_error, Toast.LENGTH_SHORT)
                    .show();
        }
    }


    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mDownloadFormsTask != null) {
            return mDownloadFormsTask;
        } else {
            return mDownloadFormListTask;
        }
    }


    @Override
    protected void onDestroy() {
        if (mDownloadFormListTask != null) {
            mDownloadFormListTask.setDownloaderListener(null);
        }
        if (mDownloadFormsTask != null) {
            mDownloadFormsTask.setDownloaderListener(null);
        }
        super.onDestroy();
    }


    @Override
    protected void onResume() {
        if (mDownloadFormListTask != null) {
            mDownloadFormListTask.setDownloaderListener(this);
        }
        if (mDownloadFormsTask != null) {
            mDownloadFormsTask.setDownloaderListener(this);
        }
        if (mAlertShowing) {
            createAlertDialog(mAlertTitle, mAlertMsg, mShouldExit);
        }
        super.onResume();
    }


    @Override
    protected void onPause() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        super.onPause();
    }

    /**
     * Determines if a local form on the device is superseded by a given version (of the same form presumably available
     * on the server).
     *
     * @param formId the form to be checked. A form with this ID may or may not reside on the local device.
     * @param latestVersion the version against which the local form (if any) is tested.
     * @return true if a form with id <code>formId</code> exists on the local device and its version is less than
     *         <code>latestVersion</code>.
     */
    public static boolean isLocalFormSuperseded(String formId, String latestVersion) {

        if ( formId == null ) {
            Log.e(t, "isLocalFormSuperseded: server is not OpenRosa-compliant. <formID> is null!");
            return true;
        }

        String[] selectionArgs = { formId };
        String selection = FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?";
        String[] fields = { FormsProviderAPI.FormsColumns.JR_VERSION };

        Cursor formCursor = null;
        try {
            formCursor = Collect.getInstance().getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, fields, selection, selectionArgs, null);
            if ( formCursor.getCount() == 0 ) {
                // form does not already exist locally
                return true;
            }
            formCursor.moveToFirst();
            int idxJrVersion = formCursor.getColumnIndex(fields[0]);
            if ( formCursor.isNull(idxJrVersion) ) {
                // any non-null version on server is newer
                return (latestVersion != null);
            }
            String jr_version = formCursor.getString(idxJrVersion);
            // apparently, the isNull() predicate above is not respected on all Android OSes???
            if ( jr_version == null && latestVersion == null ) {
                return false;
            }
            if ( jr_version == null ) {
                return true;
            }
            if ( latestVersion == null ) {
                return false;
            }
            // if what we have is less, then the server is newer
            return ( jr_version.compareTo(latestVersion) < 0 );
        } finally {
            if (formCursor != null) {
                formCursor.close();
            }
        }
    }

    /**
     * Determines if a local form on the device is superseded by a given version (of the same form presumably available
     * on the server).
     *
     * @param formId the form to be checked. A form with this ID may or may not reside on the local device.
     * @param latestVersion the version against which the local form (if any) is tested.
     * @return true if a form with id <code>formId</code> exists on the local device and its version is less than
     *         <code>latestVersion</code>.
     */
    public static boolean isLocalFormSuperseded(String formId, String latestVersion, String md5Hash) {

        if ( formId == null ) {
            Log.e(t, "isLocalFormSuperseded: server is not OpenRosa-compliant. <formID> is null!");
            return true;
        }

        String[] selectionArgs = { formId };
        String selection = FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?";
        String[] fields = { FormsProviderAPI.FormsColumns.JR_VERSION, FormsProviderAPI.FormsColumns.MD5_HASH };
        String sortOrder = FormsProviderAPI.FormsColumns.JR_VERSION + " DESC";

        Cursor formCursor = null;
        try {
            formCursor = Collect.getInstance().getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, fields, selection, selectionArgs, sortOrder);
            if ( formCursor.getCount() == 0 ) {
                // form does not already exist locally
                return true;
            }
            formCursor.moveToFirst();
            int idxJrVersion = formCursor.getColumnIndex(fields[0]);
            int idxMd5Hash = formCursor.getColumnIndex(fields[1]);
            String jr_version = formCursor.getString(idxJrVersion);
            String formMd5Hash = "md5:" + formCursor.getString(idxMd5Hash);

            Log.d("Form Id ", "Form id" + formId);
            Log.d("Form md5Hash ", "New form hash" + md5Hash);
            Log.d("Form formmd5Hash ", "Old file hash" + formMd5Hash);
            Log.d("Form version ", "New form version" + latestVersion);
            Log.d("Form formversion ", "Old file version" + jr_version);

            if ( formCursor.isNull(idxMd5Hash) ) {
                // any non-null md5Hash on server is newer
                return (md5Hash != null);
            }
            if ( formMd5Hash == null && md5Hash == null ) {
                return false;
            }
            if ( formMd5Hash == null ) {
                return true;
            }
            if ( md5Hash == null ) {
                return false;
            }
            if (!md5Hash.equals(formMd5Hash)) {
                return true;
            }

            if ( formCursor.isNull(idxJrVersion) ) {
                // any non-null version on server is newer
                return (latestVersion != null);
            }
            // apparently, the isNull() predicate above is not respected on all Android OSes???
            if ( jr_version == null && latestVersion == null ) {
                return false;
            }
            if ( jr_version == null ) {
                return true;
            }
            if ( latestVersion == null ) {
                return false;
            }
            // if what we have is less, then the server is newer
            return ( jr_version.compareTo(latestVersion) < 0 );
        } finally {
            if (formCursor != null) {
                formCursor.close();
            }
        }
    }

    /**
     * Causes any local forms that have been updated on the server to become checked in the list. This is a prompt and a
     * convenience to users to download the latest version of those forms from the server.
     */
    private void selectSupersededForms() {

        ListView ls = getListView();
        for (int idx = 0; idx < mFormList.size(); idx++) {
            HashMap<String, String> item = mFormList.get(idx);
            ls.setItemChecked(idx, true);
        }
    }

    /**
     * Called when the form list has finished downloading. results will either contain a set of
     * <formname, formdetails> tuples, or one tuple of DL.ERROR.MSG and the associated message.
     *
     * @param result
     */
    public void formListDownloadingComplete(HashMap<String, FormDetails> result) {
        dismissDialog(PROGRESS_DIALOG);
        mDownloadFormListTask.setDownloaderListener(null);
        mDownloadFormListTask = null;

        if (result == null) {
            Log.e(t, "Formlist Downloading returned null.  That shouldn't happen");
            // Just displayes "error occured" to the user, but this should never happen.
            createAlertDialog(getString(R.string.load_remote_form_error),
                    getString(R.string.error_occured), EXIT);
            return;
        }

        if (result.containsKey(DownloadFormListTask.DL_AUTH_REQUIRED)) {
            // need authorization
            showDialog(AUTH_DIALOG);
        } else if (result.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
            // Download failed
            String dialogMessage =
                    getString(R.string.list_failed_with_error,
                            result.get(DownloadFormListTask.DL_ERROR_MSG).errorStr);
            String dialogTitle = getString(R.string.load_remote_form_error);
            createAlertDialog(dialogTitle, dialogMessage, DO_NOT_EXIT);
        } else {
            // Everything worked. Clear the list and add the results.
            mFormNamesAndURLs = result;

            mFormList.clear();

            ArrayList<String> ids = new ArrayList<String>(mFormNamesAndURLs.keySet());
            for (int i = 0; i < result.size(); i++) {
                String formDetailsKey = ids.get(i);
                FormDetails details = mFormNamesAndURLs.get(formDetailsKey);
                HashMap<String, String> item = new HashMap<String, String>();
                item.put(FORMNAME, details.formName);
                item.put(FORMID_DISPLAY,
                        ((details.formVersion == null) ? "" : (getString(R.string.version) + " " + details.formVersion + " ")) +
                                "ID: " + details.formID );
                item.put(FORMDETAIL_KEY, formDetailsKey);
                item.put(FORM_ID_KEY, details.formID);
                item.put(FORM_VERSION_KEY, details.formVersion);
                item.put(FORM_MD5_HASH, details.md5Hash);
                item.put(PROJECT, details.project);
                item.put(ORGANIZATION, details.organization);

                // Add the date form is shown for first time.
                if (!dateOfDownload.containsKey(item)) {
                    dateOfDownload.put(item, new Date());
                }

                boolean notDismissed = !filesToDismiss.contains(item);
                boolean notOlderThan30 = olderThan30Days(dateOfDownload.get(item));

                // Insert the new form in alphabetical order.
                if (mFormList.size() == 0) {
                    if (notDismissed && notOlderThan30 &&
                            isLocalFormSuperseded(item.get(FORM_ID_KEY), item.get(FORM_VERSION_KEY), item.get(FORM_MD5_HASH))) {
                        mFormList.add(item);
                    }
                } else {
                    int j;
                    for (j = 0; j < mFormList.size(); j++) {
                        HashMap<String, String> compareMe = mFormList.get(j);
                        String name = compareMe.get(FORMNAME);
                        if (name.compareTo(mFormNamesAndURLs.get(ids.get(i)).formName) > 0) {
                            break;
                        }
                    }
                    if (!filesToDismiss.contains(item)) {
                        if (notDismissed && notOlderThan30 &&
                                isLocalFormSuperseded(item.get(FORM_ID_KEY), item.get(FORM_VERSION_KEY), item.get(FORM_MD5_HASH))) {
                            mFormList.add(j, item);
                        }
                    }
                }
            }
            // Update number of new forms.
            SharedPreferences settings =
                    PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(NewFormDownloadList.KEY_NEW_FORMS, mFormList.size());
            editor.commit();

            selectSupersededForms();
            mFormListAdapter.notifyDataSetChanged();
            mDownloadButton.setEnabled(!(selectedItemCount() == 0));
            mDismissButton.setEnabled(!(selectedItemCount() == 0));
            new FileStorageTask(this).saveDatesOfDownload(dateOfDownload);
        }
    }


    /**
     * Creates an alert dialog with the given tite and message. If shouldExit is set to true, the
     * activity will exit when the user clicks "ok".
     *
     * @param title
     * @param message
     * @param shouldExit
     */
    private void createAlertDialog(String title, String message, final boolean shouldExit) {
        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // ok
                        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "OK");
                        // just close the dialog
                        mAlertShowing = false;
                        // successful download, so quit
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), quitListener);
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertMsg = message;
        mAlertTitle = title;
        mAlertShowing = true;
        mShouldExit = shouldExit;
        mAlertDialog.show();
    }


    @Override
    public void progressUpdate(String currentFile, int progress, int total) {
        mAlertMsg = getString(R.string.fetching_file, currentFile, progress, total);
        mProgressDialog.setMessage(mAlertMsg);
    }


    @Override
    public void formsDownloadingComplete(HashMap<FormDetails, String> result) {
        if (mDownloadFormsTask != null) {
            mDownloadFormsTask.setDownloaderListener(null);
        }

        if (mProgressDialog.isShowing()) {
            // should always be true here
            mProgressDialog.dismiss();
        }

        Set<FormDetails> keys = result.keySet();
        StringBuilder b = new StringBuilder();
        for (FormDetails k : keys) {
            b.append(k.formName +
                    " (" +
                    ((k.formVersion != null) ?
                            (this.getString(R.string.version) + ": " + k.formVersion + " ")
                            : "") +
                    "ID: " + k.formID + ") - " +
                    result.get(k));
            b.append("\n\n");
        }

        createAlertDialog(getString(R.string.download_forms_result), b.toString().trim(), EXIT);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case 0:
                searchFormField.setHint(R.string.search_form_name);
                searchCategory = SEARCH_FORM_NAME;
                searchAttribute = FORMNAME;
                break;
            case 1:
                searchFormField.setHint(R.string.search_form_project);
                searchCategory = SEARCH_PROJECT;
                searchAttribute = PROJECT;
                break;
            case 2:
                searchFormField.setHint(R.string.search_form_organization);
                searchCategory = SEARCH_ORGANIZATION;
                searchAttribute = ORGANIZATION;
                break;

        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public static boolean olderThan30Days(Date itemDate) {
        Date currentDate = new Date();
        long day30 = 30l * 24 * 60 * 60 * 1000;
        if (itemDate != null) {
            return currentDate.before(new Date((itemDate.getTime() + day30)));
        } else return true;
    }
}
