package org.openthos.seafile.seaapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Window;
import org.openthos.seafile.R;

@SuppressLint("ValidFragment")
public class LoadingDialog extends Dialog {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow() ;
        window.setContentView(R.layout.dialog_loading);
    }

    public LoadingDialog(@NonNull Context context) {
        super(context);
    }
}
