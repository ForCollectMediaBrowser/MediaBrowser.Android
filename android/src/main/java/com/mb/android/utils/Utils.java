package com.mb.android.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.mb.android.MainApplication;
import com.mb.android.R;
import com.mb.android.logging.AppLogger;
import com.mb.network.Connectivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dlna.AudioOptions;
import mediabrowser.model.dlna.PlaybackException;
import mediabrowser.model.dlna.StreamInfo;
import mediabrowser.model.dlna.VideoOptions;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.MediaSourceInfo;
import mediabrowser.model.entities.MediaStream;
import mediabrowser.model.entities.SeriesStatus;
import mediabrowser.model.livetv.RecordingInfoDto;

/**
 * Created by Mark on 11/12/13.
 *
 * Class contains various helper methods
 */
public class Utils {

    public static String TicksToRuntimeString(long ticks) {

        long hours = ticks / 36000000000L;
        long remainder = ticks % 36000000000L;
        long mins = remainder / 600000000L;

        String runtime = "";

        if (hours > 0) {
            runtime = String.valueOf(hours) + "h ";
        }

        runtime += String.valueOf(mins) + "m";

        return runtime;
    }


    public static String TicksToMinutesString(long ticks) {

        long mins = ticks / 600000000L;

        String runtime = "";

        runtime += String.valueOf(mins) + " min";

        return runtime;
    }


    /**
     * This will display a runtime in numerical format displaying as few leading 0's as possible
     *
     * Output Example:
     * 1:03:00
     *    8:12
     *
     * @param milliseconds The milliseconds to display
     * @return The milliseconds formatted to a standard time value
     */
    public static String PlaybackRuntimeFromMilliseconds(long milliseconds) {

        long hours = 0;
        long minutes = 0;
        long seconds = 0;

        if (milliseconds >= (1000 * 60 * 60)) {
            hours = milliseconds / (1000 * 60 * 60);
        }
        if (milliseconds >= (1000 * 60)) {
            minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        }
        if (milliseconds >= 1000) {
            seconds = ((milliseconds % (1000 * 60 * 60)) % (1000 * 60)) / 1000;
        }

        String runtime = "";

        if (hours > 0) {
            runtime = String.valueOf(hours) + ":";
        }
        if (minutes > 0) {
            if (minutes < 10 && hours > 0) {
                runtime += "0";
            }
            runtime += String.valueOf(minutes) + ":";
        } else {
            if (hours > 0) {
                runtime += "00:";
            }
        }

        if (seconds > 0) {
            if (seconds < 10) {
                runtime += "0";
            }
            runtime += String.valueOf(seconds);
            if (hours < 1 && minutes < 1) {
                runtime = "0:" + runtime;
            }
        } else {
            if (hours < 1 && minutes < 1) {
                runtime += "0:00";
            } else {
                runtime += "00";
            }
        }

        return runtime;
    }


    public static String getFriendlyTimeString(long delta) {

        long SECOND = 1000;
        long MINUTE = 60 * SECOND;
        long HOUR = 60 * MINUTE;
        long DAY = 24 * HOUR;
        long MONTH = 30 * DAY;

        if (delta < 0) {
            return "not yet";
        }
        if (delta < 2 * MINUTE) {
            return "a minute ago";
        }
        if (delta < 45 * MINUTE) {
            return delta / MINUTE + " minutes ago";
        }
        if (delta < 90 * MINUTE) {
            return "an hour ago";
        }
        if (delta < 24 * HOUR) {
            return delta / HOUR + " hours ago";
        }
        if (delta < 48 * HOUR) {
            return "yesterday";
        }
        if (delta < 30 * DAY) {
            return delta / DAY + " days ago";
        }
        if (delta < 12 * MONTH) {
            int months = (int) (delta / MONTH);
            return months <= 1 ? "one month ago" : months + " months ago";
        } else {
            int years = (int) (delta / (MONTH * 12));
            return years <= 1 ? "one year ago" : years + " years ago";
        }
    }

