package org.openthos.seafile;

import android.accounts.NetworkErrorException;
import android.annotation.SuppressLint;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openthos.seafile.seaapp.SeafException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class RecoveryService extends Service {
    private static final String SYSTEM_PATH_DATA = "/data/data/";
    private static final String SYSTEM_PATH_WALLPAPER = "/data/system/users/0/wallpaper";
    private static final String SYSTEM_PATH_WIFI_INFO = "/data/misc/wifi";
    private static final String SYSTEM_PATH_STATUSBAR = "com.android.systemui";
    private static final String SEAFILE_PATH_WALLPAPER = "/wallpaper";
    private static final String SEAFILE_PATH_APPSTORE = "/app_pkg_names";
    private static final String SEAFILE_PATH_WIFI = "/wifi.tar.gz";
    private static final String SEAFILE_PATH_APPDATA = "/appdata/";
    private boolean mWallpaper, mWifi, mAppdata, mStartupmenu, mAppstore;
    private List<ResolveInfo> mAllAppDatas = new ArrayList();
    private List<ResolveInfo> mImportList = new ArrayList();
    private List<String> mSyncAppdata = new ArrayList();
    private Intent mAppdataIntent;
    private boolean mIsBusy;
    private boolean mTempStartupMenu = false;
    private ServiceBinder mBinder = new ServiceBinder();
    private PackageManager mPackageManager;
    private Timer mTimer;
    private AutoBackupTask mAutoTask;
    public static final String CONFIG_DIR_PATH = "/data/data/org.openthos.seafile/seafile";
    private static final String CONFIG_PATH = "/data/data/org.openthos.seafile/seafile/config";
    private static final String DEBUG_TAG = "SeafConnection";
    private static final int CONNECTION_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static final long TIMEOUT_COUNT = 5;
    private String token, server, repoID;
    private List<FileBean> mFileBeans = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mPackageManager = getPackageManager();
        new File(CONFIG_DIR_PATH).mkdirs();
        updateEnvironment();
    }

    private void updateEnvironment() {
        SeafileAccount account = new SeafileAccount(this);
        server = SeafileService.mAccount.mOpenthosUrl;
        token = SeafileService.mAccount.mToken;
        readConfigs();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateEnvironment();
        if (intent != null) {
            boolean restore = intent.getBooleanExtra("restore", false);
            boolean backup = intent.getBooleanExtra("backup", false);
            boolean timer = intent.getBooleanExtra("timer", false);
            if (restore) {
                restoreFinish();
            } else if (backup) {
                stopTimer();
                startTimer();
            } else if (timer) {
                stopTimer();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class ServiceBinder extends Binder {

        public RecoveryService getService() {
            return RecoveryService.this;
        }
    }

    @SuppressLint("WrongConstant")
    public List<ResolveInfo> getAppsInfo(int tag) {
        readConfigs();
        mAppdataIntent = new Intent(Intent.ACTION_MAIN, null);
        mAppdataIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mAllAppDatas = mPackageManager.queryIntentActivities(mAppdataIntent, 0);
        switch (tag) {
            case Utils.TAG_APPDATA_EXPORT:
                return mAllAppDatas;
            case Utils.TAG_APPDATA_IMPORT:
                mImportList.clear();
                for (String appName : mSyncAppdata) {
                    for (ResolveInfo info : mAllAppDatas) {
                        if (appName.equals(info.activityInfo.packageName)) {
                            mImportList.add(info);
                            break;
                        }
                    }
                }
                return mImportList;
        }
        return null;
    }

    public void restoreSettings(final boolean wallpaper, final boolean wifi, final boolean appdata,
                                final List<String> syncAppdata, final boolean startupmenu, final boolean appstore) {
        if (mIsBusy) {
            return;
        }
        new Thread() {
            public void run() {
                mIsBusy = true;
                ArrayList<String> list = new ArrayList<>();
                if (wallpaper)
                    list.add("wallpaper");
                if (wifi)
                    list.add("wifi.tar.gz");
                if (startupmenu)
                    list.add("com.android.systemui.tar.gz");
                if (appdata) {
                    for (String s : syncAppdata) {
                        list.add(s + ".tar.gz");
                    }
                }
      
                downloadFile(list);

                mTempStartupMenu = startupmenu;
                if (wallpaper) {
                    importWallpaperFiles();
                }
                if (wifi) {
                    importWifiFiles();
                }
                if (appdata) {
                    importFiles(syncAppdata, SEAFILE_PATH_APPDATA);
                }
                if (appstore) {
                    ArrayList<String> pkgNames = new ArrayList();
                    File file = new File("SEAFILE_PATH_APPSTORE_PKGNAME");
                    BufferedReader appReader = null;
                    try {
                        String path = CONFIG_DIR_PATH + SEAFILE_PATH_APPSTORE;
                        Utils.exec("busybox chmod 777 " + path);
                        appReader = new BufferedReader(new FileReader(path));
                        String line = null;
                        while ((line = appReader.readLine()) != null) {
                            pkgNames.add(line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (appReader != null) {
                            try {
                                appReader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // download apps from appstore
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName("org.openthos.appstore",
                            "org.openthos.appstore.download.DownloadService"));
                    intent.putStringArrayListExtra("packageNames", pkgNames);
                    startService(intent);
                } else {
                    restoreFinish();
                }
                mIsBusy = false;
            }
        }.start();
    }

    private void readConfigs() {
        new Thread() {
            public void run() {
                if (!new File(CONFIG_PATH).exists()) {
                    ArrayList<String> list = new ArrayList<>();
                    list.add("config");
                    downloadFile(list);
                }
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(new File(CONFIG_PATH))));
                    JSONObject obj = new JSONObject(reader.readLine());
                    mWallpaper = obj.getBoolean("wallpaper");
                    mWifi = obj.getBoolean("wifi");
                    mAppdata = obj.getBoolean("appdata");
                    mStartupmenu = obj.getBoolean("startupmenu");
                    mAppstore = obj.getBoolean("appstore");
                    JSONArray array = obj.getJSONArray("packages");
                    ArrayList<String> temp = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        temp.add(array.getString(i));
                    }
                    mSyncAppdata.clear();
                    mSyncAppdata.addAll(temp);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
    }


    private void saveConfigs(boolean wallpaper, boolean wifi, boolean appdata,
                             List<String> syncAppdata, boolean startupmenu, boolean appstore) {
        BufferedWriter writer = null;
        try {
            JSONObject obj = new JSONObject();
            obj.put("wallpaper", wallpaper);
            obj.put("wifi", wifi);
            obj.put("startupmenu", startupmenu);
            obj.put("appstore", appstore);
            obj.put("appdata", appdata);
            JSONArray array = new JSONArray();
            for (String s : syncAppdata) {
                array.put(s);
            }
            obj.put("packages", array);
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(new File(CONFIG_PATH))));
            writer.write(obj.toString());
            writer.flush();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void saveSettings(boolean changeConfig, boolean wallpaper, boolean wifi, boolean appdata,
                             List<String> syncAppdata, boolean startupmenu, boolean appstore) {
        if (mIsBusy) {
            return;
        }
        mIsBusy = true;
        mWallpaper = wallpaper;
        mWifi = wifi;
        mAppdata = appdata;
        mStartupmenu = startupmenu;
        mAppstore = appstore;
        mSyncAppdata = syncAppdata;
        if (changeConfig) {
            saveConfigs(wallpaper, wifi, appdata, syncAppdata, startupmenu, appstore);
            stopTimer();
            mIsBusy = false;
            startTimer();
            return;
        }
        if (wallpaper) {
            exportWallpaperFiles();
        }
        if (wifi) {
            exportWifiFiles();
        }
        if (appdata) {
            exportFiles(syncAppdata);
        }
        if (startupmenu) {
            List<String> packages = new ArrayList();
            packages.add(SYSTEM_PATH_STATUSBAR);
            exportFiles(packages);
        }
        if (appstore) {
            String path = CONFIG_DIR_PATH + SEAFILE_PATH_APPSTORE;
            if (Utils.checkFile(path)) {
                Utils.exec("rm " + path);
            }
            exportAppstoreFiles();
        }
        File file = new File(CONFIG_DIR_PATH);
        File[] files = file.listFiles();
        final ArrayList<String> list = new ArrayList<>();
        for (File f : files) {
            list.add(f.getAbsolutePath());
        }
        new Thread() {
            @Override
            public void run() {
                super.run();
                uploadFile("", list, false);
            }
        }.start();
        mIsBusy = false;
    }

    private void exportWallpaperFiles() {
        String path = CONFIG_DIR_PATH + SEAFILE_PATH_WALLPAPER;
        if (Utils.checkFile(SYSTEM_PATH_WALLPAPER)) {
            Utils.exec("cp -f " + SYSTEM_PATH_WALLPAPER + " " + path);
            Utils.chownFile(path, String.valueOf(1111));
        } else {
            Utils.exec("rm -r " + path);
        }
    }

    private void importWallpaperFiles() {
        String path = CONFIG_DIR_PATH + SEAFILE_PATH_WALLPAPER;
        if (Utils.checkFile(path)) {
            Utils.exec("busybox chmod 777 " + path);
            try {
                WallpaperManager.getInstance(this).setStream(new FileInputStream(path));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        new File(path).delete();
    }

    private void importWifiFiles() {
        String path = CONFIG_DIR_PATH + SEAFILE_PATH_WIFI;
        if (Utils.checkFile(path)) {
            WifiManager wifiManager = (WifiManager) (getApplicationContext().getSystemService(Context.WIFI_SERVICE));
            if (wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Utils.untarFile(path);
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        new File(path).delete();
    }

    private void exportWifiFiles() {
        Utils.tarFile(SYSTEM_PATH_WIFI_INFO, CONFIG_DIR_PATH + SEAFILE_PATH_WIFI);
//        Utils.chownFile(CONFIG_DIR_PATH + SEAFILE_PATH_WIFI, String.valueOf(mUid));
    }

    private void exportAppstoreFiles() {
        List<PackageInfo> tempInfos = getPackageManager().getInstalledPackages(0);
        List<PackageInfo> packageInfos = new ArrayList<>();
        for (PackageInfo f : tempInfos) {
            if ((f.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                packageInfos.add(f);
            }
        }
        BufferedWriter appWriter = null;
        try {
            String path = CONFIG_DIR_PATH + SEAFILE_PATH_APPSTORE;
            if (packageInfos.size() > 0) {
                Utils.exec("echo > " + path + ";busybox chmod 777 " + path);
                appWriter = new BufferedWriter(new FileWriter(path));
                for (PackageInfo f : packageInfos) {
                    appWriter.write(f.packageName);
                    appWriter.newLine();
                    appWriter.flush();
                }
            }
            if (appWriter != null) {
                appWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (appWriter != null) {
                try {
                    appWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void importFiles(List<String> packages, String configPath) {
        HashMap<String, String> map = Utils.getFiles("/data/data");
        for (int i = 0; i < packages.size(); i++) {
            String uid = map.get(packages.get(i));
            if (!TextUtils.isEmpty(uid)) {
                Utils.untarFile(CONFIG_DIR_PATH +
                        configPath + packages.get(i) + ".tar.gz");
                Utils.chownFile(SYSTEM_PATH_DATA + packages.get(i), uid);
            }
        }
    }

    private void exportFiles(List<String> packages) {
        for (int i = 0; i < packages.size(); i++) {
            File configFile = new File(CONFIG_DIR_PATH);
            Utils.tarFile(SYSTEM_PATH_DATA + packages.get(i),
                    configFile.getAbsolutePath() + "/" + packages.get(i) + ".tar.gz");
//            Utils.chownFile(configFile.getAbsolutePath() + "/" + packages.get(i) + ".tar.gz",
//                    String.valueOf(mUid));
        }
    }

    private void restoreFinish() {
        if (mTempStartupMenu) {
            List<String> packages = new ArrayList();
            packages.add(SYSTEM_PATH_STATUSBAR);
            importFiles(packages, "/");
            new Handler().post(new Runnable() {
                public void run() {
                    Process pro = null;
                    BufferedReader in = null;
                    ArrayList<String> temp = new ArrayList();
                    try {
                        pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "netcfg"});
                        in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
                        String line;

                        while ((line = in.readLine()) != null) {
                            String tempStr = line.split("\\s+")[0];
                            if (tempStr.startsWith("eth")) {
                                temp.add(tempStr);
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
                    StringBuffer sb = new StringBuffer();
                    for (String str : temp) {
                        sb.append("netcfg ").append(str).append(" down;");
                    }
                    Utils.exec(new String[]{"su", "-c",
                            sb.toString() + "kill " + Jni.nativeKillPid()});
                }
            });
        }
    }

    public void startTimer() {
        mTimer = new Timer();
        mAutoTask = new AutoBackupTask();
        mTimer.schedule(mAutoTask, 0, 3600000);
    }

    public void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mAutoTask != null) {
            mAutoTask.cancel();
            mAutoTask = null;
        }
    }

    private class AutoBackupTask extends TimerTask {
        @Override
        public void run() {
            saveSettings(false, mWallpaper, mWifi, mAppdata, mSyncAppdata, mStartupmenu, mAppstore);
        }
    }

    @Override
    public void onDestroy() {
        stopTimer();
        super.onDestroy();
    }


    public void uploadFile(final String dirPath, final ArrayList<String> files, final boolean update) {
        Log.i("wwww", token);
        new Thread() {
            @Override
            public void run() {

                mFileBeans.clear();
                for (String file : files) {
                    FileBean bean = new FileBean(new File(file), file);
                    mFileBeans.add(bean);
                }
                String url = null;
                String fileStr = null;
                String localFilePath = null;
                try {
                    url = getUploadLink(repoID, update);
                    while (mFileBeans.size() != 0) {
                        FileBean fileBean = mFileBeans.get(0);
                        try {
                            delete(repoID, "/" + fileBean.file.getName(), false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        File file = fileBean.file;
                        localFilePath = file.getAbsolutePath();
                        fileStr = uploadFileCommon(url, repoID, dirPath, localFilePath, update);
                        mFileBeans.remove(0);
                        if (fileStr == null) {
                            fileBean.repeat++;
                            if (fileBean.repeat >= 3) {
                                // todo show error tips
                            } else {
                                mFileBeans.add(fileBean);
                            }
                        } else {
                            // todo progressbar
                            if (!fileBean.file.getAbsolutePath().contains(CONFIG_PATH)) {
                                fileBean.file.delete();
                                android.util.Log.i("chenp", "uploadfile = " + file.getPath());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void downloadFile(final List<String> downloadPaths) {
        mFileBeans.clear();
        for (String path : downloadPaths) {
            mFileBeans.add(new FileBean(null, path));
        }

        while (mFileBeans.size() != 0) {
            File downloadFile = null;

            FileBean fileBean = mFileBeans.get(0);
            String path = fileBean.path;
            final File localFile = getLocalRepoFile(path);
            try {
                Pair<String, String> ret = getDownloadLink(repoID, path, false);
                mFileBeans.remove(0);
                if (ret == null) {
                    continue;
                }
                String dlink = ret.first;
                String fileID = ret.second;

                File file = getFileFromLink(dlink, path, localFile.getPath(), fileID);
                Pair<String, File> newRet;

                if (file != null) {
                    newRet = new Pair<String, File>(fileID, file);
                    downloadFile = newRet.second;
                    if (downloadFile == null) {
                        fileBean.repeat++;
                        if (fileBean.repeat >= 3) {
                            // todo show error tips
                        } else {
                            mFileBeans.add(fileBean);
                        }
                    } else {
                        // todo progressbar
                        android.util.Log.i("chenp", "downloadFile = " + downloadFile.getPath());
                    }
                    //
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Pair<String, String> getDownloadLink(String repoID, String path, boolean isReUsed) {

        Pair pair = null;
        try {
            String apiPath = String.format("api2/repos/%s/file/", repoID);
            Map<String, Object> params = new HashMap<>();
            params.put("p", URLEncoder.encode(path, "UTF-8"));
            params.put("op", "download");
            if (isReUsed) {
                params.put("reuse", 1);
            }

            HttpRequest req = HttpRequest.get(server + apiPath, params, false);
            req.readTimeout(READ_TIMEOUT)
                    .connectTimeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .header("Authorization", "Token " + token);

            HttpURLConnection conn = req.getConnection();
            if (conn instanceof HttpsURLConnection) {
                req.trustAllHosts();
                HttpsURLConnection sconn = (HttpsURLConnection) conn;
            }

            if (req.code() != HttpURLConnection.HTTP_OK) {
                Log.d(DEBUG_TAG, "HTTP request failed : " + req.url() + ", " + req.code() + ", " + req.message());
                if (req.message() == null) {
                    throw new NetworkErrorException();
                } else if (req.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    String wiped = req.header("X-Seafile-Wiped");
                    if (wiped != null) {
                        throw new RemoteException();
                    } else {
                        throw new Exception(req.message());
                    }
                } else {
                    if (req.code() == 404) {
                        return null;
                    }
                    throw new Exception(req.message());
                }
            } else {
                Log.v(DEBUG_TAG, "HTTP request ok : " + req.url());
            }

            String result = new String(req.bytes(), "UTF-8");
            String fileID = req.header("oid");
            if (result.startsWith("\"http") && fileID != null) {
                String url = result.substring(1, result.length() - 1);
                pair = new Pair<String, String>(url, fileID);
                return pair;
            } else {
            }
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        } catch (NetworkErrorException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pair;
    }

    private File getFileFromLink(String dlink, String path, String localPath,
                                 String oid)
            throws Exception {
        if (dlink == null)
            return null;

        File file = new File(localPath);

        int i = dlink.lastIndexOf('/');
        String quoted = dlink.substring(0, i) + "/" +
                URLEncoder.encode(dlink.substring(i + 1), "UTF-8");

        HttpRequest req = HttpRequest.get(quoted).connectTimeout(CONNECTION_TIMEOUT).followRedirects(true);
        HttpURLConnection conn = req.getConnection();
        if (conn instanceof HttpsURLConnection) {
            req.trustAllHosts();
            HttpsURLConnection sconn = (HttpsURLConnection) conn;
        }

        req.receive(file);
        return file;
    }

    public File getLocalRepoFile(String path) throws RuntimeException {
        String localPath = CONFIG_DIR_PATH + "/" + path;
        Log.i("wwww", localPath);
        File parentDir = new File(getParentPath(localPath));
        if (!parentDir.exists()) {
            // TODO should check if the directory creation succeeds
            parentDir.mkdirs();
        }

        return new File(localPath);
    }

    public String pathJoin(String first, String... rest) {
        StringBuilder result = new StringBuilder(first);
        for (String b : rest) {
            boolean resultEndsWithSlash = result.toString().endsWith("/");
            boolean bStartWithSlash = b.startsWith("/");
            if (resultEndsWithSlash && bStartWithSlash) {
                result.append(b.substring(1));
            } else if (resultEndsWithSlash || bStartWithSlash) {
                result.append(b);
            } else {
                result.append("/");
                result.append(b);
            }
        }
        return result.toString();
    }

    public String getParentPath(String path) {
        if (path == null) {
            // the caller should not give null
            Log.w(DEBUG_TAG, "null in getParentPath");
            return null;
        }

        if (!path.contains("/")) {
            return "/";
        }

        String parent = path.substring(0, path.lastIndexOf("/"));
        if (parent.equals("")) {
            return "/";
        } else
            return parent;
    }

    private String getUploadLink(String repoID, boolean update) throws Exception {
        try {
            String apiPath;
            if (update) {
                apiPath = "api2/repos/" + repoID + "/update-link/";
            } else {
                apiPath = "api2/repos/" + repoID + "/upload-link/";
            }
//            req = prepareApiGetRequest(apiPath);
            HttpRequest req = HttpRequest.get(server + apiPath, null, false);
            req.readTimeout(READ_TIMEOUT)
                    .connectTimeout(CONNECTION_TIMEOUT)
                    .followRedirects(true)
                    .header("Authorization", "Token " + token);
            HttpURLConnection conn = req.getConnection();
            if (conn instanceof HttpsURLConnection) {
                req.trustAllHosts();
                HttpsURLConnection sconn = (HttpsURLConnection) conn;
            }

            String result = new String(req.bytes(), "UTF-8");
            // should return "\"http://gonggeng.org:8082/...\"" or "\"https://gonggeng.org:8082/...\"
            if (result.startsWith("\"http")) {
                // remove the starting and trailing quote
                return result.substring(1, result.length() - 1);
            } else
                throw new Exception();
        } catch (Exception e) {
            Log.d(DEBUG_TAG, e.getMessage());
            throw e;
        }
    }

    private String uploadFileCommon(String link, String repoID, String dir,
                                    String filePath, boolean update)
            throws Exception, IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("File not exists");
        }
        MultipartBody.Builder builder = new MultipartBody.Builder();
        //set type
        builder.setType(MultipartBody.FORM);
        // "target_file" "parent_dir"  must be "/" end off
        if (update) {
            String targetFilePath = pathJoin(dir, file.getName());
            builder.addFormDataPart("target_file", targetFilePath);
        } else {
            builder.addFormDataPart("parent_dir", dir);
        }

        builder.addFormDataPart("file", file.getName(), createProgressRequestBody(file));
        //create RequestBody
        RequestBody body = builder.build();
        //create Request
        final Request request = new Request.Builder().url(link).post(body).header("Authorization", "Token " + token).build();
        OkHttpClient client;
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.addInterceptor(new LoggingInterceptor()); //add okhttp log
        client = clientBuilder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        }).retryOnConnectionFailure(true)
                .connectTimeout(TIMEOUT_COUNT, TimeUnit.MINUTES)
                .readTimeout(TIMEOUT_COUNT, TimeUnit.MINUTES)
                .writeTimeout(TIMEOUT_COUNT, TimeUnit.MINUTES)
                .build();


        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            String str = response.body().string();

            if (!TextUtils.isEmpty(str)) {
                return str.replace("\"", "");
            }
        }
        throw new Exception("File upload failed");
    }

    public <T> RequestBody createProgressRequestBody(final File file) {
        return new RequestBody() {

            public long temp = System.currentTimeMillis();

            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() {
                return file.length();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source;
                try {
                    source = Okio.source(file);
                    Buffer buf = new Buffer();
                    // long remaining = contentLength();
                    long current = 0;
                    for (long readCount; (readCount = source.read(buf, 2048)) != -1; ) {
                        sink.write(buf, readCount);
                        current += readCount;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * output log interceptor
     */
    private class LoggingInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            String content = response.body().string();
            Log.i("LoggingInterceptor", content);
            return response.newBuilder()
                    .body(okhttp3.ResponseBody.create(response.body().contentType(), content))
                    .build();
        }
    }

    private class FileBean {
        File file;
        int repeat;
        String path;

        public FileBean(File file, String path) {
            this.file = file;
            this.path = path;
        }
    }

    public Pair<String, String> delete(String repoID, String path,
                                       boolean isdir) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            URLEncoder.encode(path, "UTF-8");
            params.put("p", URLEncoder.encode(path, "UTF-8").replaceAll("\\+", "%20"));
            params.put("reloaddir", "true");
            String suffix = isdir ? "/dir/" : "/file/";
            HttpRequest req = HttpRequest.delete(server + "api2/repos/" + repoID + suffix, params, false)
                    .followRedirects(true)
                    .connectTimeout(CONNECTION_TIMEOUT);

            req.header("Authorization", "Token " + token);

            HttpURLConnection conn = req.getConnection();
            if (conn instanceof HttpsURLConnection) {
                req.trustAllHosts();
                HttpsURLConnection sconn = (HttpsURLConnection) conn;
            }

            if (req.code() != HttpURLConnection.HTTP_OK) {
                if (req.message() == null) {
                    throw new NetworkErrorException();
                } else if (req.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    String wiped = req.header("X-Seafile-Wiped");
                    if (wiped != null) {
                        throw new RemoteException();
                    } else {
                        throw new Exception(req.message());
                    }
                } else {
                    throw new Exception(req.message());
                }
            }

//            checkRequestResponseStatus(req, HttpURLConnection.HTTP_OK);

            String newDirID = req.header("oid");
            if (newDirID == null) {
                return null;
            }

            String content = new String(req.bytes(), "UTF-8");
            if (content.length() == 0) {
                return null;
            }

            return new Pair<String, String>(newDirID, content);
        } catch (Exception e) {
            android.util.Log.i("chenp", e.getMessage());
            throw e;
        }
    }


    public void getLibraryContent(String repoID, String path)
            throws SeafException {
        Pair pair = null;
        String apiPath = String.format("api2/repos/%s/dir/", repoID);
//            Map<String, Object> params = Maps.newHashMap();
        Map<String, Object> params = new HashMap<>();
        try {
            params.put("p", URLEncoder.encode(path, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        HttpRequest req = HttpRequest.get(server + apiPath, params, false);
        req.readTimeout(READ_TIMEOUT)
                .connectTimeout(CONNECTION_TIMEOUT)
                .followRedirects(true)
                .header("Authorization", "Token " + token);

        HttpURLConnection conn = req.getConnection();
        if (conn instanceof HttpsURLConnection) {
            req.trustAllHosts();
            HttpsURLConnection sconn = (HttpsURLConnection) conn;
        }

        String dirID = req.header("oid");
        String content;
        if (dirID == null) {
            throw SeafException.unknownException;
        }

        byte[] rawBytes = req.bytes();
        if (rawBytes == null) {
            throw SeafException.unknownException;
        }
        try {
            content = new String(rawBytes, "UTF-8");
            pair = new Pair<String, String>(dirID, content);
            if (pair.second != null) {
                dirID = (String) pair.first;
                content = (String) pair.second;
                android.util.Log.i("chenpeng", dirID + ", " + content);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void createNewLibrary(String repoName/*, String description*/, boolean withToken) {
        // ""
        HttpRequest req = HttpRequest.post(server + "api2/repos/", null, false)
                .followRedirects(true)
                .connectTimeout(CONNECTION_TIMEOUT);

        if (withToken) {
            req.header("Authorization", "Token " + token);
        }

        HttpURLConnection conn = req.getConnection();
        if (conn instanceof HttpsURLConnection) {
            req.trustAllHosts();
            HttpsURLConnection sconn = (HttpsURLConnection) conn;
        }

        req.form("name", repoName);

//        if (description.length() > 0) {
//            req.form("desc", description);
//        }

        try {
            if (req.code() != HttpURLConnection.HTTP_OK) {
                if (req.message() == null) {

                    throw new NetworkErrorException();

                } else if (req.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    String wiped = req.header("X-Seafile-Wiped");
                    if (wiped != null) {
                        throw new RemoteException();
                    } else {
                        throw new Exception(req.code() + req.message());
                    }
                } else {
                    throw new Exception(req.code() + req.message());
                }
            }
        } catch (NetworkErrorException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
