package com.mb.android.ui.tv.playback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.mb.android.MainApplication;
import com.mb.android.PlaylistItem;
import com.mb.android.ui.tv.ActivityResults;
import com.mb.android.ui.tv.MbBaseActivity;
import mediabrowser.apiinteraction.EmptyResponse;
import mediabrowser.apiinteraction.Response;
import com.mb.android.SubtitleDownloader;
import com.mb.android.logging.AppLogger;
import com.mb.android.profiles.ExternalPlayerProfile;
import com.mb.android.subtitles.FatalParsingException;
import com.mb.android.subtitles.FormatSRT;
import com.mb.android.subtitles.TimedTextFileFormat;
import com.mb.android.subtitles.TimedTextObject;
import com.mb.network.Connectivity;

import mediabrowser.apiinteraction.android.profiles.AndroidProfile;
import mediabrowser.model.dlna.AudioOptions;
import mediabrowser.model.dlna.StreamBuilder;
import mediabrowser.model.dlna.StreamInfo;
import mediabrowser.model.dlna.SubtitleDeliveryMethod;
import mediabrowser.model.dlna.SubtitleStreamInfo;
import mediabrowser.model.dlna.VideoOptions;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.MediaSourceInfo;
import mediabrowser.model.logging.NullLogger;
import mediabrowser.model.querying.ItemsResult;
import mediabrowser.model.session.PlayMethod;
import mediabrowser.model.session.PlaybackProgressInfo;
import mediabrowser.model.session.PlaybackStartInfo;
import mediabrowser.model.session.PlaybackStopInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mark on 2014-11-01.
 *
 * Static class that provides methods used by both the video and audio player
 */
public final class PlayerHelpers {

