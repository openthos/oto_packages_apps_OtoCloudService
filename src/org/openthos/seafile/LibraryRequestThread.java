package org.openthos.seafile;

import android.widget.Toast;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LibraryRequestThread extends Thread {
    public static final int MSG_REGIST_SEAFILE_OK = 0x1001;
    public static final int MSG_REGIST_SEAFILE_FAILED = 0x1002;
    public static final int MSG_LOGIN_SEAFILE_OK = 0x1003;
    public static final int MSG_LOGIN_SEAFILE_FAILED = 0x1004;
    private String registeUri = SeafileUtils.mOpenthosUrl + "id/u/register";
    private String loginGetUri = SeafileUtils.mOpenthosUrl + "oauth/login/";
    private String loginPostUri =
            SeafileUtils.mOpenthosUrl + "id/user/login?destination=oauth2/authorize";
    private String referer = SeafileUtils.mOpenthosUrl + "accounts/login/?next=/";
    private String redirect = "http.protocol.handle-redirects";
    private String location, csrftoken, sessionid, sess, form_build_id;
    private String name , pass, id, email, passwd;
    private Mark mark;
    private Context context;
    private Handler handler;
    private Message message;

    public LibraryRequestThread(Handler handler, Context context,
            String name, String pass, Mark mark) {
        super();
        this.handler = handler;
        this.context = context;
        this.name = name;
        this.pass = pass;
        this.mark = mark;
    }

    public LibraryRequestThread(Handler handler, Context context,
            String id, String email, String passwd, Mark mark) {
        super();
        this.handler = handler;
        this.context = context;
        this.id = id;
        this.email = email;
        this.passwd = passwd;
        this.mark = mark;
    }

    @Override
    public void run() {
        try {
            if (mark == Mark.REGISTE) {
                registePost();
            } else if (mark == Mark.LOGIN) {
                message = new Message();
                boolean success = loginStep1Get();
                if (success) {
                    SeafileService.mSp.edit()
                            .putString("user", name).putString("password", pass).commit();
                    message.what = MSG_LOGIN_SEAFILE_OK;
                    Bundle bundle = new Bundle();
                    bundle.putString("user", name + "@openthos.org");
                    bundle.putString("password", pass);
                    message.setData(bundle);
                    handler.sendMessage(message);
                } else {
                    message.what = MSG_LOGIN_SEAFILE_FAILED;
                    message.obj = context.getString(R.string.toast_login_failed);
                    handler.sendMessage(message);
                }
            }
        } catch (Exception e) {
            System.out.println("Error=" + e.toString());
        }
    }

    private void registePost() throws Exception {
        URI uri = new URI(registeUri);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, true);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpPost post = new HttpPost(uri);

        List<NameValuePair> valuePairList = new ArrayList<>();
        valuePairList.add(new BasicNameValuePair("id", id));
        valuePairList.add(new BasicNameValuePair("email", email));
        valuePairList.add(new BasicNameValuePair("passwd", passwd));

        post.setEntity(new UrlEncodedFormEntity(valuePairList, HTTP.UTF_8));
        post.setParams(httpParams);

        HttpResponse httpResponse = httpClient.execute(post);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 200) {
            HttpEntity entity = httpResponse.getEntity();
            InputStream in = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.equals(context.getResources()
                            .getString(R.string.registe_success_info))) {
                        //handler.sendEmptyMessage(MSG_REGIST_SEAFILE_OK);
                        Message msg = new Message();
                        msg.what = MSG_REGIST_SEAFILE_OK;
                        msg.obj = context.getString(R.string.toast_registe_successful);
                        handler.sendMessage(msg);
                    } else {
                        Message msg = new Message();
                        msg.what = MSG_REGIST_SEAFILE_FAILED;
                        msg.obj = "failed";
                        handler.sendMessage(msg);
                    }
                    break;
                }
            } catch (IOException e) {
                System.out.println("Error=" + e.toString());
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    System.out.println("Error=" + e.toString());
                }
            }
        }
    }

    private boolean loginStep1Get() throws Exception{
        URI uri = new URI(SeafileUtils.mOpenthosUrl);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, true);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 200) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("csrftoken")) {
                    String[] strings = allHeaders[i].toString().split("=");
                    csrftoken = strings[1].split(";")[0];
                }
                if (allHeaders[i].toString().contains("sessionid")) {
                    String[] strings = allHeaders[i].toString().split("=");
                    sessionid = strings[1].split(";")[0];
                    break;
                }
            }
            return loginStep2Get(csrftoken, sessionid);
        }
        return false;
    }

    private boolean loginStep2Get(String csrftoken, String sessionid) throws Exception{
        URI uri = new URI(loginGetUri);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer", referer);
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid);

        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 302) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("Location")) {
                    String[] strings = allHeaders[i].toString().split("Location: ");
                    location = strings[1];
                    break;
                }
            }
            return loginStep3Get(location, csrftoken, sessionid);
        }
        return false;
    }

    private boolean loginStep3Get(String location, String csrftoken, String sessionid) throws Exception{
        URI uri = new URI(location);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer", referer);
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid);

        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 302) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("Location")) {
                    String[] strings = allHeaders[i].toString().split("Location: ");
                    location = strings[1];
                }
                if (allHeaders[i].toString().contains("Set-Cookie:")) {
                    String[] strings = allHeaders[i].toString().split("; ");
                    sess = strings[0].split("Set-Cookie: ")[1];
                    break;
                }
            }
            return loginStep4Get(location, csrftoken, sessionid, sess);
        }
        return false;
    }

    private boolean loginStep4Get(String location, String csrftoken,
                          String sessionid, String sess) throws Exception{
        URI uri = new URI(location);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer", referer);
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid + "; " + sess);

        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 200) {
            HttpEntity entity = httpResponse.getEntity();
            InputStream in = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.contains("form_build_id")) {
                        String[] split = line.split("=");
                        String s = split[split.length - 1];
                        form_build_id = s.substring(0, s.length() - 3);
                        break;
                    }
                }
                return loginStep5Post(csrftoken, sessionid, sess, form_build_id);
            } catch (IOException e) {
                System.out.println("Error=" + e.toString());
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    System.out.println("Error=" + e.toString());
                }
            }
        }
        return false;
    }

    private boolean loginStep5Post(String csrftoken, String sessionid, String sess,
                           String form_build_id) throws Exception {
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpResponse httpResponse = null;
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        URI url = new URI(loginPostUri);
        HttpPost post = new HttpPost(url);
        post.setHeader("Referer", loginPostUri);
        post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        post.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid + "; " + sess);

        List<NameValuePair> valuePairList = new ArrayList<>();
        valuePairList.add(new BasicNameValuePair("name", name));
        valuePairList.add(new BasicNameValuePair("pass", pass));
        valuePairList.add(new BasicNameValuePair("form_build_id", form_build_id));
        valuePairList.add(new BasicNameValuePair("form_id", "user_login"));
        valuePairList.add(new BasicNameValuePair("op", "Log+in"));
        post.setEntity(new UrlEncodedFormEntity(valuePairList, HTTP.UTF_8));
        post.setParams(httpParams);

        httpResponse = httpClient.execute(post);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 302) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("Location")) {
                    String[] strings = allHeaders[i].toString().split("Location: ");
                    location = strings[1];
                }
                if (allHeaders[i].toString().contains("Set-Cookie:")) {
                    String[] strings = allHeaders[i].toString().split("; ");
                    sess = strings[0].split("Set-Cookie: ")[1];
                    break;
                }
            }
            return step6Get(location, csrftoken, sessionid, sess);
        }

        if (statusCode == 200) {
            HttpEntity entity = httpResponse.getEntity();
            InputStream in = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Sorry, unrecognized username or password")) {
                        return false;
                    }
                }
            } catch (IOException e) {
                System.out.println("Error=" + e.toString());
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    System.out.println("Error=" + e.toString());
                }
            }
        }
        return false;
    }

    private boolean step6Get(String location, String csrftoken,
                          String sessionid, String sess) throws Exception{
        URI uri = new URI(location);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer",
                loginPostUri);
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid + "; " + sess);
        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        android.util.Log.i("step6Get", statusCode + "");

        if (statusCode == 302) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("Location")) {
                    String[] strings = allHeaders[i].toString().split("Location: ");
                    location = strings[1];
                    break;
                }
            }
            return step7Get(location, csrftoken, sessionid, sess);
        }
        return false;
    }

    private boolean step7Get(String location, String csrftoken,
                          String sessionid, String sess) throws Exception{
        URI uri = new URI(location);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer",
                SeafileUtils.mOpenthosUrl + "id/user/login?destination=oauth2/authorize");
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid + "; " + sess);

        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        android.util.Log.i("step7Get", statusCode + "");
        if (statusCode == 302) {
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (int i = 0; i < allHeaders.length; i++) {
                if (allHeaders[i].toString().contains("Location")) {
                    String[] strings = allHeaders[i].toString().split("Location: ");
                    location = strings[1];
                }
                if (allHeaders[i].toString().contains("sessionid")) {
                    String[] strings = allHeaders[i].toString().split("=");
                    sessionid = strings[1].split(";")[0];
                    break;
                }
            }
            return step8Get(location, csrftoken, sessionid, sess);
        }
        return false;
    }

    private boolean step8Get(String location, String csrftoken,
                          String sessionid, String sess) throws Exception{
        URI uri = new URI(location);
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(redirect, false);
        HttpClient httpClient = new DefaultHttpClient(httpParams);
        HttpUriRequest get = new HttpGet(uri);
        get.setHeader("Referer",
                SeafileUtils.mOpenthosUrl + "id/user/login?destination=oauth2/authorize");
        get.setHeader("Cookie","csrftoken=" + csrftoken + ";" +
                " django_language=zh-cn; has_js=1; sessionid=" +  sessionid + "; " + sess);

        get.setParams(httpParams);
        HttpResponse httpResponse = httpClient.execute(get);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        android.util.Log.i("step8Get", statusCode + "");

        if (statusCode == 200) {
            return true;
        }
        return false;
    }
}
