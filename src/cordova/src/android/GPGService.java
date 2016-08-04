package com.ludei.googleplaygames;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.Players;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.AchievementBuffer;
import com.google.android.gms.games.achievement.Achievements;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.android.gms.games.stats.PlayerStats;
import com.google.android.gms.games.stats.Stats;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

public class GPGService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    public class Session {

        public String accessToken;
        public String playerId;
        public String playerAlias;
        public ArrayList<String> scopes;
        public long expirationDate;


        public Session(String token, ArrayList<String> scopes, Player p, long expirationDate)
        {
            this.accessToken = token;
            this.scopes = scopes;
            if (p != null) {
                this.playerId = p.getPlayerId();
                this.playerAlias = p.getDisplayName();
            }
            this.expirationDate = expirationDate;
        }

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("access_token", accessToken != null ? accessToken : "");
            String scope = GPUtils.scopeArrayToString(scopes);
            dic.put("state", scope);
            dic.put("playerId", playerId != null ? playerId : "");
            dic.put("playerAlias", playerAlias != null ? playerAlias : "");
            dic.put("expirationDate", expirationDate);
            return dic;
        }
    }
    public static class Error {
        public String message;
        public int code;

        public Error(String msg, int code) {
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

    public class GPGPlayer {
        public String playerId;
        public String playerAlias;
        public long lastPlayed;

        public GPGPlayer(Player player) {
            if (player != null) {
                playerId = player.getPlayerId();
                playerAlias = player.getDisplayName();
                lastPlayed = player.getLastPlayedWithTimestamp();
            }
        }

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("playerId", playerId != null ? playerId : "");
            dic.put("playerAlias", playerAlias != null ? playerAlias : "");
            dic.put("lastPlayed", lastPlayed);
            return dic;
        }
    }

    public class GPGAchievement {
        public String title;
        public String description;
        public String identifier;
        public boolean unlocked;

        GPGAchievement(Achievement ach) {
            if (ach != null) {
                this.title = ach.getName();
                this.description = ach.getDescription();
                this.identifier = ach.getAchievementId();
                unlocked = ach.getState() == Achievement.STATE_UNLOCKED;
            }
        }

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("title", title != null ? title : "");
            dic.put("description", description != null ? description : "");
            dic.put("identifier", identifier != null ? identifier : "");
            dic.put("unlocked", unlocked);
            return dic;
        }
    }

    public static class GameSnapshot {
        public String identifier;
        public String title;
        public String description;
        public byte[] bytes;

        public HashMap<String, Object> toMap()
        {
            HashMap<String,Object> dic = new HashMap<String, Object>();
            dic.put("identifier", identifier != null ? identifier : "");
            dic.put("description", description != null ? description : "");
            try {
                if (bytes != null) {
                    dic.put("data", new String(bytes, "UTF-8"));
                }
            }catch (Exception ex) {
                dic.put("data", new String(bytes));
            }
            //dic.put("data", data); TODO
            return dic;
        }

        public static GameSnapshot fromJSONObject(JSONObject obj) {
            GameSnapshot data = new GameSnapshot();
            data.identifier = obj.optString("identifier");
            data.description = obj.optString("description");
            String strData = obj.optString("data");
            if (strData != null && strData.length() > 0) {
                try {
                    data.bytes = strData.getBytes("UTF-8");
                }
                catch (Exception ex) {
                    data.bytes = strData.getBytes();
                }
            }
            //data.data = obj.optString("data"); TODO
            return data;
        }

        public static GameSnapshot fromMetadata(SnapshotMetadata metadada, byte[] bytes) {
            GameSnapshot data = new GameSnapshot();
            data.identifier = metadada.getUniqueName();
            data.description = metadada.getDescription();
            data.bytes = bytes;
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


    public interface LoadPlayerCallback {
        void onComplete(GPGPlayer player, Error error);
    }

    public interface LoadScoreCallback {
        void onComplete(long score, Error error);
    }

    public interface SavedGameCallback {
        void onComplete(GameSnapshot data, Error error);
    }

    public interface AchievementsCallback {
        void onComplete(ArrayList<GPGAchievement> achievements, Error error);
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
    protected SavedGameCallback intentSavedGameCallback;
    protected ArrayList<SessionCallback> loginCallbacks = new ArrayList<SessionCallback>();
    protected String authToken;
    protected SessionCallback sessionListener;
    protected WillStartActivityCallback willStartListener;
    protected boolean trySilentAuthentication = false;
    protected Executor executor;
    protected Player me;
    protected Runnable errorDialogCallback;
    protected Runnable requestPermission = null;
    protected ImageManager imageManager = null;

    public GPGService(Activity activity)
    {
        sharedInstance = this;
        this.activity = activity;
        this.trySilentAuthentication = this.activity.getPreferences(Activity.MODE_PRIVATE).getBoolean(GP_SIGNED_IN_PREFERENCE, false);
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



        Session session = authToken != null ? new Session(authToken, scopes, getMe(), 0) : null;
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
                    me = Games.Players.getCurrentPlayer(client);
                    String token = me.getPlayerId();
                    return token;
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
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

    private Player getMe() {
        if (me != null) {
            return me;
        }
        if (isLoggedIn()) {
            try {
                me = Games.Players.getCurrentPlayer(client);

            } catch(IllegalStateException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public GPGService.Session getSession() {

        if (authToken != null && client != null) {
            return new GPGService.Session(authToken, scopes, getMe(), 0);
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

        client.connect();
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

    public void showLeaderboard(String leaderboard, CompletionCallback callback)
    {
        if (intentCallback != null) {
            if (callback != null) {
                callback.onComplete(new Error("Intent already running", 0));
            }
            return;
        }

        try {
            intentCallback = callback;
            notifyWillStart();
            if (leaderboard == null || leaderboard.length() == 0) {
                activity.startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(client), GP_DIALOG_REQUEST_CODE);
            }
            else {
                activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(client, leaderboard), GP_DIALOG_REQUEST_CODE);
            }
        }
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(new Error(ex.toString(), 0));
            }
        }

    }


    public void loadAchievements(final AchievementsCallback callback)
    {
        if (!isLoggedIn()) {
            if (callback != null) {
                callback.onComplete(null, new Error("User is not logged into Google Play Game Services", 0));
            }
            return;
        }

        try {
            Games.Achievements.load(client, false).setResultCallback(new ResultCallback<Achievements.LoadAchievementsResult>() {
                @Override
                public void onResult(Achievements.LoadAchievementsResult result) {
                    if (callback == null) {
                        return;
                    }
                    if (result.getStatus().isSuccess()) {
                        AchievementBuffer buffer = result.getAchievements();
                        Iterator<Achievement> it = buffer.iterator();

                        ArrayList<GPGAchievement> data = new ArrayList<GPGAchievement>();

                        while (it.hasNext()) {
                            data.add(new GPGAchievement(it.next()));
                        }
                        buffer.close();
                        callback.onComplete(data, null);
                    } else {
                        int code = result.getStatus().getStatusCode();
                        callback.onComplete(null, new Error("Code: " + code, code));
                    }
                }
            });
        }
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(null, new Error(ex.toString(), 0));
            }
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

        try {
            intentCallback = callback;
            notifyWillStart();
            activity.startActivityForResult(Games.Achievements.getAchievementsIntent(client), GP_DIALOG_REQUEST_CODE);
        }
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(new Error(ex.toString(), 0));
            }
        }
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

    private static final int MAX_SNAPSHOT_RESOLVE_RETRIES = 3;


    private void processFinalSnapshot(final Snapshot snapshot, final GPGService.Error error, final SavedGameCallback callback) {

        if (callback == null) {
            return;
        }

        if (snapshot != null) {
            try {
                AsyncTask<Void, Void, Object> task = new AsyncTask<Void, Void, Object>() {
                    @Override
                    protected Object doInBackground(Void... params) {
                        try
                        {
                            SnapshotMetadata meta = snapshot.getMetadata();
                            SnapshotContents contents = snapshot.getSnapshotContents();
                            if (meta != null && contents != null) {
                                GameSnapshot data = GameSnapshot.fromMetadata(meta, contents.readFully());
                                return data;
                            }
                            else {
                                return null; //no snapshot
                            }

                        }
                        catch (Exception ex)
                        {
                            return new Error(ex.toString(), 0);
                        }
                    }

                    @Override
                    protected void onPostExecute(Object info) {
                        try {
                            if (info == null) {
                                callback.onComplete(null, new Error("Empty snapshot", 0));
                            }
                            else if (info instanceof GameSnapshot) {
                                callback.onComplete((GameSnapshot) info, null);
                            }
                            else {
                                callback.onComplete(null, (Error)info);
                            }
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
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
            catch (Exception e) {
                callback.onComplete(null, new Error(e.toString(), 0));
            }
        }
        else {
            callback.onComplete(null, error);
        }


    }

    private void processSnapshotOpenResult(final Snapshots.OpenSnapshotResult result, final SavedGameCallback callback, final int retryCount) {


        int status = result.getStatus().getStatusCode();

        if (status == GamesStatusCodes.STATUS_OK) {
            processFinalSnapshot(result.getSnapshot(), null, callback);
        }
        else if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {

            if (retryCount > MAX_SNAPSHOT_RESOLVE_RETRIES) {
                // Failed, log error and show Toast to the user
                processFinalSnapshot(result.getSnapshot(), new Error("Could not resolve snapshot conflicts", 1), callback);
                return;
            }
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Snapshot snapshot = result.getSnapshot();
                    Snapshot conflictSnapshot = result.getConflictingSnapshot();
                    byte[] data = null;
                    byte[] conflictData = null;
                    try {
                        data = snapshot.getSnapshotContents().readFully();
                        conflictData = snapshot.getSnapshotContents().readFully();
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    Snapshot resolvedSnapshot = snapshot;

                    if (data != null && conflictData != null && data.length != conflictData.length) {
                        // Resolve between conflicts by selecting the snapshot with more data
                        if (conflictData.length > data.length) {
                            resolvedSnapshot = conflictSnapshot;
                        }
                    }
                    else if (snapshot.getMetadata().getLastModifiedTimestamp() < conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
                        // Resolve between conflicts by selecting the newest of the conflicting snapshots.
                        resolvedSnapshot = conflictSnapshot;
                    }


                    Snapshots.OpenSnapshotResult resolveResult = Games.Snapshots.resolveConflict(client, result.getConflictId(), resolvedSnapshot).await();
                    // Recursively attempt again
                    processSnapshotOpenResult(resolveResult, callback, retryCount + 1);
                }
            });
            thread.start();

        }
        else {
            processFinalSnapshot(result.getSnapshot(), new Error("Status: " + status, status), callback);
        }
    }

    public void loadSavedGame(final String snapshotName, final SavedGameCallback callback) {
        try {
            PendingResult<Snapshots.OpenSnapshotResult> pendingResult = Games.Snapshots.open(client, snapshotName, false);
            ResultCallback<Snapshots.OpenSnapshotResult> cb =
                    new ResultCallback<Snapshots.OpenSnapshotResult>() {
                        @Override
                        public void onResult(Snapshots.OpenSnapshotResult result) {
                            processSnapshotOpenResult(result, callback, 0);
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

        final Error potentialError = new Error("", 0);

        AsyncTask<Void, Void, Boolean> updateTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Snapshots.OpenSnapshotResult open = Games.Snapshots.open(client, snapshotName, createIfMissing).await();

                    if (!open.getStatus().isSuccess()) {
                        potentialError.message = "Could not open Snapshot for update.";
                        return false;
                    }

                    // Change data but leave existing metadata
                    Snapshot snapshot = open.getSnapshot();
                    snapshot.getSnapshotContents().writeBytes(snapshotData.bytes);

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
                catch (Exception ex) {
                    potentialError.message = ex.toString();
                    return false;
                }
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
        if (this.executor != null) {
            updateTask.executeOnExecutor(executor);
        }
        else  {
            updateTask.execute();
        }
    }


    public void loadAvatar(Player player, String destFile, CompletionCallback callback) {
        if (player == null) {
            callback.onComplete(new Error("Player not found", 0));
            return;
        }

        if (!player.hasHiResImage()) {
            callback.onComplete(new Error("Player has not avatar", 0));
            return;
        }

        loadAvatar(player.getHiResImageUri(), destFile, callback);
    }


    private ArrayList<ImageManager.OnImageLoadedListener> holdedImageListeners = new ArrayList<ImageManager.OnImageLoadedListener>();
    public void loadAvatar(final Uri uri, final String destFile, final CompletionCallback callback) {

        if (uri == null) {
            callback.onComplete(new Error("Player has not avatar", 0));
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadAvatar(uri, destFile, callback);
                }
            });
            return;
        }

        try {
            if (this.imageManager == null) {
                this.imageManager = ImageManager.create(activity);
            }



            ImageManager.OnImageLoadedListener listener = new ImageManager.OnImageLoadedListener() {
                @Override
                public void onImageLoaded(Uri uri, Drawable drawable, boolean b) {
                    if (drawable == null) {
                        callback.onComplete(new Error("Player has not avatar", 0));
                        return;
                    }

                    try {
                        int w = drawable.getIntrinsicWidth();
                        int h = drawable.getIntrinsicHeight();
                        Bitmap  bitmap = Bitmap.createBitmap( w, h, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        drawable.setBounds(0, 0, w, h);
                        drawable.draw(canvas);
                        FileOutputStream fos = new FileOutputStream(new File(destFile));
                        final BufferedOutputStream bos = new BufferedOutputStream(fos, 1024 * 8);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                        bos.flush();
                        bos.close();
                        fos.close();
                        callback.onComplete(null);
                    }
                    catch (Exception ex) {
                        callback.onComplete(new Error(ex.toString(), 0));
                    }
                    holdedImageListeners.remove(this);
                }
            };
            //Google Documentation: Note that you should hold a reference to the listener provided until the callback is complete.
            // For this reason, the use of anonymous implementations is discouraged."
            //I didn't do it the first time and some callbacks never reached...
            holdedImageListeners.add(listener);
            imageManager.loadImage(listener, uri);
        }
        catch (Exception ex) {
            callback.onComplete(new Error(ex.toString(), 0));
        }
    }

    public void loadAvatar(final String playerID, final String destFile, final CompletionCallback callback)
    {
        try {
            if (!isLoggedIn()) {

                if (callback != null) {
                    callback.onComplete(new Error("User is not logged into Google Play Game Services", 0));
                }
                return;
            }

            if (playerID == null || playerID.length() == 0) {
                loadAvatar(getMe(), destFile, callback);
            }
            else  {
                Games.Players.loadPlayer(client, playerID).setResultCallback(new ResultCallback<Players.LoadPlayersResult>() {
                    @Override
                    public void onResult(@NonNull Players.LoadPlayersResult loadPlayersResult) {
                        if (loadPlayersResult.getStatus().isSuccess()) {
                            loadAvatar(loadPlayersResult.getPlayers().get(0), destFile, callback);
                        }
                        else if (callback != null ) {
                            callback.onComplete(new Error("Player not found", 0));
                        }
                    }
                });
            }


        }
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(new Error(ex.toString(), 0));
            }
        }

    }

    public void unlockAchievement(String achievementID, boolean showNotification, final CompletionCallback callback)
    {
        try {
            if (!isLoggedIn()) {

                if (callback != null) {
                    callback.onComplete(new Error("User is not logged into Google Play Game Services", 0));
                }
                return;
            }

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
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(new Error(ex.toString(), 0));
            }
        }

    }

    public void loadPlayer(final String playerId, final LoadPlayerCallback callback) {
        try {
            if (!isLoggedIn()) {
                callback.onComplete(null, new Error("User is not logged into Google Play Game Services", 0));
                return;
            }

            if (playerId == null || playerId.length() == 0) {
                callback.onComplete(new GPGPlayer(getMe()), null);
                return;
            }

            Games.Players.loadPlayer(client, playerId).setResultCallback(new ResultCallback<Players.LoadPlayersResult>() {
                @Override
                public void onResult(@NonNull Players.LoadPlayersResult loadPlayersResult) {
                    if (GamesStatusCodes.STATUS_OK == loadPlayersResult.getStatus().getStatusCode() && loadPlayersResult.getPlayers().getCount() > 0) {
                        callback.onComplete(new GPGPlayer(loadPlayersResult.getPlayers().get(0)), null);
                        return;
                    }
                    callback.onComplete(null, new Error("Error fetching user score", 0));
                }
            });
        }
        catch (Exception ex) {
            callback.onComplete(null, new Error(ex.getLocalizedMessage(), 0));
        }
    }

    public void loadScore(final String leaderboardID, final LoadScoreCallback callback) {
        try {
            if (!isLoggedIn()) {
                callback.onComplete(0, new Error("User is not logged into Google Play Game Services", 0));
                return;
            }
            Games.Leaderboards.loadCurrentPlayerLeaderboardScore(client, leaderboardID, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
                @Override
                public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
                    if (scoreResult != null) {
                        if (GamesStatusCodes.STATUS_OK == scoreResult.getStatus().getStatusCode()) {
                            long score = 0;
                            if (scoreResult.getScore() != null) {
                                score = scoreResult.getScore().getRawScore();
                            }
                            callback.onComplete(score, null);
                        }
                    }
                    else {
                        callback.onComplete(0, new Error("Error fetching user score", 0));
                    }
                }
            });
        }
        catch (Exception ex) {
            callback.onComplete(0, new Error(ex.getLocalizedMessage(), 0));
        }
    }

    public void addScore(final long scoreToAdd, final String leaderboardID, final CompletionCallback callback) {

        loadScore(leaderboardID, new LoadScoreCallback() {
            @Override
            public void onComplete(long score, Error error) {
                if (error != null) {
                    if (callback != null) {
                        callback.onComplete(error);
                        return;
                    }
                }
                submitScore(score + scoreToAdd, leaderboardID, callback);
            }
        });

    }

    public void submitScore(final long score, final String leaderboardID, final CompletionCallback callback) {
        try {
            if (!isLoggedIn()) {

                if (callback != null) {
                    callback.onComplete(new Error("User is not logged into Google Play Game Services", 0));
                }
                return;
            }
            Games.Leaderboards.submitScore(client, leaderboardID, score);
            if (callback != null) {
                callback.onComplete(null);
            }
        }
        catch (Exception ex) {
            if (callback != null) {
                callback.onComplete(new Error(ex.getLocalizedMessage(), 0));
            }
        }
    }

    public void shareMessage(ShareData data, CompletionCallback callback)
    {
        if (callback != null) {
            callback.onComplete(new Error("TODO", 0));
        }
    }

    public void submitEvent(String eventId, int increment) {
        Games.Events.increment(client, eventId, increment);
    }

    public void loadPlayerStats(final RequestCallback callback) {
        PendingResult<Stats.LoadPlayerStatsResult> result =
                Games.Stats.loadPlayerStats(
                client, false /* forceReload */);
        result.setResultCallback(new ResultCallback<Stats.LoadPlayerStatsResult>() {
            public void onResult(Stats.LoadPlayerStatsResult result) {
                Status status = result.getStatus();
                if (status.isSuccess()) {
                    PlayerStats stats = result.getPlayerStats();
                    JSONObject data = new JSONObject();
                    if (stats != null) {
                        try {
                            data.put("averageSessionLength", stats.getAverageSessionLength());
                            data.put("churnProbability", stats.getChurnProbability());
                            data.put("daysSinceLastPlayed", stats.getDaysSinceLastPlayed());
                            data.put("highSpenderProbability", stats.getHighSpenderProbability());
                            data.put("numberOfPurchases", stats.getNumberOfPurchases());
                            data.put("numberOfSessions", stats.getNumberOfSessions());
                            data.put("sessionPercentile", stats.getSessionPercentile());
                            data.put("spendPercentile", stats.getSpendPercentile());
                            data.put("spendProbability", stats.getSpendProbability());
                            data.put("totalSpendNext28Days", stats.getTotalSpendNext28Days());
                        }
                        catch (JSONException e) {
                            e.printStackTrace();
                        }
                        callback.onComplete(data, new Error("Player stats fetched successfully",  GamesStatusCodes.STATUS_OK));
                    } else {
                        callback.onComplete(null, new Error("getPlayerStats returned 'null'",  GamesStatusCodes.STATUS_INTERNAL_ERROR));
                    }
                } else {
                    callback.onComplete(null, new Error("status.isSuccess did not return 'true'",  GamesStatusCodes.STATUS_NETWORK_ERROR_NO_DATA));
                }
            }
        });
    }

    private void notifyWillStart()
    {
        if (this.willStartListener != null) {
            this.willStartListener.onWillStartActivity();
        }
    }

}