    /**
     *
     * @param streamInfo The StreamInfo for the item being played
     * @param position   The current position, in ticks, of playback
     * @param volume     The current volume of the MediaPlayer
     * @param isMuted    True if the MediaPlayer is muted, false otherwise
     * @param isPaused   True if the MediaPlayer is paused, false otherwise
     * @param response   The EmptyResponse to be invoked when the request completes
     */
    public static void sendPlaybackStartedToServer(StreamInfo streamInfo, Long position, float volume, boolean isMuted, boolean isPaused, EmptyResponse response) {
        if (streamInfo == null) return;
        PlaybackStartInfo info = new PlaybackStartInfo();
        info.setQueueableMediaTypes(new ArrayList<String>() {{ add("Audio"); add("Video"); }});
        info.setPositionTicks(position);
        info.setAudioStreamIndex(streamInfo.getAudioStreamIndex());
        info.setCanSeek(true);
        info.setIsMuted(isMuted);
        info.setIsPaused(isPaused);
        info.setItemId(streamInfo.getItemId());
        info.setMediaSourceId(streamInfo.getMediaSourceId());
        info.setPlayMethod(streamInfo.getIsDirectStream() ? PlayMethod.DirectStream : PlayMethod.Transcode);
        info.setSubtitleStreamIndex(streamInfo.getSubtitleStreamIndex());
        info.setVolumeLevel((int) volume * 100);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackStart(
                info,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    /**
     *
     * @param streamInfo The StreamInfo for the item being played
     * @param position   The current position, in ticks, of playback
     * @param volume     The current volume of the MediaPlayer
     * @param isMuted    True if the MediaPlayer is muted, false otherwise
     * @param isPaused   True if the MediaPlayer is paused, false otherwise
     * @param response   The EmptyResponse to be invoked when the request completes
     */
    public static void sendPlaybackProgressToServer(StreamInfo streamInfo, Long position, float volume, boolean isMuted, boolean isPaused, EmptyResponse response) {
        if (streamInfo == null) return;
        PlaybackProgressInfo progressInfo = new PlaybackProgressInfo();
        progressInfo.setPositionTicks(position);
        progressInfo.setAudioStreamIndex(streamInfo.getAudioStreamIndex());
        progressInfo.setCanSeek(true);
        progressInfo.setIsMuted(isMuted);
        progressInfo.setIsPaused(isPaused);
        progressInfo.setItemId(streamInfo.getItemId());
        progressInfo.setMediaSourceId(streamInfo.getMediaSourceId());
        progressInfo.setPlayMethod(streamInfo.getIsDirectStream() ? PlayMethod.DirectStream : PlayMethod.Transcode);
        progressInfo.setSubtitleStreamIndex(streamInfo.getSubtitleStreamIndex());
        progressInfo.setVolumeLevel((int) volume * 100);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackProgress(
                progressInfo,
                streamInfo,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    /**
     *
     * @param streamInfo The StreamInfo for the item being played
     * @param position   The current position, in ticks, of playback
     * @param response   The EmptyResponse to be invoked when the request completes
     */
    public static void sendPlaybackStoppedToServer(StreamInfo streamInfo, Long position, EmptyResponse response) {
        if (streamInfo == null) return;
        PlaybackStopInfo stopInfo = new PlaybackStopInfo();
        stopInfo.setItemId(streamInfo.getItemId());
        stopInfo.setMediaSourceId(streamInfo.getMediaSourceId());
        stopInfo.setPositionTicks(position);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackStopped(
                stopInfo,
                streamInfo,
                MainApplication.getInstance().API.getServerInfo().getId(),
                MainApplication.getInstance().API.getCurrentUserId(),
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    /**
     * Generate the Video URL to be requested from MB Server
     *
     * @param id                  The ID of the item to be played
     * @param mediaSources        The available MediaSourceInfo's for the item being played
     * @param startPositionTicks  The position in ticks that playback should commence from.
     * @param audioStreamIndex    Integer representing the media stream index to use for audio.
     * @param subtitleStreamIndex Integer representing the media stream index to use for subtitles.
     * @return A String containing the formed URL.
     */
    public static StreamInfo buildStreamInfoVideo(String id,
                                    ArrayList<MediaSourceInfo> mediaSources,
                                    Long startPositionTicks,
                                    String mediaSourceId,
                                    Integer audioStreamIndex,
                                    Integer subtitleStreamIndex) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        String bitrate;

        if (Connectivity.isConnectedLAN(MainApplication.getInstance())) {
            bitrate = prefs.getString("pref_local_bitrate", "1800000");
        } else {
            bitrate = prefs.getString("pref_cellular_bitrate", "450000");
        }

        boolean hlsEnabled = prefs.getBoolean("pref_enable_hls", true);

        AppLogger.getLogger().Info("Create VideoOptions");
        VideoOptions options = new VideoOptions();
        options.setItemId(id);
        options.setMediaSources(mediaSources);
        options.setProfile(new AndroidProfile(hlsEnabled, false));
        options.setDeviceId(MainApplication.getInstance().getConnectionManager().getDevice().getDeviceId());
        options.setMaxBitrate(Integer.valueOf(bitrate));

        if (audioStreamIndex != null) {
            options.setAudioStreamIndex(audioStreamIndex);
            options.setMediaSourceId(mediaSourceId);
        }
        if (subtitleStreamIndex != null) {
            options.setSubtitleStreamIndex(subtitleStreamIndex);
            options.setMediaSourceId(mediaSourceId);
        }

        AppLogger.getLogger().Info("Create StreamInfo");
        StreamInfo streamInfo = new StreamBuilder(new NullLogger()).BuildVideoItem(options);

        if (streamInfo.getSubProtocol() == null || !streamInfo.getSubProtocol().equalsIgnoreCase("hls")) {
            streamInfo.setStartPositionTicks(startPositionTicks);
        }

        return streamInfo;
    }

    /**
     *
     * @param id
     * @param mediaSources
     * @param startPositionTicks
     * @param mediaSourceId
     * @return
     */
    public static StreamInfo buildStreamInfoAudio(String id,
                                                  ArrayList<MediaSourceInfo> mediaSources,
                                                  Long startPositionTicks,
                                                  String mediaSourceId) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        String bitrate;

        if (Connectivity.isConnectedLAN(MainApplication.getInstance())) {
            bitrate = prefs.getString("pref_local_bitrate", "1800000");
        } else {
            bitrate = prefs.getString("pref_cellular_bitrate", "450000");
        }

        boolean hlsEnabled = prefs.getBoolean("pref_enable_hls", true);
        boolean h264StrictModeEnabled = prefs.getBoolean("pref_h264_strict", true);

        AppLogger.getLogger().Info("Create AudioOptions");
        AudioOptions options = new AudioOptions();
        options.setItemId(id);
        options.setMediaSources(mediaSources);
        options.setProfile(new AndroidProfile(hlsEnabled, false));
        options.setDeviceId(MainApplication.getInstance().getConnectionManager().getDevice().getDeviceId());
        options.setMaxBitrate(Integer.valueOf(bitrate));

        AppLogger.getLogger().Info("Create StreamInfo");
        StreamInfo streamInfo = new StreamBuilder(AppLogger.getLogger()).BuildAudioItem(options);

        if (streamInfo == null) {
            AppLogger.getLogger().Info("streamInfo is null");
            return null;
        }

        if (streamInfo.getSubProtocol() == null || !streamInfo.getSubProtocol().equalsIgnoreCase("hls")) {
            streamInfo.setStartPositionTicks(startPositionTicks);
        }

        return streamInfo;
    }

    /**
     *
     * @param id
     * @param mediaSources
     * @param startPositionTicks
     * @param mediaSourceId
     * @param audioStreamIndex
     * @param subtitleStreamIndex
     * @return
     */
    public static StreamInfo buildExternalPlayerStreamInfo(String id,
                                                           ArrayList<MediaSourceInfo> mediaSources,
                                                           Long startPositionTicks,
                                                           String mediaSourceId,
                                                           Integer audioStreamIndex,
                                                           Integer subtitleStreamIndex) {


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        String bitrate;

        if (Connectivity.isConnectedLAN(MainApplication.getInstance())) {
            bitrate = prefs.getString("pref_local_bitrate", "1800000");
        } else {
            bitrate = prefs.getString("pref_cellular_bitrate", "450000");
        }

        AppLogger.getLogger().Info("Create VideoOptions");
        VideoOptions options = new VideoOptions();
        options.setItemId(id);
        options.setMediaSources(mediaSources);
        options.setProfile(new ExternalPlayerProfile());
        options.setDeviceId(MainApplication.getInstance().getConnectionManager().getDevice().getDeviceId());
        options.setMaxBitrate(Integer.valueOf(bitrate));

        AppLogger.getLogger().Info("Create StreamInfo");
        StreamInfo streamInfo = new StreamBuilder(AppLogger.getLogger()).BuildVideoItem(options);

        if (streamInfo.getSubProtocol() == null || !streamInfo.getSubProtocol().equalsIgnoreCase("hls")) {
            streamInfo.setStartPositionTicks(startPositionTicks);
        }

        return streamInfo;
    }


    public void playItems(MbBaseActivity context) {
        AppLogger.getLogger().Info("PlayerHelpers: playitems");
        if ("audio".equalsIgnoreCase(MainApplication.getInstance().PlayerQueue.PlaylistItems.get(0).Type)) {
            AppLogger.getLogger().Info("PlayerHelpers: calling audio player");
            Intent intent = new Intent(MainApplication.getInstance(), AudioPlayer.class);
            context.startActivity(intent);
        } else {
            AppLogger.getLogger().Info("PlayerHelpers: calling video player");
            Intent intent = new Intent(MainApplication.getInstance(), VideoPlayer.class);
            context.startActivityForResult(intent, ActivityResults.PLAYBACK_COMPLETED);
        }
    }

    public void playItem(final MbBaseActivity context, final BaseItemDto item, final Long startPositionTicks, final Integer audioStreamIndex, final Integer subtitleStreamIndex, final String mediaSourceId, boolean ignoreCinemaMode) {
        if (context == null) {
            throw new IllegalArgumentException("context");
        }
        if (item == null) {
            throw new IllegalArgumentException("item");
        }

        // Kill audio playback
//        AudioService.PlayerState currentState = MB3Application.getAudioService().getPlayerState();
//        if (currentState.equals(AudioService.PlayerState.PLAYING) || currentState.equals(AudioService.PlayerState.PAUSED)) {
//            MB3Application.getAudioService().stopMedia();
//        }

        // Just in case the TV Theme is still playing
        MainApplication.getInstance().StopMedia();

        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("pref_enable_external_player", false)) {

            AppLogger.getLogger().Info("Play requested: External player");

            StreamInfo info = PlayerHelpers.buildExternalPlayerStreamInfo(
                    item.getId(),
                    item.getMediaSources(),
                    startPositionTicks != null ? startPositionTicks : 0L,
                    mediaSourceId,
                    audioStreamIndex,
                    subtitleStreamIndex);
            String url = info.ToUrl(MainApplication.getInstance().API.getApiUrl(), MainApplication.getInstance().API.getAccessToken());
            AppLogger.getLogger().Info("External player URL: " + url);
            AppLogger.getLogger().Debug("External Player url", url);

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivityForResult(intent, ActivityResults.PLAYBACK_COMPLETED);
        /*
        Playback is to commence on the internal player
        */
        } else {
            AppLogger.getLogger().Info("Play requested: Internal player");

            if ("audio".equalsIgnoreCase(item.getMediaType())) {
                MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
                addToPlaylist(item, startPositionTicks, null, null);
                Intent intent = new Intent(MainApplication.getInstance(), AudioPlayer.class);
                context.startActivity(intent);
            } else if ("photo".equalsIgnoreCase(item.getMediaType())) {
                MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
                addToPlaylist(item, startPositionTicks, null, null);
                Intent intent = new Intent(MainApplication.getInstance(), PhotoPlayer.class);
                context.startActivity(intent);
            } else {
                if (!ignoreCinemaMode && cinemaModeSupportedMedia(item)) {
                    MainApplication.getInstance().API.GetIntrosAsync(item.getId(), MainApplication.getInstance().user.getId(), new Response<ItemsResult>() {
                        @Override
                        public void onResponse(ItemsResult result) {
                            MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
                            if (result != null && result.getItems() != null) {
                                addToPlaylist(result.getItems());
                                addToPlaylist(item, startPositionTicks, audioStreamIndex, subtitleStreamIndex);
                                Intent intent = new Intent(MainApplication.getInstance(), VideoPlayer.class);
                                context.startActivityForResult(intent, ActivityResults.PLAYBACK_COMPLETED);
                            }
                        }
                        @Override
                        public void onError(Exception ex) {
                            MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
                            addToPlaylist(item, startPositionTicks, audioStreamIndex, subtitleStreamIndex);
                            Intent intent = new Intent(MainApplication.getInstance(), VideoPlayer.class);
                            context.startActivityForResult(intent, ActivityResults.PLAYBACK_COMPLETED);
                        }
                    });
                } else {
                    MainApplication.getInstance().PlayerQueue.PlaylistItems = new ArrayList<>();
                    addToPlaylist(item, startPositionTicks, audioStreamIndex, subtitleStreamIndex);
                    Intent intent = new Intent(MainApplication.getInstance(), VideoPlayer.class);
                    context.startActivityForResult(intent, ActivityResults.PLAYBACK_COMPLETED);
                }
            }
        }
    }

    private boolean cinemaModeSupportedMedia(BaseItemDto item) {
        return "movie".equalsIgnoreCase(item.getType()) || "episode".equalsIgnoreCase(item.getType());
    }

    public static void addToPlaylist(BaseItemDto[] items) {
        if (items == null) return;
        for (BaseItemDto item : items) {
            addToPlaylist(item, 0L, null, null);
        }
    }

    public static void addToPlaylist(List<BaseItemDto> items) {
        if (items == null) return;
        for (BaseItemDto item : items) {
            addToPlaylist(item, 0L, null, null);
        }
    }

    public static void addToPlaylist(BaseItemDto item, Long startPositionTicks, Integer audioStreamIndex, Integer subtitleStreamIndex) {
        PlaylistItem playableItem = new PlaylistItem();
        playableItem.Id = item.getId();
        playableItem.Name = item.getName();
        playableItem.startPositionTicks = startPositionTicks;
        playableItem.Type = item.getType();
        playableItem.Runtime = item.getRunTimeTicks();

        if ("episode".equalsIgnoreCase(item.getType())) {
            if (item.getIndexNumber() != null) {
                playableItem.Name = " " + playableItem.Name;
                if (item.getIndexNumberEnd() != null && item.getIndexNumberEnd() != item.getIndexNumber()) {
                    playableItem.Name = "-" + String.valueOf(item.getIndexNumberEnd() + playableItem.Name);
                }
                playableItem.Name = "E" + String.valueOf(item.getIndexNumber() + playableItem.Name);
                if (item.getParentIndexNumber() != null) {
                    playableItem.Name = "S" + String.valueOf(item.getParentIndexNumber() + playableItem.Name);
                }
            }
            playableItem.SecondaryText = item.getSeriesName();

        } else if ("audio".equalsIgnoreCase(item.getType())) {
            if (item.getArtists() != null && item.getArtists().size() > 0) {
                playableItem.SecondaryText = item.getArtists().get(0);
            }
        }

        if (audioStreamIndex != null) {
            playableItem.AudioStreamIndex = audioStreamIndex;
            playableItem.MediaSourceId = item.getMediaSources().get(0).getId();
        }
        if (subtitleStreamIndex != null) {
            playableItem.SubtitleStreamIndex = subtitleStreamIndex;
            playableItem.MediaSourceId = item.getMediaSources().get(0).getId();
        }
        MainApplication.getInstance().PlayerQueue.PlaylistItems.add(playableItem);
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

    /**
     * Download external subtitles for a file. Does nothing if the provided StreamInfo isn't requesting external
     * subtitles.
     *
     * @param streamInfo The StreamInfo to process for subtitles
     * @param response   The Response to be invoked when the task completes
     */
    public static void downloadSubtitles(StreamInfo streamInfo, final Response<TimedTextObject> response) {

        if (!streamInfo.getSubtitleDeliveryMethod().equals(SubtitleDeliveryMethod.External)) {
            return;
        }
        streamInfo.setSubtitleFormat("srt");
        final List<SubtitleStreamInfo> subtitles = streamInfo.GetExternalSubtitles(false, false, MainApplication.getInstance().API.getApiUrl(), MainApplication.getInstance().API.getAccessToken());

        if (subtitles != null && subtitles.size() > 0) {
            new SubtitleDownloader(new Response<File>() {
                @Override
                public void onResponse(File subFile) {
                    AppLogger.getLogger().Info("Subtitle Downloader: onResponse");
                    if (subFile != null) {
                        try {
                            InputStream is = new FileInputStream(subFile);
                            TimedTextFileFormat ttff = new FormatSRT();
                            TimedTextObject tto = ttff.parseFile(subFile.getName(), is);
                            if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(tto.warnings)) {
                                AppLogger.getLogger().Info(tto.warnings);
                            }
                            if (tto.captions == null || tto.captions.size() == 0) {
                                AppLogger.getLogger().Info("Subtitle Downloader: Subtitle file parsed. Nothing to display");
                            }
                            response.onResponse(tto);
                        } catch (FatalParsingException | IOException e) {
                            response.onError(e);
                        } finally {
                            if (!subFile.delete()) {
                                AppLogger.getLogger().Info("Subtitle Downloader: Error deleting subtitle file");
                            }
                        }
                    } else {
                        AppLogger.getLogger().Info("Subtitle Downloader: Unable to retrieve physical file");
                    }
                }
                @Override
                public void onError(Exception ex) {
                    AppLogger.getLogger().Error("Error downloading subtitle file");
                    response.onError(ex);
                }
            }).execute(subtitles.get(0).getUrl());

        } else {
            AppLogger.getLogger().Info("onPrepared: StreamInfo returned no subtitles");
        }
    }

    public static boolean isCollectionPlayableAsAudio(String collectionType) {
        return "music".equalsIgnoreCase(collectionType);
    }

    public static boolean isCollectionPlayableAsVideo(String collectionType) {
        return "boxsets".equalsIgnoreCase(collectionType)
                || "movies".equalsIgnoreCase(collectionType)
                || "tvshows".equalsIgnoreCase(collectionType)
                || "homevideos".equalsIgnoreCase(collectionType)
                || "musicvideos".equalsIgnoreCase(collectionType);
    }

    public static boolean isItemPlayableAsVideo(BaseItemDto item) {
        return "movie".equalsIgnoreCase(item.getType())
                || "series".equalsIgnoreCase(item.getType())
                || "season".equalsIgnoreCase(item.getType())
                || "episode".equalsIgnoreCase(item.getType())
                || "movie".equalsIgnoreCase(item.getType())
                || ( "playlist".equalsIgnoreCase(item.getType()) && "video".equalsIgnoreCase(item.getMediaType()) );

    }

    public static boolean isItemPlayableAsAudio(BaseItemDto item) {
        return "musicartist".equalsIgnoreCase(item.getType())
                || "musicalbum".equalsIgnoreCase(item.getType())
                || "audio".equalsIgnoreCase(item.getType())
                || ( "playlist".equalsIgnoreCase(item.getType()) && "audio".equalsIgnoreCase(item.getMediaType()) );

    }
}


