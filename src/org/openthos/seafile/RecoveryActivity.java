package org.openthos.seafile;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.openthos.seafile.SeafileService.SeafileBinder;

public class RecoveryActivity extends Activity {

    private SeafileBinder mSeafileBinder;
    private AlertDialog mLoadingDailog;
    private TextView mTvChooseFile;
    private Button mStartRecsovery, mChooseFile, mManualBackup;
    private Switch mSwitchAutoRecovery;
    private OnClickListener mOnClickListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescovery);
        ActionBar mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
        initView();
        Intent intent = new Intent(RecoveryActivity.this, SeafileService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Builder builder = new Builder(RecoveryActivity.this);
        builder.setMessage(getString(R.string.wait_for_ready));
        builder.setPositiveButton(R.string.exit,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                finish();
                            }
                        }, 1000);
                    }});
        mLoadingDailog = builder.create();
        mLoadingDailog.setCancelable(false);
        mLoadingDailog.show();
    }

    private void initView() {
        mOnClickListener = new ClickListener();
        mSwitchAutoRecovery = (Switch) findViewById(R.id.switch_auto_recovery);
        mSwitchAutoRecovery.setOnClickListener(mOnClickListener);
        mTvChooseFile = (TextView) findViewById(R.id.tv_choose_one);
        mChooseFile = (Button) findViewById(R.id.choose_one);
        mChooseFile.setOnClickListener(mOnClickListener);
        mStartRecsovery = (Button) findViewById(R.id.start_recovery);
        mStartRecsovery.setOnClickListener(mOnClickListener);
        mManualBackup= (Button) findViewById(R.id.manual_backup);
        mManualBackup.setOnClickListener(mOnClickListener);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSeafileBinder = (SeafileBinder) service;
            if (TextUtils.isEmpty(mSeafileBinder.getUserName())) {
                mLoadingDailog.setMessage(getString(R.string.no_openthos_id));
            } else {
                mLoadingDailog.dismiss();
                mSwitchAutoRecovery.setChecked(mSeafileBinder.getFlagAutoRecovery());
            }
        }

        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ClickListener implements OnClickListener {
        private String path;
        private String wallpaper;

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.choose_one:
                    File f = new File("/sdcard/seafile/" + mSeafileBinder.getUserName() + "/.UserConfig");
                    if (!f.exists() || f.list().length == 0) {
                        Toast.makeText(RecoveryActivity.this, "no file", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] temp = f.list();
                    List<String> fileList = Arrays.asList(temp);
                    Collections.sort(fileList, new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            return o1.compareTo(o2);
                        }
                    });
                    final String[] files = fileList.toArray(new String[fileList.size()]);
                    mTvChooseFile.setText(getString(R.string.choose_one_day) + ":" +files[0]);
                    mStartRecsovery.setVisibility(View.VISIBLE);
                    Builder b = new Builder(RecoveryActivity.this);
                    wallpaper = "/sdcard/seafile/" + mSeafileBinder.getUserName() + "/.UserConfig/wallpaper.tar.gz";
                    b.setSingleChoiceItems(files, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                           mTvChooseFile.setText(getString(R.string.choose_one_day) + ":" + files[which]);
                           path = "/sdcard/seafile/" + mSeafileBinder.getUserName() + "/.UserConfig/" + files[which];
                        }
                    });
                    b.setPositiveButton(R.string.okay, null);
                    b.create().show();
                    break;
                case R.id.start_recovery:
                    Builder builder = new Builder(RecoveryActivity.this);
                    builder.setMessage(getString(R.string.warn_restore_startupmenu));
                    builder.setPositiveButton(R.string.okay, null);
                    builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            startRecovery(path, wallpaper);
                        }
                    });
                    builder.setNegativeButton(R.string.cancel, null);
                    builder.create().show();
                    break;
                case R.id.switch_auto_recovery:
                    mSeafileBinder.setFlagAutoRecovery(mSwitchAutoRecovery.isChecked());
                    break;
                case R.id.manual_backup:
                    mSeafileBinder.manualBackup();
                    Toast.makeText(RecoveryActivity.this, "backup success", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private void startRecovery(String path, String wallpaper) {
        initEnvironment();
        BufferedReader br = null;
        try {
            Process pro = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "./data/data/org.openthos.seafile/rescovery " + path + " " + wallpaper});
            br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line = "";
            while ((line = br.readLine()) != null) {
            }
            br.close();
            br = null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        softRebootSystem();
    }

    private void softRebootSystem() {
        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        pm.reboot("true");
        //try {
        //    Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
        //    Method getService =
        //           ServiceManager.getMethod("getService", java.lang.String.class);
        //    Object oRemoteService =
        //           getService.invoke(null,Context.POWER_SERVICE);
        //    Class<?> cStub =
        //           Class.forName("android.os.IPowerManager$Stub");
        //    Method asInterface =
        //           cStub.getMethod("asInterface", android.os.IBinder.class);
        //    Object oIPowerManager =
        //           asInterface.invoke(null, oRemoteService);
        //    Method shutdown =
        //           oIPowerManager.getClass().getMethod("shutdown",
        //                                               boolean.class, boolean.class);
        //    shutdown.invoke(oIPowerManager,false,true);
        //} catch (ClassNotFoundException | NoSuchMethodException
        //        | IllegalAccessException | InvocationTargetException e) {
        //    e.printStackTrace();
        //}
        //new Handler().post(new Runnable() {
        //    public void run() {
        //        Process pro = null;
        //        BufferedReader in = null;
        //        ArrayList<String> temp = new ArrayList();
        //        try {
        //            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "netcfg"});
        //            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
        //            String line;
        //            while ((line = in.readLine()) != null) {
        //                String tempStr = line.split("\\s+")[0];
        //                if (tempStr.startsWith("eth")) {
        //                    temp.add(tempStr);
        //                }
        //            }
        //        } catch (IOException e) {
        //            e.printStackTrace();
        //        } finally {
        //            if (in != null) {
        //                try {
        //                    in.close();
        //                } catch (IOException e) {
        //                    e.printStackTrace();
        //                }
        //            }
        //        }
        //        StringBuffer sb = new StringBuffer();
        //        for (String str : temp) {
        //            sb.append("netcfg ").append(str).append(" down;");
        //        }
        //        Utils.exec(new String[]{"su", "-c",
        //                sb.toString() + "kill " + Jni.nativeKillSeafilePid()});
        //        Utils.exec(new String[]{"su", "-c",
        //                sb.toString() + "kill " + Jni.nativeKillPid()});
        //    }
        //});
    }

    private void initEnvironment() {
        String mRescoveryPath = "/data/data/org.openthos.seafile/rescovery";
        File f = new File(mRescoveryPath);
        if (!f.exists()) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = getAssets().open("rescovery");
                out = new FileOutputStream(f);
                int byteconut;
                byte[] bytes = new byte[1024];
                while ((byteconut = in.read(bytes)) != -1) {
                    out.write(bytes, 0, byteconut);
                }
                in.close();
                out.close();
                in = null;
                out = null;
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
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        BufferedReader br = null;
        try {
            Process pro = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "chmod 755 " + f.getAbsolutePath()});
            br = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line = "";
            while ((line = br.readLine()) != null) {
            }
            br.close();
            br = null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }
}
