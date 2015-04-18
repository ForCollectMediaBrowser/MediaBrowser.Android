package com.mb.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.mb.android.MainApplication;
import com.mb.android.R;
import com.mb.android.ui.mobile.library.LibraryPresentationActivity;
import com.mb.android.activities.mobile.SeriesViewActivity;
import com.mb.android.adapters.HomeScreenItemsAdapter;
import com.mb.android.adapters.MediaAdapterPosters;
import mediabrowser.apiinteraction.Response;
import mediabrowser.model.querying.ItemsResult;
import mediabrowser.model.querying.EpisodeQuery;
import mediabrowser.model.querying.SeasonQuery;
import mediabrowser.model.querying.ItemFields;
import com.mb.android.logging.AppLogger;

import java.util.Arrays;

/**
 * Created by Mark on 12/12/13.
 *
 * Fragment that shows a grid of seasons for the current Series
 */
public class SeasonsFragment extends Fragment {

    private View mView;
    private String mSeriesid;
    private boolean mIsTabletLayout;
    private boolean mPostersEnabled;
    private SeriesViewActivity mSeriesActivity;

    /**
     * Class Constructor
     */
    public SeasonsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        AppLogger.getLogger().Info("SeasonsFragment: onCreateView");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mSeriesActivity);
        mPostersEnabled = true;

        if (mPostersEnabled)
            mView = inflater.inflate(R.layout.fragment_library_presentation_poster,
                    container, false);
        else
            mView = inflater.inflate(R.layout.fragment_library_presentation, container, false);

        Bundle args = getArguments();

        mSeriesid = args.getString("SeriesId");
        mIsTabletLayout = args.getBoolean("IsTabletLayout", false);

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();

        AppLogger.getLogger().Info("SeasonsFragment: creating query");
        SeasonQuery query = new SeasonQuery();
        query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
        query.setSeriesId(mSeriesid);
        query.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio, ItemFields.SortName});

        AppLogger.getLogger().Info("SeasonsFragment: Requesting seasons");
        MainApplication.getInstance().API.GetSeasonsAsync(query, getItemsResponse);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity != null) {
            try {
                mSeriesActivity = (SeriesViewActivity) activity;
            } catch (ClassCastException e) {
                AppLogger.getLogger().Debug("ServerSelectionFragment", "onAttach: Exception casting activity");
            }
        }
    }

    private Response<ItemsResult> getItemsResponse = new Response<ItemsResult>() {

        @Override
        public void onResponse(final ItemsResult response) {
            AppLogger.getLogger().Info("SeasonsFragment: GetItemsCallback");

            if (response != null && response.getItems() != null && response.getItems().length > 0) {

                AppLogger.getLogger()
                        .Info("SeasonsFragment: Initialize GridView");

                GridView seasonsList = (GridView) mView.findViewById(R.id.gvLibrary);
                if (mPostersEnabled) {
                    seasonsList.setAdapter(
                            new MediaAdapterPosters(
                                    Arrays.asList(response.getItems()),
                                    MainApplication.getInstance().getResources().getInteger(R.integer.library_columns_poster),
                                    MainApplication.getInstance().API,
                                    R.drawable.default_video_portrait
                            )
                    );
                } else {
                    seasonsList.setAdapter(new HomeScreenItemsAdapter(response.getItems(), true));
                }

                seasonsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {

                        Intent intent = new Intent(mSeriesActivity, LibraryPresentationActivity.class);

                        EpisodeQuery query = new EpisodeQuery();
                        query.setUserId(MainApplication.getInstance().API.getCurrentUserId());
                        query.setSeriesId(mSeriesid);
                        query.setSeasonId(String.valueOf(response.getItems()[position].getId()));
                        query.setFields(new ItemFields[]{ItemFields.SortName, ItemFields.PrimaryImageAspectRatio});

                        String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(query);

                        intent.putExtra("EpisodeQuery", jsonData);
                        intent.putExtra("DisableIndexing", true);

                        startActivity(intent);
                    }
                });

                if (mIsTabletLayout)
                    seasonsList.requestFocus();
            } else {
                if (response == null) {
                    AppLogger.getLogger().Info("SeasonsFragment: Results is null");
                } else {
                    AppLogger.getLogger().Info("SeasonsFragment: Results is empty");
                }
            }
        }
        @Override
        public void onError(Exception ex) {

        }
    };
}
