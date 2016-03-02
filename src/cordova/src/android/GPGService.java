package com.ludei.googleplaygames;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.PlusShare;
import com.google.android.gms.plus.model.people.Person;

import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;

public class GPGService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    public class Session {

        public String accessToken;
        public String playerId;
        public ArrayList<String> scopes;
        public long expirationDate;


        public Session(String token, ArrayList<String> scopes, String playerId, long expirationDate)
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

    public static class GameSnapshot {
        public String identifier;
        public String title;
        public String description;
        public String data;

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("identifier", identifier);
            dic.put("description", description);
            dic.put("data", data);
            return dic;
        }

        public static GameSnapshot fromJSONObject(JSONObject obj) {
            GameSnapshot data = new GameSnapshot();
            data.identifier = obj.optString("identifier");
            data.description = obj.optString("description");
            data.data = obj.optString("data");
            return data;
        }

        public static GameSnapshot fromMetadata(SnapshotMetadata metadada, byte[] bytes) {
            GameSnapshot data = new GameSnapshot();
            data.identifier = metadada.getUniqueName();
            data.description = metadada.getDescription();
            data.data = bytes  != null ? new String(bytes) : "";
            return data;
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

    public interface SavedGameCallback {
        void onComplete(GameSnapshot data, Error error);
    }

    public interface RequestCallback {
        void onComplete(JSONObject responseJSON, Error error);
    }

    public interface WillStartActivityCallback {
        void onWillStartActivity();
    }


    private static final int GP_DIALOG_REQUEST_CODE = 0x000000000001112;
    private static final int RESOLUTION_REQUEST_CODE = 0x00000000001113;
    private static final int GP_ERROR_DIALOG_REQUEST_CODE = 0x00000000001114;
    private static final int GP_SAVED_GAMES_REQUEST_CODE = 0x000000000001117;
    private static final String GP_SIGNED_IN_PREFERENCE = "gp_signedIn";

    private static GPGService sharedInstance = null;

    //static values used to communicate with Ludei's Google Play Multiplayer Service
    public static Runnable onConnectedCallback;
    public static Invitation multiplayerInvitation;
    public static GoogleApiClient getGoogleAPIClient() {
        return sharedInstance != null? sharedInstance.client : null;
    }
    public static GPGService currentInstance() {
        return sharedInstance;
    }

    protected Activity activity;
    protected GoogleApiClient client;
    protected static final String[] defaultScopes = new String[]{Scopes.GAMES, Scopes.PLUS_LOGIN};
    protected ArrayList<String> scopes = new ArrayList<String>();
    protected CompletionCallback intentCallback;
    protected CompletionCallback permissionsCallback;
    protected SavedGameCallback intentSavedGameCallback;
    protected ArrayList<SessionCallback> loginCallbacks = new ArrayList<SessionCallback>();
    protected String authToken;
    protected SessionCallback sessionListener;
    protected WillStartActivityCallback willStartListener;
    protected boolean trySilentAuthentication = false;
    protected Executor executor;
    protected Runnable errorDialogCallback;

    protected Runnable requestPermission = null;

    public GPGService(Activity activity)
    {
        sharedInstance = this;
        this.activity = activity;
        this.trySilentAuthentication = this.activity.getPreferences(Activity.MODE_PRIVATE).getBoolean(GP_SIGNED_IN_PREFERENCE, false);
        this.requestPermission = new Runnable() {
            @Override
            public void run() {
                GPGService.this.requestPermission();
            }
        };
    }

    public void init() {
        this.init(null);
    }
    public void init(String[] extraScopes)
    {
        for (String scope : defaultScopes) {
            this.scopes.add(scope);
        }
        if (extraScopes != null) {
            for (String scope : extraScopes) {
                String value = GPUtils.mapScope(scope);
                if (!this.scopes.contains(value)) {
                    this.scopes.add(value);
                }
            }
        }
        if (this.isAvailable()) {
            this.createClient();
            if (this.trySilentAuthentication) {
                client.connect();
            }
        }
    }

    public boolean isAvailable() {
        return  GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.activity) == ConnectionResult.SUCCESS;
    }

    public void destroy()
    {
        this.activity = null;
        sharedInstance = null;
    }

