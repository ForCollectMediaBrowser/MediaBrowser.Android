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

import com.mb.android.MainApplication;
import com.mb.android.PlaylistItem;
import com.mb.android.R;
import mediabrowser.apiinteraction.Response;
import com.mb.android.interfaces.ICommandListener;
import com.mb.android.logging.AppLogger;
import com.mb.android.ui.mobile.musicartist.ArtistActivity;
import com.mb.android.activities.mobile.BookDetailsActivity;
import com.mb.android.ui.mobile.library.LibraryPresentationActivity;
import com.mb.android.activities.mobile.MediaDetailsActivity;
import com.mb.android.ui.mobile.album.MusicAlbumActivity;
import com.mb.android.activities.mobile.PhotoDetailsActivity;
import com.mb.android.activities.mobile.SeriesViewActivity;
import com.mb.android.adapters.HomeScreenItemsAdapter;
import com.mb.android.ui.mobile.playback.PlaybackActivity;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.querying.ItemsResult;
import mediabrowser.model.querying.ItemQuery;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.ItemFilter;
import mediabrowser.model.querying.ItemSortBy;
import mediabrowser.model.entities.SortOrder;

import java.util.ArrayList;

/**
 * Created by Mark on 12/12/13.
 *
 * Fragment shows a grid of items that the user has toggled as favorite
 */
public class FavoritesFragment extends Fragment implements ICommandListener {

    private static final String TAG = "FavoritesFragment";
    private BaseItemDto[] mItems;
    private ProgressBar mActivityIndicator;
    private GridView mFavoriteItemsGrid;
    private TextView noContentText;

    /**
     * Class Constructor
     */
    public FavoritesFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        AppLogger.getLogger().Info(TAG + " onCreateView() reached");
        View view = inflater.inflate(R.layout.fragment_homescreen_items, container, false);
        if (view != null) {
            mActivityIndicator = (ProgressBar) view.findViewById(R.id.pbActivityIndicator);
            mFavoriteItemsGrid = (GridView) view.findViewById(R.id.gvUpNext);
            noContentText = (TextView) view.findViewById(R.id.tvNoContentWarning);
        }
        AppLogger.getLogger().Info(TAG + " finish onCreateView()");
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        AppLogger.getLogger().Info(TAG + "onResume");
        if (MainApplication.getInstance().API != null
                && !tangible.DotNetToJavaStringHelper.isNullOrEmpty(MainApplication.getInstance().API.getCurrentUserId())) {
            ItemQuery query = buildNewItemsQuery();
            MainApplication.getInstance().API.GetItemsAsync(query, getFavoritesResponse);
        }
        AppLogger.getLogger().Info(TAG + "finish onResume");
    }

    @Override
    public void onStop() {
        super.onStop();
        mItems = null;
    }

    private ItemQuery buildNewItemsQuery() {
        ItemQuery query = new ItemQuery();
        query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
        query.setLimit(20);
        query.setRecursive(true);
        query.setSortBy(new String[]{ItemSortBy.DateCreated});
        query.setSortOrder(SortOrder.Descending);
        query.setFilters(new ItemFilter[]{ItemFilter.IsFavorite});
        query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.ParentId});
        query.setExcludeItemTypes(new String[]{"CollectionFolder", "TrailerCollectionFolder"});
        return query;
    }

    private Response<ItemsResult> getFavoritesResponse = new Response<ItemsResult>() {
        @Override
        public void onResponse(ItemsResult response) {
            AppLogger.getLogger().Info(TAG + ": GetFavoriteItems callback");
            hideActivityIndicator();
            processResponse(response);
            toggleNoContentWarning();
            refreshOrInitializeGridContent();
            AppLogger.getLogger().Info(TAG + ": Finished GetFavoriteItems callback");
        }
        @Override
        public void onError(Exception ex) {
            AppLogger.getLogger().Info("********* ON ERROR *********");
        }
    };

    private void hideActivityIndicator() {
        if (null != mActivityIndicator) {
            mActivityIndicator.setVisibility(View.GONE);
        }
    }

    private void processResponse(ItemsResult response) {
        if (null == response) {
            AppLogger.getLogger().Info(TAG + " - processResponse: Invalid response");
            return;
        }
        try {
            if (null != response.getItems() && response.getItems().length > 0) {
                AppLogger.getLogger().Info(TAG + " - processResponse: Items received.");
                mItems = response.getItems();
            }
        } catch (Exception ex) {
            AppLogger.getLogger().Info(TAG + " - processResponse: Failed to cast data");
        }
    }

    private void toggleNoContentWarning() {
        if (null == noContentText) return;
        boolean showNoContentWarning = mItems == null || mItems.length == 0;
        noContentText.setVisibility(showNoContentWarning ? TextView.VISIBLE : TextView.INVISIBLE);
    }

    private void refreshOrInitializeGridContent() {
        if (mFavoriteItemsGrid == null) return;
        HomeScreenItemsAdapter adapter = (HomeScreenItemsAdapter) mFavoriteItemsGrid.getAdapter();
        if (adapter != null) {
            adapter.addNewDataset(mItems);
            adapter.notifyDataSetChanged();
        } else if (null != mItems) {
            mFavoriteItemsGrid.setAdapter(new HomeScreenItemsAdapter(mItems, false));
            mFavoriteItemsGrid.setOnItemClickListener(onItemClickListener);
        }
    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, int index, long id) {
            // Favorites are going to be pretty much anything...
            String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(mItems[index]);
            Intent intent;

            if (mItems[index].getType().equalsIgnoreCase("series")) {
                intent = new Intent(MainApplication.getInstance(), SeriesViewActivity.class);
                intent.putExtra("Item", jsonData);
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
    public void onPreviousButton() {

    }


    @Override
    public void onNextButton() {

    }


    @Override
    public void onPlayPauseButton() {
        playRequest();
    }


    @Override
    public void onPlayButton() {
        playRequest();
    }


    @Override
    public void onPauseButton() {

    }


    private void playRequest() {
        if (mItems == null || mItems.length == 0) return;

        BaseItemDto item = mItems[mFavoriteItemsGrid.getSelectedItemPosition()];

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
