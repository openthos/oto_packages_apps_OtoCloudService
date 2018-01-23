package com.openthos.seafile;

import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.IBinder;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class MainSeafileService extends Service {
    private InitSeafileThread mInitSeafileThread;
    private SeafileThread mSeafileThread;
    public static SeafileAccount mAccount;
    public static SeafileUtils.SeafileSQLConsole mConsole;
    public static String mLibrary;


    @Override
    public void onCreate() {
        super.onCreate();

        mInitSeafileThread = new InitSeafileThread();
        mInitSeafileThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class InitSeafileThread extends Thread {
        @Override
        public void run() {
            super.run();
            SeafileUtils.init();
            SeafileUtils.start();
            mSeafileThread = new SeafileThread();
            mSeafileThread.start();
        }
    }

    private class SeafileThread extends Thread {
        private boolean isExistsSetting = false;
        private boolean isExistsFileManager = false;
        private String id = "";
        private String settingId = "";

        @Override
        public void run() {
            super.run();
            ContentResolver mResolver = MainSeafileService.this.getContentResolver();
            Uri uriQuery = Uri.parse(SeafileUtils.OPENTHOS_URI);
            Cursor cursor = mResolver.query(uriQuery, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    //current openthos id and password
                    SeafileUtils.mUserId = cursor.getString(cursor.getColumnIndex("openthosID"));
                    SeafileUtils.mUserPassword =
                            cursor.getString(cursor.getColumnIndex("password"));
                    break;
                }
                cursor.close();
            }
            if (TextUtils.isEmpty(SeafileUtils.mUserId)
                    || TextUtils.isEmpty(SeafileUtils.mUserPassword)) {
                return;
            }
            mLibrary = getLibrarys();
            //
            mAccount = new SeafileAccount();
            mAccount.mUserName = SeafileUtils.mUserId;
            mConsole = new SeafileUtils.SeafileSQLConsole(MainSeafileService.this);
            mAccount.mUserId = mConsole.queryAccountId(mAccount.mUserName);
            mAccount.mFile = new File(SeafileUtils.SEAFILE_DATA_PATH, mAccount.mUserName);
            if (!mAccount.mFile.exists()) {
                mAccount.mFile.mkdirs();
            }
            //
            File config = new File(SeafileUtils.SEAFILE_DATA_PATH, SeafileUtils.mUserId);
            if (!config.exists()) {
                config.mkdirs();
            }
            try {
                if (mLibrary == null || TextUtils.isEmpty(mLibrary)) {
                    mLibrary = getSharedPreferences(SeafileUtils.SEAFILE_DATA,
                            Context.MODE_PRIVATE).getString(SeafileUtils.SEAFILE_DATA, "");
                    isExistsFileManager = true;
                }
                JSONArray jsonArray = new JSONArray(mLibrary);
                JSONObject jsonObject = null;
                for (int i = 0; i < jsonArray.length(); i++) {
                    SeafileLibrary seafileLibrary = new SeafileLibrary();
                    jsonObject = jsonArray.getJSONObject(i);
                    seafileLibrary.libraryName = jsonObject.getString("name");
                    seafileLibrary.libraryId = jsonObject.getString("id");
                    if (seafileLibrary.libraryName.equals(SeafileUtils.SETTING_SEAFILE_NAME)) {
                        isExistsSetting = true;
                        settingId = seafileLibrary.libraryId;
                        continue;
                    }
                    if (!seafileLibrary.libraryName.equals(SeafileUtils.FILEMANAGER_SEAFILE_NAME)) {
                        continue;
                    }
                    isExistsFileManager = true;
                    id = seafileLibrary.libraryId;
                    mAccount.mLibrarys.add(seafileLibrary);
                }
                getSharedPreferences(SeafileUtils.SEAFILE_DATA, Context.MODE_PRIVATE).edit()
                        .putString(SeafileUtils.SEAFILE_DATA, mLibrary).commit();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (!isExistsFileManager) {
                SeafileLibrary seafileLibrary = new SeafileLibrary();
                seafileLibrary.libraryName = SeafileUtils.FILEMANAGER_SEAFILE_NAME;
                seafileLibrary.libraryId
                        = SeafileUtils.create(SeafileUtils.FILEMANAGER_SEAFILE_NAME);
                mAccount.mLibrarys.add(seafileLibrary);
            }
            if (mAccount.mLibrarys.size() > 0) {
                for (SeafileLibrary seafileLibrary : mAccount.mLibrarys) {
                    String name = seafileLibrary.libraryName;
                    int isSync = mConsole.queryFile(mAccount.mUserId,
                            seafileLibrary.libraryId, seafileLibrary.libraryName);
                    seafileLibrary.isSync = isSync;
                    if (isSync == SeafileUtils.SYNC) {
                        SeafileUtils.sync(seafileLibrary.libraryId,
                                new File(mAccount.mFile, seafileLibrary.libraryName)
                                        .getAbsolutePath());
                    }
                }
            }
            File settingSeafile = new File(SeafileUtils.SETTING_SEAFILE_PATH);
            if (!settingSeafile.exists()) {
                settingSeafile.mkdirs();
            }
            if (!isExistsSetting) {
                settingId = SeafileUtils.create(SeafileUtils.SETTING_SEAFILE_NAME);
            }
            SeafileUtils.sync(settingId, SeafileUtils.SETTING_SEAFILE_PROOT_PATH);
        }
    }

    public boolean isInitSeafile() {
        return mInitSeafileThread.isAlive();
    }

    public boolean isSeafile() {
        return mSeafileThread.isAlive();
    }

    private String getLibrarys() {
        if (!SeafileUtils.isNetworkOn(this)) {
            return "";
        }
        String token = SeafileUtils.getToken(this);
        if (token == null || TextUtils.isEmpty(token)) {
            return "";
        }
        return SeafileUtils.getResult(token);
    }
}