    public static String TotalMemory(Context context) {

        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalMemory = memInfo.availMem;

        totalMemory = (totalMemory / 1024) / 1024;

        return String.valueOf(totalMemory) + " MB";
    }

    public static String MaxApplicationMemory() {
        long maxmemory = Runtime.getRuntime().maxMemory();
        maxmemory = (maxmemory / 1024) / 1024;

        return String.valueOf(maxmemory) + " MB";
    }

    public static void ShowStarRating(Float rating, ImageView imageView) {

        if (rating == null || imageView == null)
            return;

        if (rating >= 9.5)
            imageView.setImageResource(R.drawable.star10);
        else if (rating >= 8.5)
            imageView.setImageResource(R.drawable.star9);
        else if (rating >= 7.5)
            imageView.setImageResource(R.drawable.star8);
        else if (rating >= 6.5)
            imageView.setImageResource(R.drawable.star7);
        else if (rating >= 5.5)
            imageView.setImageResource(R.drawable.star6);
        else if (rating >= 4.5)
            imageView.setImageResource(R.drawable.star5);
        else if (rating >= 3.5)
            imageView.setImageResource(R.drawable.star4);
        else if (rating >= 2.5)
            imageView.setImageResource(R.drawable.star3);
        else if (rating >= 1.5)
            imageView.setImageResource(R.drawable.star2);
        else if (rating >= 0.5)
            imageView.setImageResource(R.drawable.star1);
        else
            imageView.setImageResource(R.drawable.star0);

    }




    public static String buildSubtitleDisplayString(MediaStream stream) {

        String desc;

        if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(stream.getLanguage())) {
            Locale local = new Locale(stream.getLanguage());
            desc = local.getDisplayLanguage();
        } else {
            desc = MainApplication.getInstance().getResources().getString(R.string.unknown_language);
        }

