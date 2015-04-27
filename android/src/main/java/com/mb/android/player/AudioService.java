package com.mb.android.player;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

import com.mb.android.MainApplication;
import com.mb.android.PlaylistItem;
import com.mb.android.logging.AppLogger;
import com.mb.android.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import mediabrowser.apiinteraction.EmptyResponse;
import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dlna.StreamInfo;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.session.PlayMethod;
import mediabrowser.model.session.PlaybackProgressInfo;
import mediabrowser.model.session.PlaybackStartInfo;
import mediabrowser.model.session.PlaybackStopInfo;

/**
 * Created by Mark on 2014-07-08.
 *
 * Service that handles queue management and playback of audio
 */
public class AudioService
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    private static final String TAG = "AudioService";
    private static final Object LOCK = new Object();
    private static AudioService sInstance;
    private List<AudioPlayerListener> mAudioPlayerListeners;
    private MediaPlayer mPlayer;
    private BaseItemDto mMediaItem;
    private int mRuntime;
    private boolean mResume;
    private StreamInfo mStreamInfo;
    private int mCurrentlyPlayingIndex = 0;
    private List<String> mShuffledItemIds;
    private boolean mMuted;
    private boolean mShuffleEnabled;
    private boolean mRepeatEnabled;
    private float mVolume = 1.0f;
    private final float mVolumeIncrement = 0.1f;
    private int mCurrentPositionMilliseconds = 0;
    private long mLastProgressReport = 0L;
    private PlayerState mPlayerState = PlayerState.IDLE;
    protected int mVisibilityCounter;
    protected boolean mUiVisible;
    private Handler mHandler;
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            if (mPlayer != null) {
                mCurrentPositionMilliseconds = mPlayer.getCurrentPosition();

                if (mLastProgressReport > 0 && SystemClock.elapsedRealtime() - mLastProgressReport > 3000) {
                    // TODO: Fix to handle resuming
                    sendPlaybackProgressToServer((long) mCurrentPositionMilliseconds * 10000);
                    mLastProgressReport = SystemClock.elapsedRealtime();
                }
            }

            mHandler.postDelayed(this, 1000);
        }
    };

    public static enum PlayerState { PREPARING, PLAYING, PAUSED, IDLE }


    private AudioService() {}

    public static AudioService initialize() {
        AppLogger.getLogger().Debug(TAG, "initialize()");
        if (null == sInstance) {
            sInstance = new AudioService();
        }

        sInstance.mHandler = new Handler();
        sInstance.mAudioPlayerListeners = new ArrayList<>();

        return sInstance;
    }

    public void Terminate() {
        AppLogger.getLogger().Debug(TAG, "onDestroy()");
        mHandler.removeCallbacks(mRunnable);
        final Handler volumeHandler = new Handler();
        Runnable volumeRunnable = new Runnable() {
            @Override
            public void run() {
                if (mVolume <= 0.0f) {
                    if (mPlayer != null && mPlayer.isPlaying()) {
                        mPlayer.stop();
                        mPlayer.release();
                    }
                    mPlayer = null;
                } else {
                    // Lower the volume gradually rather than just killing the song.
                    if (mPlayer != null) {
                        mVolume -= mVolumeIncrement;
                        mPlayer.setVolume(mVolume, mVolume);
                    }
                    volumeHandler.postDelayed(this, 250);
                }
            }
        };
        volumeHandler.post(volumeRunnable);
    }


    @Override
    public void onCompletion(MediaPlayer mp) {

        AppLogger.getLogger().Info("AudioService: onCompletion");
        AppLogger.getLogger().Info(TAG, "onCompletion");
        AppLogger.getLogger().Info(TAG, "mCurrentlyPlayingIndex: " + String.valueOf(mCurrentlyPlayingIndex));

        performPostPlaybackTasks();

        if (mShuffleEnabled) {
            removeLastPlayedItemFromShuffleQueue();
        }

        if (hasMoreItemsToPlay()) {
            AppLogger.getLogger().Debug(TAG, "playlist contains more items");
            updateCurrentlyPlayingIndex();
            MainApplication.getInstance().API.GetItemAsync(
                    MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id,
                    MainApplication.getInstance().API.getCurrentUserId(),
                    getItemResponse);
        } else {
            AppLogger.getLogger().Debug(TAG, "nothing left to play");
            mPlayerState = PlayerState.IDLE;
            notifyListenersPlaylistComplete();
        }
    }

    private void performPostPlaybackTasks() {
        mHandler.removeCallbacks(mRunnable);
        if (mPlayer == null) return;

        if (mPlayer.isPlaying()) {
            playerStop();
        }
        sendPlaybackStoppedToServer(mResume
                ? ((long) mCurrentPositionMilliseconds * 10000) + (mMediaItem.getUserData().getPlaybackPositionTicks() / 10000)
                : (long) mCurrentPositionMilliseconds * 10000);
        playerReset();
        mCurrentPositionMilliseconds = 0;
    }

    private void removeLastPlayedItemFromShuffleQueue() {
        if (mShuffledItemIds == null || mShuffledItemIds.size() == 0) {
            return;
        }
        try {
            String idToRemove = MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id;
            if (mShuffledItemIds != null && mShuffledItemIds.size() > 0) {
                mShuffledItemIds.remove(idToRemove);
            }
        } catch (Exception e) {
            AppLogger.getLogger().Debug(TAG, "error removing played item");
        }
    }

    private boolean hasMoreItemsToPlay() {
        if (MainApplication.getInstance().PlayerQueue.PlaylistItems == null || MainApplication.getInstance().PlayerQueue.PlaylistItems.size() == 0) {
            return false;
        } else if (mShuffleEnabled) {
            return mShuffledItemIds != null && mShuffledItemIds.size() > 0;
        } else {
            return mRepeatEnabled || MainApplication.getInstance().PlayerQueue.PlaylistItems.size() > mCurrentlyPlayingIndex + 1;
        }
    }

    private void updateCurrentlyPlayingIndex() {
        if (mShuffleEnabled) {
            pickRandomUnPlayedIndex();
        } else if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() > mCurrentlyPlayingIndex + 1) {
            mCurrentlyPlayingIndex += 1;
        } else {
            mCurrentlyPlayingIndex = 0;
        }
    }

    private void pickRandomUnPlayedIndex() {
        try {
            Random r = new Random();
            String selectedId = mShuffledItemIds.get(r.nextInt(mShuffledItemIds.size()));
            for (int i = 0; i < MainApplication.getInstance().PlayerQueue.PlaylistItems.size(); i++) {
                if (MainApplication.getInstance().PlayerQueue.PlaylistItems.get(i).Id.equalsIgnoreCase(selectedId)) {
                    mCurrentlyPlayingIndex = i;
                    return;
                }
            }
            mCurrentlyPlayingIndex = 0;
        } catch (Exception e) {
            // Don't care about the error, just set the index to 0 and let it play
            mCurrentlyPlayingIndex = 0;
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        if (what == MediaPlayer.MEDIA_ERROR_IO) {
            AppLogger.getLogger().Error("Playback Error: Media Error IO");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error IO");
        } else if (what == MediaPlayer.MEDIA_ERROR_MALFORMED) {
            AppLogger.getLogger().Error("Playback Error: Media Error Malformed");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Malformed");
        } else if (what == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
            AppLogger.getLogger().Error("Playback Error: Media Error Not Valid For Progressive Playback");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Not Valid For Progressive Playback");
        } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            AppLogger.getLogger().Error("Playback Error: Media Error Server Died");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Server Died");
        } else if (what == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
            AppLogger.getLogger().Error("Playback Error: Media Error Timed Out");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Timed Out");
        } else if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
            AppLogger.getLogger().Error("Playback Error: Media Error Unknown");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Unknown");
        } else if (what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
            AppLogger.getLogger().Error("Playback Error: Media Error Unsupported");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Media Error Unsupported");
        } else {
            AppLogger.getLogger().Error("Playback Error: Unknown Error");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: Unknown Error");
        }

        if (extra == -1004) {
            AppLogger.getLogger().Error("Playback Error: -1004");
            AppLogger.getLogger().Debug("AudioService","Playback Error: -1004" );
        } else if (extra == -1007) {
            AppLogger.getLogger().Error("Playback Error: -1007");
            AppLogger.getLogger().Debug("AudioService","Playback Error: -1007" );
        } else if (extra == -1010) {
            AppLogger.getLogger().Error("Playback Error: -1010");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: -1010");
        } else if (extra == -110) {
            AppLogger.getLogger().Error("Playback Error: -110");
            AppLogger.getLogger().Debug("AudioService", "Playback Error: -110");
        } else {
            AppLogger.getLogger().Error("Playback Error: " + Utils.PlayerStatusFromExtra(extra));
            AppLogger.getLogger().Debug("AudioService", "Playback Error: " + Utils.PlayerStatusFromExtra(extra));
        }

        return true;
    }


    @Override
    public void onPrepared(MediaPlayer mp) {

        AppLogger.getLogger().Debug("AudioService", "onPrepared");
        playerStart();
        mPlayerState = PlayerState.PLAYING;

        mHandler.postDelayed(mRunnable, 1000);

        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onItemLoaded(mMediaItem, mCurrentlyPlayingIndex);
                }
            }

        }

        sendPlaybackStartedToServer(0L);
        mLastProgressReport = SystemClock.elapsedRealtime();
    }


    public void addAudioPlayerListener(AudioPlayerListener listener) {
        if (mAudioPlayerListeners == null) {
            mAudioPlayerListeners = new ArrayList<>();
        }
        mAudioPlayerListeners.add(listener);
    }

    public void removeAudioPlayerListener(AudioPlayerListener listener) {
        if (mAudioPlayerListeners == null || mAudioPlayerListeners.size() == 0) {
            return;
        }
        mAudioPlayerListeners.remove(listener);
    }


    private void initMusicPlayer() {
        mPlayer = new MediaPlayer();
        mPlayer.setWakeMode(MainApplication.getInstance(), PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnErrorListener(this);
    }


    //**********************************************************************************************
    // Playback Methods
    //**********************************************************************************************

    public void playMedia() {

        if (mPlayerState.equals(PlayerState.PLAYING)) return;
        if (mPlayer == null) {
            initMusicPlayer();
        }
        MainApplication.getInstance().API.GetItemAsync(
                MainApplication.getInstance().PlayerQueue.PlaylistItems.get(0).Id,
                MainApplication.getInstance().API.getCurrentUserId(),
                getItemResponse);
    }

    public void playMediaAt(int index) {

        if (mPlayerState.equals(PlayerState.PLAYING)) {
            mCurrentlyPlayingIndex = index;
            stopMedia();
            MainApplication.getInstance().API.GetItemAsync(
                    MainApplication.getInstance().PlayerQueue.PlaylistItems.get(index).Id,
                    MainApplication.getInstance().API.getCurrentUserId(),
                    getItemResponse);
        }
    }

    public void stopMedia() {

        if (mPlayerState.equals(PlayerState.IDLE)) return;
        mPlayerState = PlayerState.IDLE;
        performPostPlaybackTasks();
        mShuffledItemIds = new ArrayList<>();
        mShuffleEnabled = false;
        mRepeatEnabled = false;
        notifyListenersPlaylistComplete();
    }

    public void togglePause() {

        if (mPlayer == null) return;

        if (mPlayer.isPlaying()) {
            playerPause();
            mPlayerState = PlayerState.PAUSED;
        } else {
            playerStart();
            mPlayerState = PlayerState.PLAYING;
        }

        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onPlayPauseChanged(mPlayerState.equals(PlayerState.PAUSED));
                }
            }
        }
    }

    public void toggleMute() {

        if (mMuted) {
            mPlayer.setVolume(mVolume, mVolume);
            mMuted = false;
        } else {
            mPlayer.setVolume(0f, 0f);
            mMuted = true;
        }

        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onVolumeChanged(mMuted, mVolume);
                }
            }

        }

    }

    public void toggleShuffle() {
        mShuffleEnabled = !mShuffleEnabled;
        if (mShuffleEnabled) {
            createShufflePlaylist();
        } else {
            deleteShufflePlaylist();
        }
        notifyListenersShuffleChanged();
    }

    private void createShufflePlaylist() {
        if (MainApplication.getInstance().PlayerQueue.PlaylistItems == null
                || MainApplication.getInstance().PlayerQueue.PlaylistItems.size() == 0) {
            return;
        }
        mShuffledItemIds = new ArrayList<>();
        for (PlaylistItem playlistItem : MainApplication.getInstance().PlayerQueue.PlaylistItems) {
            mShuffledItemIds.add(playlistItem.Id);
        }

    }

    private void deleteShufflePlaylist() {
        mShuffledItemIds = new ArrayList<>();
    }

    public void toggleRepeat() {

        mRepeatEnabled = !mRepeatEnabled;

        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onRepeatChanged(mRepeatEnabled);
                }
            }
        }
    }

    /**
     *
     */
    public void next() {

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() == 1) return;

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() > mCurrentlyPlayingIndex + 1) {
            mCurrentlyPlayingIndex += 1;
        } else {
            mCurrentlyPlayingIndex = 0;
        }

            playerStop();

