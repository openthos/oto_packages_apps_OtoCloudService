package org.openthos.seafile;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.TextView;

import org.openthos.seafile.R;

public class ChangeUrlDialog extends Dialog {
    public static final int MSG_CHANGE_URL = 0x1005;
    private Context mContext;
    private String mUrl = SeafileUtils.mOpenthosUrl;
    private ButtonClickListener mClickListener;
    private Handler mHandler;

    public ChangeUrlDialog(Context context, Handler handler) {
        super(context);
        mContext = context;
        mHandler = handler;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        setContentView(R.layout.dialog_change_url);
        initView();
    }

    private void initView() {
        mClickListener = new ButtonClickListener();
        RadioButton dev = (RadioButton) findViewById(R.id.url_dev);
        RadioButton lab = (RadioButton) findViewById(R.id.url_lab);
        RadioButton devs = (RadioButton) findViewById(R.id.url_devs);
        TextView confirm = (TextView) findViewById(R.id.confirm);
        TextView cancel = (TextView) findViewById(R.id.cancel);
        if (SeafileUtils.mOpenthosUrl.equals(dev.getText().toString())) {
            dev.setChecked(true);
        } else if (SeafileUtils.mOpenthosUrl.equals(lab.getText().toString())) {
            lab.setChecked(true);
        } else if (SeafileUtils.mOpenthosUrl.equals(devs.getText().toString())) {
            devs.setChecked(true);
        }
        dev.setOnClickListener(mClickListener);
        lab.setOnClickListener(mClickListener);
        devs.setOnClickListener(mClickListener);
        confirm.setOnClickListener(mClickListener);
        cancel.setOnClickListener(mClickListener);
    }

    private class ButtonClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.confirm:
                    if (!SeafileUtils.mOpenthosUrl.equals(mUrl)) {
                        SeafileUtils.mOpenthosUrl = mUrl;
                        mHandler.sendEmptyMessage(MSG_CHANGE_URL);
                    }
                    dismiss();
                    break;
                case R.id.cancel:
                    dismiss();
                    break;
                default:
                    if (v instanceof RadioButton) {
                        mUrl = ((RadioButton) v).getText().toString();
                    }
                    break;
            }
        }
    }

    public void showDialog() {
        show();
        Window dialogWindow = getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        lp.dimAmount = 0.0f;
        dialogWindow.setGravity(Gravity.CENTER);
        dialogWindow.setAttributes(lp);
    }
}
