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
    public static final String SEAFILE_KEEPER_STATE_FILE = "Keeper.state";
    public static final String SEAFILE_DATA_ROOT_PATH = "/sdcard/seafile";

    public static final String SEAFILE_BASE_COMMAND
            = "./system/linux/sea/proot.sh -b /data/seafile-config:/data/seafile-config seaf-cli ";

    public static final String SETTING_SEAFILE_NAME = "UserConfig";
    public static final String DATA_SEAFILE_NAME = "DATA";
    public static final String SEAFILE_URL_LIBRARY = "http://dev.openthos.org/";

    public static final int UNSYNC = 0;
    public static final int SYNC = 1;

    public static void init() {
        File seafileAtDisk = new File("/sdcard/seafile");
        if (!seafileAtDisk.exists()) {
            seafileAtDisk.mkdir();
        }

        Utils.exec(new String[]{"su", "-c", "busybox chmod -R 777 /system/linux/sea/*;" 
                + "busybox mkdir -m 777 -p /system/linux/sea/sdcard/seafile;"
                + "busybox mount --bind /sdcard/seafile /system/linux/sea/sdcard/seafile;"
                + "busybox mkdir -m 777 /data/seafile-config;"
                + SEAFILE_BASE_COMMAND + "init -d /data/seafile-config"});
    }

    public static void start() {
        Utils.exec(new String[]{"su", "-c", SEAFILE_BASE_COMMAND + "start"});
    }

    public static void stop() {
        Utils.exec(new String[]{"su", "-c", SEAFILE_BASE_COMMAND + "stop"});
    }

    public static String getToken(String service, String username, String password) {
        Process pro;
        BufferedReader in = null;
        String token = "";
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    SEAFILE_BASE_COMMAND + "get-token -s " + service + " -u " + username + " -p " + password});
            android.util.Log.i("wwww1", SEAFILE_BASE_COMMAND  + "get-token -s " + service + " -u " + username + " -p " + password);
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                android.util.Log.i("wwww1", line);
                token = line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return token;
    }

    public static String create(String fileName, String url, String name, String token) {
        fileName = fileName.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        String id = "";
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    SEAFILE_BASE_COMMAND + "create -n " + fileName + " -s " +
                            url + " -u " + name + " -tk " + token});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                id = line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return id;
    }

    public static void sync(String libraryid, String filePath,
                            String url, String name, String token) {
        filePath = filePath.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            File f = new File(filePath);
            if (!f.exists()) {
                f.mkdirs();
            }
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_BASE_COMMAND
                    + "sync -l " + libraryid + " -d "
                    + filePath + " -s " + url + " -u " + name + " -tk " + token});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void desync(String filePath) {
        filePath = filePath.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_BASE_COMMAND
                    + "desync -d " + filePath});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getResult(String token, String url)
            throws UnsupportedEncodingException, HttpRequest.HttpRequestException {
        HttpRequest ret = null;
        ret = HttpRequest.get(url + "api2/repos/", null, false);
        ret.readTimeout(15000).connectTimeout(15000).followRedirects(false)
                .header("Authorization", "Token " + token);
        if (ret.ok()) {
            return new String(ret.bytes(), "UTF-8");
        } else {
            throw new UnsupportedEncodingException();
        }
    }

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

    public static String readLog(Context context) {
        StringBuffer buffer = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(
                    new File(SEAFILE_KEEPER_STATE_PATH, SEAFILE_KEEPER_STATE_FILE)));

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
