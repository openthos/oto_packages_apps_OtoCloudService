package org.openthos.seafile;

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
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by Wang Zhixu on 12/23/16.
 */

public class SeafileUtils {
    public static final String OPENTHOS_URI = "content://com.otosoft.tools.myprovider/openthosID";

    public static final String SEAFILE_SOURCECODE_PATH = "/system/opt/sea.tar.bz";
    public static final String SEAFILE_CONFIG_PATH = "/data/seafile-config";
    public static final String SEAFILE_PROOT_PATH = "/data/sea";
    public static final String SEAFILE_PROOT_BASEPATH = "/data";
    public static final String SEAFILE_DATA_ROOT_PATH = "/sdcard/seafile";

    public static final String SEAFILE_COMMAND_SEAFILE = "seaf-cli";
    public static final String SEAFILE_COMMAND_BASE
            = "./data/sea/proot.sh -b /data/seafile-config:/data/seafile-config seaf-cli ";

    public static final String SETTING_SEAFILE_NAME = "UserConfig";
    public static final String DATA_SEAFILE_NAME = "DATA";

    public static final String SEAFILE_BASE_URL = "-s https://dev.openthos.org/ ";
    public static String mUserId = "";
    public static String mUserPassword = "";
    public static String getUserAccount() {
        return "-u " + mUserId + " -p " + mUserPassword;
    }

    public static final int SEAFILE_ID_LENGTH = 36;
    public static final int UNSYNC = 0;
    public static final int SYNC = 1;

    public static final String SEAFILE_DATA = "seeafile_data";

    public static final int TAG_APPDATA_IMPORT = 0;
    public static final int TAG_APPDATA_EXPORT = 1;
    public static final int TAG_BROWSER_IMPORT = 2;
    public static final int TAG_BROWSER_EXPORT = 3;

    public static boolean isExistsAccount() {
        return !TextUtils.isEmpty(mUserId) || !TextUtils.isEmpty(mUserPassword);
    }
                  // "mkdir -m 777 " + SEAFILE_CONFIG_PATH
                  //  + ";" + "chmod 777 " + SEAFILE_CONFIG_PATH

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
            exec(new String[]{"su", "-c", "rm -r /data/sea"
                    + ";" + "tar xvf " + SEAFILE_SOURCECODE_PATH + " -C /data"
                    + ";" + "chmod -R 777 /data/sea"
                    + ";" + "busybox mkdir -m 777 -p /data/sea/sdcard/seafile"});
        }
        Log.i("wwww", i+"");
        exec(new String[]{"su", "-c", "busybox mount --bind " + seafileAtDisk.getAbsolutePath()
                + " /data/sea/sdcard/seafile" + ";" + "busybox mkdir -m 777 " + SEAFILE_CONFIG_PATH
                + ";" + SEAFILE_COMMAND_BASE + "init -d " + config.getAbsolutePath()});
    }

    public static void start() {
        exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE + "start"});
    }

    public static void stop() {
        exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE + "stop"});
        mUserId = "";
        mUserPassword = "";
    }

    public static void download(String libraryid, String filePath) {
        filePath = filePath.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            File f = new File(filePath);
            if (!f.exists()) {
                f.mkdirs();
            }
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE
                    + "download -l " + libraryid + " -d "
                    + filePath + " " + SEAFILE_BASE_URL + getUserAccount()});
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

    public static String create(String fileName) {
        fileName = fileName.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        String id = "";
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE
                    + "create -n " + fileName +  " " + SEAFILE_BASE_URL + getUserAccount()});
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

    public static void sync(String libraryid, String filePath) {
        filePath = filePath.replace(" ", "\\ ");
        Process pro;
        BufferedReader in = null;
        try {
            File f = new File(filePath);
            if (!f.exists()) {
                f.mkdirs();
            }
            Log.i("wwww", SEAFILE_COMMAND_BASE
                     + "sync -l " + libraryid + " -d "
                     + filePath + " " + SEAFILE_BASE_URL + getUserAccount());
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", SEAFILE_COMMAND_BASE
                    + "sync -l " + libraryid + " -d "
                    + filePath + " " + SEAFILE_BASE_URL + getUserAccount()});
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

    public static void exec(String[] commands) {
        Process pro = null;
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
            if (pro != null) {
                pro.destroy();
            }
        }
    }

    public static void exec(String cmd) {
        if (TextUtils.isEmpty(cmd)) {
            return;
        }
        Process pro = null;
        DataOutputStream dos = null;
        try {
            Runtime rt = Runtime.getRuntime();
            pro = rt.exec("su");
            dos = new DataOutputStream(pro.getOutputStream());
            dos.writeBytes(cmd + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            pro.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (pro != null) {
                pro.destroy();
            }
        }
    }

    /*
     * eg:
     *     from : /data/temp
     *     to   : /dev/tmp/temp.tar.gz
     */
    public static void tarFile(String from, String to) {
        if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)) {
            return;
        }
        Process pro = null;
        DataOutputStream dos = null;
        try {
            File f = new File(to);
            Runtime rt = Runtime.getRuntime();
            pro = rt.exec("su");//Root
            dos = new DataOutputStream(pro.getOutputStream());
            dos.writeBytes("mkdirs -p " + f.getParent().replace(" ", "\\ ") + "\n");
            dos.writeBytes("tar -czpf " + to.replace(" ", "\\ ") + " "
                    + from.replace(" ", "\\ ") + "\n");
            Log.i("wwww", "tar -czpf " + to.replace(" ", "\\ ") + " " + from.replace(" ", "\\ ") + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            pro.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (pro != null) {
                pro.destroy();
            }
        }
    }

    /*
     * eg:
     *     from : /dev/tmp/temp.tar.gz
     */
    public static void untarFile(String from) {
        if (TextUtils.isEmpty(from)) {
            return;
        }
        Process pro = null;
        DataOutputStream dos = null;
        try {
            Runtime rt = Runtime.getRuntime();
            pro= rt.exec("su");//Root
            dos = new DataOutputStream(pro.getOutputStream());
            dos.writeBytes("tar -xzpf " + from.replace(" ", "\\ ") + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            pro.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (pro != null) {
                pro.destroy();
            }
        }
    }

    public static boolean checkFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        Process pro = null;
        BufferedReader in = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "ls "
                    + path.replace(" ", "\\ ")});
            in = new BufferedReader(new InputStreamReader(pro.getErrorStream()));
            String line;
            while ((line = in.readLine()) != null) {
                return false;
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
            if (pro != null) {
                pro.destroy();
            }
        }
        return true;
    }

    public static String[] listFiles(String path) {
        if (TextUtils.isEmpty(path)) {
            return new String[]{};
        }
        Process pro = null;
        BufferedReader in = null;
        String[] files = null;
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "ls "
                    + path.replace(" ", "\\ ")});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            ArrayList<String> temp = new ArrayList();
            while ((line = in.readLine()) != null) {
                temp.add(line);
            }
            files = (String[]) temp.toArray();
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
            if (pro != null) {
                pro.destroy();
            }
        }
        return files;
    }

    public static void chownFile(String path, int uid) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        Process pro = null;
        DataOutputStream dos = null;
        try {
            Runtime rt = Runtime.getRuntime();
            pro = rt.exec("su");
            dos = new DataOutputStream(pro.getOutputStream());
            dos.writeBytes("chown -R " + uid + ":" + uid + " " + path + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            pro.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (pro != null) {
                pro.destroy();
            }
        }
    }
}
