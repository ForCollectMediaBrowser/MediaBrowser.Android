package com.mb.android.ui.main;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mb.android.DialogFragments.IncognitoLoginDialogFragment;
import com.mb.android.DialogFragments.LoginPasswordDialogFragment;
import com.mb.android.DialogFragments.ServerConnectionDialogFragment;
import com.mb.android.MainApplication;
import com.mb.android.interfaces.IServerDialogClickListener;
import com.mb.android.logging.AppLogger;
import com.mb.android.ui.mobile.homescreen.HomescreenActivity;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.mb.android.R;
import mediabrowser.apiinteraction.ConnectionResult;
import mediabrowser.apiinteraction.Response;
import mediabrowser.apiinteraction.android.AndroidApiClient;
import mediabrowser.model.apiclient.ServerInfo;
import mediabrowser.model.dto.UserDto;
import mediabrowser.model.net.HttpException;
import mediabrowser.model.users.AuthenticationResult;


public class ConnectionActivity extends FragmentActivity implements IServerDialogClickListener, ILoginDialogListener {

    private GridView mContentGrid;
    private LinearLayout mHeader;
    private TextView mHeaderText;
    private Button mChangeServerButton;
    private Button mLoginIncognitoButton;
    private UserDto[] mUsers;
    private AlertDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showWelcomeScreen = preferences.getBoolean("is_first_run", true);

        if (showWelcomeScreen && isKnownTVDevice()) {
            preferences.edit().putString("pref_application_profile", "Television").apply();
        }

        boolean showServerSelection = false;
        boolean showUserSelection = false;

        Intent mainIntent = getIntent();
        if (mainIntent != null) {
            showWelcomeScreen = mainIntent.getBooleanExtra("show_welcome", showWelcomeScreen);
            showServerSelection = mainIntent.getBooleanExtra("show_servers", false);
            showUserSelection = mainIntent.getBooleanExtra("show_users", false);
        }

