package com.mb.android.DialogFragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;
import com.mb.android.MainApplication;
import com.mb.android.R;
import com.mb.android.activities.mobile.MediaDetailsActivity;
import com.mb.android.activities.mobile.SeriesViewActivity;
import com.mb.android.logging.AppLogger;
import com.mb.android.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;

import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.ImageOptions;
import mediabrowser.model.entities.ImageType;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.LatestItemsQuery;

/**
 * Created by Mark on 12/12/13.
 *
 * When a user enters a new address for an existing server (through the add new server button). This
 * dialog will show the user the existing addresses and confirm which one the user wants to overwrite.
 *
 * The user must select either the local or remote address before continuing.
 */
public class LatestItemsDialogFragment extends DialogFragment {

    private BaseItemDto mParent;
    private NetworkImageView mHeaderImage;
    private ListView mLatestItemsList;
    private BaseItemDto[] mLatestItems;

    private Comparator<BaseItemDto> baseItemDtoComparator = new Comparator<BaseItemDto>() {
        @Override
        public int compare(BaseItemDto baseItemDto, BaseItemDto t1) {
            return compareEpisodeName(baseItemDto, t1);
        }
    };

    private int compareEpisodeName(BaseItemDto item1, BaseItemDto item2) {
        if (item1.getParentIndexNumber() != null && item1.getIndexNumber() != null && item2.getParentIndexNumber() != null && item2.getIndexNumber() != null) {
            try {
                int compareResult=item1.getParentIndexNumber().compareTo(item2.getParentIndexNumber());
                if(compareResult==0)compareResult=item1.getIndexNumber().compareTo(item2.getIndexNumber());
                return compareResult;
            } catch (Exception e) {
                AppLogger.getLogger().ErrorException("Error setting episode text", e);
                return item1.getName().compareTo(item2.getName());
            }
        } else {
            return item1.getName().compareTo(item2.getName());
        }
    }

    /**
     * Class Constructor
     */
    public LatestItemsDialogFragment() {}

    public void setSeries(BaseItemDto item) {
        mParent = item;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        @SuppressLint("InflateParams")
        View mDialogContent = inflater.inflate(R.layout.fragment_latest_items_popup, null);
        mHeaderImage = (NetworkImageView) mDialogContent.findViewById(R.id.ivLatestItemsHeaderImage);
        mLatestItemsList = (ListView) mDialogContent.findViewById(R.id.lvLatestItems);

        setImage();
        getListContent();

        builder.setView(mDialogContent);

        return builder.create();

    }


