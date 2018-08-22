package org.openthos.seafile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileUtils {

    public static final String SEAFILE_SOURCECODE_PATH = "/system/opt/sea.tar.bz";
    public static final String SEAFILE_CONFIG_PATH = "/data/seafile-config";
    public static final String SEAFILE_PROOT_PATH = "/data/sea";
    public static final String SEAFILE_DATA_ROOT_PATH = "/sdcard/seafile";

    public static final String SEAFILE_COMMAND_BASE
            = "./data/sea/proot.sh -b /data/seafile-config:/data/seafile-config seaf-cli ";

    public static final String SETTING_SEAFILE_NAME = "UserConfig";
    public static final String DATA_SEAFILE_NAME = "DATA";
    public static final String SEAFILE_URL_LIBRARY = "http://dev.openthos.org/";

    public static final int UNSYNC = 0;
    public static final int SYNC = 1;

    public static void init() {
        File seafileAtDisk = new File(SEAFILE_DATA_ROOT_PATH);
        if (!seafileAtDisk.exists()) {
            seafileAtDisk.mkdir();
        }
        File seafile = new File("/data/sea/proot.sh");
        File config = new File(SEAFILE_CONFIG_PATH);
        int i = 0;
        while (!seafile.exists()) {
            i++;
            Utils.exec(new String[]{"su", "-c", "rm -r /data/sea"
                    + ";" + "tar xvf " + SEAFILE_SOURCECODE_PATH + " -C /data"
                    + ";" + "chmod -R 777 /data/sea"
                    + ";" + "busybox mkdir -m 777 -p /data/sea/sdcard/seafile"});
        }
        Utils.exec(new String[]{"su",
                "-c", "busybox mount --bind " + seafileAtDisk.getAbsolutePath()
                + " /data/sea/sdcard/seafile" + ";" + "busybox mkdir -m 777 " + SEAFILE_CONFIG_PATH
                + ";" + SEAFILE_COMMAND_BASE + "init -d " + config.getAbsolutePath()});
    }

    public static void start() {
        Utils.exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE + "start"});
    }

    public static void stop() {
        Utils.exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE + "stop"});
    }

    public static String create(String fileName, String url, String name, String password) {
        fileName = fileName.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        String id = "";
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    SEAFILE_COMMAND_BASE + "create -n " + fileName +  " -s " +
                    url + " -u " + name + " -p " + password});
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
            String url, String name, String password) {
        filePath = filePath.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            File f = new File(filePath);
            if (!f.exists()) {
                f.mkdirs();
            }
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE
                    + "sync -l " + libraryid + " -d "
                    + filePath + " -s " + url + " -u " + name + " -p " + password});
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
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE
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

    public static class SeafileSQLConsole {
        SeafileSQLiteHelper mSeafileSQLiteHelper;

        public SeafileSQLConsole(Context context) {
            mSeafileSQLiteHelper = new SeafileSQLiteHelper(context, SeafileSQLiteHelper.NAME,
                    null, SeafileSQLiteHelper.VERSION);
            SQLiteDatabase db = mSeafileSQLiteHelper.getWritableDatabase();
            db.close();
        }

        public int queryAccountId(String account) {
            int id = 0;
            SQLiteDatabase db = mSeafileSQLiteHelper.getWritableDatabase();
            Cursor c = db.rawQuery("select * from seafileaccount where username like ?",
                    new String[]{account});
            while (c.moveToNext()) {
                id = c.getInt(c.getColumnIndex("id"));
            }
            c.close();
            if (id == 0) {
                db.execSQL("insert into seafileaccount (username) " +
                        "values ('" + account + "')");
                id = queryAccountId(account);
            }
            db.close();
            return id;
        }

        public int queryFile(int userId, String libraryId, String libraryName) {
            int isSync = -1;
            SQLiteDatabase db = mSeafileSQLiteHelper.getWritableDatabase();
            Cursor c = db.rawQuery("select * from seafilefile where userid like ?"
                            + " and libraryid like ? and libraryname like ?",
                    new String[]{userId + "", libraryId, libraryName});
            while (c.moveToNext()) {
                isSync = c.getInt(c.getColumnIndex("isSync"));
            }
            c.close();
            if (isSync == -1) {
                db.execSQL("insert into seafilefile (userid,libraryid,libraryname,isSync) "
                        + "values (" + userId + ",'" + libraryId
                        + "' ,'" + libraryName + "'," + SYNC + ")");
                isSync = queryFile(userId, libraryId, libraryName);
            }
            db.close();
            return isSync;
        }

        public int updateSync(int userId, String libraryId, String libraryName, int isSync) {
            SQLiteDatabase db = mSeafileSQLiteHelper.getWritableDatabase();
            db.execSQL("update seafilefile set isSync= " + isSync + " where userid=" + userId
                    + " and libraryid='" + libraryId + "' and libraryname='"
                    + libraryName + "'");
            Cursor c = db.rawQuery("select * from seafilefile where userid like ?"
                            + " and libraryid like ? and libraryname like ?",
                    new String[]{userId + "", libraryId, libraryName});
            if (c.moveToNext()) {
                isSync = c.getInt(c.getColumnIndex("isSync"));
            }
            c.close();
            db.close();
            return isSync;
        }

        public int insertLibrary(int userId, String libraryId, String libraryName) {
            SQLiteDatabase db = mSeafileSQLiteHelper.getWritableDatabase();
            db.execSQL("insert into seafilefile (userid,libraryid,libraryname,isSync) "
                    + "values (" + userId + ",'" + libraryId
                    + "' ,'" + libraryName + "'," + SYNC + ")");
            Cursor c = db.rawQuery("select * from seafilefile where userid like ?"
                            + " and libraryid like ? and libraryname like ?",
                    new String[]{userId + "", libraryId, libraryName});
            int isSync = -1;
            if (c.moveToNext()) {
                isSync = c.getInt(c.getColumnIndex("isSync"));
            }
            c.close();
            db.close();
            return isSync;
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
        if (rep.ok()){
            contentAsString = new String(rep.bytes(), "UTF-8");
            return new JSONObject(contentAsString).getString("token");
        } else {
            throw new UnsupportedEncodingException();
        }
    }
}
