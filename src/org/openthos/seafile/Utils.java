package org.openthos.seafile;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

public class Utils {

    public static final String ACCOUNT_INFO_FILE = "account";
    public static final int TAG_APPDATA_IMPORT = 0;
    public static final int TAG_APPDATA_EXPORT = 1;
    public static final int TAG_BROWSER_IMPORT = 2;
    public static final int TAG_BROWSER_EXPORT = 3;

    public static ArrayList<String> exec(String[] commands) {
        Process pro = null;
        BufferedReader in = null;
        ArrayList<String> result = new ArrayList();
        try {
            pro = Runtime.getRuntime().exec(commands);
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result.add(line);
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
                processDestroy(pro);
                pro = null;
            }
        }
        return result;
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
                processDestroy(pro);
                pro = null;
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
        File f = new File(to);
        exec("mkdirs -p " + f.getParent().replace(" ", "\\ ") + "\n" +
                "tar -czpf " + to.replace(" ", "\\ ") + " " + from.replace(" ", "\\ ") + "\n");
    }

    /*
     * eg:
     *     from : /dev/tmp/temp.tar.gz
     */
    public static void untarFile(String from) {
        if (TextUtils.isEmpty(from)) {
            return;
        }
        exec("tar -xzpf " + from.replace(" ", "\\ ") + "\n");
    }

    public static void chownFile(String path, String uid) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        exec("chown -R " + uid + ":" + uid + " " + path + "\n");
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
                processDestroy(pro);
                pro = null;
            }
        }
        return true;
    }

    public static HashMap<String, String> getFiles(String path) {
        Process pro = null;
        BufferedReader in = null;
        HashMap<String, String> result = new HashMap();
        try {
            pro = Runtime.getRuntime().exec(new String[]{"su", "-c", "ls -l "
                    + path.replace(" ", "\\ ")});
            in = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                String[] lines = line.split("\\s+");
                if (lines.length > 2) {
                    result.put(lines[lines.length - 1], lines[1]);
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
                processDestroy(pro);
                pro = null;
            }
        }
        return result;
    }

    private static void processDestroy(Process process) {
        if (process != null) {
            try {
                if (process.exitValue() != 0) {
                    killProcess(process);
                }
            } catch (IllegalThreadStateException e) {
                killProcess(process);
            }
        }
    }

    private static void killProcess(Process process) {
        int pid = getProcessId(process);
        if (pid != 0) {
            try {
                //android kill process
                android.os.Process.killProcess(pid);
            } catch (Exception e) {
                try {
                    process.destroy();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static int getProcessId(Process process) {
        String str = process.toString();
        try {
            int i = str.indexOf("=") + 1;
            int j = str.indexOf("]");
            str = str.substring(i, j);
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean writeAccount(Context context, String name, String password) {
        BufferedWriter writer = null;
        try {
            JSONObject obj = new JSONObject();
            obj.put("url", SeafileService.mAccount.mOpenthosUrl);
            obj.put("name", name);
            obj.put("pass", password);
            writer = new BufferedWriter(new OutputStreamWriter(
                    context.openFileOutput(ACCOUNT_INFO_FILE, Context.MODE_PRIVATE)));
            writer.write(obj.toString());
            writer.flush();
        } catch (JSONException | IOException e) {
            if (!(e instanceof FileNotFoundException)) {
                Toast.makeText(context, context.getString(R.string.write_error), 0).show();
            }
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    public static SeafileAccount readAccount(Context context) {
        SeafileAccount account = SeafileAccount.getDefaultAccount(SeafileUtils.SEAFILE_URL_LIBRARY);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    context.openFileInput(ACCOUNT_INFO_FILE)));
            JSONObject obj = new JSONObject(reader.readLine());
            if (!TextUtils.isEmpty(obj.getString("url"))) {
                account.mOpenthosUrl = obj.getString("url");
                account.mUserName = obj.getString("name");
                account.mUserPassword = obj.getString("pass");
            }
        } catch (JSONException | IOException e) {
            if (!(e instanceof FileNotFoundException) && context != null) {
                Toast.makeText(context, context.getString(R.string.read_error), 0).show();
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return account;
    }

    public static String getAppName(Context context, File sourceFile) {
        try {
            PackageParser parser = new PackageParser();
            PackageParser.Package pkg = parser.parseMonolithicPackage(sourceFile, 0);
            //parser.collectManifestDigest(pkg);
            PackageInfo info = PackageParser.generatePackageInfo(pkg, null,
                     PackageManager.GET_PERMISSIONS, 0, 0, null,
                     new PackageUserState());
            Resources pRes = context.getResources();
            AssetManager assmgr = new AssetManager();
            assmgr.addAssetPath(sourceFile.getAbsolutePath());
            Resources res = new Resources(assmgr,
                                 pRes.getDisplayMetrics(), pRes.getConfiguration());
            CharSequence label = null;
            if (info.applicationInfo.labelRes != 0) {
                label = res.getText(info.applicationInfo.labelRes);
            }
            if (label == null) {
                label = (info.applicationInfo.nonLocalizedLabel != null) ?
                      info.applicationInfo.nonLocalizedLabel : info.applicationInfo.packageName;
            }
            return label.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
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
}