        if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(stream.getCodec())) {
            desc += " (" + stream.getCodec().toUpperCase() + ")";
        }

        return desc;
    }

    public static String buildAudioDisplayString(MediaStream stream) {

        String desc = "";

        if (stream.getCodec() != null && stream.getChannelLayout() != null) {
            desc = getFullLanguageName(stream.getLanguage());
            desc += " (" + (stream.getCodec().equalsIgnoreCase("dca") ? stream.getProfile().toUpperCase() : stream.getCodec().toUpperCase());
            desc += " " + stream.getChannelLayout() + ")";
        }

        return desc;
    }

    public static String getFullLanguageName(String shortLanguage) {
        if (shortLanguage != null) {
            Locale local = new Locale(shortLanguage);
            return local.getDisplayLanguage(local);
        } else {
            return "Unknown";
        }
    }

    public static boolean isAddressValid(EditText address) {

        Editable editable = address.getText();

        if (editable == null) return false;

        String addressString = editable.toString();

        int colonCount = 0;

        for (int i = 0; i < addressString.length(); i++) {
            if (addressString.charAt(i) == ':') {
                colonCount++;
            }
        }

        // Port is in the address. We don't want that
        if (colonCount == 2) return false;

        // Port is in the address. We don't want that
        if (colonCount == 1 && !addressString.startsWith("http")) return false;

        // Address appears valid
        return true;
    }

    public static boolean isPortValid(EditText port) {

        Editable editable = port.getText();

        if (editable == null) return false;

        String portString = editable.toString();

        try {
            int portInt = Integer.valueOf(portString);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void getStreamInfo(BaseItemDto item, Response<StreamInfo> response) {
        getStreamInfo(item, 0L, null, null, null, response);
    }

    public static void getAudioStreamInfo(String id,
                                          final Long startPositionTicks,
                                          ArrayList<MediaSourceInfo> mediaSources,
                                          String bitrate,
                                          final Response<StreamInfo> outerResponse) {

        AppLogger.getLogger().Info("Create AudioOptions");
        AudioOptions options = new AudioOptions();
        options.setItemId(id);
        options.setMediaSources(mediaSources);
        options.setProfile(MainApplication.getInstance().getDeviceProfile());
        options.setMaxBitrate(Integer.valueOf(bitrate));

        AppLogger.getLogger().Info("Create Audio StreamInfo");
        MainApplication.getInstance().getPlaybackManager().getAudioStreamInfo(
                MainApplication.getInstance().API.getServerInfo().getId(),
                options,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new Response<StreamInfo>() {
                    @Override
                    public void onResponse(StreamInfo response) {
                        if (response.getSubProtocol() == null || !response.getSubProtocol().equalsIgnoreCase("hls")) {
                            response.setStartPositionTicks(startPositionTicks);
                        }
                        outerResponse.onResponse(response);
                    }

                    @Override
                    public void onError(Exception exception) {

                        if (exception instanceof PlaybackException) {
                            handleStreamError((PlaybackException) exception);
                        }

                        outerResponse.onError(exception);
                    }
                }
        );
    }

    public static void getNewVideoStreamInfo(StreamInfo current,
                                             final Long positionTicks,
                                             Integer audioStreamIndex,
                                             Integer subtitleStreamIndex,
                                             final Response<StreamInfo> outerResponse) {

        AppLogger.getLogger().Info("Create New VideoOptions");
        VideoOptions options = new VideoOptions();
        options.setItemId(current.getItemId());
        options.setMaxBitrate(current.getVideoBitrate());
        options.setProfile(current.getDeviceProfile());

        if (audioStreamIndex != null) {
            options.setAudioStreamIndex(audioStreamIndex);
            options.setMediaSourceId(current.getMediaSourceId());
        }
        if (subtitleStreamIndex != null) {
            options.setSubtitleStreamIndex(subtitleStreamIndex);
            options.setMediaSourceId(current.getMediaSourceId());
        }

        AppLogger.getLogger().Info("Create New Stream Info");
        MainApplication.getInstance().getPlaybackManager().changeVideoStream(
                current,
                MainApplication.getInstance().API.getServerInfo().getId(),
                options,
                MainApplication.getInstance().API,
                new Response<StreamInfo>() {
                    @Override
                    public void onResponse(StreamInfo response) {
                        if (response.getSubProtocol() == null || !response.getSubProtocol().equalsIgnoreCase("hls")) {
                            response.setStartPositionTicks(positionTicks);
                        }
                        outerResponse.onResponse(response);
                    }

                    @Override
                    public void onError(Exception exception) {

                        if (exception instanceof PlaybackException) {
                            handleStreamError((PlaybackException) exception);
                        }

                        outerResponse.onError(exception);
                    }
                });


    }

    public static void getVideoStreamInfo(String id,
                                          final Long startPositionTicks,
                                          ArrayList<MediaSourceInfo> mediaSources,
                                          String mediaSourceId,
                                          Integer audioStreamIndex,
                                          Integer subtitleStreamIndex,
                                          boolean hlsEnabled,
                                          String bitrate,
                                          final Response<StreamInfo> outerResponse) {

        AppLogger.getLogger().Info("Create VideoOptions");
        VideoOptions options = new VideoOptions();
        options.setItemId(id);
        options.setMediaSources(mediaSources);
        options.setProfile(MainApplication.getInstance().getDeviceProfile());
        options.setMaxBitrate(Integer.valueOf(bitrate));

        if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(mediaSourceId)) {
            if (audioStreamIndex != null) {
                options.setAudioStreamIndex(audioStreamIndex);
                options.setMediaSourceId(mediaSourceId);
            }
            if (subtitleStreamIndex != null) {
                options.setSubtitleStreamIndex(subtitleStreamIndex);
                options.setMediaSourceId(mediaSourceId);
            }
        }

        AppLogger.getLogger().Info("Create Video StreamInfo");
        MainApplication.getInstance().getPlaybackManager().getVideoStreamInfo(
                MainApplication.getInstance().API.getServerInfo().getId(),
                options,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new Response<StreamInfo>() {
                    @Override
                    public void onResponse(StreamInfo response) {
                        if (response.getSubProtocol() == null || !response.getSubProtocol().equalsIgnoreCase("hls")) {
                            response.setStartPositionTicks(startPositionTicks);
                        }
                        outerResponse.onResponse(response);
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (exception instanceof PlaybackException) {
                            handleStreamError((PlaybackException) exception);
                        }

                        outerResponse.onError(exception);
                    }
                }
        );
    }

    public static void getStreamInfo(BaseItemDto item,
                                     final Long startPositionTicks,
                                     String mediaSourceId,
                                     Integer audioStreamIndex,
                                     Integer subtitleStreamIndex,
                                     final Response<StreamInfo> outerResponse) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());

        if (item.getMediaType() != null && item.getMediaType().equalsIgnoreCase("audio")) {
            getAudioStreamInfo(item.getId(), startPositionTicks, item.getMediaSources(), getPrefsBitrate(prefs), outerResponse);
        } else {
            getVideoStreamInfo(
                    item.getId(),
                    startPositionTicks,
                    item.getMediaSources(),
                    mediaSourceId,
                    audioStreamIndex,
                    subtitleStreamIndex,
                    prefs.getBoolean("pref_enable_hls", true),
                    getPrefsBitrate(prefs),
                    outerResponse);
        }

    }

    public static void getStreamInfo(RecordingInfoDto recording,
                                     final Long startPositionTicks,
                                     String mediaSourceId,
                                     Integer audioStreamIndex,
                                     Integer subtitleStreamIndex,
                                     final Response<StreamInfo> outerResponse) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());

        getVideoStreamInfo(
                recording.getId(),
                startPositionTicks,
                recording.getMediaSources(),
                mediaSourceId,
                audioStreamIndex,
                subtitleStreamIndex,
                prefs.getBoolean("pref_enable_hls", true),
                getPrefsBitrate(prefs),
                outerResponse);
    }


    public static String getPrefsBitrate(SharedPreferences prefs) {
        if (Connectivity.isConnectedLAN(MainApplication.getInstance())) {
            return prefs.getString("pref_local_bitrate", "1800000");
        } else {
            return prefs.getString("pref_cellular_bitrate", "450000");
        }


    }

    private static void handleStreamError(PlaybackException ex) {
        AppLogger.getLogger().ErrorException("Playback stream error", ex);

        switch (ex.getErrorCode()) {

            case NotAllowed:
                Toast.makeText(MainApplication.getInstance(), R.string.message_playback_not_allowed, Toast.LENGTH_LONG);
                break;
            case NoCompatibleStream:
                Toast.makeText(MainApplication.getInstance(), R.string.message_playback_no_compat, Toast.LENGTH_LONG);
                break;
            case RateLimitExceeded:
                Toast.makeText(MainApplication.getInstance(), R.string.message_playback_rate_exceeded, Toast.LENGTH_LONG);
                break;
        }
    }

    public static Date convertToLocalDate(Date utcDate) {

        TimeZone timeZone = TimeZone.getDefault();
        Date convertedDate = new Date( utcDate.getTime() + timeZone.getRawOffset() );

        if ( timeZone.inDaylightTime(convertedDate) ) {
            Date dstDate = new Date( convertedDate.getTime() + timeZone.getDSTSavings() );

            if (timeZone.inDaylightTime( dstDate )) {
                convertedDate = dstDate;
            }
        }

        return convertedDate;
    }

    /**
     * Returns a formatted string that shows the episode indexes in a readable manor.
     *
     * Output Examples:
     *  01:12
     *  03:18-20
     *
     * @param item The Episode to parse
     *
     * @return The formatted String
     */
    public static String getShortEpisodeIndexString(BaseItemDto item) {
        String title = "";
        if (item == null) {
            return title;
        }
        String episodeIndex = "";
        String episodeEndIndex = "";
        String parentIndex = "";

        try {
            if (item.getIndexNumber() != null) {
                episodeIndex = item.getIndexNumber().toString();
                if (episodeIndex.length() == 1) {
                    episodeIndex = "0" + episodeIndex;
                }
            }
            if (item.getIndexNumberEnd() != null && !item.getIndexNumberEnd().equals(item.getIndexNumber())) {
                episodeEndIndex = item.getIndexNumberEnd().toString();
                if (episodeEndIndex.length() == 1) {
                    episodeEndIndex = 0 + episodeEndIndex;
                }
            }
            if (item.getParentIndexNumber() != null) {
                parentIndex = item.getParentIndexNumber().toString();
                if (parentIndex.length() == 1) {
                    parentIndex = 0 + parentIndex;
                }
            }
            title = "s" + parentIndex + "e" + episodeIndex;
            if (episodeEndIndex.length() > 0) {
                title += "-" + episodeEndIndex;
            }
        } catch (Exception e) {
            AppLogger.getLogger().ErrorException("PopulateTvInfo - ", e);
        }
        return title;
    }

    public static String buildAiringInfoString(BaseItemDto item) {
        if ("series".equalsIgnoreCase(item.getType())) {
            return buildSeriesAiringInfoString(item);
        } else if ("episode".equalsIgnoreCase(item.getType())) {
            return buildEpisodeAiringInfoString(item);
        } else {
            return "";
        }
    }

    private static String buildSeriesAiringInfoString(BaseItemDto item) {
        String aInfo = "";

        if (item.getAirDays() != null && item.getAirDays().size() > 0) {
            String daysString = "";

            if (item.getAirDays().size() == 7) {
                daysString = "daily";
            } else {
                for (String day : item.getAirDays()) {
                    if (!daysString.isEmpty())
                        daysString += ", ";

                    daysString += day + "s";
                }
            }

            if (!daysString.isEmpty())
                aInfo += daysString;
        }

        if (item.getAirTime() != null && !item.getAirTime().isEmpty()) {
            aInfo += " " + MainApplication.getInstance().getResources().getString(R.string.at_string) + " ";
            aInfo += item.getAirTime();
        }

        if (item.getStudios() != null && item.getStudios().length > 0) {
            aInfo += " on ";
            aInfo += item.getStudios()[0].getName();
        }

        if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(aInfo)) {
            if (item.getStatus() != null && item.getStatus().equals(SeriesStatus.Ended)) {
                aInfo = MainApplication.getInstance().getResources().getString(R.string.aired_string) + " " + aInfo;
            } else {
                aInfo = MainApplication.getInstance().getResources().getString(R.string.airs_string) + " " + aInfo;
            }
        }

        return aInfo;
    }

    private static String buildEpisodeAiringInfoString(BaseItemDto item) {
        String aInfo = "";

        if (item.getPremiereDate() != null) {
            DateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");

            Date premiereDate = Utils.convertToLocalDate(item.getPremiereDate());
            aInfo = outputFormat.format(premiereDate);

            if (premiereDate != null) {
                if (premiereDate.before(new Date())) {
                    aInfo = MainApplication.getInstance().getResources().getString(R.string.aired_string) + " " + aInfo;
                } else {
                    aInfo = MainApplication.getInstance().getResources().getString(R.string.airs_string) + " " + aInfo;
                }
            }
        }
        return aInfo;
    }

    public static String PlayerStatusFromExtra(int extra) {

        String status = "unknown extra status";

		/*
         Return code for general failure
		 */
        if (extra == -1)
            status = "PVMFFailure (-1)";
        /*
		 Error due to cancellation
		 */
        else if (extra == -2)
            status = "PVMFErrCancelled (-2)";
		/*
		 Error due to no memory being available
		 */
        else if (extra == -3)
            status = "PVMFErrNoMemory (-3)";
		/*
		 Error due to request not being supported
		 */
        else if (extra == -4)
            status = "PVMFErrNotSupported (-4)";
		/*
		 Error due to invalid argument
		 */
        else if (extra == -5)
            status = "PVMFErrArgument (-5)";
		/*
		 Error due to invalid resource handle being specified
		 */
        else if (extra == -6)
            status = "PVMFErrBadHandle (-6)";
		/*
		 Error due to resource already exists and another one cannot be created
		 */
        else if (extra == -7)
            status = "PVMFErrAlreadyExists (-7)";
		/*
		 Error due to resource being busy and request cannot be handled
		 */
        else if (extra == -8)
            status = "PVMFErrBusy (-8)";
		/*
		 Error due to resource not ready to accept request
		 */
        else if (extra == -9)
            status = "PVMFErrNotReady (-9)";
		/*
		 Error due to data corruption being detected
		 */
        else if (extra == -10)
            status = "PVMFErrCorrupt (-10)";
		/*
		 Error due to request timing out
		 */
        else if (extra == -11)
            status = "PVMFErrTimeout (-11)";
		/*
		 Error due to general overflow
		 */
        else if (extra == -12)
            status = "PVMFErrOverflow (-12)";
		/*
		 Error due to general underflow
		 */
        else if (extra == -13)
            status = "PVMFErrUnderflow (-13)";
		/*
		 Error due to resource being in wrong state to handle request
		 */
        else if (extra == -14)
            status = "PVMFErrInvalidState (-14)";
		/*
		 Error due to resource not being available
		 */
        else if (extra == -15)
            status = "PVMFErrNoResources (-15)";
		/*
		 Error due to invalid configuration of resource
		 */
        else if (extra == -16)
            status = "PVMFErrResourceConfiguration (-16)";
		/*
		 Error due to general error in underlying resource
		 */
        else if (extra == -17)
            status = "PVMFErrResource (-17)";
		/*
		 Error due to general data processing
		 */
        else if (extra == -18)
            status = "PVMFErrProcessing (-18)";
		/*
		 Error due to general port processing
		 */
        else if (extra == -19)
            status = "PVMFErrPortProcessing (-19)";
		/*
		 Error due to lack of authorization to access a resource.
		 */
        else if (extra == -20)
            status = "PVMFErrAccessDenied (-20)";
		/*
		 Error due to the lack of a valid license for the content
		 */
        else if (extra == -21)
            status = "PVMFErrLicenseRequired (-21)";
		/*
		 Error due to the lack of a valid license for the content.  However
		 a preview is available.
		 */
        else if (extra == -22)
            status = "PVMFErrLicenseRequiredPreviewAvailable (-22)";
		/*
		 Error due to the download content length larger than the maximum request size
		 */
        else if (extra == -23)
            status = "PVMFErrContentTooLarge (-23)";
		/*
		 Error due to a maximum number of objects in use
		 */
        else if (extra == -24)
            status = "PVMFErrMaxReached (-24)";
		/*
		 Return code for low disk space
		 */
        else if (extra == -25)
            status = "PVMFLowDiskSpace (-25)";
		/*
		 Error due to the requirement of user-id and password input from app for HTTP basic/digest authentication
		 */
        else if (extra == -26)
            status = "PVMFErrHTTPAuthenticationRequired (-26)";
		/*
		 PVMFMediaClock specific error. Callback has become invalid due to change in direction of NPT clock.
		*/
        else if (extra == -27)
            status = "PVMFErrCallbackHasBecomeInvalid (-27)";
		/*
		 PVMFMediaClock specific error. Callback is called as clock has stopped.
		*/
        else if (extra == -28)
            status = "PVMFErrCallbackClockStopped (-28)";
		/*
		 Error due to missing call for ReleaseMatadataValue() API
		 */
        else if (extra == -29)
            status = "PVMFErrReleaseMetadataValueNotDone (-29)";
		/*
		 Error due to the redirect error
		*/
        else if (extra == -30)
            status = "PVMFErrRedirect (-30)";
		/*
		 Error if a given method or API is not implemented. This is NOT the same as PVMFErrNotSupported.
		*/
        else if (extra == -31)
            status = "PVMFErrNotImplemented (-31)";
		/*
		 Error: the video container is not valid for progressive playback.
		 */
        else if (extra == -32)
            status = "PVMFErrContentInvalidForProgressivePlayback (-32)";


        return status;
    }
}