//            if (mResume)
//                mApp.API.ReportPlaybackStoppedAsync(mMediaItem.Id,
//                        MB3Application.getInstance().Payload.User.Id,
//                        ((long) mPlaybackProgress.getProgress() * 10000)
//                                + (mMediaItem.UserData.PlaybackPositionTicks / 10000), null);
//            else
//                mApp.API.ReportPlaybackStoppedAsync(mMediaItem.Id, MB3Application.getInstance().Payload.User.Id, (long) mPlaybackProgress.getProgress() * 10000, null);

            playerReset();
        // Make sure the activity knows to update the playlist
//        mPlaybackActivity.UpdateCurrentPlayingIndex(currentlyPlayingIndex);

        MainApplication.getInstance().API.GetItemAsync(
                MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id,
                MainApplication.getInstance().API.getCurrentUserId(),
                getItemResponse);
    }

    /**
     *
     */
    public void previous() {

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() == 1) return;

        if (mCurrentlyPlayingIndex - 1 < 0) {
            mCurrentlyPlayingIndex = MainApplication.getInstance().PlayerQueue.PlaylistItems.size() - 1;
        } else {
            mCurrentlyPlayingIndex -= 1;
        }

        playerStop();
        playerReset();

        MainApplication.getInstance().API.GetItemAsync(
                MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id,
                MainApplication.getInstance().API.getCurrentUserId(), getItemResponse);
    }

    public void loadItemAtIndex(int index) {

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems == null
                || MainApplication.getInstance().PlayerQueue.PlaylistItems.size() == 0) return;

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() > index) {

            playerStop();
            playerReset();

            mCurrentlyPlayingIndex = index;

            MainApplication.getInstance().API.GetItemAsync(
                    MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id,
                    MainApplication.getInstance().API.getCurrentUserId(), getItemResponse);
        }
    }

    private Response<BaseItemDto> getItemResponse = new Response<BaseItemDto>() {

        @Override
        public void onResponse(BaseItemDto response) {

            if (response == null) {
                AppLogger.getLogger().Debug(TAG, "response.data is null");
                return;
            }

            mMediaItem = response;

            if (mMediaItem.getRunTimeTicks() != null)
                mRuntime = (int) (mMediaItem.getRunTimeTicks() / 10000);
            else
                mRuntime = 0;

            if (mMediaItem.getType().equalsIgnoreCase("VodCastVideo")
                    || mMediaItem.getType().equalsIgnoreCase("PodCastAudio")) {
                String mUrl = mMediaItem.getPath();

                if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(mUrl)) {
                    loadUrlIntoPlayer(mUrl);
                }

            } else {
                mResume = mCurrentlyPlayingIndex == 0
                        && MainApplication.getInstance().PlayerQueue.PlaylistItems.get(0).startPositionTicks != null
                        && MainApplication.getInstance().PlayerQueue.PlaylistItems.get(0).startPositionTicks > 0L;

                final Response<BaseItemDto> outerResponse = this;

                Utils.getStreamInfo(mMediaItem, new Response<StreamInfo>() {
                    @Override
                    public void onResponse(StreamInfo response) {
                        mStreamInfo = response;
                        if (MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).SubtitleStreamIndex == null) {
                            AppLogger.getLogger().Debug("MediaPlaybackFragment", "Subtitle index is null");
                        } else {
                            AppLogger.getLogger().Debug("MediaPlaybackFragment", "Subtitle index is " + String.valueOf(MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).SubtitleStreamIndex));
                        }

                        if (mStreamInfo != null) {
                            AppLogger.getLogger().Info("Load Media into player");
                            loadStreamInfoIntoPlayer();
                        } else {

                            if (MainApplication.getInstance().PlayerQueue.PlaylistItems.size() > mCurrentlyPlayingIndex + 1) {
                                mCurrentlyPlayingIndex += 1;

                                // Make sure the activity knows to update the playlist
//                        mPlaybackActivity.UpdateCurrentPlayingIndex(currentlyPlayingIndex);

                                MainApplication.getInstance().API.GetItemAsync(
                                        MainApplication.getInstance().PlayerQueue.PlaylistItems.get(mCurrentlyPlayingIndex).Id,
                                        MainApplication.getInstance().API.getCurrentUserId(),
                                        outerResponse);
                            } else {
                                mPlayerState = PlayerState.PREPARING;
                            }
                        }

                    }
                });

            }
        }
        @Override
        public void onError(Exception ex) {

        }
    };

    private void loadStreamInfoIntoPlayer() {

        if (mPlayer != null) {
            loadUrlIntoPlayer(mStreamInfo.ToUrl(MainApplication.getInstance().API.getApiUrl(), MainApplication.getInstance().API.getAccessToken()));
        }
    }

    private void loadUrlIntoPlayer(String url) {
        AppLogger.getLogger().Debug(TAG, "loadUrlIntoPlayer: " + url);
        AppLogger.getLogger().Info("Attempt to load: " + url);
        try {
            mPlayer.setDataSource(url);
            mPlayer.prepareAsync();
        } catch (IllegalStateException e) {
            AppLogger.getLogger().ErrorException("Exception handled: ", e);
            AppLogger.getLogger().Debug("loadUrlIntoPlayer", "IllegalStateException");
        } catch (IllegalArgumentException e) {
            AppLogger.getLogger().ErrorException("Exception handled: ", e);
            AppLogger.getLogger().Debug("loadUrlIntoPlayer", "IllegalArgumentException");
        } catch (SecurityException e) {
            AppLogger.getLogger().ErrorException("Exception handled: ", e);
            AppLogger.getLogger().Debug("loadUrlIntoPlayer", "SecurityException");
        } catch (IOException e) {
            AppLogger.getLogger().ErrorException("Exception handled: ", e);
            AppLogger.getLogger().Debug("loadUrlIntoPlayer", "IOException");
        }
    }

    public int getDuration() {

        return mRuntime;
    }

    public int getCurrentPosition() {

        return mCurrentPositionMilliseconds;
    }

    public int getCurrentlyPlayingIndex() {
        return mCurrentlyPlayingIndex;
    }

    public void setCurrentlyPlayingIndex(int index) {
        mCurrentlyPlayingIndex = index;
    }

    public BaseItemDto getCurrentItem() {
        return mMediaItem;
    }

    public int getQueueItemCount() {

        int queueSize = 0;

        if (MainApplication.getInstance().PlayerQueue.PlaylistItems != null) {
            queueSize = MainApplication.getInstance().PlayerQueue.PlaylistItems.size();
        }

        return queueSize;
    }

    public PlayerState getPlayerState() {
        return mPlayerState;
    }

    //**********************************************************************************************
    // player safety wrappers
    //**********************************************************************************************

    private void playerStart() {
        try {
            if (mPlayer != null) {
                mPlayer.start();
            }
        } catch (IllegalStateException e) {
            AppLogger.getLogger().ErrorException("Exception handled ", e);
        }
    }

    private void playerPause() {
        try {
            if (mPlayer != null) {
                mPlayer.pause();
            }
        } catch (IllegalStateException e) {
            AppLogger.getLogger().ErrorException("Exception handled ", e);
        }
    }

    private void playerStop() {
        try {
            AppLogger.getLogger().Debug(TAG, "Player Stop");
            if (mPlayer != null) {
                mPlayer.stop();
            }
        } catch (IllegalStateException e) {
            AppLogger.getLogger().Debug(TAG, "Exception handled in Player Stop");
            AppLogger.getLogger().ErrorException("Exception handled ", e);
        }
    }

    private void playerReset() {
        try {
            AppLogger.getLogger().Debug(TAG, "Player Reset");
            if (mPlayer != null) {
                mPlayer.reset();
            }
        } catch (IllegalStateException e) {
            AppLogger.getLogger().Debug(TAG, "Exception handled in Player Reset");
            AppLogger.getLogger().ErrorException("Exception handled ", e);
        }
    }

    public void playerSeekTo(int milliSeconds) {
        try {
            if (mPlayer != null) {
                mPlayer.seekTo(milliSeconds);
            }
        } catch (IllegalStateException e) {
            AppLogger.getLogger().ErrorException("Exception handled ", e);
        }
    }

    //**********************************************************************************************
    // Progress Reporting Methods
    //**********************************************************************************************

    private void sendPlaybackStartedToServer(Long position) {

        PlaybackStartInfo info = new PlaybackStartInfo();
        info.setQueueableMediaTypes(new ArrayList<String>() {{ add("Audio"); add("Video"); }});
        info.setPositionTicks(position);
        info.setAudioStreamIndex(mStreamInfo.getAudioStreamIndex());
        info.setCanSeek(true);
        info.setIsMuted(mMuted);
        info.setIsPaused(mPlayerState.equals(PlayerState.PAUSED));
        info.setItemId(mStreamInfo.getItemId());
        info.setMediaSourceId(mStreamInfo.getMediaSourceId());
        info.setPlayMethod(mStreamInfo.getIsDirectStream() ? PlayMethod.DirectStream : PlayMethod.Transcode);
        info.setSubtitleStreamIndex(mStreamInfo.getSubtitleStreamIndex());
        info.setVolumeLevel((int) mVolume * 100);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackStart(
                info,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    private void sendPlaybackProgressToServer(Long position) {

        PlaybackProgressInfo progressInfo = new PlaybackProgressInfo();
        progressInfo.setPositionTicks(position);
        progressInfo.setAudioStreamIndex(mStreamInfo.getAudioStreamIndex());
        progressInfo.setCanSeek(true);
        progressInfo.setIsMuted(mMuted);
        progressInfo.setIsPaused(mPlayerState.equals(PlayerState.PAUSED));
        progressInfo.setItemId(mStreamInfo.getItemId());
        progressInfo.setMediaSourceId(mStreamInfo.getMediaSourceId());
        progressInfo.setPlayMethod(mStreamInfo.getIsDirectStream() ? PlayMethod.DirectStream : PlayMethod.Transcode);
        progressInfo.setSubtitleStreamIndex(mStreamInfo.getSubtitleStreamIndex());
        progressInfo.setVolumeLevel((int) mVolume * 100);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackProgress(
                progressInfo,
                mStreamInfo,
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    private void sendPlaybackStoppedToServer(Long position) {

        PlaybackStopInfo stopInfo = new PlaybackStopInfo();
        stopInfo.setItemId(mStreamInfo.getItemId());
        stopInfo.setMediaSourceId(mStreamInfo.getMediaSourceId());
        stopInfo.setPositionTicks(position);

        MainApplication.getInstance().getPlaybackManager().reportPlaybackStopped(
                stopInfo,
                mStreamInfo,
                MainApplication.getInstance().API.getServerInfo().getId(),
                MainApplication.getInstance().API.getCurrentUserId(),
                MainApplication.getInstance().isOffline(),
                MainApplication.getInstance().API,
                new EmptyResponse());
    }

    public synchronized void incrementUiCounter() {
        mVisibilityCounter++;
        if (!mUiVisible) {
            mUiVisible = true;
        }
        if (mVisibilityCounter == 0) {
            AppLogger.getLogger().Debug(TAG, "UI is no longer visible");
        } else {
            AppLogger.getLogger().Debug(TAG, "UI is visible");
        }
        AppLogger.getLogger().Debug(TAG, "Visibility counter increased to " + String.valueOf(mVisibilityCounter));
    }

    public synchronized void decrementUiCounter() {
        if (--mVisibilityCounter == 0) {
            AppLogger.getLogger().Debug(TAG, "UI is no longer visible");
            if (mUiVisible) {
                mUiVisible = false;
            }
            AppLogger.getLogger().Debug(TAG, "Visibility counter decreased to " + String.valueOf(mVisibilityCounter));
        } else {
            AppLogger.getLogger().Debug(TAG, "UI is visible");
        }
    }

    private void notifyListenersPlaylistComplete() {
        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onPlaylistCompleted();
                }
            }
        }
    }

    private void notifyListenersShuffleChanged() {
        if (mAudioPlayerListeners != null) {
            synchronized (LOCK) {
                for (AudioPlayerListener listener : mAudioPlayerListeners) {
                    listener.onShuffleChanged(mShuffleEnabled);
                }
            }
        }
    }
}