        if (showWelcomeScreen) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            this.finish();
        } else {
            setContentView(R.layout.activity_connection);
            setOverscanValues();
            mContentGrid = (GridView) findViewById(R.id.gvContent);
            mHeader = (LinearLayout) findViewById(R.id.llContent);
            mHeaderText = (TextView) findViewById(R.id.tvHeaderText);
            mLoginIncognitoButton = (Button) findViewById(R.id.ivIncognito);
            mLoginIncognitoButton.setOnClickListener(onLoginIncognitoClick);
            mChangeServerButton = (Button) findViewById(R.id.ivChangeServer);
            mChangeServerButton.setOnClickListener(onChangeServerClick);
            Button connectSignInButton = (Button) findViewById(R.id.btnConnect);
            connectSignInButton.setOnClickListener(onMbConnectClick);

            // Always show debug logging during initial connection
            AppLogger.setDebugLoggingEnabled(true);
            if (showServerSelection) {
                onServerSelection();
            } else if (showUserSelection) {
                mChangeServerButton.setOnClickListener(onChangeServerClick);
                updateHeader(getResources().getString(R.string.select_mb_user), false);
                if (MainApplication.getInstance().API != null) {
                    MainApplication.getInstance().API.GetPublicUsersAsync(getPublicUsersResponse);
                }
            } else {

                showActivityDialog("Connecting");

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        MainApplication.getInstance().getConnectionManager().Connect(connectionResponse);
                    }
                };
                thread.start();
            }
        }
    }

    private Response<ConnectionResult> connectionResponse = new Response<ConnectionResult>() {
        @Override
        public void onResponse(ConnectionResult result) {

            switch (result.getState()) {
                case Unavailable:
                    ConnectionActivity.this.onUnavailable(result);
                    break;
                case ServerSelection:
                    ConnectionActivity.this.onServerSelection();
                    break;
                case ServerSignIn:
                    ConnectionActivity.this.onServerSignIn(result);
                    break;
                case SignedIn:
                    ConnectionActivity.this.onSignedIn(result);
                    break;
                case ConnectSignIn:
                    ConnectionActivity.this.onServerSelection();
                    break;
            }
        }
    };

    private void onUnavailable(ConnectionResult result) {

        // Connection to server failed
        AppLogger.getLogger().Info("**** UNAVAILABLE ***");
        dismissActivityDialog();

        Toast.makeText(this, "Server Unreachable", Toast.LENGTH_LONG).show();
    }

    private void onServerSelection() {
        // Multiple servers available
        // Display a selection screen
        AppLogger.getLogger().Info("**** SERVER SELECTION ****");
        MainApplication.getInstance().getConnectionManager().GetAvailableServers(getAvailableServersResponse);
    }

    private void onServerSignIn(ConnectionResult result) {
        // A server was found and the user needs to login.
        // Display a login screen and authenticate with the server using result.ApiClient
        AppLogger.getLogger().Info("**** SERVER SIGN IN ****");
        dismissActivityDialog();

        showUserSelection(result);
    }

    private void onSignedIn(ConnectionResult result) {
        // A server was found and the user has been signed in using previously saved credentials.
        // Ready to browse using result.ApiClient
        AppLogger.getLogger().Info("**** SIGNED IN ****");
        dismissActivityDialog();
        MainApplication.getInstance().API = (AndroidApiClient)result.getApiClient();
        MainApplication.getInstance().user = new UserDto();
        MainApplication.getInstance().user.setId(MainApplication.getInstance().API.getCurrentUserId());

        proceedToHomescreen();
    }

    private Response<ArrayList<ServerInfo>> getAvailableServersResponse = new Response<ArrayList<ServerInfo>>() {
        @Override
        public void onResponse(ArrayList<ServerInfo> servers) {
            dismissActivityDialog();
            showServerSelection(servers);
        }
        @Override
        public void onError(Exception e) {
            dismissActivityDialog();
            showServerSelection(new ArrayList<ServerInfo>());
        }
    };


    private void showServerSelection(final ArrayList<ServerInfo> servers) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                AppLogger.getLogger().Debug("Updating header buttons for server selection");
                mChangeServerButton.setOnClickListener(onAddServerClick);
                updateHeader(getResources().getString(R.string.select_mb_server), true);

                AppLogger.getLogger().Debug("Creating server list");
                AppLogger.getLogger().Debug(String.valueOf(servers.size()) + " servers to display");

                mContentGrid.setAdapter(new ServerAdapter(servers, ConnectionActivity.this, null));
                mContentGrid.setOnItemClickListener(onServerClick);
            }
        });
    }

    private void showUserSelection(final ConnectionResult result) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                AppLogger.getLogger().Debug("Updating header buttons for user selection");
                mChangeServerButton.setOnClickListener(onChangeServerClick);
                updateHeader(getResources().getString(R.string.select_mb_user), false);
                if (result.getApiClient() != null) {
                    MainApplication.getInstance().API = (AndroidApiClient)result.getApiClient();
                    result.getApiClient().GetPublicUsersAsync(getPublicUsersResponse);
                }
            }
        });
    }

    private Response<UserDto[]> getPublicUsersResponse = new Response<UserDto[]>() {
        @Override
        public void onResponse(UserDto[] users) {
            AppLogger.getLogger().Debug("Get public users response received");
            if (users == null) {
                users = new UserDto[0];
            }
            mUsers = users;
            buildUserGrid();
        }
        @Override
        public void onError(Exception ex) {
            AppLogger.getLogger().Debug("Error handled getting public users: " + ex);
            mUsers = new UserDto[0];
            buildUserGrid();
        }
    };

    private void buildUserGrid() {
        AppLogger.getLogger().Debug("Building user selection grid");
        AppLogger.getLogger().Debug(String.valueOf(mUsers.length) + " public users to display");
        mContentGrid.setAdapter(new UserAdapter(mUsers));
        mContentGrid.setOnItemClickListener(onUserClick);
    }

    private void updateHeader(final String text, final boolean isServerSelectionVisible) {
        mHeader.setVisibility(View.VISIBLE);
        mHeaderText.setText(text);
        if (isServerSelectionVisible) {
            mLoginIncognitoButton.setVisibility(View.INVISIBLE);
        } else {
            mLoginIncognitoButton.setVisibility(View.VISIBLE);
        }
    }

    private AdapterView.OnItemClickListener onServerClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            AppLogger.getLogger().Debug("Server at position " + String.valueOf(i) + " clicked");
            ServerInfo server = (ServerInfo) adapterView.getItemAtPosition(i);
            if (server == null) {
                AppLogger.getLogger().Debug("Adapter returned null for Server at position " + String.valueOf(i));
                return;
            }
            AppLogger.getLogger().Debug("Passing server to ConnectionManager");
            MainApplication.getInstance().getConnectionManager().Connect(server, connectionResponse);
            showActivityDialog(!tangible.DotNetToJavaStringHelper.isNullOrEmpty(server.getName()) ? "Connecting to " + server.getName() : "Connecting");
        }
    };

    private AdapterView.OnItemClickListener onUserClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            AppLogger.getLogger().Debug("User at position " + String.valueOf(i) + " clicked");
            UserDto user = (UserDto) adapterView.getItemAtPosition(i);
            if (user == null) {
                AppLogger.getLogger().Debug("Adapter returned null for User at position " + String.valueOf(i));
                return;
            }
            if (user.getHasPassword()) {
                AppLogger.getLogger().Debug("User requires password: Showing login dialog");
                showLoginDialog(user);
            } else {
                AppLogger.getLogger().Debug("User does not require a password: Logging in.");
                onLoginDialogPositiveButtonClick(user, "");
            }
        }
    };

    private void showLoginDialog(UserDto user) {

        LoginPasswordDialogFragment dialog = new LoginPasswordDialogFragment();
        dialog.setUser(user);
        dialog.show(ConnectionActivity.this.getSupportFragmentManager(), "LoginDialog");
        AppLogger.getLogger().Debug("Login dialog visible");
    }

    //******************************************************************************************************************
    // Server Connection methods
    //******************************************************************************************************************

    private View.OnClickListener onAddServerClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            AppLogger.getLogger().Debug("Showing manual server entry dialog");
            DialogFragment dialog = new ServerConnectionDialogFragment();
            dialog.show(ConnectionActivity.this.getSupportFragmentManager(), "ServerConnectionDialog");
            AppLogger.getLogger().Debug("Dialog visible");
        }
    };

    private View.OnClickListener onChangeServerClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            ConnectionActivity.this.onServerSelection();
        }
    };

    @Override
    public void onOkClick(String address) {
        AppLogger.getLogger().Debug("Attempting to manually connect to " + address);
        MainApplication.getInstance().getConnectionManager().Connect(address, connectionResponse);
    }

    @Override
    public void onCancelClick() {

    }

    private View.OnClickListener onMbConnectClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AppLogger.getLogger().Debug("Switching to MbConnect login activity");
            Intent intent = new Intent(ConnectionActivity.this, MbConnectActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    };

    //******************************************************************************************************************
    // User Login methods
    //******************************************************************************************************************

    private View.OnClickListener onLoginIncognitoClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            DialogFragment dialog = new IncognitoLoginDialogFragment();
            dialog.show(ConnectionActivity.this.getSupportFragmentManager(), "IncognitoLoginDialog");
        }
    };

    @Override
    public void onLoginDialogPositiveButtonClick(UserDto user, String password) {
        onLoginDialogPositiveButtonClick(user.getName(), password);
    }

    @Override
    public void onLoginDialogPositiveButtonClick(String username, String password) {
        try {
            AppLogger.getLogger().Debug("Attempting to log in user " + username);
            MainApplication.getInstance().API.AuthenticateUserAsync(username, password, authenticationResultResponse);
            showActivityDialog("Logging In");
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            AppLogger.getLogger().ErrorException("Error handled attempting login request: ", e);
            e.printStackTrace();
        }
    }

    @Override
    public void onLoginDialogNegativeButtonClick() {

    }

    private Response<AuthenticationResult> authenticationResultResponse = new Response<AuthenticationResult>() {
        @Override
        public void onResponse(AuthenticationResult result) {
            AppLogger.getLogger().Debug("AuthenticationResult received");
            if (result == null || result.getUser() == null) {
                AppLogger.getLogger().Debug("AuthenticationResult or AuthenticationResult.User is null");
                return;
            }
            dismissActivityDialog();
            MainApplication.getInstance().user = result.getUser();

            proceedToHomescreen();
        }
        @Override
        public void onError(Exception ex) {
            dismissActivityDialog();
            AppLogger.getLogger().Debug("Error handled authenticating user");
            try {
                HttpException exception = (HttpException) ex;
                if (exception.getStatusCode() != null && exception.getStatusCode() == 401) {
                    Toast.makeText(ConnectionActivity.this, "Error logging in. Possibly incorrect password", Toast.LENGTH_LONG).show();
                    AppLogger.getLogger().Debug("Login failure: Incorrect password");
                    return;
                }
            } catch (ClassCastException cce) {
                // silently fall through.
            }
            Toast.makeText(ConnectionActivity.this, "Error logging in. Please try again later", Toast.LENGTH_LONG).show();
            AppLogger.getLogger().ErrorException("Exception handled: ", ex);
        }
    };

    //******************************************************************************************************************

    private void proceedToHomescreen() {

        AppLogger.getLogger().Debug("ensuring is_first_run is now false");
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.edit().putBoolean("is_first_run", false).apply();

        // Restore logging to the level defined in the user preferences
        AppLogger.setDebugLoggingEnabled(sharedPrefs.getBoolean("pref_debug_logging_enabled", false));

        MainApplication.getInstance().startContentSync();

        Intent intent;
        AppLogger.getLogger().Info("proceeding to mobile homescreen");
        // proceed to the mobile layouts
        intent = new Intent(this, HomescreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    protected void setOverscanValues() {
        RelativeLayout overscanLayout = (RelativeLayout) findViewById(R.id.rlOverscanPadding);

        if (overscanLayout == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int left = prefs.getInt("overscan_left", 0);
        int top = prefs.getInt("overscan_top", 0);
        int right = prefs.getInt("overscan_right", 0);
        int bottom = prefs.getInt("overscan_bottom", 0);

        ViewGroup.MarginLayoutParams overscanMargins = (ViewGroup.MarginLayoutParams) overscanLayout.getLayoutParams();
        overscanMargins.setMargins(left, top, right, bottom);
        overscanLayout.requestLayout();
    }

    private boolean isKnownTVDevice() {

        if (Build.MANUFACTURER != null && Build.MANUFACTURER.equalsIgnoreCase("amazon")) {
            if (Build.MODEL != null && !Build.MODEL.isEmpty()) {
                if (Build.MODEL.startsWith("AFT"))
                    return true;
            }
        }

        return false;
    }

    private void showActivityDialog(String message) {

        dialog = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.app_name))
                .setMessage(message)
                .setCancelable(false)
                .create();

        dialog.show();
    }

    private void dismissActivityDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
     public void onDestroy(){
        super.onDestroy();
        dismissActivityDialog();
    }
}
