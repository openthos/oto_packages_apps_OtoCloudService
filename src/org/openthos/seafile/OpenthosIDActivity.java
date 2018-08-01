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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.openthos.seafile.ISeafileService;

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
        private AlertDialog mDialog = null;
        private TextServicesManager mTsm;
        private String mToastRelogin;

        private String mOpenthosID;
        private String mPassword;
        private Handler mHandler;

        private String mRegisterID, mRegisterEmail, mRegisterPass, mRegisterPassConfirm;
        private ISeafileService mISeafileService;
        private SeafileServiceConnection mSeafileServiceConnection;
        private IBinder mSeafileBinder = new SeafileBinder();


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
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
            Preference screen = findPreference(KEY_OPENTHOS_SCREEN);
            screen.setLayoutResource(R.layout.activity_main);
            mOpenthosIDPref = findPreference(KEY_OPENTHOS_ID);
            mRegisterPref = findPreference(KEY_REGISTER);
            mRegisterPref.setOnPreferenceClickListener(this);
            mBindPref = findPreference(KEY_BIND);
            mBindPref.setOnPreferenceClickListener(this);
            mUnbundPref = findPreference(KEY_UNBUND);
            mUnbundPref.setOnPreferenceClickListener(this);
            mUrlPref = findPreference(KEY_URL);
            mUrlPref.setOnPreferenceClickListener(this);
            mUrlPref.setSummary(SeafileUtils.mOpenthosUrl);

            if (SeafileUtils.isExistsAccount()) {
                updateID(SeafileUtils.mUserId);
                mBindPref.setEnabled(false);
                mUnbundPref.setEnabled(true);
            } else {
                updateID(null);
                mBindPref.setEnabled(true);
                mUnbundPref.setEnabled(false);
            }
            mToastRelogin = getText(R.string.toast_relogin).toString();
            mHandler = new Handler() {

                @Override
                public void handleMessage (Message msg) {
                    switch (msg.what) {
                        case MSG_REGIST_SEAFILE:
                             try {
                                 mISeafileService.setBinder(mSeafileBinder);
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
                        case MSG_REGIST_SEAFILE_OK:
                            Toast.makeText(getActivity(), msg.obj.toString(),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case MSG_REGIST_SEAFILE_FAILED:
                            Toast.makeText(getActivity(), msg.obj.toString(),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case MSG_LOGIN_SEAFILE_OK:
                            Toast.makeText(getActivity(), msg.obj.toString(),
                                    Toast.LENGTH_SHORT).show();
                            updateID(SeafileUtils.mUserId);
                            mBindPref.setEnabled(false);
                            mUnbundPref.setEnabled(true);
                            break;
                        case MSG_LOGIN_SEAFILE_FAILED:
                            Toast.makeText(getActivity(), msg.obj.toString(),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case MSG_CHANGE_URL:
                            updateOpenthosUrl(msg.obj.toString());
                            break;
                        default:
                            Toast.makeText(getActivity(),
                                    getText(R.string.toast_network_not_connect),
                                    Toast.LENGTH_SHORT).show();
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
                AlertDialog.Builder builder_register = new AlertDialog.Builder(getActivity());
                builder_register.setTitle(R.string.account_register);
                builder_register.setView(viewRegister);
                builder_register.setPositiveButton(R.string.account_user_register,
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
                            }

                            mRegisterID = openthosIDRegister.getText().toString().trim();
                            mRegisterEmail = openthosEmailRegister.getText().toString().trim();
                            mRegisterPass = openthosPassRegister.getText().toString().trim();
                            mRegisterPassConfirm = openthosPassConfirm.getText().toString().trim();
                            mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE);
                            dialog.dismiss();
                        }
                    });
                builder_register.setNegativeButton(R.string.cancel, null);
                builder_register.show();
            } else if (pref == mBindPref) {
                View viewBind = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_bind, null);
                final EditText userID_bind = (EditText) viewBind.findViewById(R.id.dialog_name);
                final EditText userPassword_bind = (EditText) viewBind.
                                                   findViewById(R.id.dialog_name_bind);
                AlertDialog.Builder builder_bind = new AlertDialog.Builder(getActivity());
                builder_bind.setTitle(R.string.account_bind);
                builder_bind.setView(viewBind);
                builder_bind.setPositiveButton(R.string.confirm,
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
                builder_bind.setNegativeButton(R.string.cancel, null);
                builder_bind.show();
            } else if (pref == mUnbundPref) {
                AlertDialog.Builder builder_unbund = new AlertDialog.Builder(getActivity());
                builder_unbund.setMessage(R.string.account_judge);
                builder_unbund.setPositiveButton(R.string.account_yes,
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
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                builder_unbund.setNegativeButton(R.string.account_no,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                             dialog.dismiss();
                        }
                    });
                builder_unbund.create().show();
            } else if (pref == mUrlPref) {
                try {
                    mISeafileService.setBinder(mSeafileBinder);
                    mISeafileService.setOpenthosUrl("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
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
            mUrlPref.setSummary(url);
            mBindPref.setEnabled(true);
            mUnbundPref.setEnabled(false);
            updateID(null);
            try {
                if (!TextUtils.isEmpty(SeafileUtils.mUserId)) {
                    mISeafileService.stopAccount();
                }
                Toast.makeText(OpenthosIDActivity.this, mToastRelogin, Toast.LENGTH_SHORT).show();
            } catch (RemoteException e) {
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
            if (code == mISeafileService.getCodeRegiestSuccess()) {
                Message msg = new Message();
                msg.obj = data.readString();
                msg.what = MSG_REGIST_SEAFILE_OK;
                mHandler.sendMessage(msg);
                reply.writeNoException();
                return true;
            } else if (code == mISeafileService.getCodeRegiestFailed()) {
                Message msg = new Message();
                msg.obj = data.readString();
                msg.what = MSG_REGIST_SEAFILE_FAILED;
                mHandler.sendMessage(msg);
                reply.writeNoException();
                return true;
            }
            if (code == mISeafileService.getCodeLoginSuccess()) {
                Message msg = new Message();
                msg.obj = data.readString();
                msg.what = MSG_LOGIN_SEAFILE_OK;
                mHandler.sendMessage(msg);
                return true;
            } else if (code == mISeafileService.getCodeLoginFailed()) {
                Message msg = new Message();
                msg.obj = data.readString();
                msg.what = MSG_LOGIN_SEAFILE_FAILED;
                mHandler.sendMessage(msg);
                reply.writeNoException();
                return true;
            } else if (code == mISeafileService.getCodeChangeUrl()) {
                Message msg = new Message();
                msg.obj = data.readString();
                msg.what = MSG_CHANGE_URL;
                mHandler.sendMessage(msg);
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
    }
}
