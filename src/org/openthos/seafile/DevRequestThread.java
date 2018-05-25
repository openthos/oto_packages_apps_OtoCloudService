package org.openthos.seafile;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.security.KeyStore;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.openthos.seafile.CookieUtils;

public class DevRequestThread extends Thread {
    private static final String URL_REGIEST_ACCOUNT = "https://dev.openthos.org/accounts/register/";
    private static final String CODE_WRONG_USERNAME = "1002";
    private static final String CODE_WRONG_PASSWORD = "1001";

    private Handler mHandler;
    private String mHttpUrl;
    private List<NameValuePair> mValueList = new ArrayList<>();
    private String mCookies = "";
    private String mUser = "";
    private String mPassword = "";
    private String mName , mPass, mId, mEmail, mPasswd;
    private Mark mMark;
    private Context mContext;
    private final Map<String, String> params = new HashMap<String, String>();

    public DevRequestThread(Handler handler, Context context,
            String name, String pass, Mark mark) {
        super();
        mHandler = handler;
        mContext = context;
        mName = name;
        mPass = pass;
        mMark = mark;
    }

    public DevRequestThread(Handler handler, Context context,
            String id, String email, String passwd, Mark mark) {
        super();
        mHandler = handler;
        mContext = context;
        mId = id;
        mEmail = email;
        mPasswd = passwd;
        mMark = mark;
    }

    @Override
    public void run() {
        try {
            if (mMark == Mark.REGISTE) {
                params.put("name", mEmail);
                params.put("mail", mEmail);
                params.put("form_id", "user_register_form");
                params.put("form_build_id",
                           "form-WkUSPmAzO4z-HBjYe03NyRvjNsx44ZDrMGJ8nYAJWfU");
                submitRegisterPostData(params);
            } else if (mMark == Mark.LOGIN) {
                params.put("username", mName);
                params.put("password", mPass);
                submitPostData(params);
            }
        } catch (Exception e) {
            System.out.println("Error=" + e.toString());
        }
    }

