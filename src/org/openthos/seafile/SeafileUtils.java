package org.openthos.seafile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileUtils {
    public static final String SEAFILE_STATE_PATH = "/system/linux/sea/tmp/state/";
    public static final String SEAFILE_STATE_FILE = "DATA.state";
    public static final String SEAFILE_KEEPER_STATE_PATH = "/system/linux/sea/tmp/logs/";
    public static final String SEAFILE_QUOTA_STATE_PATH = "/system/linux/sea/tmp/quota/";
    public static final String SEAFILE_KEEPER_STATE_FILE = "Keeper.state";
    public static final String SEAFILE_QUOTA_STATE_FILE = "Quota.state";
    public static final String SEAFILE_DATA_ROOT_PATH = "/sdcard/seafile";
    public static final String SEAFILE_ACCOUNT_CONFIG = "/system/linux/sea/tmp/account.conf";

    public static final String SEAFILE_BASE_COMMAND
            = "./system/linux/sea/proot.sh -b /data/seafile-config:/data/seafile-config seaf-cli ";

    public static final String SETTING_SEAFILE_NAME = ".UserConfig";
    public static final String DATA_SEAFILE_NAME = "DATA";
    public static final String SEAFILE_URL_LIBRARY = "http://dev.openthos.org/";

    public static final int UNSYNC = 0;
    public static final int SYNC = 1;

    public static String getToken(Context context, String url, String name, String password)
            throws UnsupportedEncodingException, JSONException,
            HttpRequest.HttpRequestException, PackageManager.NameNotFoundException {
        HttpRequest rep = null;
        rep = HttpRequest.post(url + "api2/auth-token/",
                null, false).followRedirects(true).connectTimeout(15000);
        rep.form("username", name);
        rep.form("password", password);
        PackageInfo packageInfo = null;
        packageInfo = context.getPackageManager().
                getPackageInfo(context.getPackageName(), 0);
        String deviceId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        rep.form("platform", "android");
        rep.form("device_id", deviceId);
        rep.form("device_name", Build.MODEL);
        rep.form("client_version", packageInfo.versionName);
        rep.form("platform_version", Build.VERSION.RELEASE);
        String contentAsString = null;
        if (rep.ok()) {
            contentAsString = new String(rep.bytes(), "UTF-8");
            return new JSONObject(contentAsString).getString("token");
        } else {
            throw new UnsupportedEncodingException();
        }
    }

    public static String readLog(Context context, String path, String file) {
        StringBuffer buffer = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(
                    new File(path, file)));

            String s = null;
            while((s = reader.readLine()) != null) {
                buffer.append(s + "\n");
            }
        } catch (IOException e) {
            if (!(e instanceof FileNotFoundException) && context != null) {
                Toast.makeText(context, context.getString(R.string.read_error), 0).show();
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return buffer.toString();
    }
}