    private void setImage() {

        if (mParent.getHasBanner()) {
            ImageOptions options = new ImageOptions();
            options.setImageType(ImageType.Banner);

            String imageUrl = MainApplication.getInstance().API.GetImageUrl(mParent.getId(), options);
            mHeaderImage.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());
            mHeaderImage.setOnClickListener(onHeaderClickListener);
        }

    }


    private void getListContent() {

        int limit = 20;

        if (mParent.getUserData() != null && mParent.getUserData().getUnplayedItemCount() != null)
            limit = Math.min(mParent.getUserData().getUnplayedItemCount(), 20);

        LatestItemsQuery query = new LatestItemsQuery();
        query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
        query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.ParentId, ItemFields.DateCreated});
        query.setParentId(mParent.getId());
        query.setLimit(limit);
        query.setIncludeItemTypes(new String[] { "episode" });
        query.setGroupItems(false);

        MainApplication.getInstance().API.GetLatestItems(query, getNewItemsResponse);
    }


    private Response<BaseItemDto[]> getNewItemsResponse = new Response<BaseItemDto[]>() {
        @Override
        public void onResponse(BaseItemDto[] response) {
            mLatestItems = response;

            if (mLatestItems == null) {
                AppLogger.getLogger().Debug("LatestItemsDialogFragment", "mLatestItems was null");
                return;
            }

            Arrays.sort(mLatestItems, baseItemDtoComparator);

            AppLogger.getLogger().Debug("LatestItemsDialogFragment", String.valueOf(mLatestItems.length) + " Items returned");

            mLatestItemsList.setAdapter(new LatestEpisodesAdapter());
            mLatestItemsList.setOnItemClickListener(onItemClickListener);
        }
        @Override
        public void onError(Exception ex) {
            AppLogger.getLogger().Info("********* ON ERROR *********");
        }
    };


    private class LatestEpisodesAdapter extends BaseAdapter {

        private LayoutInflater mLayoutInflater;
        private float mDensity;


        public LatestEpisodesAdapter() {
            mLayoutInflater = (LayoutInflater) MainApplication.getInstance().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            DisplayMetrics metrics = MainApplication.getInstance().getResources().getDisplayMetrics();
            mDensity = metrics.density;
        }

        @Override
        public int getCount() {
            return mLatestItems.length;
        }

        @Override
        public Object getItem(int position) {
            return mLatestItems[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            EpisodeHolder episodeHolder;

            if (convertView == null) {

                convertView = mLayoutInflater.inflate(R.layout.widget_latest_episodes, parent, false);

                episodeHolder = new EpisodeHolder();
                episodeHolder.episodeImage = (NetworkImageView) convertView.findViewById(R.id.ivEpisodeImage);
                episodeHolder.episodeTitle = (TextView) convertView.findViewById(R.id.tvEpisodeTitle);
                episodeHolder.addedDate = (TextView) convertView.findViewById(R.id.tvAddedDate);

                convertView.setTag(episodeHolder);

            } else {
                episodeHolder = (EpisodeHolder) convertView.getTag();
            }

            if (mLatestItems[position].getParentIndexNumber() != null && mLatestItems[position].getIndexNumber() != null) {
                try {
                    String epTitle = String.valueOf(mLatestItems[position].getParentIndexNumber()) + "." +
                            String.valueOf(mLatestItems[position].getIndexNumber());
                    if (mLatestItems[position].getIndexNumberEnd() != null && !mLatestItems[position].getIndexNumber().equals(mLatestItems[position].getIndexNumberEnd())) {
                        epTitle += "-" + String.valueOf(mLatestItems[position].getIndexNumberEnd());
                    }

                    if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(epTitle)) {
                        epTitle += " - ";
                    }

                    epTitle += mLatestItems[position].getName();
                    episodeHolder.episodeTitle.setText(epTitle);
                } catch (Exception e) {
                    AppLogger.getLogger().ErrorException("Error setting episode text", e);
                    episodeHolder.episodeTitle.setText(mLatestItems[position].getName());
                }
            } else {
                episodeHolder.episodeTitle.setText(mLatestItems[position].getName());
            }

            if (mLatestItems[position].getHasPrimaryImage()) {
                ImageOptions options = new ImageOptions();
                options.setImageType(ImageType.Primary);
                options.setWidth((int)(140 * mDensity));

                String imageUrl = MainApplication.getInstance().API.GetImageUrl(mLatestItems[position].getId(), options);
                episodeHolder.episodeImage.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());
            }

            if (mLatestItems[position].getDateCreated() != null) {

                Date date = Utils.convertToLocalDate(mLatestItems[position].getDateCreated());

                SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd/yyyy");
                TimeZone tz = TimeZone.getDefault();
                outputFormat.setTimeZone(tz);

                String addedDateString =
                        String.format(MainApplication.getInstance().getResources().getString(R.string.added_on_date), outputFormat.format(date));

                episodeHolder.addedDate.setText(addedDateString);
            }

            return convertView;
        }
    }

    private View.OnClickListener onHeaderClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(mParent);
            Intent intent = new Intent(MainApplication.getInstance(), SeriesViewActivity.class);
            intent.putExtra("Item", jsonData);

            startActivity(intent);
            LatestItemsDialogFragment.this.dismiss();
        }
    };

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(mLatestItems[position]);

            Intent intent = new Intent(MainApplication.getInstance(), MediaDetailsActivity.class);
            intent.putExtra("Item", jsonData);
            intent.putExtra("LaunchedFromHomeScreen", true);

            startActivity(intent);
            LatestItemsDialogFragment.this.dismiss();
        }
    };

    private class EpisodeHolder {
        public TextView episodeTitle;
        public TextView addedDate;
        public NetworkImageView episodeImage;
    }
}
