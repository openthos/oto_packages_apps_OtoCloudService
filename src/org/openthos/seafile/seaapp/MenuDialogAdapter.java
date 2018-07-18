package org.openthos.seafile.seaapp;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import org.openthos.seafile.R;

import java.util.List;

import static android.R.color.holo_purple;
import static android.R.color.transparent;

public class MenuDialogAdapter extends BaseAdapter implements View.OnHoverListener {
    private Context mContext;
    private List<String> mData;
    private boolean mCanCopy;

    public MenuDialogAdapter(Context context, List data) {
        mContext = context;
        mData = data;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = View.inflate(mContext, R.layout.dialog_menu_item, null);
        TextView mTvDialogItem = (TextView) view.findViewById(R.id.dialog_base_item);
        String content = mData.get(i);
        mTvDialogItem.setText(content);
        boolean isSetHoverListener = true;
        if (isSetHoverListener) {
            view.setOnHoverListener(this);
            view.setTag(content);
        } else {
            view.setTag("");
        }
        return view;
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_HOVER_ENTER:
                v.setBackgroundColor(mContext.getResources().getColor(holo_purple));
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                v.setBackgroundColor(mContext.getResources().getColor(transparent));
                break;
        }
        return false;
    }
}