    public void setRequestPermission(Runnable task) {
        requestPermission = task;
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

    public boolean handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == GPGService.REQUEST_PERMISSIONS_GET_ACCOUNTS) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                permissionsCallback.onComplete(new Error("GET_ACCOUNTS permission not granted", -1));

            } else {
                permissionsCallback.onComplete(null);
            }

            return true;
        }

        return false;
    }

    public boolean handleActivityResult(final int requestCode, final int resultCode, final Intent intent) {

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
        else if (requestCode == GP_ERROR_DIALOG_REQUEST_CODE) {
            if (errorDialogCallback != null) {
                errorDialogCallback.run();
            }
        }
        else if (requestCode == GP_SAVED_GAMES_REQUEST_CODE && intentSavedGameCallback != null) {
            if (resultCode == Activity.RESULT_CANCELED) {
                intentSavedGameCallback.onComplete(null, null);
            }
            else if (resultCode != Activity.RESULT_OK) {
                intentSavedGameCallback.onComplete(null, new Error("resultCode: " + resultCode, resultCode));
            }
            if (intent.hasExtra(Snapshots.EXTRA_SNAPSHOT_METADATA)) {
                // Load a snapshot.
                SnapshotMetadata snapshotMetadata = (SnapshotMetadata)intent.getParcelableExtra(Snapshots.EXTRA_SNAPSHOT_METADATA);
                GameSnapshot snapshot = GameSnapshot.fromMetadata(snapshotMetadata, null);
                intentSavedGameCallback.onComplete(snapshot, null);
            }
            else if (intent.hasExtra(Snapshots.EXTRA_SNAPSHOT_NEW)) {
                intentSavedGameCallback.onComplete(new GameSnapshot(), null);
            }
            intentSavedGameCallback = null;
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
        //TODO: better way to handle extra scopes
        if (scopes != null) {
            for (String str: scopes) {
                if (str.equalsIgnoreCase(Drive.SCOPE_APPFOLDER.toString()) || str.toLowerCase().contains("appfolder")) {
                    builder.addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER);
                }
            }
        }

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
            callbacks = new ArrayList<SessionCallback>(loginCallbacks);
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

    public void login(final String[] userScopes, final SessionCallback callback)
    {

        final int errorCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.activity);
        if (errorCode != ConnectionResult.SUCCESS) {

            if (GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)) {
                errorDialogCallback = new Runnable() {
                    @Override
                    public void run() {
                        if (isAvailable()) {
                            login(userScopes, callback);
                        }
                        else if (callback != null) {
                            callback.onComplete(null, null);
                        }
                    }
                };
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, GP_ERROR_DIALOG_REQUEST_CODE);
                        dialog.setCancelable(true);
                        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                errorDialogCallback = null;
                                if (callback != null) {
                                    callback.onComplete(null, null);
                                }
                            }
                        });
                        dialog.show();
                    }
                });

            }
            else if (callback != null) {
                callback.onComplete(null, new Error(GPUtils.errorCodeToString(errorCode), errorCode));
            }
            return;
        }

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

        /*boolean recreateClient = false;
        if (userScopes != null) {
            for (String scope: userScopes) {
                String value = GPUtils.mapScope(scope);
                if (!this.scopes.contains(value)) {
                    this.scopes.add(value);
                    recreateClient = true;
                }

            }
        }*/

        if (client == null) { // || recreateClient) {
            this.createClient();
        }

        if (hasPermission()) {
            client.connect();

        } else {
            permissionsCallback = new CompletionCallback() {
                @Override
                public void onComplete(Error error) {
                    if (error == null)
                        client.connect();
                    else
                        callback.onComplete(null, error);
                }
            };
            requestPermission.run();
        }
    }

    public void logout(CompletionCallback callback) {
        if (client != null && client.isConnected()) {
            client.disconnect();

            if (trySilentAuthentication) {
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
            activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(client, leaderboard), GP_DIALOG_REQUEST_CODE);
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

    public void showSavedGames(SavedGameCallback callback) {
        if (intentCallback != null) {
            if (callback != null) {
                callback.onComplete(null, new Error("Intent already running", 0));
            }
            return;
        }

        intentSavedGameCallback = callback;
        notifyWillStart();

        int maxNumberOfSavedGamesToShow = 5;
        Intent savedGamesIntent = Games.Snapshots.getSelectSnapshotIntent(this.client, "Saved Games", true, true, maxNumberOfSavedGamesToShow);
        activity.startActivityForResult(savedGamesIntent, GP_SAVED_GAMES_REQUEST_CODE);
    }

    public void loadSavedGame(final String snapshotName, final SavedGameCallback callback) {
        try {
            PendingResult<Snapshots.OpenSnapshotResult> pendingResult = Games.Snapshots.open(client, snapshotName, false);
            ResultCallback<Snapshots.OpenSnapshotResult> cb =
                    new ResultCallback<Snapshots.OpenSnapshotResult>() {
                        @Override
                        public void onResult(Snapshots.OpenSnapshotResult openSnapshotResult) {
                            if (openSnapshotResult.getStatus().isSuccess()) {
                                try {
                                    Snapshot snapshot = openSnapshotResult.getSnapshot();
                                    byte[] data = openSnapshotResult.getSnapshot().getSnapshotContents().readFully();
                                    GameSnapshot result = GameSnapshot.fromMetadata(snapshot.getMetadata(), data);
                                    callback.onComplete(result, null);
                                }
                                catch (IOException e) {
                                    callback.onComplete(null, new Error("Exception reading snapshot: " + e.getMessage(), 3));
                                }
                            }
                            else {
                                callback.onComplete(null, new Error("Failed to load snapshot", 11));
                            }

                        }
                    };
            pendingResult.setResultCallback(cb);
        }
        catch (Exception ex) {
            callback.onComplete(null, new Error(ex.getLocalizedMessage(), 0));
        }
    }

    public void writeSavedGame(final GameSnapshot snapshotData, final CompletionCallback callback) {
        final String snapshotName = snapshotData.identifier;
        final boolean createIfMissing = true;

        // Use the data from the EditText as the new Snapshot data.
        final byte[] data = snapshotData.data.getBytes();
        final Error potentialError = new Error("", 0);

        AsyncTask<Void, Void, Boolean> updateTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                Snapshots.OpenSnapshotResult open = Games.Snapshots.open(client, snapshotName, createIfMissing).await();

                if (!open.getStatus().isSuccess()) {
                    potentialError.message = "Could not open Snapshot for update.";
                    return false;
                }

                // Change data but leave existing metadata
                Snapshot snapshot = open.getSnapshot();
                snapshot.getSnapshotContents().writeBytes(data);

                SnapshotMetadataChange metadataChange = null;
                if (!GPUtils.isEmpty(snapshotData.description)) {
                    metadataChange = new SnapshotMetadataChange.Builder()
                            .setDescription(snapshotData.description != null ? snapshotData.description : "")
                            .build();
                }
                else {
                    metadataChange = SnapshotMetadataChange.EMPTY_CHANGE;
                }

                Snapshots.CommitSnapshotResult commit = Games.Snapshots.commitAndClose(
                        client, snapshot, metadataChange).await();

                if (!commit.getStatus().isSuccess()) {
                    potentialError.message =  "Failed to commit Snapshot.";
                    return false;
                }

                // No failures
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    callback.onComplete(null);
                } else {
                    callback.onComplete(potentialError);
                }

            }
        };
        updateTask.execute();
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

    public void addScore(final long scoreToAdd, final String leaderboardID, final CompletionCallback callback) {
        Games.Leaderboards.loadCurrentPlayerLeaderboardScore(client, leaderboardID, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
            @Override
            public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
                if (scoreResult != null) {
                    if (GamesStatusCodes.STATUS_OK == scoreResult.getStatus().getStatusCode()) {
                        long score = 0;
                        if (scoreResult.getScore() != null) {
                            score = scoreResult.getScore().getRawScore();
                        }
                        Games.Leaderboards.submitScore(client, leaderboardID, score + scoreToAdd);
                        if (callback != null) {
                            callback.onComplete(null);
                        }
                    }
                }
                else if (callback != null) {
                    callback.onComplete(new Error("Error fetching user score",0));
                }
            }
        });
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

    public void submitEvent(String eventId, int increment) {
        Games.Events.increment(client, eventId, increment);
    }


    private void notifyWillStart()
    {
        if (this.willStartListener != null) {
            this.willStartListener.onWillStartActivity();
        }
    }

    private boolean hasPermission() {
        try {
            Method checkSelfPermission = Activity.class.getMethod("checkSelfPermission", String.class);
            int hasWriteContactsPermission = (Integer)checkSelfPermission.invoke(this.activity, Manifest.permission.GET_ACCOUNTS);
            if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED)
                return false;

        } catch (NoSuchMethodException e) {
            LOG.d(this.getClass().getSimpleName(), "No need to check for permission " + Manifest.permission.GET_ACCOUNTS);
            return true;

        } catch (InvocationTargetException e) {
            LOG.e(this.getClass().getSimpleName(), "invocationTargetException when checking permission " + Manifest.permission.GET_ACCOUNTS, e);
            return false;

        } catch (IllegalAccessException e) {
            LOG.e(this.getClass().getSimpleName(), "IllegalAccessException when checking permission " + Manifest.permission.GET_ACCOUNTS, e);
            return false;
        }

        return true;
    }

    private void requestPermission() {
        try {
            Method requestPermission = Activity.class.getDeclaredMethod("requestPermissions", String[].class, int.class);
            requestPermission.invoke(this.activity, new String[]{Manifest.permission.GET_ACCOUNTS}, REQUEST_PERMISSIONS_GET_ACCOUNTS);

        } catch (NoSuchMethodException e) {
            LOG.d(this.getClass().getSimpleName(), "No need to request permissions " + Manifest.permission.GET_ACCOUNTS);

        } catch (InvocationTargetException e) {
            LOG.e(this.getClass().getSimpleName(), "invocationTargetException when requesting permissions " + Manifest.permission.GET_ACCOUNTS, e);

        } catch (IllegalAccessException e) {
            LOG.e(this.getClass().getSimpleName(), "IllegalAccessException when requesting permissions " + Manifest.permission.GET_ACCOUNTS, e);
        }
    }

    public static final int REQUEST_PERMISSIONS_GET_ACCOUNTS = 3001;
}
