package com.ludei.googleplaygames;


import android.app.Activity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.games.GamesActivityResultCodes;

import java.util.HashMap;

public class GPUtils
{
    public static String scopeArrayToString(String[] scopes)
    {
        String scope = "";
        for(String str: scopes) {
            if (scope.length() > 0)
                scope+=" ";
            scope+=str;
        }
        return scope;
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static String makeJSONError(int errorCode, String message) {
        if (message == null)
            return null;
        String error = "{\"code\":"+ errorCode + ",\"message\":\"" + message + "\"}";
        return error;
    }

    public static <T> boolean contains( final T[] array, final T v ) {
        for ( final T e : array )
            if ( e == v || v != null && v.equals( e ) )
                return true;

        return false;
    }

    public static String errorCodeToString(int errorCode) {
        switch (errorCode) {
            case ConnectionResult.DEVELOPER_ERROR:
                return "DEVELOPER_ERROR: The application is misconfigured.";
            case ConnectionResult.INTERNAL_ERROR:
                return "INTERNAL_ERROR: An internal error occurred.";
            case ConnectionResult.INVALID_ACCOUNT:
                return "INVALID_ACCOUNT: The client attempted to connect to the service with an invalid account name specified.";
            case ConnectionResult.LICENSE_CHECK_FAILED:
                return "LICENSE_CHECK_FAILED: The application is not licensed to the user.";
            case ConnectionResult.NETWORK_ERROR:
                return "NETWORK_ERROR: A network error occurred.";
            case ConnectionResult.RESOLUTION_REQUIRED:
                return "RESOLUTION_REQUIRED: Completing the connection requires some form of resolution.";
            case ConnectionResult.SERVICE_DISABLED:
                return "SERVICE_DISABLED: The installed version of Google Play services has been disabled on this device.";
            case ConnectionResult.SERVICE_INVALID:
                return "SERVICE_INVALID: The version of the Google Play services installed on this device is not compatible.";
            case ConnectionResult.SERVICE_MISSING:
                return "SERVICE_MISSING: Google Play services is missing on this device.";
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                return "UPDATE_REQUIRED: The installed version of Google Play services is out of date, update it.";
            case ConnectionResult.SIGN_IN_REQUIRED:
                return "SIGN_IN_REQUIRED: The client attempted to connect to the service but the user is not signed in.";
            case ConnectionResult.SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown error code " + errorCode;
        }
    }


    public static String activityResponseCodeToString(int respCode) {
        switch (respCode) {
            case Activity.RESULT_OK:
                return "RESULT_OK";
            case Activity.RESULT_CANCELED:
                return "RESULT_CANCELED";
            case GamesActivityResultCodes.RESULT_APP_MISCONFIGURED:
                return "RESULT_APP_MISCONFIGURED";
            case GamesActivityResultCodes.RESULT_LEFT_ROOM:
                return "RESULT_LEFT_ROOM";
            case GamesActivityResultCodes.RESULT_LICENSE_FAILED:
                return "RESULT_LICENSE_FAILED";
            case GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED:
                return "RESULT_RECONNECT_REQUIRED";
            case GamesActivityResultCodes.RESULT_SIGN_IN_FAILED:
                return "SIGN_IN_FAILED";
            default:
                return String.valueOf(respCode);
        }
    }
}
