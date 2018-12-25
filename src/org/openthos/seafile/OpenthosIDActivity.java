/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openthos.seafile;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenthosIDActivity extends Activity {
    public static final int MSG_REGIST_SEAFILE_OK = 0x1004;
    public static final int MSG_REGIST_SEAFILE_FAILED = 0x1005;
    public static final int MSG_LOGIN_SEAFILE_OK = 0x1006;
    public static final int MSG_LOGIN_SEAFILE_FAILED = 0x1007;
    public static final int MSG_REGIST_SEAFILE = 0x1008;
    public static final int MSG_LOGIN_SEAFILE = 0x1009;
    public static final int MSG_CHANGE_URL = 0x1010;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id);
        getFragmentManager().beginTransaction().
                replace(R.id.content, new OpenthosIDFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ValidFragment")
    private class OpenthosIDFragment extends PreferenceFragment
        implements  OnPreferenceClickListener {
        private static final String TAG = "OpenthosIDActivity";
        private static final String KEY_OPENTHOS_SCREEN = "openthos_screen";
        private static final String KEY_OPENTHOS_ID = "openthos_id";
        private static final String KEY_REGISTER = "openthos_register";
        private static final String KEY_BIND = "openthos_bind";
        private static final String KEY_UNBUND = "openthos_unbund";
        private static final String KEY_URL = "openthos_url";

        private Preference mOpenthosIDPref;
        private Preference mRegisterPref;
        private Preference mBindPref;
        private Preference mUnbundPref;
        private Preference mUrlPref;
        private PreferenceScreen mScreenPref;

        private String mOpenthosID;
        private String mPassword;
        private Handler mHandler;
        private SeafileAccount mAccount;

        private String mRegisterID, mRegisterEmail, mRegisterPass, mRegisterPassConfirm;
        private ISeafileService mISeafileService;
        private SeafileServiceConnection mSeafileServiceConnection;
        private IBinder mSeafileBinder = new SeafileBinder();


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mAccount = new SeafileAccount(OpenthosIDActivity.this);
            mSeafileServiceConnection = new SeafileServiceConnection();
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("org.openthos.seafile",
                        "org.openthos.seafile.SeafileService"));
            getActivity().bindService(intent, mSeafileServiceConnection, Context.BIND_AUTO_CREATE);

            addPreferencesFromResource(R.xml.openthos_id_prefs);
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            mScreenPref = getPreferenceScreen();
            mOpenthosIDPref = findPreference(KEY_OPENTHOS_ID);
            mRegisterPref = findPreference(KEY_REGISTER);
            mRegisterPref.setOnPreferenceClickListener(this);
            mBindPref = findPreference(KEY_BIND);
            mBindPref.setOnPreferenceClickListener(this);
            mUnbundPref = findPreference(KEY_UNBUND);
            mUnbundPref.setOnPreferenceClickListener(this);
            mUrlPref = findPreference(KEY_URL);
            mUrlPref.setOnPreferenceClickListener(this);
            mUrlPref.setSummary(mAccount.mOpenthosUrl);
            if (mAccount.isExistsAccount()) {
                updateID(mAccount.mUserName);
                mBindPref.setEnabled(false);
                mUnbundPref.setEnabled(true);
            } else {
                updateID(null);
                mBindPref.setEnabled(true);
                mUnbundPref.setEnabled(false);
            }
            mHandler = new Handler() {

                @Override
                public void handleMessage (Message msg) {
                    switch (msg.what) {
                        case MSG_REGIST_SEAFILE:
                             try {
                                 mISeafileService.registeAccount(
                                         mRegisterID, mRegisterEmail, mRegisterPass);
                             } catch (RemoteException e) {
                                 e.printStackTrace();
                             }
                            break;
                        case MSG_LOGIN_SEAFILE:
                            try {
                                mISeafileService.setBinder(mSeafileBinder);
                                mISeafileService.loginAccount(mOpenthosID, mPassword);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            break;
                        case MSG_LOGIN_SEAFILE_OK:
                            mAccount = null;
                            mAccount = new SeafileAccount(OpenthosIDActivity.this);
                            updateID(mAccount.mUserName);
                            mBindPref.setEnabled(false);
                            mUnbundPref.setEnabled(true);
                            break;
                        case MSG_CHANGE_URL:
                            updateOpenthosUrl(msg.obj.toString());
                            break;
                    }
                }
            };
        }

        @Override
        public void onDestroy() {
            try {
                mISeafileService.unsetBinder(mSeafileBinder);
                mBindPref.setEnabled(false);
                mUnbundPref.setEnabled(true);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            super.onDestroy();
        }

        @Override
        public boolean onPreferenceClick(final Preference pref) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (pref == mRegisterPref) {
                View viewRegister = LayoutInflater.from(getActivity())
                                                        .inflate(R.layout.dialog_register, null);
                final EditText openthosIDRegister = (EditText) viewRegister
                                                      .findViewById(R.id.dialog_openthosId);
                final EditText openthosEmailRegister = (EditText) viewRegister
                                                      .findViewById(R.id.dialog_openthos_email);
                final EditText openthosPassRegister = (EditText) viewRegister
                                                      .findViewById(R.id.dialog_openthos_pass);
                final EditText openthosPassConfirm = (EditText) viewRegister
                                                      .findViewById(R.id.dialog_openthos_pass_confirm);
                builder.setTitle(R.string.account_register);
                builder.setView(viewRegister);
                builder.setPositiveButton(R.string.account_user_register,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // TODO Auto-generated method stub
                            ConnectivityManager mCM = (ConnectivityManager)getSystemService(
                                                          Context.CONNECTIVITY_SERVICE);
                            NetworkInfo networkINfo = mCM.getActiveNetworkInfo();
                            if (networkINfo == null) {
                                Toast.makeText(getActivity(),
                                               getText(R.string.toast_network_not_connect),
                                               Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String regEx="[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~]"; 
                            Pattern pattern = Pattern.compile(regEx);
                            Matcher matcher = pattern.matcher(openthosIDRegister.getText().toString().trim());
                            if (matcher.find()) {
                                Toast.makeText(getActivity(),
                                               getText(R.string.username_illegal),
                                               Toast.LENGTH_SHORT).show();
                                return;
                            }
 
                            mRegisterID = openthosIDRegister.getText().toString().trim();
                            mRegisterEmail = openthosEmailRegister.getText().toString().trim();
                            mRegisterPass = openthosPassRegister.getText().toString().trim();
                            mRegisterPassConfirm = openthosPassConfirm.getText().toString().trim();
                            mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE);
                            dialog.dismiss();
                        }
                    });
            } else if (pref == mBindPref) {
                View viewBind = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_bind, null);
                final EditText userID_bind = (EditText) viewBind.findViewById(R.id.dialog_name);
                final EditText userPassword_bind = (EditText) viewBind.
                                                   findViewById(R.id.dialog_name_bind);
                builder.setTitle(R.string.account_bind);
                builder.setView(viewBind);
                builder.setPositiveButton(R.string.confirm,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // TODO Auto-generated method stub
                            mOpenthosID = userID_bind.getText().toString().trim();
                            mPassword = userPassword_bind.getText().toString().trim();
                            mHandler.sendEmptyMessage(MSG_LOGIN_SEAFILE);

                            dialog.dismiss();
                        }
                    });
            } else if (pref == mUnbundPref) {
                builder.setMessage(R.string.account_judge);
                builder.setPositiveButton(R.string.account_yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                            Uri uriDelete =
                                Uri.parse("content://com.otosoft.tools.myprovider/openthosID");
                            updateID(null);
                            dialog.dismiss();
                            try {
                                mISeafileService.stopAccount();
                                mBindPref.setEnabled(true);
                                mUnbundPref.setEnabled(false);
                                notifySeafileKeeper();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
            } else if (pref == mUrlPref) {
                LayoutInflater layoutInflater = getActivity().getLayoutInflater();
                View changeUrlDialog = layoutInflater
                        .inflate(R.layout.dialog_change_url, null);
                builder.setTitle(R.string.title_change_url);
                builder.setView(changeUrlDialog);
                builder.setCancelable(true);

                RadioGroup group = (RadioGroup) changeUrlDialog.findViewById(R.id.url_group);
                final RadioButton rbDev = (RadioButton) changeUrlDialog.findViewById(R.id.url_dev);
                final RadioButton rbLab = (RadioButton) changeUrlDialog.findViewById(R.id.url_lab);
                final RadioButton rbCloud = (RadioButton) changeUrlDialog.findViewById(R.id.url_cloud);

                if (mAccount.mOpenthosUrl.equals(rbDev.getText().toString())) {
                    rbDev.setChecked(true);
                } else if (mAccount.mOpenthosUrl.equals(rbLab.getText().toString())) {
                    rbLab.setChecked(true);
                } else if (mAccount.mOpenthosUrl.equals(rbCloud.getText().toString())) {
                    rbCloud.setChecked(true);
                }
                group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        if (checkedId == rbDev.getId()) {
                            rbDev.setChecked(true);
                        } else if (checkedId == rbLab.getId()) {
                            rbLab.setChecked(true);
                        } else if (checkedId == rbCloud.getId()) {
                            rbCloud.setChecked(true);
                        }
                    }
                });
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        String tempUrl = "";
                        if (rbDev.isChecked()) {
                            tempUrl = rbDev.getText().toString();
                        } else if (rbLab.isChecked()) {
                            tempUrl = rbLab.getText().toString();
                        } else if (rbCloud.isChecked()) {
                            tempUrl = rbCloud.getText().toString();
                        }
                        if (!tempUrl.equals(mAccount.mOpenthosUrl)) {
                            updateOpenthosUrl(tempUrl);
                        }
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                    }
                });
            }
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.create().show();
            return false;
        }

        private void updateID(String ID) {
            mOpenthosIDPref.setTitle(ID);
            if (!TextUtils.isEmpty(ID)) {
                mScreenPref.addPreference(mOpenthosIDPref);
            } else {
                mScreenPref.removePreference(mOpenthosIDPref);
            }
        }

        private void updateOpenthosUrl(String url) {
            boolean success = false;
            try {
                success = mISeafileService.setOpenthosUrl(url);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (success) {
                mUrlPref.setSummary(url);
                mBindPref.setEnabled(true);
                mUnbundPref.setEnabled(false);
                updateID(null);
                mAccount = null;
                mAccount = new SeafileAccount(OpenthosIDActivity.this);
                notifySeafileKeeper();
            }
        }

        private void notifySeafileKeeper() {
            try {
                String serverUrl = "server_url=" + mAccount.mOpenthosUrl;
                String user = "user=" + mAccount.mUserName;
                String action = "action=logout";
                FileWriter writer = new FileWriter(new File(SeafileUtils.SEAFILE_ACCOUNT_CONFIG));
                BufferedWriter bufferedWriter = new BufferedWriter(writer);
                bufferedWriter.write(serverUrl + "\n" + user + "\n" + action);
                bufferedWriter.flush();
                writer.close();
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private class SeafileServiceConnection implements ServiceConnection {
            public void onServiceConnected(ComponentName name, IBinder service) {
                mISeafileService = ISeafileService.Stub.asInterface(service);
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        }

        private class SeafileBinder extends Binder {

            @Override
            protected boolean onTransact(
                    int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == mISeafileService.getCodeLoginSuccess()) {
                    Message msg = new Message();
                    msg.obj = data.readString();
                    msg.what = MSG_LOGIN_SEAFILE_OK;
                    mHandler.sendMessage(msg);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        }
    }
}
