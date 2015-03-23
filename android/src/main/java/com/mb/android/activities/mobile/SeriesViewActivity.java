package com.mb.android.activities.mobile;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.volley.toolbox.NetworkImageView;
import com.mb.android.activities.BaseMbMobileActivity;
import mediabrowser.apiinteraction.Response;

import com.mb.android.logging.AppLogger;
import com.mb.android.playbackmediator.cast.exceptions.NoConnectionException;
import com.mb.android.playbackmediator.cast.exceptions.TransientNetworkDisconnectionException;
import com.mb.android.playbackmediator.widgets.MiniController;
import com.mb.android.MainApplication;
import com.mb.android.Playlist;
import com.mb.android.PlaylistItem;
import com.mb.android.R;
import com.mb.android.fragments.NavigationMenuFragment;
import com.mb.android.player.AudioService;
import com.mb.android.ui.mobile.playback.PlaybackActivity;
import com.mb.android.utils.Utils;

import mediabrowser.model.dlna.StreamInfo;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.ImageOptions;
import mediabrowser.model.dto.UserItemDataDto;
import mediabrowser.model.querying.ItemsResult;
import mediabrowser.model.querying.ItemQuery;
import mediabrowser.model.querying.ThemeMediaResult;
import mediabrowser.model.entities.ImageType;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.ItemSortBy;
import mediabrowser.model.library.PlayAccess;
import mediabrowser.model.entities.SortOrder;
import com.mb.android.fragments.SeasonsFragment;
import com.mb.android.fragments.SeriesDetailsFragment;

import mediabrowser.model.session.PlayCommand;

/**
 * Created by Mark on 12/12/13.
 *
 * This Activity displays various information relevant to the currently selected series
 */
public class SeriesViewActivity extends BaseMbMobileActivity {

    private static final String TAG = "SeriesViewActivity";
    private ActionBarDrawerToggle mDrawerToggle;
    private BaseItemDto mSeries;
    private boolean mAddFavoriteMenuItemVisible;
    private boolean mRemoveFavoriteMenuItemVisible;
    private boolean mSetPlayedMenuItemVisible;
    private boolean mSetUnPlayedMenuItemVisible;
    private NetworkImageView mBackdropImage;
    private int mImageIndex = 1;
    private boolean mDying = false;
    private boolean shouldPlayThemeSong;
    private SeriesDetailsFragment seriesDetailsFragment;

    public void setSeriesDetailsFragment(SeriesDetailsFragment fragment) {
        seriesDetailsFragment = fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_view);

        AppLogger.getLogger().Info("SeriesViewActivity: onCreate");

        ((PagerTabStrip) findViewById(R.id.pager_title_strip)).setDrawFullUnderline(false);

        String jsonData = getMb3Intent().getStringExtra("Item");
        mSeries = MainApplication.getInstance().getJsonSerializer().DeserializeFromString(jsonData, BaseItemDto.class);

        if (mSeries != null) {
            ViewPager pager = (ViewPager) findViewById(R.id.seriesPager);

            if (pager != null) {
                AppLogger.getLogger().Info("SeriesViewActivity: Initialize ViewPager");
                pager.setAdapter(new SeriesPagerAdapter(getSupportFragmentManager()));
            }
        }

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawer.setFocusableInTouchMode(false);

        NavigationMenuFragment fragment = (NavigationMenuFragment) getSupportFragmentManager().findFragmentById(R.id.left_drawer);
        if (fragment != null && fragment.isInLayout()) {
            fragment.setDrawerLayout(drawer);
        }

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                drawer,
                R.string.abc_action_bar_home_description,
                R.string.abc_action_bar_up_description) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
