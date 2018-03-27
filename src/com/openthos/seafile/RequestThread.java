package com.openthos.seafile;

import android.os.Handler;
import android.os.Message;
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

import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.List;
import android.util.Log;

import com.openthos.seafile.CookieUtils;

public class RequestThread extends Thread {
    public static final int MSG_REGIST_SEAFILE_OK = 0x1001;
    public static final int MSG_REGIST_SEAFILE_FAILED = 0x1002;

    private Handler mHandler;
    private String mHttpUrl;
    private List<NameValuePair> mValueList;
    private RequestType mType;
    private String mCookies = "";

    public RequestThread(Handler handler, String httpUrl, List<NameValuePair> list) {
        super();
        mHandler = handler;
        mHttpUrl = httpUrl;
        mValueList = list;
        mType = RequestType.GET;
    }

    public RequestThread(Handler handler, String httpUrl, List<NameValuePair> list,
                         RequestType type, String cookies) {
        this(handler, httpUrl, list);
        mType = type;
        mCookies = cookies;
    }

    @Override
    public void run() {
        try {
            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, 10000);
            HttpConnectionParams.setSoTimeout(httpParameters, 10000);
            HttpClient hc = getHttpClient(httpParameters);
            switch (mType) {
                case GET:
                    requestGet(httpParameters, hc);
                    break;
                case POST:
                    requestPost(httpParameters, hc);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.run();
    }

    private void requestGet(HttpParams httpParameters, HttpClient hc) throws Exception {
        Log.i ("wwww", "JOIN ");
        HttpGet get = new HttpGet(mHttpUrl);
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
                Thread thread = new RequestThread(
                        mHandler, mHttpUrl, mValueList, RequestType.POST, mCookies);
                thread.join();
                thread.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
                mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE_FAILED);
                return;
            }
            mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE_OK);
        } else {
            mHandler.sendEmptyMessage(MSG_REGIST_SEAFILE_FAILED);
            Log.i ("wwww", "failed");
        }
    }
    private int code;
    private void requestPost(HttpParams httpParameters, HttpClient hc) throws Exception {
        Log.i ("wwww", "POST ");
        String result;
        HttpPost post = new HttpPost(mHttpUrl);
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
        Log.i ("wwww", "POST " + sCode);
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

    private enum RequestType {GET, POST}
}
