package org.openthos.seafile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements View.OnClickListener {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView id = (TextView) findViewById(R.id.id);
        TextView seafile = (TextView) findViewById(R.id.seafile);
        id.setText("帐号设置");
        seafile.setText("备份还原");
        id.setOnClickListener(this);
        seafile.setOnClickListener(this);
        Intent intent = new Intent(this, SeafileService.class);
        startService(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.id:
                Intent intent1 = new Intent();
                intent1.setClass(this, OpenthosIDActivity.class);
                startActivity(intent1);
                break;
            case R.id.seafile:
                Intent intent2 = new Intent();
                intent2.setClass(this, RecoveryActivity.class);
                startActivity(intent2);
                break;
        }
    }
}
