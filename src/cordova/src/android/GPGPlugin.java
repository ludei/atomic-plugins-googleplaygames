package com.ludei.googleplaygames.cordova;

import android.Manifest;
import android.content.Intent;

import com.ludei.googleplaygames.GPGService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


public class GPGPlugin extends CordovaPlugin implements GPGService.SessionCallback, GPGService.WillStartActivityCallback {


    protected GPGService _service;
    protected CallbackContext _sessionListener;

    @Override
    protected void pluginInitialize() {
        _service = new GPGService(this.cordova.getActivity());
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        try
        {
            Method method = this.getClass().getDeclaredMethod(action, CordovaArgs.class, CallbackContext.class);
            method.invoke(this, args, callbackContext);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        this._service.handleActivityResult(requestCode, resultCode, intent);
    }



    @SuppressWarnings("unused")
    public void setListener(CordovaArgs args, CallbackContext ctx) throws JSONException {
        _sessionListener = ctx;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        ctx.sendPluginResult(result);
    }

    @SuppressWarnings("unused")
    public void init(CordovaArgs args, CallbackContext ctx) throws JSONException {

        JSONObject params = args.optJSONObject(0);
        String[] scopes = null;
        if (params != null) {
            JSONArray array = params.optJSONArray("scopes");
            if (array != null) {
                scopes = new String[array.length()];
                for (int i = 0; i < array.length(); ++i) {
                    scopes[i] = array.getString(i);
                }
            }
        }


        _service.setSessionListener(this);
        _service.setWillStartActivityListener(this);
        _service.setExecutor(cordova.getThreadPool());
        _service.init(scopes);
        ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
    }

    @SuppressWarnings("unused")
    public void login(CordovaArgs args, final CallbackContext ctx) throws JSONException {
        JSONObject obj = args.optJSONObject(0);
        String[] scopes = null;
        if (obj != null) {
            JSONArray array = obj.optJSONArray("scope");
            if (array != null) {
                scopes = new String[array.length()];
                for (int i = 0; i < array.length(); ++i) {
                    scopes[i] = array.getString(i);
                }
            }
        }


        _service.login(scopes, new GPGService.SessionCallback() {
            @Override
            public void onComplete(GPGService.Session session, GPGService.Error error) {
                ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, toSessionData(session, error)));
            }
        });
    }

    @SuppressWarnings("unused")
    public void disconnect(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        _service.logout(new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {

                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }

            }
        });

    }

    @SuppressWarnings("unused")
    public void showLeaderboard(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String leaderboardId = args.optString(0);
        _service.showLeaderboard(leaderboardId, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void showAchievements(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        _service.showAchievements(new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    String str = null;
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void loadAchievements(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        _service.loadAchievements(new GPGService.AchievementsCallback() {
            @Override
            public void onComplete(ArrayList<GPGService.GPGAchievement> achievements, GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
                else {
                    JSONArray array = new JSONArray();
                    if (achievements != null) {
                        for (GPGService.GPGAchievement ach: achievements) {
                            array.put(new JSONObject(ach.toMap()));
                        }
                    }
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, array));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void submitAchievement(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String achievement = args.getString(0);
        boolean show = args.optBoolean(1);
        _service.unlockAchievement(achievement, show, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }


    @SuppressWarnings("unused")
    public void loadScore(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String leaderboardId = args.getString(0);

        _service.loadScore(leaderboardId, new GPGService.LoadScoreCallback() {
            @Override
            public void onComplete(long score, GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, score));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void submitScore(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        long score = args.getLong(0);
        String leaderboardId = args.getString(1);

        _service.submitScore(score, leaderboardId, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void addScore(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        long score = args.getLong(0);
        String leaderboardId = args.getString(1);

        _service.addScore(score, leaderboardId, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }


    @SuppressWarnings("unused")
    public void loadPlayer(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String playerId = args.optString(0);

        _service.loadPlayer(playerId, new GPGService.LoadPlayerCallback() {
            @Override
            public void onComplete(GPGService.GPGPlayer player, GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
                else if (player != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, new JSONObject(player.toMap())));
                }
                else {
                    error = new GPGService.Error("Player not found", 0);
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
            }
        });
    }

    protected void notifySavedGameCallback(GPGService.GameSnapshot data, GPGService.Error error, CallbackContext ctx) {
        ArrayList<PluginResult> args = new ArrayList<PluginResult>();
        if (data != null) {
            args.add(new PluginResult(PluginResult.Status.OK, new JSONObject(data.toMap())));
        } else {
            args.add(new PluginResult(PluginResult.Status.OK, (String) null)); //null value
        }

        if (error != null) {
            args.add(new PluginResult(PluginResult.Status.OK, new JSONObject(error.toMap())));
        }
        ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, args));
    }

    @SuppressWarnings("unused")
    public void loadSavedGame(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String identifier = args.getString(0);
        _service.loadSavedGame(identifier, new GPGService.SavedGameCallback() {
            @Override
            public void onComplete(GPGService.GameSnapshot data, GPGService.Error error) {
                notifySavedGameCallback(data, error, ctx);
            }
        });
    }

    @SuppressWarnings("unused")
    public void writeSavedGame(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        JSONObject data = args.getJSONObject(0);
        GPGService.GameSnapshot snapshot = GPGService.GameSnapshot.fromJSONObject(data);

        _service.writeSavedGame(snapshot, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                } else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void showSavedGames(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        _service.showSavedGames(new GPGService.SavedGameCallback() {
            @Override
            public void onComplete(GPGService.GameSnapshot data, GPGService.Error error) {
                notifySavedGameCallback(data, error, ctx);
            }
        });
    }


    @SuppressWarnings("unused")
    public void submitEvent(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String eventId = args.getString(0);
        int increment = args.optInt(1);
        if (increment == 0) {
            increment  = 1;
        }
        _service.submitEvent(eventId, increment);
        ctx.success();
    }

    @SuppressWarnings("unused")
    public void loadPlayerStats(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        _service.loadPlayerStats(new GPGService.RequestCallback() {
            @Override
            public void onComplete(JSONObject responseJSON, GPGService.Error error) {
                if (responseJSON != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, responseJSON));
                } else if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION, new JSONObject(error.toMap())));
                }
                // should never happen
                else {
                    error = new GPGService.Error("Player Stats could not be accessed, no specific error code", 1);
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
            }
        });
    }

    //Session Listener
    @Override
    public void onComplete(GPGService.Session session, GPGService.Error error)
    {
        if (_sessionListener != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, this.toSessionData(session, error));
            result.setKeepCallback(true);
            _sessionListener.sendPluginResult(result);
        }
    }

    @Override
    public void onWillStartActivity() {
        this.cordova.setActivityResultCallback(this);
    }



    //utilities
    private JSONObject toSessionData(GPGService.Session session, GPGService.Error error) {
        JSONObject object = new JSONObject();
        try {
            if (session != null) {
                object.put("session", new JSONObject(session.toMap()));
            }
            if (error != null) {
                object.put("error", new JSONObject(error.toMap()));
            }
        }
        catch (JSONException ex) {
            ex.printStackTrace();
        }
        return object;
    }


}