//                getActionBar().setTitle(mTitle);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
//                getActionBar().setTitle(mDrawerTitle);
            }

        };

        drawer.setDrawerListener(mDrawerToggle);

        // Don't request the theme song if the feature is disabled in settings or the Audio player is playing
        if (savedInstanceState == null &&
                PreferenceManager.getDefaultSharedPreferences(SeriesViewActivity.this)
                        .getBoolean("pref_play_theme", false)) {
            if (!MainApplication.getAudioService().getPlayerState().equals(AudioService.PlayerState.PLAYING)) {
                shouldPlayThemeSong = true;
            }

        }

        mMini = (MiniController) findViewById(R.id.miniController1);
        mCastManager.addMiniController(mMini);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    @Override
    public void onResume() {
        super.onResume();
        buildUi();
        mDying = false;
    }


    @Override
    public void onPause() {

        mMini.removeOnMiniControllerChangedListener(mCastManager);
        mDying = true;
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {

        if (mSeries != null) {
            String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(mSeries);
            savedInstanceState.putString("Item",jsonData);
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onConnectionRestored() {
        buildUi();
    }

    private void buildUi() {
        if (mSeries == null) return;

        if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(mSeries.getId())) {

            AppLogger.getLogger().Info("SeriesViewActivity: GetItemAsync");
            MainApplication.getInstance().API.GetItemAsync(
                    mSeries.getId(),
                    MainApplication.getInstance().API.getCurrentUserId(),
                    getItemResponse);

            if (shouldPlayThemeSong) {
                MainApplication.getInstance().API.GetThemeSongsAsync(
                        MainApplication.getInstance().API.getCurrentUserId(),
                        mSeries.getId(), false,
                        getLocalThemeSongsResponse
                );
            }
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Just in case the theme is still playing
            MainApplication.getInstance().StopMedia();
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
// && !mCastManager.isConnected()
        if (mSeries != null && mSeries.getPlayAccess().equals(PlayAccess.Full)) {
            menu.add(getResources().getString(R.string.play_all_action_bar_button))
                    .setIcon(R.drawable.play)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(getResources().getString(R.string.shuffle_action_bar_button))
                    .setIcon(R.drawable.shuffle)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        MenuItem mRemoveFavoriteMenuItem = menu.add(getResources().getString(R.string.un_favorite_action_bar_button));
        mRemoveFavoriteMenuItem.setIcon(R.drawable.nfav);
        mRemoveFavoriteMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (mRemoveFavoriteMenuItemVisible)
            mRemoveFavoriteMenuItem.setVisible(true);
        else
            mRemoveFavoriteMenuItem.setVisible(false);

        MenuItem mAddFavoriteMenuItem = menu.add(getResources().getString(R.string.favorite_action_bar_button));
        mAddFavoriteMenuItem.setIcon(R.drawable.fav);
        mAddFavoriteMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (mAddFavoriteMenuItemVisible)
            mAddFavoriteMenuItem.setVisible(true);
        else
            mAddFavoriteMenuItem.setVisible(false);

        MenuItem mSetPlayedMenuItem = menu.add(getResources().getString(R.string.played_action_bar_button));
        mSetPlayedMenuItem.setIcon(R.drawable.set_played);
        mSetPlayedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (mSetPlayedMenuItemVisible)
            mSetPlayedMenuItem.setVisible(true);
        else
            mSetPlayedMenuItem.setVisible(false);

        MenuItem mSetUnplayedMenuItem = menu.add(getResources().getString(R.string.un_played_action_bar_button));
        mSetUnplayedMenuItem.setIcon(R.drawable.set_unplayed);
        mSetUnplayedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (mSetUnPlayedMenuItemVisible)
            mSetUnplayedMenuItem.setVisible(true);
        else
            mSetUnplayedMenuItem.setVisible(false);

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        /*
        Play all
         */
        if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.play_all_action_bar_button))) {
            handlePlayOrShuffleRequest(false);

        /*
        Shuffle
         */
        } else if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.shuffle_action_bar_button))) {
            handlePlayOrShuffleRequest(true);

        /*
        Favorite
         */
        } else if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.favorite_action_bar_button))) {

            MainApplication.getInstance().API.UpdateFavoriteStatusAsync(
                    mSeries.getId(),
                    MainApplication.getInstance().API.getCurrentUserId(),
                    true,
                    new UpdateFavoriteResponse()
            );

        /*
        Remove Favorite
         */
        } else if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.un_favorite_action_bar_button))) {

            MainApplication.getInstance().API.UpdateFavoriteStatusAsync(
                    mSeries.getId(),
                    MainApplication.getInstance().API.getCurrentUserId(),
                    false,
                    new UpdateFavoriteResponse()
            );

        /*
        Set Played
         */
        } else if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.played_action_bar_button))) {

            MainApplication.getInstance().API.MarkPlayedAsync(
                    mSeries.getId(),
                    MainApplication.getInstance().API.getCurrentUserId(),
                    null,
                    new UpdatePlaystateResponse()
            );

        /*
        Remove Played
         */
        } else if (((String) item.getTitle()).equalsIgnoreCase(getResources().getString(R.string.un_played_action_bar_button))) {

            MainApplication.getInstance().API.MarkUnplayedAsync(
                    mSeries.getId(),
                    MainApplication.getInstance().API.getCurrentUserId(),
                    new UpdatePlaystateResponse()
            );

        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void handlePlayOrShuffleRequest(boolean shuffleMedia) {

        AppLogger.getLogger().Info("Library Presentation Fragment: Play-all/Shuffle clicked");

        AudioService.PlayerState currentState = MainApplication.getAudioService().getPlayerState();
        if (currentState.equals(AudioService.PlayerState.PLAYING) || currentState.equals(AudioService.PlayerState.PAUSED)) {
            MainApplication.getAudioService().stopMedia();
        }
        MainApplication.getInstance().PlayerQueue = new Playlist();
        MainApplication.getInstance().StopMedia();

        if (mCastManager.isConnected()) {
            mCastManager.playItem(mSeries, shuffleMedia ? PlayCommand.PlayShuffle : PlayCommand.PlayNow, 0L);
        } else {
            ItemQuery query = new ItemQuery();
            query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
            query.setParentId(mSeries.getId());
            query.setSortBy(new String[]{shuffleMedia ? ItemSortBy.Random : ItemSortBy.SortName});
            query.setSortOrder(SortOrder.Ascending);
            query.setIncludeItemTypes(new String[]{"Episode"});
            query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.SortName, ItemFields.DateCreated});
            query.setRecursive(true);
            query.setIsMissing(false);
            query.setIsVirtualUnaired(false);

            MainApplication.getInstance().API.GetItemsAsync(query, playAllShuffleResponse);
        }
    }

    public void updateFavoriteVisibleIcons() {

        AppLogger.getLogger().Info("", "updateFavoriteVisibleIcons called");
        AppLogger.getLogger().Info("Update favorite visible icons");

        if (mSeries != null && mSeries.getUserData() != null && mSeries.getUserData().getIsFavorite()) {

            AppLogger.getLogger().Info("Show remove favorite");
            // only show the remove favorite
            mAddFavoriteMenuItemVisible = false;
            mRemoveFavoriteMenuItemVisible = true;

        } else {

            AppLogger.getLogger().Info("Show add favorite");
            // only show the add favorite
            mAddFavoriteMenuItemVisible = true;
            mRemoveFavoriteMenuItemVisible = false;
        }

        this.invalidateOptionsMenu();
    }

    private void updatePlaystateVisibleIcons() {

        AppLogger.getLogger().Info("", "updatePlaystateVisibleIcons called");
        AppLogger.getLogger().Info("Update playstate visible icons");

        if (mSeries != null && mSeries.getUserData() != null && mSeries.getUserData().getPlayed()) {

            // only show the remove favorite
            mSetPlayedMenuItemVisible = false;
            mSetUnPlayedMenuItemVisible = true;

        } else {

            // only show the add favorite
            mSetPlayedMenuItemVisible = true;
            mSetUnPlayedMenuItemVisible = false;
        }

        this.invalidateOptionsMenu();
    }

    private class UpdateFavoriteResponse extends Response<UserItemDataDto> {
        @Override
        public void onResponse(UserItemDataDto response) {

            if (response == null) return;
            mSeries.getUserData().setIsFavorite(response.getIsFavorite());
            updateFavoriteVisibleIcons();
        }
    }

    private class UpdatePlaystateResponse extends Response<UserItemDataDto> {
        @Override
        public void onResponse(UserItemDataDto userItemDataDto) {
            if (userItemDataDto == null) return;

            mSeries.getUserData().setPlayed(userItemDataDto.getPlayed());
            updatePlaystateVisibleIcons();
        }
    }


    private Response<BaseItemDto> getItemResponse = new Response<BaseItemDto>() {

        @Override
        public void onResponse(BaseItemDto item) {
            AppLogger.getLogger().Info("GetInitialItemCallback", "Item Callback");
            AppLogger.getLogger().Info("SeriesViewActivity: GetInitialItemCallback");

            if (item == null) {
                AppLogger.getLogger().Info("GetInitialItemCallback", "result is null");
                AppLogger.getLogger().Info("SeriesViewActivity: item is null");
                return;
            }

            mSeries = item;

            if (seriesDetailsFragment != null) {
                seriesDetailsFragment.setSeries(mSeries);
            }

            if (com.mb.android.playbackmediator.utils.Utils.getBooleanFromPreference(SeriesViewActivity.this, "CONTENT_MIRROR_ENABLED", false)) {
                try {
                    mCastManager.displayItem(item);
                } catch (TransientNetworkDisconnectionException | NoConnectionException | IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }

            updateFavoriteVisibleIcons();
            updatePlaystateVisibleIcons();

            mBackdropImage = (NetworkImageView) findViewById(R.id.ivMediaBackdrop);

            if (mBackdropImage == null)
                return;

            if (item.getBackdropCount() > 0) {

                ImageOptions options = new ImageOptions();
                options.setImageType(ImageType.Backdrop);
                options.setWidth(getResources().getDisplayMetrics().widthPixels);

                String imageUrl = MainApplication.getInstance().API.GetImageUrl(item, options);
                mBackdropImage.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());

                if (item.getBackdropCount() > 1)
                    mBackdropImage.postDelayed(CycleBackdrop, 8000);
            }
        }
        @Override
        public void onError(Exception ex) {

        }
    };


    private class SeriesPagerAdapter extends FragmentPagerAdapter {

        public SeriesPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {



            switch (position) {
                case 0:
                    return new SeriesDetailsFragment();
                case 1:
                    Bundle args = new Bundle();
                    args.putSerializable("SeriesId", mSeries.getId());
                    SeasonsFragment mSeasonsFragment = new SeasonsFragment();
                    mSeasonsFragment.setArguments(args);
                    return mSeasonsFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {

            if (position == 0)
                return getResources().getString(R.string.overview_header);

            return getResources().getString(R.string.seasons_header);
        }
    }


    private Response<ItemsResult> playAllShuffleResponse = new Response<ItemsResult>() {

        @Override
        public void onResponse(ItemsResult response) {
            AppLogger.getLogger().Info(TAG + ": play-all/shuffle response received");

            if (response == null) {
                AppLogger.getLogger().Info(TAG + ": response was null");
                return;
            }

            if (response.getItems() == null) {
                AppLogger.getLogger().Info(TAG + ": items is null");
                return;
            }

            if (response.getItems().length == 0) {
                AppLogger.getLogger().Info(TAG + ": items is empty");
                return;
            }

            AppLogger.getLogger().Info(TAG + ": processing response");
            for (BaseItemDto item : response.getItems()) {
                PlaylistItem playableItem = new PlaylistItem();
                playableItem.Id = item.getId();
                playableItem.Name = item.getName();
                if (item.getType().equalsIgnoreCase("Episode") && item.getParentIndexNumber() != null && item.getIndexNumber() != null)
                    playableItem.SecondaryText = "Season " + String.valueOf(item.getParentIndexNumber()) + " | Episode " + String.valueOf(item.getIndexNumber());
                playableItem.Type = item.getType();

                MainApplication.getInstance().PlayerQueue.PlaylistItems.add(playableItem);
            }

            Intent intent = new Intent(SeriesViewActivity.this, PlaybackActivity.class);
            startActivity(intent);
        }
        @Override
        public void onError(Exception ex) {

        }
    };


    private Response<ThemeMediaResult> getLocalThemeSongsResponse = new Response<ThemeMediaResult>() {

        @Override
        public void onResponse(ThemeMediaResult themeSongs) {

            if (themeSongs == null || themeSongs.getItems() == null || themeSongs.getItems().length < 1)
                return;

            Utils.getStreamInfo(themeSongs.getItems()[0], new Response<StreamInfo>() {
                @Override
                public void onResponse(StreamInfo response) {
                    MainApplication.getInstance().PlayMedia(response.ToUrl(MainApplication.getInstance().API.getApiUrl(), MainApplication.getInstance().API.getAccessToken()));
                }

                @Override
                public void onError(Exception exception) {
                    AppLogger.getLogger().ErrorException("Error playing theme media", exception);
                }
            });
        }
        @Override
        public void onError(Exception ex) {
            AppLogger.getLogger().ErrorException("Error retrieving theme media", ex);
        }
    };


    private Runnable CycleBackdrop = new Runnable() {

        @Override
        public void run() {

            if (mDying)
                return;

            if (mImageIndex >= mSeries.getBackdropCount())
                mImageIndex = 0;

            ImageOptions options = new ImageOptions();
            options.setImageType(ImageType.Backdrop);
            options.setWidth(getResources().getDisplayMetrics().widthPixels);
            options.setImageIndex(mImageIndex);

            mImageIndex += 1;

            String imageUrl = MainApplication.getInstance().API.GetImageUrl(mSeries, options);
            mBackdropImage.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());

            mBackdropImage.postDelayed(CycleBackdrop, 8000);
        }
    };
}
