package org.openthos.seafile;

import android.app.ActionBar;
import android.app.Activity;
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
import android.os.IBinder;
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

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class RecoveryActivity extends Activity {
    private RadioGroup mRadioGroup;
    private Switch mSwitchWallpaper;
    private Switch mSwitchWifi;
    private Switch mSwitchAppdata;
    private Switch mSwitchAppstore;
    private Switch mSwitchStartupmenu;

    private Button mButtonConfirm;
    private TextView mChooseAppdata;

    private List<ResolveInfo> mExportAppdata = new ArrayList();
    private List<ResolveInfo> mImportAppdata = new ArrayList();
    private List<ResolveInfo> mSyncAppdata = new ArrayList();
    private ResolveAdapter mAppdataAdapter = new ResolveAdapter();
    private PackageManager mPackageManager;
    private int mTag = 0;


    private RadioCheckedChangeListener mRadioCheckedChangeListener;
    private ClickListener mClickListener;
    private CheckedChangeListener mCheckedChangeListener;

    private RecoveryService mRecoveryService;

    private static final int IMPORT_TAG = 0;
    private static final int EXPORT_TAG = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_cloud_service);
        ActionBar mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
        Intent intent = new Intent(RecoveryActivity.this, RecoveryService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mPackageManager = getPackageManager();
        initView();
    }

    private void initView() {
        mRadioGroup = (RadioGroup) findViewById(R.id.radio_group);
        mSwitchWallpaper = (Switch) findViewById(R.id.switch_wallpaper);
        mSwitchWifi = (Switch) findViewById(R.id.switch_wifi);
        mSwitchAppdata = (Switch) findViewById(R.id.switch_appdata);
        mSwitchAppstore = (Switch) findViewById(R.id.switch_appstore);
        mSwitchStartupmenu = (Switch) findViewById(R.id.switch_startmenu);
        mChooseAppdata = (TextView) findViewById(R.id.tv_appdata_import);
        mButtonConfirm = (Button) findViewById(R.id.cloud_import);

        // switch status
        restoreSwitchStatus();

        mRadioCheckedChangeListener = new RadioCheckedChangeListener();
        mClickListener = new ClickListener();
        mCheckedChangeListener = new CheckedChangeListener();

        mRadioGroup.setOnCheckedChangeListener(mRadioCheckedChangeListener);
        mChooseAppdata.setOnClickListener(mClickListener);
        mButtonConfirm.setOnClickListener(mClickListener);
        mSwitchAppdata.setOnCheckedChangeListener(mCheckedChangeListener);
        mSwitchStartupmenu.setOnCheckedChangeListener(mCheckedChangeListener);
    }

    private void restoreSwitchStatus() {
    }

    private void saveSwitchStatus() {
    }

    private void showExportConfirmDialog() {
        new Builder(this)
                .setMessage(getString(R.string.export_confirm_dialog_info))
                .setPositiveButton(getString(R.string.cloud_service_dialog_confirm),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                exportAllFiles();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create().show();
    }

    private void importAllFiles() {
        mRecoveryService.restoreSettings(mSwitchWallpaper.isChecked(), mSwitchWifi.isChecked(),
                mSwitchAppdata.isChecked(), getPackages(), mSwitchStartupmenu.isChecked(),
                mSwitchAppstore.isChecked());
    }

    private List<String> getPackages() {
        ArrayList<String> list = new ArrayList<>();
        for (ResolveInfo resolveInfo : mSyncAppdata) {
            list.add(resolveInfo.activityInfo.packageName);
        }
        return list;
    }

    private void exportAllFiles() {
        mRecoveryService.saveSettings(true, mSwitchWallpaper.isChecked(),
                mSwitchWifi.isChecked(), mSwitchAppdata.isChecked(), getPackages(),
                mSwitchStartupmenu.isChecked(), mSwitchAppstore.isChecked());
    }

    private class ResolveAdapter extends BaseAdapter {
        private List<ResolveInfo> allList = new ArrayList();
        private List<String> syncList = new ArrayList();

        private ResolveAdapter() {
        }

        private void setList(List<ResolveInfo> allList) {
            this.allList = allList;
        }

        @Override
        public int getCount() {
            return allList.size();
        }

        @Override
        public Object getItem(int i) {
            return allList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder;
            View convertView = view;
            if (convertView == null) {
                convertView = LayoutInflater.from(RecoveryActivity.this).
                        inflate(R.layout.cloud_list_item, viewGroup, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            }
            holder = (ViewHolder) convertView.getTag();
            holder.text.setText(allList.get(i).loadLabel(mPackageManager));
            holder.image.setImageDrawable(allList.get(i).loadIcon(mPackageManager));
            if (mSyncAppdata.contains(allList.get(i))) {
                holder.background.setSelected(true);
            } else {
                holder.background.setSelected(false);
            }
            holder.background.setTag(allList.get(i));
            holder.background.setOnClickListener(mClickListener);
            return convertView;
        }
    }

    private class ViewHolder {
        public LinearLayout background;
        public TextView text;
        public ImageView image;

        public ViewHolder(View view) {
            background = (LinearLayout) view.findViewById(R.id.background);
            text = (TextView) view.findViewById(R.id.tv_item);
            image = (ImageView) view.findViewById(R.id.iv_item);
        }
    }

    private class ClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.cloud_import:
                    if (mTag == IMPORT_TAG) {
                        // import
                        importAllFiles();
                    } else if (mTag == EXPORT_TAG) {
                        // export
                        showExportConfirmDialog();
                    }
                    break;
                case R.id.tv_appdata_import:
                    showAppdataList();
                    break;
                case R.id.background:
                    if (view.isSelected()) {
                        Log.d("wwww", "-------------click background-true-");
                        view.setSelected(false);
                        mSyncAppdata.remove(view.getTag());
                    } else {
                        Log.d("wwww", "-------------click background-false-");
                        view.setSelected(true);
                        mSyncAppdata.add((ResolveInfo) view.getTag());
                    }
                    break;
            }
        }
    }

    private void showAppdataList() {
        Builder builder = new Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.appdata_list, null);
        GridView gridView = (GridView) view.findViewById(R.id.lv_appdata);
        if (mTag == IMPORT_TAG) {
            mAppdataAdapter.setList(mImportAppdata);
            builder.setTitle(getText(R.string.cloud_appdata_import));
        } else {
            mAppdataAdapter.setList(mExportAppdata);
            builder.setTitle(getText(R.string.cloud_appdata_export));
        }
        gridView.setAdapter(mAppdataAdapter);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d("nnnnnn", "-------------------dialog-ok-" + mSyncAppdata.size());
            }
        });
        builder.create().show();
    }

    private void initList() {
        mImportAppdata = mRecoveryService.getAppsInfo(Utils.TAG_APPDATA_IMPORT);
        mExportAppdata = mRecoveryService.getAppsInfo(Utils.TAG_APPDATA_EXPORT);
        for (int i = 0; i < mImportAppdata.size(); i++) {
            try {
                if ((mPackageManager.getPackageInfo(mImportAppdata.get(i).activityInfo.packageName, 0).
                        applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0) {
                    mSyncAppdata.add(mImportAppdata.get(i));
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private void refreshView() {
        Log.d("nnnnnn", "---------------------------refresh-");
        if (mTag == IMPORT_TAG) {
            mButtonConfirm.setText(R.string.cloud_import);
            mChooseAppdata.setText(R.string.cloud_appdata_import);
        } else if (mTag == EXPORT_TAG) {
            mButtonConfirm.setText(R.string.cloud_export);
            mChooseAppdata.setText(R.string.cloud_appdata_export);

        }
    }

    private class RadioCheckedChangeListener implements RadioGroup.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            switch (checkedId) {
                case R.id.rb_import:
                    mTag = IMPORT_TAG;
                    refreshView();
                    break;
                case R.id.rb_export:
                    mTag = EXPORT_TAG;
                    refreshView();
                    break;
            }
        }
    }

    private class CheckedChangeListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.switch_startmenu:
                    if (isChecked) {
                        Builder builder = new Builder(RecoveryActivity.this);
                        builder.setMessage(getString(R.string.warn_restore_startupmenu));
                        builder.setPositiveButton(R.string.okay, null);
                        builder.create().show();
                    }
                    break;
                case R.id.switch_appdata:
                    break;
            }
        }
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

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRecoveryService = ((RecoveryService.ServiceBinder) service).getService();
            //if (TextUtils.isEmpty(mRecoveryService.getUserName())) {
            //}
        }

        public void onServiceDisconnected(ComponentName name) {
        }
    };
}
