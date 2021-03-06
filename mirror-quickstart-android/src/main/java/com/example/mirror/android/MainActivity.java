package com.example.mirror.android;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.TextView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "Glass";
    private static final String PARAM_AUTH_TOKEN =
            "com.example.mirror.android.AUTH_TOKEN";

    private static final int REQUEST_ACCOUNT_PICKER = 1;
    private static final int REQUEST_AUTHORIZATION = 2;

    private static final String GLASS_TIMELINE_SCOPE =
            "https://www.googleapis.com/auth/glass.timeline";
    private static final String GLASS_LOCATION_SCOPE =
            "https://www.googleapis.com/auth/glass.location";
    private static final String SCOPE = String.format("oauth2: %s %s",
            GLASS_TIMELINE_SCOPE, GLASS_LOCATION_SCOPE);

    private static ExecutorService sThreadPool =
            Executors.newSingleThreadExecutor();

    private final Handler mHandler = new Handler();

    private String mAuthToken;
    private Button mStartAuthButton;
    private Button mExpireTokenButton;
    private ImageButton mNewCardButton;
    private EditText mNewCardEditText;
    private MenuItem mHtmlMenuItem;


    private boolean htmlMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Define our layout
        setContentView(R.layout.activity_main);

        // Get our views
        mStartAuthButton = (Button) findViewById(R.id.oauth_button);
        mExpireTokenButton = (Button) findViewById(R.id.oauth_expire_button);
        mNewCardButton = (ImageButton) findViewById(R.id.new_card_button);
        mNewCardEditText = (EditText) findViewById(R.id.new_card_message);
        mHtmlMenuItem = (MenuItem) findViewById(R.id.action_settings);

        mNewCardEditText.setHorizontallyScrolling(true);


        // Restore any saved instance state
        if (savedInstanceState != null) {
            onTokenResult(savedInstanceState.getString(PARAM_AUTH_TOKEN));
        } else {
            mStartAuthButton.setEnabled(true);
            mExpireTokenButton.setEnabled(false);
        }

        mStartAuthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Present the user with an account picker dialog with a list
                // of their Google accounts
                Intent intent = AccountPicker.newChooseAccountIntent(
                        null, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE},
                        false, null, null, null, null);
                startActivityForResult(intent, REQUEST_ACCOUNT_PICKER);
            }
        });

        mExpireTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(mAuthToken)) {
                    // Expire the token, if any
                    GoogleAuthUtil.invalidateToken(MainActivity.this, mAuthToken);
                    mAuthToken = null;
                    mExpireTokenButton.setEnabled(false);
                    mStartAuthButton.setEnabled(true);
                }
            }
        });

        mNewCardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createNewTimelineItem();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PARAM_AUTH_TOKEN, mAuthToken);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                if (htmlMode)
                {
                    htmlMode = false;
                    ((TextView) findViewById(R.id.cardMessageTextView)).setText(getString(R.string.textMessage));
                    mNewCardEditText.setHint(getString(R.string.textHint));
                    //mNewCardEditText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    //mNewCardEditText.setSingleLine(true);
                    //mNewCardEditText.setLines(1);
                    //mNewCardEditText.setMaxLines(1);
                    mNewCardEditText.setText(getString(R.string.textTemplate));
                } else {
                    htmlMode = true;
                    ((TextView) findViewById(R.id.cardMessageTextView)).setText(getString(R.string.htmlMessage));
                    mNewCardEditText.setHint(getString(R.string.htmlHint));
                    //mNewCardEditText.setSingleLine(false);
                    //mNewCardEditText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    //mNewCardEditText.setLines(5);
                    mNewCardEditText.setText(getString(R.string.htmlTemplate));


                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (RESULT_OK == resultCode) {
                    String account = data.getStringExtra(
                            AccountManager.KEY_ACCOUNT_NAME);
                    String type = data.getStringExtra(
                            AccountManager.KEY_ACCOUNT_TYPE);

                    // TODO: Cache the chosen account
                    Log.i(TAG, String.format("User selected account %s of type %s",
                            account, type));
                    fetchTokenForAccount(account);
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (RESULT_OK == resultCode) {
                    String token = data.getStringExtra(
                            AccountManager.KEY_AUTHTOKEN);

                    Log.i(TAG, String.format(
                            "Authorization request returned token %s", token));
                    onTokenResult(token);
                }
                break;
        }
    }

    private void createNewTimelineItem() {
        if (!TextUtils.isEmpty(mAuthToken)) {
            String message = mNewCardEditText.getText().toString();
            if (!TextUtils.isEmpty(message)) {
                try {
                    JSONObject notification = new JSONObject();
                    notification.put("level", "DEFAULT"); // Play a chime

                    JSONArray menuItems = new JSONArray();
                    JSONObject menuAction;

                    if (((CheckBox) findViewById(R.id.deleteCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "DELETE");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.shareCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "SHARE");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.readAloudCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "READ_ALOUD");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.togglePinnedCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "TOGGLE_PINNED");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.voiceCallCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "VOICE_CALL");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.navigateCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "NAVIGATE");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.replyCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "REPLY");
                        menuItems.put(menuAction);
                    }

                    if (((CheckBox) findViewById(R.id.replyAllCheckBox)).isChecked())
                    {
                        menuAction = new JSONObject();
                        menuAction.put("action", "REPLY_ALL");
                        menuItems.put(menuAction);
                    }

                    JSONObject json = new JSONObject();
                    if (htmlMode)
                        json.put("html", message);
                    else
                        json.put("text", message);

                    json.put("notification", notification);
                    json.put("menuItems", menuItems);

                    MirrorApiClient client = MirrorApiClient.getInstance(this);
                    client.createTimelineItem(mAuthToken, json, new MirrorApiClient.Callback() {
                        @Override
                        public void onSuccess(HttpResponse response) {
                            try {
                                Log.v(TAG, "onSuccess: " + EntityUtils.toString(response.getEntity()));
                            } catch (IOException e1) {
                                // Pass
                            }
                            Toast.makeText(MainActivity.this, "Created new timeline item",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(HttpResponse response, Throwable e) {
                            try {
                                Log.v(TAG, "onFailure: " + EntityUtils.toString(response.getEntity()));
                            } catch (IOException e1) {
                                // Pass
                            }
                            Toast.makeText(MainActivity.this, "Failed to create new timeline item",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (JSONException e) {
                    Toast.makeText(this, "Sorry, can't serialize that to JSON",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Sorry, can't create an empty timeline item",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Sorry, can't create a new timeline card without a token",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onTokenResult(String token) {
        Log.d(TAG, "onTokenResult: " + token);
        if (!TextUtils.isEmpty(token)) {
            mAuthToken = token;
            mExpireTokenButton.setEnabled(true);
            mStartAuthButton.setEnabled(false);
            Toast.makeText(this, "New token result", Toast.LENGTH_SHORT).show();
        } else {
            mExpireTokenButton.setEnabled(false);
            mStartAuthButton.setEnabled(true);
            Toast.makeText(this, "Sorry, invalid token result", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchTokenForAccount(final String account) {
        // We fetch the token on a background thread otherwise Google Play
        // Services will throw an IllegalStateException
        sThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // If this returns immediately the OAuth framework thinks
                    // the token should be usable
                    final String token = GoogleAuthUtil.getToken(
                            MainActivity.this, account, SCOPE);

                    if (token != null) {
                        // Pass the token back to the UI thread
                        Log.i(TAG, String.format("getToken returned token %s", token));
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onTokenResult(token);
                            }
                        });
                    }
                } catch (final UserRecoverableAuthException e) {
                    // This means that the app hasn't been authorized by the user for access
                    // to the scope, so we're going to have to fire off the (provided) Intent
                    // to arrange for that. But we only want to do this once. Multiple
                    // attempts probably mean the user said no.
                    Log.i(TAG, "Handling a UserRecoverableAuthException");

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                        }
                    });
                } catch (IOException e) {
                    // Something is stressed out; the auth servers are by definition
                    // high-traffic and you can't count on 100% success. But it would be
                    // bad to retry instantly, so back off
                    Log.e(TAG, "Failed to fetch auth token!", e);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Failed to fetch token, try again later", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (GoogleAuthException e) {
                    // Can't recover from this!
                    Log.e(TAG, "Failed to fetch auth token!", e);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Failed to fetch token, can't recover", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }
}
