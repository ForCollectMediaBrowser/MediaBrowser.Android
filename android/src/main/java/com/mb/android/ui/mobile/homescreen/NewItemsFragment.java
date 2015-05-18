package com.mb.android.ui.mobile.homescreen;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mb.android.DialogFragments.LatestItemsDialogFragment;
import com.mb.android.MainApplication;
import com.mb.android.PlaylistItem;
import com.mb.android.R;
import com.mb.android.activities.mobile.BookDetailsActivity;
import com.mb.android.activities.mobile.MediaDetailsActivity;
import com.mb.android.activities.mobile.PhotoDetailsActivity;
import com.mb.android.adapters.HomeScreenItemsAdapter;
import com.mb.android.logging.AppLogger;
import com.mb.android.ui.mobile.album.MusicAlbumActivity;
import com.mb.android.ui.mobile.library.LibraryPresentationActivity;
import com.mb.android.ui.mobile.musicartist.ArtistActivity;
import com.mb.android.ui.mobile.playback.PlaybackActivity;

import java.util.ArrayList;

import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.LatestItemsQuery;

/**
 * Created by Mark on 12/12/13.
 *
 * Fragment that shows all items recently added to a users library.
 */
public class NewItemsFragment extends Fragment {

    private static final String TAG = "NewItemsFragment";
    private BaseItemDto[] mItems;
    private ProgressBar   mActivityIndicator;
    private GridView      mNewItemsGrid;
    private TextView      noContentText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_homescreen_items, container, false);
        if (view != null) {
            mActivityIndicator = (ProgressBar) view.findViewById(R.id.pbActivityIndicator);
            mNewItemsGrid = (GridView) view.findViewById(R.id.gvUpNext);
            noContentText = (TextView) view.findViewById(R.id.tvNoContentWarning);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        AppLogger.getLogger().Info(TAG + "onResume");
        if (MainApplication.getInstance().API != null
                && !tangible.DotNetToJavaStringHelper.isNullOrEmpty(MainApplication.getInstance().API.getCurrentUserId())) {
            LatestItemsQuery query = new LatestItemsQuery();
            query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
            query.setLimit(20);
            query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.ParentId});
            query.setIsPlayed(false);
            query.setGroupItems(true);
            MainApplication.getInstance().API.GetLatestItems(query, getNewItemsResponse);
        }
        AppLogger.getLogger().Info(TAG + "finish onResume");
    }

    private Response<BaseItemDto[]> getNewItemsResponse = new Response<BaseItemDto[]>() {
        @Override
        public void onResponse(BaseItemDto[] response) {
            AppLogger.getLogger().Info(TAG + ": GetNewItems");
            hideActivityIndicator();
            processResponse(response);
            toggleNoContentWarning();
            refreshOrInitializeGridContent();
            AppLogger.getLogger().Info(TAG + ": Finished GetNewItems");
        }
        @Override
        public void onError(Exception ex) {
            AppLogger.getLogger().Info(TAG + ": error getting new items");
        }
    };

    private void hideActivityIndicator() {
        if (null != mActivityIndicator) {
            mActivityIndicator.setVisibility(View.GONE);
        }
    }

    private void processResponse(BaseItemDto[] response) {
        if (null == response) {
            AppLogger.getLogger().Info(TAG + " - processResponse: Invalid response");
            return;
        }
        AppLogger.getLogger().Info(TAG + " - processResponse: " + String.valueOf(response.length) + " Items received.");
        mItems = response;
    }

    private void toggleNoContentWarning() {
        if (null == noContentText) return;
        boolean showWarning = null == mItems || mItems.length == 0;
        noContentText.setText(R.string.no_new_items_warning);
        noContentText.setVisibility(showWarning ? TextView.VISIBLE : TextView.INVISIBLE);
    }

    private void refreshOrInitializeGridContent() {
        if (mNewItemsGrid == null) return;

        HomeScreenItemsAdapter adapter = (HomeScreenItemsAdapter) mNewItemsGrid.getAdapter();
        if (adapter != null) {
            adapter.addNewDataset(mItems);
            adapter.notifyDataSetChanged();
        } else if (null != mItems) {
            mNewItemsGrid.setAdapter(new HomeScreenItemsAdapter(mItems, false));
            mNewItemsGrid.setOnItemClickListener(onItemClickListener);
        }
    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, int index, long id) {
            if (mItems == null || mItems.length == 0) {
                return;
            }

            String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(mItems[index]);
            Intent intent;

            // New items are going to be pretty much anything... awesome.

            if (mItems[index].getType().equalsIgnoreCase("series")) {
                LatestItemsDialogFragment fragment = new LatestItemsDialogFragment();
                fragment.setSeries(mItems[index]);
                fragment.show(getFragmentManager(), "LatestItemsDialog");
                return;
            } else if (mItems[index].getType().equalsIgnoreCase("musicartist")) {
                intent = new Intent(MainApplication.getInstance(), ArtistActivity.class);
                intent.putExtra("ArtistId", mItems[index].getId());
            } else if (mItems[index].getType().equalsIgnoreCase("musicalbum")) {
                intent = new Intent(MainApplication.getInstance(), MusicAlbumActivity.class);
                intent.putExtra("AlbumId", mItems[index].getId());
            } else if (mItems[index].getType().equalsIgnoreCase("audio")) {
                MainApplication.getInstance().API.GetItemAsync(
                        mItems[index].getAlbumId(),
                        MainApplication.getInstance().API.getCurrentUserId(),
                        getAlbumResponse);
                return;
            } else if (mItems[index].getType().equalsIgnoreCase("photo")) {
                intent = new Intent(MainApplication.getInstance(), PhotoDetailsActivity.class);
                intent.putExtra("Item", jsonData);
            } else if (mItems[index].getType().equalsIgnoreCase("book")) {
                intent = new Intent(MainApplication.getInstance(), BookDetailsActivity.class);
                intent.putExtra("Item", jsonData);
            } else if (mItems[index].getIsFolder()) {
                intent = new Intent(MainApplication.getInstance(), LibraryPresentationActivity.class);
                intent.putExtra("Item", jsonData);
            } else {
                intent = new Intent(MainApplication.getInstance(), MediaDetailsActivity.class);
                intent.putExtra("Item", jsonData);
                intent.putExtra("LaunchedFromHomeScreen", true);
            }

            startActivity(intent);
        }
    };

    private Response<BaseItemDto> getAlbumResponse = new Response<BaseItemDto>() {

        @Override
        public void onResponse(BaseItemDto item) {

            if (item == null) return;

            Intent intent = new Intent(MainApplication.getInstance(), MusicAlbumActivity.class);
            intent.putExtra("AlbumId", item.getId());

            startActivity(intent);
        }
        @Override
        public void onError(Exception ex) {

        }
    };

    @Override
    public void onStop() {
        super.onStop();
        mItems = null;
    }

    private void playRequest() {
        if (mItems == null || mItems.length == 0) return;

        BaseItemDto item = mItems[mNewItemsGrid.getSelectedItemPosition()];

        if ("series".equalsIgnoreCase(item.getType())) {
            handleSeriesPlayRequest(item.getId());
        } else {
            PlaylistItem playableItem = new PlaylistItem();
            playableItem.Id = item.getId();
            playableItem.Name = item.getName();
            playableItem.startPositionTicks = 0L;
            playableItem.Type = item.getType();

            if (item.getType().equalsIgnoreCase("episode"))
                playableItem.SecondaryText = item.getSeriesName();

            MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
            MainApplication.getInstance().PlayerQueue.PlaylistItems.add(playableItem);

            Intent intent = new Intent(MainApplication.getInstance(), PlaybackActivity.class);
            startActivity(intent);
        }
    }

    private void handleSeriesPlayRequest(String seriesId) {
        LatestItemsQuery query = new LatestItemsQuery();
        query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
        query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.ParentId, ItemFields.DateCreated});
        query.setParentId(seriesId);
        query.setIncludeItemTypes(new String[] { "episode" });
        query.setGroupItems(false);

        MainApplication.getInstance().API.GetLatestItems(query, new getSeriesUnwatchedItemsResponse());
    }

    private class getSeriesUnwatchedItemsResponse extends Response<BaseItemDto[]> {
        @Override
        public void onResponse(BaseItemDto[] episodes) {
            if (episodes == null || episodes.length == 0) return;

            MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();

            for (BaseItemDto item : episodes) {
                PlaylistItem playableItem = new PlaylistItem();
                playableItem.Id = item.getId();
                playableItem.Name = item.getName();
                playableItem.startPositionTicks = 0L;
                playableItem.Type = item.getType();
                playableItem.SecondaryText = item.getSeriesName();

                MainApplication.getInstance().PlayerQueue.PlaylistItems.add(playableItem);
            }

            Intent intent = new Intent(MainApplication.getInstance(), PlaybackActivity.class);
            startActivity(intent);
        }
    }
}
