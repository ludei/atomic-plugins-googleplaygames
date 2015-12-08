package com.ludei.googleplaygames;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.PlusShare;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.auth.GoogleAuthUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class GPGService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    public class Session {

        public String accessToken;
        public String playerId;
        public String[] scopes;
        public long expirationDate;


        public Session(String token, String[] scopes, String playerId, long expirationDate)
        {
            this.accessToken = token;
            this.scopes = scopes;
            this.playerId = playerId;
            this.expirationDate = expirationDate;
        }

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("access_token", accessToken);
            String scope = GPUtils.scopeArrayToString(scopes);
            dic.put("state", scope);
            dic.put("playerId", playerId);
            dic.put("expirationDate", expirationDate);
            return dic;
        }
    }
    public class Error {
        public String message;
        public int code;

        Error(String msg, int code) {
            this.message = msg;
            this.code = code;
        }

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("code", code);
            dic.put("message", message);
            return dic;
        }
    }

    public static class ShareData {
        public String url;
        public String message;
    }

    public interface SessionCallback {
        void onComplete(Session session, Error error);
    }

    public interface CompletionCallback {
        void onComplete(Error error);
    }

    public interface RequestCallback {
        void onComplete(JSONObject responseJSON, Error error);
    }

    public interface WillStartActivityCallback {
        void onWillStartActivity();
    }


    private static final int GP_DIALOG_REQUEST_CODE = 0x000000000001112;
    private static final int RESOLUTION_REQUEST_CODE = 0x00000000001113;
    private static final String GP_SIGNED_IN_PREFERENCE = "gp_signedIn";

    private static GPGService sharedInstance = null;

    //static values used to communicate with Ludei's Google Play Multiplayer Service
    public static Runnable onConnectedCallback;
    public static Invitation multiplayerInvitation;
    public static GoogleApiClient getGoogleAPIClient() {
        return sharedInstance != null? sharedInstance.client : null;
    }

    protected Activity activity;
    protected GoogleApiClient client;
    protected String[] scopes = new String[]{Scopes.GAMES, Scopes.PLUS_LOGIN};
    protected CompletionCallback intentCallback;
    protected ArrayList<SessionCallback> loginCallbacks = new ArrayList<SessionCallback>();
    protected String authToken;
    protected SessionCallback sessionListener;
    protected WillStartActivityCallback willStartListener;
    protected boolean trySilentAuthentication = false;
    protected Executor executor;

    public GPGService(Activity activity)
    {
        sharedInstance = this;
        this.activity = activity;
        this.trySilentAuthentication = this.activity.getPreferences(Activity.MODE_PRIVATE).getBoolean(GP_SIGNED_IN_PREFERENCE, false);
    }

    public void init()
    {
        this.createClient();
        if (this.trySilentAuthentication) {
            client.connect();
        }
    }

    public void destroy()
    {
        this.activity = null;
        sharedInstance = null;
    }

    public void setSessionListener(SessionCallback listener)
    {
        this.sessionListener = listener;
    }

    public void setWillStartActivityListener(WillStartActivityCallback listener)
    {
        this.willStartListener = listener;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public boolean handleActivityResult(final int requestCode, final int resultCode, final Intent data) {

        boolean managed = false;
        if ((requestCode == RESOLUTION_REQUEST_CODE || requestCode == GP_DIALOG_REQUEST_CODE) &&
                resultCode == GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED) {

            //User signed out from the achievements/leaderboards settings in the upper right corner
            this.logout(null);
            if (intentCallback != null) {
                intentCallback.onComplete(new Error("User signed out",  GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED));
                intentCallback = null;
            }
            managed = true;

        }
        else if (requestCode == RESOLUTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (client == null) {
                    this.createClient();
                }
                client.connect();
            }
            else {
                String errorString = resultCode == Activity.RESULT_CANCELED ? null : GPUtils.activityResponseCodeToString(resultCode);
                processSessionChange(null, resultCode, errorString);
            }
            managed = true;

        }
        else if (requestCode == GP_DIALOG_REQUEST_CODE) {

            if (intentCallback != null) {
                Error error = resultCode != Activity.RESULT_OK ? null : new Error("resultCode: " + resultCode, resultCode);
                intentCallback.onComplete(error);
                intentCallback = null;
            }
            managed = true;

        }
        return managed;
    }

    private void createClient()
    {
        if (client != null) {
            client.unregisterConnectionCallbacks(this);
            client.unregisterConnectionFailedListener(this);
        }

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(activity, this, this);
        builder.addApi(Games.API);
        builder.addScope(Games.SCOPE_GAMES);
        builder.addApi(Plus.API);
        builder.addScope(Plus.SCOPE_PLUS_LOGIN);
        builder.addScope(Plus.SCOPE_PLUS_PROFILE);

        client = builder.build();
    }

    private void processSessionChange(String authToken, int errorCode, String errorMessage)
    {
        this.authToken = authToken;

        Session session = authToken != null ? new Session(authToken, scopes, getMyId(), 0) : null;
        Error error = errorMessage != null ? new Error(errorMessage, errorCode) : null;

        if (this.sessionListener != null) {
            this.sessionListener.onComplete(session, error);
        }


        ArrayList<SessionCallback> callbacks;
        synchronized (loginCallbacks) {
            callbacks = (ArrayList<SessionCallback>)loginCallbacks.clone();
            loginCallbacks.clear();
        }

        for (SessionCallback cb: callbacks) {
            cb.onComplete(session, error);
        }
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult connectionResult)
    {

        int code = connectionResult.getErrorCode();
        if (code == ConnectionResult.SIGN_IN_REQUIRED || code == ConnectionResult.RESOLUTION_REQUIRED) {
            try {
                this.notifyWillStart();
                connectionResult.startResolutionForResult(activity,RESOLUTION_REQUEST_CODE);
            }
            catch (Exception ex)
            {
                processSessionChange(null, connectionResult.getErrorCode(), GPUtils.errorCodeToString(connectionResult.getErrorCode()) + " " + ex.getMessage());
            }
        }
        else {
            processSessionChange(null, connectionResult.getErrorCode(), GPUtils.errorCodeToString(connectionResult.getErrorCode()));
        }
    }

    @Override
    public void onConnected(android.os.Bundle bundle)
    {
        if (trySilentAuthentication == false) {
            trySilentAuthentication = true;
            activity.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean(GP_SIGNED_IN_PREFERENCE, true).commit();
        }

        AsyncTask<Void, Void, Object> task = new AsyncTask<Void, Void, Object>() {

            @Override
            protected Object doInBackground(Void... params) {
                try
                {
                    String scope = "oauth2:" + GPUtils.scopeArrayToString(GPGService.this.scopes);
                    String token = GoogleAuthUtil.getToken(GPGService.this.activity, Plus.AccountApi.getAccountName(client), scope);
                    return token;
                }
                catch (Exception ex)
                {
                    return ex;
                }
            }

            @Override
            protected void onPostExecute(Object info) {

                if (info instanceof Exception) {
                    Exception ex = (Exception) info;
                    GPGService.this.processSessionChange(null, 0, ex.getLocalizedMessage());
                    client.disconnect();
                }
                else {
                    GPGService.this.processSessionChange(info.toString(), 0, null);
                }
            }

        };

        if (this.executor != null) {
            task.executeOnExecutor(executor);
        }
        else  {
            task.execute();
        }


        //check connection bundle for multiplayer invitations
        if (bundle != null) {
            Invitation inv = bundle.getParcelable(Multiplayer.EXTRA_INVITATION);
            if (inv != null) {
                multiplayerInvitation = inv;
            }
        }

        if (onConnectedCallback != null) {
            onConnectedCallback.run();
        }

    }

    @Override
    public void onConnectionSuspended(int i)
    {
        processSessionChange(null, 0, null);
    }


    public boolean isLoggedIn()
    {
        return client != null && client.isConnected() && authToken != null;
    }

    private String getMyId() {
        if (isLoggedIn()) {
            try {
                Person p = Plus.PeopleApi.getCurrentPerson(client);
                if (p != null) {
                    return p.getId();
                }

            } catch(IllegalStateException e) {
                e.printStackTrace();
            }
        }

        return "";

    }
    public GPGService.Session getSession() {

        if (authToken != null && client != null) {
            return new GPGService.Session(authToken, scopes, getMyId(), 0);
        }
        return null;
    }

    public void login(String[] userScopes, SessionCallback callback)
    {

        if (this.isLoggedIn()) {
            if (callback != null) {
                callback.onComplete(this.getSession(), null);
            }
            return;
        }

        synchronized (loginCallbacks) {
            this.loginCallbacks.add(callback);
        }

        if (this.client != null && client.isConnecting()) {
            return; //already connecting
        }

        //check if a user wants a scope that it not already set in the GameClient

        if (userScopes != null) {
            for (String str: userScopes) {
                if (!GPUtils.contains(this.scopes, str)) {
                    //user wants a new scope, recreate the Game client
                    this.scopes = userScopes;
                    this.createClient();
                    break;
                }
            }
        }

        if (client == null) {
            this.createClient();
        }

        client.connect();
    }

    public void logout(CompletionCallback callback) {
        if (client != null && client.isConnected()) {
            client.disconnect();

            if (trySilentAuthentication == true) {
                trySilentAuthentication = false;
                activity.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean(GP_SIGNED_IN_PREFERENCE, false);
            }

            if (this.sessionListener != null) {
                this.sessionListener.onComplete(null, null);
            }

            if (callback != null) {
                callback.onComplete(null);
            }
        }
        else {
            if (callback != null) {
                callback.onComplete(null);
            }
        }
    }

    public void request(String path,final String method,  JSONObject params, final byte[] body, final Map<String, String> headers, final RequestCallback callback) throws JSONException {

        if (!this.client.isConnected()) {

            if (callback != null) {
                callback.onComplete(null, new Error("User is not logged into Google Play Game Services", 0));
            }
            return;
        }


        if (path.startsWith("/")) {
            path = "https://www.googleapis.com" + path;
        }

        if (params != null) {
            String query = "";
            Iterator<String> it = params.keys();
            while (it.hasNext()) {
                if (query.length() == 0)
                    query+="&";
                String key = it.next();
                query+= key + "=" + params.get(key).toString();
            }
            path+= "?" + query;
        }

        final String absolutePath = path;

        AsyncTask<Void, Void, Object> task = new AsyncTask<Void, Void, Object>() {

            @Override
            protected Object doInBackground(Void... params) {
                HttpURLConnection connection = null;

                try {
                    URL url = new URL(absolutePath);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Authorization", "Bearer " + GPGService.this.authToken);
                    connection.setRequestMethod(method);

                    if (headers != null) {
                        for (String key : headers.keySet())
                        {
                            connection.setRequestProperty(key, headers.get(key));
                        }
                    }

                    if (body != null)
                    {
                        connection.setFixedLengthStreamingMode(body.length);
                        connection.setDoOutput(true);
                        if (connection.getRequestProperty("Content-Type") == null)
                            connection.setRequestProperty("Content-Type",  "text/plain;charset=UTF-8");

                        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
                        OutputStream output = null;
                        try
                        {
                            output = connection.getOutputStream();
                            output.write(body);
                        }
                        finally
                        {
                            if (output != null) {
                                output.close();
                            }
                        }
                    }

                    int statusCode = connection.getResponseCode();
                    InputStream inputStream;
                    if (statusCode >= 200 && statusCode < 300) {
                        inputStream = connection.getInputStream();
                    } else {
                        inputStream = connection.getErrorStream();
                    }

                    String content = GPUtils.convertStreamToString(inputStream);

                    JSONObject result = new JSONObject(content);
                    return result;
                }
                catch (Exception e) {
                    return new Error(e.getLocalizedMessage(), 0);
                }
                finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }

            @Override
            protected void onPostExecute(Object info) {
                if (callback == null) {
                    return;
                }
                if (info == null) {
                    callback.onComplete(null, null);
                }
                else if (info instanceof Error) {
                    callback.onComplete(null, (Error)info);
                }
                else {
                    callback.onComplete((JSONObject)info, null);
                }
            }

        };

        if (this.executor != null) {
            task.executeOnExecutor(executor);
        }
        else  {
            task.execute();
        }


    }

    public void showLeaderboard(String leaderboard, CompletionCallback callback)
    {
        if (intentCallback != null) {
            if (callback != null) {
                callback.onComplete(new Error("Intent already running", 0));
            }
            return;
        }

        intentCallback = callback;
        notifyWillStart();
        if (leaderboard == null || leaderboard.length() == 0) {
            activity.startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(client), GP_DIALOG_REQUEST_CODE);
        }
        else {
            activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(client,leaderboard), GP_DIALOG_REQUEST_CODE);
        }
    }

    public void showAchievements(CompletionCallback callback)
    {
        if (intentCallback != null) {
            if (callback != null) {
                callback.onComplete(new Error("Intent already running", 0));
            }
            return;
        }

        intentCallback = callback;
        notifyWillStart();
        activity.startActivityForResult(Games.Achievements.getAchievementsIntent(client), GP_DIALOG_REQUEST_CODE);
    }

    public void unlockAchievement(String achievementID, boolean showNotification, final CompletionCallback callback)
    {
        if (callback != null) {
            PendingResult result = Games.Achievements.unlockImmediate(client, achievementID);
            result.setResultCallback(new ResultCallback() {
                @Override
                public void onResult(Result result) {
                    int statusCode = result.getStatus().getStatusCode();
                    final Error error = statusCode == GamesStatusCodes.STATUS_OK ? null : new Error(GamesStatusCodes.getStatusString(statusCode), statusCode);
                    callback.onComplete(error);
                }
            });
        }
        else {
            Games.Achievements.unlock(client, achievementID);
        }
    }

    public void shareMessage(ShareData data, CompletionCallback callback)
    {
        if (intentCallback != null) {
            if (callback != null) {
                callback.onComplete(new Error("Intent already running", 0));
            }
            return;
        }

        this.intentCallback = callback;
        PlusShare.Builder builder = new PlusShare.Builder(this.activity)
                .setType("text/plain")
                .setText(data.message);
        if (data.url != null && data.url.length() > 0) {
            builder.setContentUrl(Uri.parse(data.url));
        }
        notifyWillStart();
        activity.startActivityForResult(builder.getIntent(), GP_DIALOG_REQUEST_CODE);
    }


    private void notifyWillStart()
    {
        if (this.willStartListener != null) {
            this.willStartListener.onWillStartActivity();
        }
    }
}
