package org.openthos.seafile.seaapp;

import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openthos.seafile.R;

public class SeafItemAdapter extends BaseAdapter {

    private List items;
    private SeafileActivity mActivity;
    private boolean repoIsEncrypted;
    private boolean actionModeOn;

    private SparseBooleanArray mSelectedItemsIds;
    private List<Integer> mSelectedItemsPositions = new ArrayList<>();

    public SeafItemAdapter(SeafileActivity activity) {
        mActivity = activity;
        items = new ArrayList<>();
        mSelectedItemsIds = new SparseBooleanArray();
    }

    /**
     * sort files type
     */
    public static final int SORT_BY_NAME = 9;
    /**
     * sort files type
     */
    public static final int SORT_BY_LAST_MODIFIED_TIME = 10;
    /**
     * sort files order
     */
    public static final int SORT_ORDER_ASCENDING = 11;
    /**
     * sort files order
     */
    public static final int SORT_ORDER_DESCENDING = 12;

    public void setItemsAndRefresh(List items) {
        SeafileActivity.mHandler.sendEmptyMessage(3);
        this.items = items;
        refresh();
    }

    public void refresh () {
        SeafileActivity.mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private View getRepoView(final SeafRepo repo, View convertView, ViewGroup parent) {
        View view = convertView;
        Viewholder viewHolder;

        if (convertView == null) {
            view = LayoutInflater.from(mActivity).inflate(R.layout.list_item, null);
            RelativeLayout container = (RelativeLayout) view.findViewById(R.id.list_item_container);
            container.setOnTouchListener(new GenericListener());
//            view = LayoutInflater.from(mainActivity).inflate(R.layout.list_item_entry, null);
            TextView title = (TextView) view.findViewById(R.id.list_item_title);
            TextView subtitle = (TextView) view.findViewById(R.id.list_item_subtitle);
            ImageView multiSelect = (ImageView) view.findViewById(R.id.list_item_multi_select_btn);
            ImageView icon = (ImageView) view.findViewById(R.id.list_item_icon);
//            RelativeLayout action = (RelativeLayout) view.findViewById(R.id.expandable_toggle_button);
            ImageView downloadStatusIcon = (ImageView) view.findViewById(R.id.list_item_download_status_icon);
            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.list_item_download_status_progressbar);
//            viewHolder = new Viewholder(title, subtitle, multiSelect, icon, action, downloadStatusIcon, progressBar);
            viewHolder = new Viewholder(container, title, subtitle, multiSelect, icon, downloadStatusIcon, progressBar);
            view.setTag(viewHolder);
        } else {
            viewHolder = (Viewholder) convertView.getTag();
        }

//        viewHolder.action.setOnClickListener(new View.OnClickListener() {
//            @Overrid
//            public void onClick(View v) {
//                mActivity.showRepoBottomSheet(repo);
//            }
//        });

        viewHolder.multiSelect.setVisibility(View.GONE);
        viewHolder.downloadStatusIcon.setVisibility(View.GONE);
        viewHolder.progressBar.setVisibility(View.GONE);
        viewHolder.title.setText(repo.getTitle());
        viewHolder.subtitle.setText(repo.getSubtitle());
        viewHolder.icon.setImageResource(repo.getIcon());
        viewHolder.container.setTag(repo);
//        if (repo.hasWritePermission()) {
//            viewHolder.action.setVisibility(View.VISIBLE);
//        } else {
//            viewHolder.action.setVisibility(View.INVISIBLE);
//        }
        return view;
    }
//
//    private View getGroupView(SeafGroup group) {
//        View view = LayoutInflater.from(mActivity).inflate(R.layout.group_item, null);
////        View view = LayoutInflater.from(mainActivity).inflate(R.layout.group_item, null);
//        TextView tv = (TextView) view.findViewById(R.id.textview_groupname);
//        String groupTitle = group.getTitle();
//        if ("Organization".equals(groupTitle)) {
//            groupTitle = mActivity.getString(R.string.shared_with_all);
//        }
//        tv.setText(groupTitle);
//        return view;
//    }
//
    private View getDirentView(final SeafDirent dirent, View convertView, ViewGroup parent, final int position) {
        View view = convertView;
        final Viewholder viewHolder;

        if (convertView == null) {
            view = LayoutInflater.from(mActivity).inflate(R.layout.list_item, null);
//            view = LayoutInflater.from(mainActivity).inflate(R.layout.list_item_entry, null);
            RelativeLayout container = (RelativeLayout) view.findViewById(R.id.list_item_container);
            container.setOnTouchListener(new GenericListener());
            TextView title = (TextView) view.findViewById(R.id.list_item_title);
            TextView subtitle = (TextView) view.findViewById(R.id.list_item_subtitle);
            ImageView icon = (ImageView) view.findViewById(R.id.list_item_icon);
            ImageView multiSelect = (ImageView) view.findViewById(R.id.list_item_multi_select_btn);
            RelativeLayout action = (RelativeLayout) view.findViewById(R.id.expandable_toggle_button);
            ImageView downloadStatusIcon = (ImageView) view.findViewById(R.id.list_item_download_status_icon);
            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.list_item_download_status_progressbar);
            viewHolder = new Viewholder(container, title, subtitle, multiSelect, icon, downloadStatusIcon, progressBar);
            view.setTag(viewHolder);
        } else {
            viewHolder = (Viewholder) convertView.getTag();
        }

        viewHolder.title.setText(dirent.getTitle());
        if (dirent.isDir()) {
            viewHolder.downloadStatusIcon.setVisibility(View.GONE);
            viewHolder.progressBar.setVisibility(View.GONE);

            viewHolder.subtitle.setText(dirent.getSubtitle());


            viewHolder.icon.setImageResource(dirent.getIcon());
        } else {
            viewHolder.downloadStatusIcon.setVisibility(View.GONE);
            viewHolder.progressBar.setVisibility(View.GONE);
            viewHolder.icon.setImageResource(dirent.getIcon());
//            setFileView(dirent, viewHolder, position);
        }
        viewHolder.container.setTag(dirent);

        return view;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = items.get(position);
        if (item instanceof SeafRepo) {
            return getRepoView((SeafRepo) item, convertView, parent);
        } else if (item instanceof SeafDirent) {
            return getDirentView((SeafDirent) item, convertView, parent, position);
//        } else if (item instanceof SeafGroup) {
//            return getGroupView((SeafGroup) item);
//        } else if (item instanceof SeafCachedFile) {
//            return getCacheView((SeafCachedFile) item, convertView, parent);

        } else {
            return null;

        }
    }

    class Viewholder {
        TextView title, subtitle;
        ImageView icon, multiSelect, downloadStatusIcon;
        // default
        ProgressBar progressBar;
        RelativeLayout container;

        public Viewholder(RelativeLayout container, TextView title,
                          TextView subtitle,
                          ImageView multiSelect,
                          ImageView icon,
//                          RelativeLayout action,
                          ImageView downloadStatusIcon,
                          ProgressBar progressBar) {

            super();
            this.container = container;
            this.icon = icon;
            this.multiSelect = multiSelect;
//            this.action = action;
            this.title = title;
            this.subtitle = subtitle;
            this.downloadStatusIcon = downloadStatusIcon;
            this.progressBar = progressBar;
        }
    }
}

