package org.openthos.seafile.seaapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.text.ClipboardManager;
import android.widget.Toast;
import org.openthos.seafile.R;

import java.io.File;
import java.util.List;

/**
 * Activity Utils
 */
public class WidgetUtils {

    public static void chooseShareApp(final SeafileActivity activity,
                                      final String repoID,
                                      final String path,
                                      final boolean isdir,
                                      final Account account,
                                      final String password,
                                      final String days) {
        final Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        // Get a list of apps
        List<ResolveInfo> infos = Utils.getAppsByIntent(shareIntent, activity);

        String title = activity.getString(isdir ? R.string.share_dir_link : R.string.share_file_link);

        AppChoiceDialog dialog = new AppChoiceDialog();
        dialog.addCustomAction(0, activity.getResources().getDrawable(R.drawable.copy_link),
                activity.getString(R.string.copy_link));
        dialog.init(title, infos, new AppChoiceDialog.OnItemSelectedListener() {
            @Override
            public void onCustomActionSelected(AppChoiceDialog.CustomAction action) {
                final GetShareLinkDialog gdialog = new GetShareLinkDialog();
                gdialog.init(repoID, path, isdir, account, password, days, activity);
                gdialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
                    @Override
                    @SuppressWarnings("deprecation")
                    public void onTaskSuccess() {
                        ClipboardManager clipboard = (ClipboardManager)
                                activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(gdialog.getLink());
                        // ClipData clip = ClipData.newPlainText("seafile shared link", gdialog.getLink());
                        // clipboard.setPrimaryClip(clip);
                        Toast.makeText(activity, R.string.link_ready_to_be_pasted, Toast.LENGTH_SHORT).show();
                    }
                });
                gdialog.show(activity.getSupportFragmentManager(), "DialogFragment");
            }

            @Override
            public void onAppSelected(ResolveInfo appInfo) {
                String className = appInfo.activityInfo.name;
                String packageName = appInfo.activityInfo.packageName;
                shareIntent.setClassName(packageName, className);

                final GetShareLinkDialog gdialog = new GetShareLinkDialog();
                gdialog.init(repoID, path, isdir, account,password, days, activity);
                gdialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
                    @Override
                    public void onTaskSuccess() {
                        shareIntent.putExtra(Intent.EXTRA_TEXT, gdialog.getLink());
                        activity.startActivity(shareIntent);
                    }
                });
                gdialog.show(activity.getSupportFragmentManager(), "DialogFragment");
            }

        });
        dialog.show(activity.getSupportFragmentManager(), "ChooseAppDialog");
    }

    public static void inputSharePassword(final SeafileActivity activity,
                                          final String repoID,
                                          final String path,
                                          final boolean isdir,
                                          final Account account){
        final GetShareLinkEncryptDialog dialog = new GetShareLinkEncryptDialog();
        dialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                String password = dialog.getPassword();
                String days = dialog.getDays();
                chooseShareApp(activity,repoID,path,isdir,account,password,days);
            }
        });
        dialog.show(activity.getSupportFragmentManager(), "ChareLinkPasswordDialog");

    }

    /**
     * if dir will share dir link .
     * if local file ,will share file to wachat app.
     * if server file , it will download file and share file.
     *
     * @param activity
     * @param account
     * @param repoID
     * @param path
     * @param fileName
     * @param fileSize
     * @param isdir
     */
    public static void ShareWeChat(final SeafileActivity activity, Account account, String repoID, String path,
                                   String fileName,
                                   long fileSize,
                                   boolean isdir) {

        if (isdir) {//share  link
            final Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            ResolveInfo weChatInfo = Utils.getWeChatIntent(shareIntent, activity);
            if (weChatInfo == null) {
                ToastUtil.showSingletonToast(activity, activity.getString(R.string.no_app_available));
                return;
            }
            String className = weChatInfo.activityInfo.name;
            String packageName = weChatInfo.activityInfo.packageName;
            shareIntent.setClassName(packageName, className);
            final GetShareLinkDialog gdialog = new GetShareLinkDialog();
            gdialog.init(repoID, path, isdir, account, null, null, activity);
            gdialog.setTaskDialogLisenter(new TaskDialog.TaskDialogListener() {
                @Override
                public void onTaskSuccess() {
                    shareIntent.putExtra(Intent.EXTRA_TEXT, gdialog.getLink());
                    activity.startActivity(shareIntent);
                }
            });
            gdialog.show(activity.getSupportFragmentManager(), "DialogFragment");
        } else {//share  files
            String repoName = activity.getNavContext().getRepoName();
            String dirPath = activity.getNavContext().getDirPath();

            String fullPath = Utils.pathJoin(dirPath, fileName);
            final File file = activity.getDataManager().getLocalRepoFile(repoName, repoID, fullPath);
            Uri uri = null;
            if (android.os.Build.VERSION.SDK_INT > 23) {
                uri = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".provider", file);
            } else {
                uri = Uri.fromFile(file);
            }
            final Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.setType(Utils.getFileMimeType(file));
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            ResolveInfo weChatInfo = Utils.getWeChatIntent(sendIntent, activity);
            if (weChatInfo == null) {
                ToastUtil.showSingletonToast(activity, activity.getString(R.string.no_app_available));
                return;
            }
            String className = weChatInfo.activityInfo.name;
            String packageName = weChatInfo.activityInfo.packageName;
            sendIntent.setClassName(packageName, className);
            if (!Utils.isNetworkOn(activity) && file.exists()) {
                activity.startActivity(sendIntent);
                return;
            }
//            SeafileActivity.mActivity.fetchFileAndExport(weChatInfo, sendIntent, repoName, repoID, path, fileSize);
        }
    }
}
