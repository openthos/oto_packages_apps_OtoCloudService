package org.openthos.seafile.seaapp;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import org.openthos.seafile.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MenuDialog extends Dialog implements ListView.OnItemClickListener {
    private ListView mListView;
    private List<String> mDatas;
    private Object mSeaf;
    private String mark = "OtoFile:///";

    public MenuDialog(Context context, String type) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_menu);
        mListView = (ListView) findViewById(R.id.dialog_base_lv);
        initData(type);
        initListener();
        setListViewBasedOnChildren(mListView);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    public void setListViewBasedOnChildren(ListView listView) {
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        ListAdapter listAdapter = listView.getAdapter();
        int maxWidth = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int width = listItem.getMeasuredWidth();
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.width = maxWidth;
        listView.setLayoutParams(params);
    }

    private void initData(String type) {
        mDatas = new ArrayList();
        switch (type) {
            case "library":
                mDatas.add(SeafileActivity.mActivity.getString(R.string.rename_repo));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.delete_repo_title));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.share_repo));
                break;
            case "library_blank":
                mDatas.add(SeafileActivity.mActivity.getString(R.string.create_new_repo));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.refresh_repo));
                break;
            case "repo":
                mDatas.add(SeafileActivity.mActivity.getString(R.string.file_share));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.rename_file));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.delete));
                break;
            case "repo_blank":
                mDatas.add(SeafileActivity.mActivity.getString(R.string.create_new_file));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.create_new_dir));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.upload));
                mDatas.add(SeafileActivity.mActivity.getString(R.string.refresh));
                break;
        }
        mListView.setAdapter(new MenuDialogAdapter(getContext(), mDatas));
    }

    protected void initListener() {
        mListView.setOnItemClickListener(this);
    }

    private void prepareData(String[] sArr) {
        for (int i = 0; i < sArr.length; i++) {
            mDatas.add(sArr[i]);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        String content = (String) view.getTag();
        SeafDirent dirent = null;
        SeafRepo repo = null;
        if (SeafileActivity.mActivity.getString(R.string.create_new_repo).equals(content)) {
            SeafileActivity.mActivity.showNewRepoDialog();
        } else if (SeafileActivity.mActivity.getString(R.string.rename_repo).equals(content)) {
            repo = (SeafRepo) mSeaf;
            SeafileActivity.mActivity.showRenameRepoDialog(repo.getID(), repo.getName());
        } else if (SeafileActivity.mActivity.getString(R.string.delete_repo_title).equals(content)) {
            repo = (SeafRepo) mSeaf;
            SeafileActivity.mActivity.deleteRepoDialog(repo.getID());
        } else if (SeafileActivity.mActivity.getString(R.string.create_new_file).equals(content)) {
            SeafileActivity.mActivity.showNewFileDialog();
        } else if (SeafileActivity.mActivity.getString(R.string.create_new_dir).equals(content)) {
            SeafileActivity.mActivity.showNewDirDialog();
        } else if (SeafileActivity.mActivity.getString(R.string.rename_file).equals(content)) {
            dirent = (SeafDirent)mSeaf;
            SeafileActivity.mActivity.showRenameFileDialog(
                    SeafileActivity.mNavContext.getRepoID(),
                    Utils.pathJoin(SeafileActivity.mNavContext.getDirPath(), dirent.name),
                    dirent.isDir());
        } else if (SeafileActivity.mActivity.getString(R.string.delete).equals(content)) {
            dirent = (SeafDirent)mSeaf;
            SeafileActivity.mActivity.showDeleteFileDialog(
                    SeafileActivity.mNavContext.getRepoID(),
                    Utils.pathJoin(SeafileActivity.mNavContext.getDirPath(), dirent.name),
                    dirent.isDir());
        } else if (SeafileActivity.mActivity.getString(R.string.file_share).equals(content)) {
            dirent = (SeafDirent) mSeaf;
            SeafileActivity.mActivity.showShareDialog(dirent);
        } else if (SeafileActivity.mActivity.getString(R.string.upload).equals(content)) {
            ClipboardManager manager = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence text = manager.getText();
            String filePath = null;
            if (!TextUtils.isEmpty(text) && text.toString().
                    startsWith(mark) && text.toString().lastIndexOf(mark) == 0) {
                String[] split = text.toString().split(mark);
                filePath = split[1];
                File file = new File(filePath);
                if (file.exists() && file.isFile()) {
                    SeafileActivity.mActivity.showUploadFileDialog(filePath);
                } else {
                    ToastUtil.showSingletonToast(getContext(),
                            SeafileActivity.mActivity.getString(R.string.upload_select_file_tip));
                }
            } else {
                ToastUtil.showSingletonToast(getContext(),
                        SeafileActivity.mActivity.getString(R.string.upload_select_file_tip));
            }
        } else if (SeafileActivity.mActivity.getString(R.string.refresh_repo).equals(content)) {
                SeafileActivity.mActivity.refreshRepo();
        } else if (SeafileActivity.mActivity.getString(R.string.refresh).equals(content)) {
                SeafileActivity.mActivity.refreshDirent();
        } else if (SeafileActivity.mActivity.getString(R.string.share_repo).equals(content)) {
                repo = (SeafRepo) mSeaf;
                WidgetUtils.chooseShareApp(SeafileActivity.mActivity, repo.getID(),
                        "/", true, SeafileActivity.mAccount, null, null);
        }
        dismiss();
    }

    public void showDialog(int x, int y, Object seaf) {
        mSeaf = seaf;
        Window dialogWindow = getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        lp.dimAmount = 0.0f;
        show();
        dialogWindow.setGravity(Gravity.LEFT | Gravity.TOP);
        lp.x = x;
        lp.y = y;
        dialogWindow.setAttributes(lp);
    }

    public void showDialog() {
        Window dialogWindow = getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.dimAmount = 0.0f;
        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        show();
        dialogWindow.setGravity(Gravity.CENTER);
        dialogWindow.setAttributes(lp);
    }
}