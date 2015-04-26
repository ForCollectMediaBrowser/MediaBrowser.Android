package com.mb.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;
import com.mb.android.MainApplication;
import com.mb.android.R;
import com.mb.android.activities.mobile.SeriesViewActivity;
import com.mb.android.ui.tv.library.LibraryTools;

import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.ImageOptions;
import mediabrowser.model.entities.ImageType;
import com.mb.android.logging.AppLogger;

/**
 * Created by Mark on 12/12/13.
 *
 * Fragment that displays various information about the current Series.
 */
public class SeriesDetailsFragment extends Fragment {

    private View mView;
    private BaseItemDto mSeries;
    private NetworkImageView mBackdropImage;
    private String mTvdbBaseUrl = "http://thetvdb.com/index.php?tab=series&id=";
    private SeriesViewActivity mSeriesActivity;


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity != null) {
            try {
                mSeriesActivity = (SeriesViewActivity) activity;
                mSeriesActivity.setSeriesDetailsFragment(this);
            } catch (ClassCastException e) {
                AppLogger.getLogger().Debug("ServerSelectionFragment", "onAttach: Exception casting activity");
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        AppLogger.getLogger().Info("SeriesDetailsFragment: onCreateView");

        mView = inflater.inflate(R.layout.fragment_series_details, container, false);
        mBackdropImage = (NetworkImageView) mView.findViewById(R.id.ivSeriesImageHeader);

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void setSeries(BaseItemDto series) {
        mSeries = series;

        AppLogger.getLogger().Info("SeriesDetailsFragment: BuildImageQuery");

        if (mBackdropImage != null) {

            // lock the dimensions to stop the image from 'jumping'. Only an issue in portrait mode
            int width = mSeriesActivity.getScreenWidth();
            int height = (width / 16) * 9;
            mBackdropImage.setLayoutParams(new LinearLayout.LayoutParams(width, height));

            ImageOptions options = null;

            if (mSeries.getHasThumb()) {

                options = MainApplication.getInstance().getImageOptions(ImageType.Thumb);
                options.setImageIndex(0);

            } else if (mSeries.getBackdropCount() > 0) {

                options = MainApplication.getInstance().getImageOptions(ImageType.Backdrop);
                options.setImageIndex(0);
            }

            if (options != null) {

                DisplayMetrics metrics = new DisplayMetrics();
                mSeriesActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

                options.setWidth(metrics.widthPixels);

                String imageUrl = MainApplication.getInstance().API.GetImageUrl(mSeries, options);
                mBackdropImage.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());
            }
        } else {
            NetworkImageView primaryImageLandscape = (NetworkImageView) mView.findViewById(R.id.ivPrimaryImage);

            ImageOptions options = MainApplication.getInstance().getImageOptions(ImageType.Primary);
            options.setMaxWidth((int) (300 * mSeriesActivity.getScreenDensity()));
            options.setMaxHeight(mSeriesActivity.getScreenHeight() - 325);

            String imageUrl = MainApplication.getInstance().API.GetImageUrl(mSeries, options);
            primaryImageLandscape.setImageUrl(imageUrl, MainApplication.getInstance().API.getImageLoader());
        }

        PopulateView();
    }

    private void PopulateView() {

        AppLogger.getLogger().Info("SeriesDetailsFragment: PopulateView");

        TextView titleText = (TextView) mView.findViewById(R.id.tvMediaTitle);
        titleText.setText(mSeries.getName());

        AppLogger.getLogger().Info("SeriesDetailsFragment: Setting Airing Info");
        TextView airingInfo = (TextView) mView.findViewById(R.id.tvSeriesViewAiringInfo);
        airingInfo.setText(LibraryTools.buildAiringInfoString(mSeries));

        AppLogger.getLogger().Info("SeriesDetailsFragment: Set Overview");
        TextView seriesOverview = (TextView) mView.findViewById(R.id.tvSeriesOverview);
        seriesOverview.setText(mSeries.getOverview());
        seriesOverview.setMovementMethod(new ScrollingMovementMethod());

        String gText = "";

        if (mSeries.getGenres() != null && !mSeries.getGenres().isEmpty()) {
            for (String genre : mSeries.getGenres()) {

                if (!gText.isEmpty())
                    gText += "<font color='#00b4ff'> &#149 </font>";

                gText += genre;
            }
        }

        TextView genreText = (TextView) mView.findViewById(R.id.tvSeriesGenre);
        genreText.setText(Html.fromHtml(gText), TextView.BufferType.SPANNABLE);

        AppLogger.getLogger().Info("SeriesDetailsFragment: Finished setting Genre(s)");

        if (mSeries.getProviderIds() != null && !mSeries.getProviderIds().isEmpty()) {
            final String tvdb = mSeries.getProviderIds().get("Tvdb");

            if (tvdb != null && !tvdb.isEmpty()) {

                TextView links = (TextView) mView.findViewById(R.id.tvLinks);
                links.setText("TheTVDB");
                links.setClickable(true);
                links.setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {

                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mTvdbBaseUrl + tvdb));
                        startActivity(browserIntent);

                    }
                });

                AppLogger.getLogger().Info("SeriesDetailsFragment: Finished setting Links");
            }
        }
    }
}
