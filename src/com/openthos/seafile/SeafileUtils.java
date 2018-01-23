package com.openthos.seafile;


import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    public static final String OPENTHOS_URI = "content://com.otosoft.tools.myprovider/openthosID";

    public static final String SEAFILE_PROOT_BASEPATH = "/data";
    public static final String SEAFILE_CONFIG_PATH = "/data/seafile-config";
    public static final String SEAFILE_DATA_PATH = "/sdcard/.seafile-data";
    public static final String SEAFILE_PATH = "/system/opt/sea.tar.bz";
    public static final String SEAFILE_DATA_PATH_REAlLY = "/data/sea/data";

    public static final String SEAFILE_NET_NAME = ".ccnet";

    public static final String SEAFILE_COMMAND_SEAFILE = "seaf-cli ";
    public static final String SEAFILE_COMMAND_PROOT = "./data/sea/proot.sh -b ";
    public static final String SEAFILE_COMMAND_PROOT_BASE = "./data/sea/proot.sh ";

    public static final String SEAFILE_BASE_ARG = "-b";
    public static final String SEAFILE_BASE_URL = "-s https://dev.openthos.org/ ";
    public static String SEAFILE_BASE_ROOT_PATH = "/data/seafile-config:/data/seafile-config ";

    public static final int SEAFILE_ID_LENGTH = 36;

    public static String mUserId = "";
    public static String mUserPassword = "";

    public static final int UNSYNC = 0;
    public static final int SYNC = 1;
    public static final String SEAFILE_DATA = "seeafile_data";
    public static final String SETTING_SEAFILE_PATH = "/data/sea/data/sdcard/cloudFolder";
    public static final String SETTING_SEAFILE_PROOT_PATH = "/sdcard/cloudFolder";
    public static final String SETTING_SEAFILE_NAME = "cloudFolder";
    public static final String FILEMANAGER_SEAFILE_NAME = "DATA";

    public static String getUserAccount() {
        return "-u " + mUserId + " -p " + mUserPassword;
    }

    public static boolean isExistsAccount() {
        return !TextUtils.isEmpty(mUserId) || !TextUtils.isEmpty(mUserPassword);
    }

    private static void exec(String[] commands) {
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(commands);
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("Started: seafile daemon")) {
                    break;
                }
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

    public static void init() {
        File seafile = new File("/data/sea/proot.sh");
        if (!seafile.exists()){
            exec(new String[]{"su", "-c", "rm -r /data/sea"});
            exec(new String[]{"tar", "xvf", SEAFILE_PATH, "-C", "/data"});
            exec(new String[]{"su", "-c", "chmod -R 777 /data/sea"});
        }
        File config = new File(SEAFILE_CONFIG_PATH);
        if (!config.exists()) {
            config.mkdirs();
        }
        exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT
                + SEAFILE_BASE_ROOT_PATH + SEAFILE_COMMAND_SEAFILE + "init -d "
                + config.getAbsolutePath()});
    }

    public static void start() {
        exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT
                + SEAFILE_BASE_ROOT_PATH + SEAFILE_COMMAND_SEAFILE + "start"});
    }

    public static void stop() {
        exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT
                + SEAFILE_BASE_ROOT_PATH + SEAFILE_COMMAND_SEAFILE + "stop"});
    }


    public static String listRemote() {
        Process pro;
        BufferedReader in = null;
        StringBuffer sb = new StringBuffer();
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT
                    + SEAFILE_BASE_ROOT_PATH + SEAFILE_COMMAND_SEAFILE + "list-remote "
                    + SEAFILE_BASE_URL + getUserAccount()});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            sb.append("[");
            boolean isOut = false;
            while ((line = in.readLine()) != null) {
                if (line.contains("Name") && line.contains("ID")) {
                    isOut = true;
                    continue;
                }
                if (line.contains("ISO(ota)")) {
                    isOut = false;
                    continue;
                }
                if (isOut) {
                    if (line.length() > SEAFILE_ID_LENGTH) {
                        String id = line.substring(line.length() - SEAFILE_ID_LENGTH);
                        sb.append("{\"id\":\"" + id);
                        String name = line.replace(" " + id, "");
                        sb.append("\",\"name\":\"" + name + "\"},");
                    }
                }
            }
            sb.delete(sb.length() - 1, sb.length());
            sb.append("]");
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
        return sb.toString();
    }

    public static void download(String libraryid, String filePath) {
        filePath = filePath.trim().replace(" ", "\\ ");
        File f = new File(filePath);
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT_BASE
                    + "mkdir -p " + SEAFILE_PROOT_BASEPATH + filePath});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
            }
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT +
                    SEAFILE_BASE_ROOT_PATH
                    + SEAFILE_COMMAND_SEAFILE + "download -l " + libraryid + " -d "
                    + SEAFILE_PROOT_BASEPATH + filePath + " "
                    + SEAFILE_BASE_URL + getUserAccount()});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
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

    public static String create(String fileName) {
        fileName = fileName.trim().replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        String id = "";
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT +
                    SEAFILE_BASE_ROOT_PATH
                    + SEAFILE_COMMAND_SEAFILE + "create -n " + fileName +  " "
                    + SEAFILE_BASE_URL + getUserAccount()});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                id = line;
                Log.i("iddidid",id);
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

    public static void sync(String libraryid, String filePath) {
        filePath = filePath.trim().replace(" ", "\\ ");
        File f = new File(filePath);
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT_BASE
                    + "mkdir -p " + SEAFILE_PROOT_BASEPATH + filePath});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
            }
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT +
                    SEAFILE_BASE_ROOT_PATH
                    + SEAFILE_COMMAND_SEAFILE + "sync -l " + libraryid + " -d "
                    + SEAFILE_PROOT_BASEPATH + filePath + " "
                    + SEAFILE_BASE_URL + getUserAccount()});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
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
        filePath = filePath.trim().replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_PROOT +
                    SEAFILE_BASE_ROOT_PATH
                    + SEAFILE_COMMAND_SEAFILE + "desync -d " + SEAFILE_PROOT_BASEPATH + filePath});
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


    public static void status() {
        String arg0 = "status";
        Runtime runtime = Runtime.getRuntime();
        Process pro;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{SEAFILE_COMMAND_PROOT, SEAFILE_BASE_ARG,
                    "", SEAFILE_COMMAND_SEAFILE, arg0});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
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

    public static void delete(File file) {
        if (file.exists()) {
            String command = "rm";
            String arg = "";
            if (file.isFile()) {
                arg = "-v";
            } else if (file.isDirectory()) {
                arg = "-rv";
            }
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(new String[]{command, arg, file.getAbsolutePath()});
            } catch (IOException e) {
                e.printStackTrace();
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

    public static boolean isNetworkOn(Context context) {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(wifi != null && wifi.isAvailable()
                && wifi.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return true;
        }
        NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if(mobile != null && mobile.isAvailable()
                && mobile.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return true;
        }
        return false;
    }

    public static String getResult(String token) {
        HttpRequest ret = null;
        try {
            ret = HttpRequest.get("https://dev.openthos.org/api2/repos/", null, false);
            ret.readTimeout(30000)
                    .connectTimeout(15000)
                    .followRedirects(false)
                    .header("Authorization", "Token " + token);
            try {
                return new String(ret.bytes(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } catch (HttpRequest.HttpRequestException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getToken(Context context) {
        HttpRequest rep = null;
        try {
            rep = HttpRequest.post("https://dev.openthos.org/api2/auth-token/", null, false)
                    .followRedirects(true)
                    .connectTimeout(15000);
            rep.form("username", mUserId);
            rep.form("password", mUserPassword);
            PackageInfo packageInfo = null;
            try {
                packageInfo = context.getPackageManager().
                        getPackageInfo(context.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            String deviceId = Settings.Secure.getString(context.getContentResolver(),
                                                        Settings.Secure.ANDROID_ID);
            rep.form("platform", "android");
            rep.form("device_id", deviceId);
            rep.form("device_name", Build.MODEL);
            rep.form("client_version", packageInfo.versionName);
            rep.form("platform_version", Build.VERSION.RELEASE);
            String contentAsString = null;
            try {
                contentAsString = new String(rep.bytes(), "UTF-8");
                return new JSONObject(contentAsString).getString("token");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (HttpRequest.HttpRequestException e) {
            e.printStackTrace();
        }
        return "";
    }
}