    private void requestGet(HttpParams httpParameters, HttpClient hc) throws Exception {
        HttpGet get = new HttpGet(URL_REGIEST_ACCOUNT);
        get.setParams(httpParameters);
        HttpResponse response = null;
        try {
            response = hc.execute(get);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        int sCode = response.getStatusLine().getStatusCode();
        if (sCode == HttpStatus.SC_OK) {
            String result = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
            mCookies = CookieUtils.getCookieskey(response).split(";")[0];
            mValueList.add(
                    new BasicNameValuePair("csrfmiddlewaretoken", mCookies.split("=")[1].trim()));
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 10000);
                HttpConnectionParams.setSoTimeout(httpParams, 10000);
                HttpClient client = getHttpClient(httpParams);
                requestPost(httpParams, client);
            } catch (InterruptedException e) {
                e.printStackTrace();
                mHandler.sendEmptyMessage(LibraryRequestThread.MSG_LOGIN_SEAFILE_FAILED);
                return;
            }
            Message msg = new Message();
            msg.what = LibraryRequestThread.MSG_LOGIN_SEAFILE_OK;
            Bundle bundle = new Bundle();
            bundle.putString("user", mName);
            bundle.putString("password", mPass);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        } else {
            mHandler.sendEmptyMessage(LibraryRequestThread.MSG_LOGIN_SEAFILE_FAILED);
        }
    }
    private int code;
    private void requestPost(HttpParams httpParameters, HttpClient hc) throws Exception {
        String result;
        HttpPost post = new HttpPost(URL_REGIEST_ACCOUNT);
        post = CookieUtils.putCookieskeyPost(post, mCookies);
        post.setEntity(new UrlEncodedFormEntity(mValueList, HTTP.UTF_8));
        post.setParams(httpParameters);
        HttpResponse response = null;
        try {
            response = hc.execute(post);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        int sCode = response.getStatusLine().getStatusCode();
    }

    private HttpClient getHttpClient(HttpParams params) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            SSLSocketFactory sslfactory = new SSLSocketFactoryImp(trustStore);
            sslfactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
            HttpProtocolParams.setUseExpectContinue(params, true);
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sslfactory, 443));
            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);
            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient(params);
        }
    }

    private void submitPostData(final Map<String, String> params) {
        byte[] data = HttpUtils.getRequestData(params, "utf-8").toString().getBytes();
        try {
            HttpURLConnection httpURLConnection =
                    (HttpURLConnection) HttpUtils.getHttpsURLConnection(
                            "http://dev.openthos.org/?q=check/userinfo");
            httpURLConnection.setConnectTimeout(3000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setUseCaches(false);
            //set the request body type is text
            httpURLConnection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            //set the request body length
            httpURLConnection.setRequestProperty("Content-Length",
                    String.valueOf(data.length));
            //get the ouput stream and write to the service
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(data);

            int response = httpURLConnection.getResponseCode();
            //get the service response
            String result = new String();
            if (response == HttpURLConnection.HTTP_OK) {
                InputStream inptStream = httpURLConnection.getInputStream();
                result = HttpUtils.dealResponseResult(inptStream);
                String code = result.split(":")[1].split("\"")[1].trim();
                if (CODE_WRONG_USERNAME.equals(code)) {
                    Message msg = new Message();
                    msg.what = LibraryRequestThread.MSG_LOGIN_SEAFILE_FAILED;
                    msg.obj = mContext.getString(R.string.toast_openthos_id_wrong);
                    mHandler.sendMessage(msg);
                } else if (CODE_WRONG_PASSWORD.equals(code)) {
                    Message msg = new Message();
                    msg.what = LibraryRequestThread.MSG_LOGIN_SEAFILE_FAILED;
                    msg.obj = mContext.getString(R.string.toast_openthos_password_wrong);
                    mHandler.sendMessage(msg);
                } else {
                    getCsrf(mName, mPass);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getCsrf(String user, String password) {
        try {
            mValueList.clear();
            mValueList.add(new BasicNameValuePair("email", user));
            mValueList.add(new BasicNameValuePair("password1", password));
            mValueList.add(new BasicNameValuePair("password2", password));
            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, 10000);
            HttpConnectionParams.setSoTimeout(httpParameters, 10000);
            HttpClient hc = getHttpClient(httpParameters);
            requestGet(httpParameters, hc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void submitRegisterPostData(final Map<String, String> params) {
        byte[] data = HttpUtils.getRequestData(params, "utf-8").toString().getBytes();
        try {
            HttpURLConnection httpURLConnection=
                    (HttpURLConnection) HttpUtils.getHttpsURLConnection(
                            "http://dev.openthos.org/?q=user/register");
            httpURLConnection.setConnectTimeout(3000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setUseCaches(false);
            //set the request body type is text
            httpURLConnection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            //set the request body length
            httpURLConnection.setRequestProperty("Content-Length",
                    String.valueOf(data.length));
            //get the ouput stream and write to the service
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(data);
            int response = httpURLConnection.getResponseCode();
            //get the service response
            String result = new String();
            if (response == HttpURLConnection.HTTP_OK) {
                InputStream inptStream = httpURLConnection.getInputStream();
                result = HttpUtils.dealResponseResult(inptStream);
                Document doc = Jsoup.parse(result);
                if (doc.select("div.messages").first() != null) {
                    Message msg = new Message();
                    msg.what = LibraryRequestThread.MSG_REGIST_SEAFILE_FAILED;
                    msg.obj = "failed";
                    mHandler.sendMessage(msg);
                } else {
                    Message msg = new Message();
                    msg.what = LibraryRequestThread.MSG_REGIST_SEAFILE_OK;
                    msg.obj = mContext.getString(R.string.toast_register_sendmail);
                    mHandler.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Message msg = new Message();
            msg.what = LibraryRequestThread.MSG_REGIST_SEAFILE_FAILED;
            msg.obj = "failed";
            mHandler.sendMessage(msg);
        }
    }
}
