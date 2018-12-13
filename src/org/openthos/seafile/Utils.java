package org.openthos.seafile;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

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

    public static boolean writeAccount(Context context, String url, String username, String token) {
        SeafileAccount account = new SeafileAccount(url);
        account.mUserName = username;
        account.mToken = token;
        return writeAccount(context, account);
    }

    public static boolean writeAccount(Context context, SeafileAccount account) {
        BufferedWriter writer = null;
        try {
            JSONObject obj = new JSONObject();
            String token = Config.FLAG_PROGUARD ?
                    encodeToken(account.mToken + account.mUserName) : account.mToken;
            obj.put("url", account.mOpenthosUrl);
            obj.put("name", account.mUserName);
            obj.put("token", token);
            writer = new BufferedWriter(new OutputStreamWriter(
                    context.openFileOutput(ACCOUNT_INFO_FILE, Context.MODE_PRIVATE)));
            writer.write(obj.toString());
            writer.flush();
        } catch (JSONException | IOException e) {
            if (!(e instanceof FileNotFoundException)) {
                Toast.makeText(context,
                        context.getString(R.string.write_error), Toast.LENGTH_SHORT).show();
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
                String token = Config.FLAG_PROGUARD ?
                        decodeToken(obj.getString("name"), obj.getString("token"))
                        : obj.getString("token");
                account.mOpenthosUrl = obj.getString("url");
                account.mUserName = obj.getString("name");
                account.mToken = token;
            }
        } catch (JSONException | IOException e) {
            if (!(e instanceof FileNotFoundException) && context != null) {
                Toast.makeText(context,
                        context.getString(R.string.read_error), Toast.LENGTH_SHORT).show();
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

    public static boolean isNetworkOn(Context context) {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi != null && wifi.isAvailable()
                && wifi.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return true;
        }
        NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mobile != null && mobile.isAvailable()
                && mobile.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return true;
        }
        return false;
    }

    private static String encodeToken(String token) {
        byte[] bytes = token.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
        byte[] key = getKey();
        byte[] result = xor(bytes, key);
        return new String(Base64.encode(result, Base64.DEFAULT));
    }

    private static String decodeToken(String name, String result) {
        byte[] key = getKey();
        byte[] bytes = xor(Base64.decode(result, Base64.DEFAULT), key);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
        result = new String(bytes);
        if (result.endsWith(name)) {
            return result.replace(name, "");
        } else {
            return null;
        }
    }

    private static byte[] xor(byte[] arg0, byte[] arg1) {
        int len = arg1.length;
        byte[] result = new byte[arg0.length];
        for (int i = 0; i < arg0.length; i++) {
            result[i] = (byte) (arg0[i] ^ arg1[i % len]);
        }
        return result;
    }

    private static byte[] getKey() {
        String localKey = ACCOUNT_INFO_FILE;
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface networks = en.nextElement();
                Enumeration<InetAddress> address = networks.getInetAddresses();
                while (address.hasMoreElements()) {
                    InetAddress ip = address.nextElement();
                    String ipaddress = ip.getHostAddress();
                    byte[] key = networks.getHardwareAddress();
                    if (key != null && key.length >0) {
                        StringBuffer stringBuffer = new StringBuffer("");
                        for (int i = 0; i < key.length; i++) {
                            int temp = key[i] & 0xff;
                            String str = Integer.toHexString(temp);
                            if (str.length() == 1) {
                                stringBuffer.append("0" + str);
                            } else {
                                stringBuffer.append(str);
                            }
                        }
                        localKey = stringBuffer.toString();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return localKey.getBytes();
    }

    public class Config {
        private static final boolean FLAG_PROGUARD = true;
    }
}
