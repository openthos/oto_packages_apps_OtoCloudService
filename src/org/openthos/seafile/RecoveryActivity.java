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
import java.util.ArrayList;

import org.openthos.seafile.SeafileService.SeafileBinder;

public class RecoveryActivity extends Activity {

    private SeafileBinder mSeafileBinder;
    private AlertDialog mLoadingDailog;
    private TextView mChooseFile;
    private Button mStartRecsovery;
    private OnClickListener mOnClickListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescovery);
        ActionBar mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
        Intent intent = new Intent(RecoveryActivity.this, SeafileService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        initView();
        Builder builder = new Builder(RecoveryActivity.this);
        builder.setMessage(getString(R.string.wait_for_ready));
        builder.setPositiveButton(R.string.exit,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }});
        mLoadingDailog = builder.create();
        mLoadingDailog.setCancelable(false);
        mLoadingDailog.show();
    }

    private void initView() {
        mOnClickListener = new ClickListener();
        mChooseFile = (TextView) findViewById(R.id.choose_one);
        mChooseFile.setOnClickListener(mOnClickListener);
        mStartRecsovery = (Button) findViewById(R.id.start_recovery);
        mStartRecsovery.setOnClickListener(mOnClickListener);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSeafileBinder = (SeafileBinder) service;
            if (TextUtils.isEmpty(mSeafileBinder.getUserName())) {
                mLoadingDailog.setMessage(getString(R.string.no_openthos_id));
            } else {
                mLoadingDailog.dismiss();
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

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.choose_one:
                    File f = new File("/sdcard/seafile/" + mSeafileBinder.getUserName() + "/.UserConfig");
                    if (!f.exists() || f.list().length == 0) {
                        Toast.makeText(RecoveryActivity.this, "no file", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final String[] files = f.list();
                    mChooseFile.setText(getString(R.string.choose_one_day) + files[0]);
                    mStartRecsovery.setVisibility(View.VISIBLE);
                    Builder b = new Builder(RecoveryActivity.this);
                    b.setSingleChoiceItems(files, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                           mChooseFile.setText(getString(R.string.choose_one_day) + files[which]);
                           path = "/sdcard/seafile/" + mSeafileBinder.getUserName() + "/.UserConfig/" + files[which];
                        }
                    });
                    b.create().show();
                    break;
                case R.id.start_recovery:
                    Builder builder = new Builder(RecoveryActivity.this);
                    builder.setMessage(getString(R.string.warn_restore_startupmenu));
                    builder.setPositiveButton(R.string.okay, null);
                    builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            startRecovery(path);
                        }
                    });
                    builder.setNegativeButton(R.string.cancel, null);
                    builder.create().show();
                    break;
            }
        }
    }

    private void startRecovery(String path) {
        initEnvironment();
        BufferedReader br = null;
        try {
            Process pro = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "./data/data/org.openthos.seafile/rescovery " + path});
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

}
