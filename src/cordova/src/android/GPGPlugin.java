package com.ludei.googleplaygames.cordova;

import android.content.Intent;
import com.ludei.googleplaygames.GPGService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
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
        _service.setSessionListener(this);
        _service.setWillStartActivityListener(this);
        _service.setExecutor(cordova.getThreadPool());
        _service.init();
        ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
    }

    @SuppressWarnings("unused")
    public void authorize(CordovaArgs args, final CallbackContext ctx) throws JSONException {

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
                }
                else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
                }

            }
        });

    }

    @SuppressWarnings("unused")
    public void request(CordovaArgs args, final CallbackContext ctx) throws JSONException {


        JSONObject params = args.getJSONObject(0);
        String path = params.getString("path");
        JSONObject requestParams = params.optJSONObject("params");
        String method = params.optString("method");
        if (method == null || method.length() == 0) {
            method = "GET";
        }
        HashMap<String, String> headers = null;
        JSONObject obj = params.optJSONObject("headers");
        if (obj != null) {
            headers = new HashMap<String, String>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, obj.get(key).toString());
            }
        }


        byte[] body = null;
        try
        {
            JSONObject bodyJSON = params.optJSONObject("body");
            if (bodyJSON != null) {
                body = bodyJSON.toString().getBytes("utf-8");
            }
            else {
                String bodyString = params.optString("body");
                if (bodyString != null && bodyString.length() > 0) {
                    body = bodyString.getBytes("utf-8");
                }
            }
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }


        _service.request(path, method, requestParams, body, headers, new GPGService.RequestCallback() {
            @Override
            public void onComplete(JSONObject responseJSON, GPGService.Error error) {

                JSONObject data = new JSONObject();
                try
                {
                    if (responseJSON != null) {
                        data.put("response", responseJSON);
                    }
                    if (error != null) {
                        data.put("error", new JSONObject(error.toMap()));
                    }

                }
                catch (JSONException ex) {
                    ex.printStackTrace();
                }
                ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, data));
            }
        });

    }

    @SuppressWarnings("unused")
    public void shareMessage(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        JSONObject params = args.getJSONObject(0);
        GPGService.ShareData message = new GPGService.ShareData();
        message.url = params.optString("url");
        message.message = params.optString("message");

        _service.shareMessage(message, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
                else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
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
                }
                else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
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
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public void unlockAchievement(CordovaArgs args, final CallbackContext ctx) throws JSONException {

        String achievement = args.getString(0);
        boolean show = args.optBoolean(1);
        _service.unlockAchievement(achievement, show, new GPGService.CompletionCallback() {
            @Override
            public void onComplete(GPGService.Error error) {
                if (error != null) {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, new JSONObject(error.toMap())));
                }
                else {
                    ctx.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String)null));
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
