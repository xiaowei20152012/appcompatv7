package com.mxtech.videoplayer.ad.online.exoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class RhapsodyPlayer implements Player.EventListener, SimpleExoPlayer.VideoListener {
    public static final String TAG = "RhapsodyPlayer";
    public static final int     STATE_ERROR                 = -1;
    public static final int     STATE_IDLE                  = 0;
    public static final int     STATE_UNLOADED              = 1;        // data source등은 설정되었으나 load되기 전 상태.
    public static final int     STATE_PREPARING             = 2;        // DNS lookup 도중에는 mp가 null일 수 있다.
    public static final int     STATE_PREPARED              = 3;
    public static final int     STATE_PAUSED                = 4;
    public static final int     STATE_PLAYING               = 5;
    public static final int     STATE_PLAYBACK_COMPLETED    = 6;

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private Context mContext;
    private SimpleExoPlayer mPlayer;
    private int mState;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceView mSurfaceView;
    private Surface mSurface;
    private MediaSource mMediaSource;
    private Handler mEventHandler;
    private AdaptiveMediaSourceEventListener mEventListener;
    private TrackSelector mTrackSelector;
    private DataSource.Factory mMediaDataSourceFactory;
    private ArrayList<Player.EventListener> mEventListenerList = new ArrayList<Player.EventListener>();
    private ArrayList<SimpleExoPlayer.VideoListener> mVideoListenerList = new ArrayList<SimpleExoPlayer.VideoListener>();
    private int mVideoWidth;
    private int mVideoHeight;

    public RhapsodyPlayer(){
        mState = STATE_IDLE;
    };

    public static RhapsodyPlayer create(Context context, Uri uri) {
        return create(context, uri, null);
    }

    public static RhapsodyPlayer create(Context context, Uri uri, String extension) {
        return create(context, uri, extension, null, null, null);
    }

    public static RhapsodyPlayer create(Context context, Uri uri, String extension, UUID drmSchemeUUID, Uri drmLicenseUrl, String[] keyRequestProperties) {
        return create(context, uri, null);
    }

    public static RhapsodyPlayer create(Context context, Uri[] uris) {
        return create(context, uris, null);
    }

    public static RhapsodyPlayer create(Context context, Uri[] uris, String[] extensions) {
        return create(context, uris, null, null,null,null);
    }

    public static RhapsodyPlayer create(Context context, Uri[] uris, String[] extensions, UUID drmSchemeUUID, Uri drmLicenseUrl, String[] keyRequestProperties) {
        try {
            RhapsodyPlayer player = new RhapsodyPlayer();
            player.setDataSource(context,uris, extensions,drmSchemeUUID,drmLicenseUrl,keyRequestProperties);
            player.prepare();
            return player;
        } catch (IOException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (SecurityException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (UnsupportedDrmException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        }
        return null;
    }

    public void setMediaDataSourceFactory( DataSource.Factory factory )
    {
        mMediaDataSourceFactory = factory;
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        mTrackSelector = trackSelector;
    }

    public void setVideoSurfaceView(SurfaceView surfaceView) {
        if (mPlayer != null) {
            mPlayer.setVideoSurfaceView(surfaceView);
        }
    }
    /**
     * Sets the {@link SurfaceHolder} to use for displaying the video
     * portion of the media.
     *
     * Either a surface holder or surface must be set if a display or video sink
     * is needed.  Not calling this method or {@link #setSurface(Surface)}
     * when playing back a video will result in only the audio track being played.
     * A null surface holder or surface will result in only the audio track being
     * played.
     *
     * @param sh the SurfaceHolder to use for video display
     */
    public void setDisplay(SurfaceHolder sh) {
        if (mPlayer != null) {
            mPlayer.setVideoSurfaceHolder(sh);
        } else {
            mSurfaceHolder = sh;
        }
    }

    /**
     * Sets the {@link Surface} to be used as the sink for the video portion of
     * the media. This is similar to {@link #setDisplay(SurfaceHolder)}, but
     * does not support {@link #setScreenOnWhilePlaying(boolean)}.  Setting a
     * Surface will un-set any Surface or SurfaceHolder that was previously set.
     * A null surface will result in only the audio track being played.
     *
     * If the Surface sends frames to a {@link SurfaceTexture}, the timestamps
     * returned from {@link SurfaceTexture#getTimestamp()} will have an
     * unspecified zero point.  These timestamps cannot be directly compared
     * between different media sources, different instances of the same media
     * source, or multiple runs of the same program.  The timestamp is normally
     * monotonically increasing and is unaffected by time-of-day adjustments,
     * but it is reset when the position is set.
     *
             * @param surface The {@link Surface} to be used for the video portion of
     * the media.
            */
    public void setSurface(Surface surface) {
        if (mPlayer != null) {
            mPlayer.setVideoSurface(surface);
        } else {
            mSurface = surface;
        }
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException, UnsupportedDrmException {
        setDataSource(context,uri,null);
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri uri, String extenstion)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException, UnsupportedDrmException {
        Uri[] uris = new Uri[1];
        String[] extensions = new String[1];
        if (uris != null && extensions != null) {
            uris[0] = uri;
            extensions[0] = extenstion;
            setDataSource(context, uris, extensions, null, null, null);
        }
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uris    the Content URIs of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri[] uris)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException, UnsupportedDrmException {
        setDataSource(context, uris, null);
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uris    the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri[] uris, String[] extensions)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException, UnsupportedDrmException {
        setDataSource(context, uris, extensions, null, null, null);
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uris     the Content URI of the data you want to play
     * @param extensions the headers to be sent together with the request for the data
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri[] uris, String[] extensions, UUID drmSchemeUUID, Uri drmLicenseUrl, String[] keyRequestProperties)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException, UnsupportedDrmException {
        mContext = context;
        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
        if (drmSchemeUUID != null) {
            if (Util.SDK_INT < 18) {
                throw new IllegalArgumentException("Protected content not supported on API levels below 18.");
            }
            drmSessionManager = buildDrmSessionManagerV18(drmSchemeUUID, drmLicenseUrl.toString(), keyRequestProperties);
            if (drmSessionManager == null) {
                throw new IllegalStateException("Failed to uild Drm Session Manager.");
            }
        }
        int extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context, drmSessionManager, extensionRendererMode);

        if (mTrackSelector == null) {
            TrackSelection.Factory adaptiveTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
            mTrackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);
        }

        if (mMediaDataSourceFactory == null) {
            mMediaDataSourceFactory = buildDataSourceFactory(true);
        }

        mPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, mTrackSelector);
        if (mPlayer != null) {
            //Add listeners once player created
            for (Player.EventListener l : mEventListenerList) {
                mPlayer.addListener(l);
            }
            mEventListenerList.clear();

            for (SimpleExoPlayer.VideoListener l : mVideoListenerList) {
                mPlayer.addVideoListener(l);
            }
            mVideoListenerList.clear();

            MediaSource[] mediaSources = new MediaSource[uris.length];
            for (int i = 0; i < uris.length; i++) {
                mediaSources[i] = buildMediaSource(uris[i], extensions != null ? extensions[i] : null);
            }

            mMediaSource = mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources);
        }
    }

    /**
     * Adds a listener to receive video events.
     *
     * @param listener The listener to register.
     */
    public void addListener(Player.EventListener listener) {
        if (mPlayer != null) {
            mPlayer.addListener(listener);
        } else if (mEventListenerList != null){
            mEventListenerList.add(listener);
        }
    }

    /**
     * Removes a listener of video events.
     *
     * @param listener The listener to unregister.
     */
    public void removeListener(Player.EventListener listener) {
        if (mPlayer != null) {
            mPlayer.removeListener(listener);
        }
        for (Player.EventListener l : mEventListenerList) {
            if (l == listener) {
                mEventListenerList.remove(l);
                break;
            }
        }
    }

    /**
     * Adds a listener to receive video events.
     *
     * @param listener The listener to register.
     */
    public void addVideoListener(SimpleExoPlayer.VideoListener listener) {
        if (mPlayer != null) {
            mPlayer.addVideoListener(listener);
        } else if (mEventListenerList != null){
            mVideoListenerList.add(listener);
        }
    }

    /**
     * Removes a listener of video events.
     *
     * @param listener The listener to unregister.
     */
    public void removeVideoListener(SimpleExoPlayer.VideoListener listener) {
        if (mPlayer != null) {
            mPlayer.removeVideoListener(listener);
        }
        for (SimpleExoPlayer.VideoListener l : mVideoListenerList) {
            if (l == listener) {
                mVideoListenerList.remove(l);
                break;
            }
        }
    }

    /**
     *
     *
     * @param eventHandler eventHandler A handler for events. May be null if delivery of events is not required.
     */
    public void setEventHandler( Handler eventHandler ){
        mEventHandler = eventHandler;
    }
    /**
     * Set a event listener for callbacks to be notified of adaptive {@link MediaSource} events.
     *
     * @param eventListener A listener of adaptive {@link MediaSource} events events. May be null if delivery of events is not required.
     */
    public void setAdaptiveMediaSourceEventListener( AdaptiveMediaSourceEventListener eventListener ){
        mEventListener = eventListener;
    }
    /**
     * Prepares the player for playback, asynchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare() or prepareAsync(). For streams, you should call prepareAsync(),
     * which returns immediately, rather than blocking until enough data has been
     * buffered.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void prepare() throws IllegalStateException {
        if (mMediaSource != null){
            mState = STATE_PREPARING;
            mPlayer.prepare(mMediaSource);
        }
        else {
            throw new IllegalStateException("Is data source provided?");
        }
    }

    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void start() throws IllegalStateException {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(true);
        }
    }
        /**
         * Stops playback after playback has been stopped or paused.
         *
         * @throws IllegalStateException if the internal player engine has not been
         * initialized.
         */
    public void stop() throws IllegalStateException {
        if (mPlayer != null) {
            mPlayer.stop();
        }
    }

    /**
     * Pauses playback. Call start() to resume.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void pause() throws IllegalStateException {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(false);
        }
    }

    public int getPlaybackState(){
        if (mPlayer!=null){
            return mPlayer.getPlaybackState();
        }
        return Player.STATE_IDLE;
    }
    public SimpleExoPlayer getPlayer(){
        if (mPlayer!=null){
            return mPlayer;
        }
        return null;
    }
    /**
     * Checks whether the MediaPlayer is playing.
     *
     * @return true if currently playing, false otherwise
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     */
    public  boolean isPlaying() {
        return true;
    }

    /**
     * Seeks to specified time position.
     *
     * @param msec the offset in milliseconds from the start to seek to
     * @throws IllegalStateException if the internal player engine has not been
     * initialized
     */
    public void seekTo(long msec) throws IllegalStateException {
        if (mPlayer != null) {
            mPlayer.seekTo(msec);
        }
    }

    /**
     * Releases resources associated with this MediaPlayer object.
     * It is considered good practice to call this method when you're
     * done using the MediaPlayer. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaPlayer object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaPlayer object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     */
    public void release() {
        if ( mPlayer != null )
        {
            mPlayer.release();
            mPlayer = null;

        }
        mTrackSelector = null;
        mMediaSource = null;
        mEventListenerList.clear();
        mVideoListenerList.clear();
    }

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public long getCurrentPosition() {
        return mPlayer != null ? mPlayer.getCurrentPosition() : -1;
    }

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds, if no duration is available
     *         (for example, if streaming live content), -1 is returned.
     */
    public long getDuration() {
        return mPlayer != null ? mPlayer.getDuration() : -1;
    }

    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManagerV18(UUID uuid,
                                                                              String licenseUrl,
                                                                              String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl, buildHttpDataSourceFactory(null));
        if (keyRequestPropertiesArray != null) {
            for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i], keyRequestPropertiesArray[i + 1]);
            }
        }
        return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback,
                null, mEventHandler, null);
    }

    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    private DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultDataSourceFactory(mContext, bandwidthMeter, buildHttpDataSourceFactory(bandwidthMeter));
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultHttpDataSourceFactory("exo", bandwidthMeter);
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        MediaSource mediaSource = null;
        switch (type) {
            case C.TYPE_SS: {
                mediaSource = new SsMediaSource(uri,
                        new DefaultDataSourceFactory(mContext, null, new DefaultHttpDataSourceFactory(null)),
                        new DefaultSsChunkSource.Factory(mMediaDataSourceFactory),
                        mEventHandler,
                        mEventListener);
            }
            break;

            case C.TYPE_DASH: {
                mediaSource = new DashMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashChunkSource.Factory(mMediaDataSourceFactory),
                        mEventHandler,
                        mEventListener);
            }
            break;

            case C.TYPE_HLS: {
                mediaSource = new HlsMediaSource(uri, mMediaDataSourceFactory, mEventHandler, mEventListener);
            }
            break;

            case C.TYPE_OTHER: {
                mediaSource = new ExtractorMediaSource(uri,
                        mMediaDataSourceFactory,
                        new DefaultExtractorsFactory(),
                        mEventHandler,
                        null);
            }
            break;

            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
        return mediaSource;
    }

    //Player.EventListener
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    public void onLoadingChanged(boolean isLoading) {
        Log.d(TAG, "onLoadingChanged isLoading:" + isLoading);
    }

    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "onPlayerStateChanged playWhenReady:" + playWhenReady + " playbackState:" + playbackState);
        if (Player.STATE_READY == playbackState) {
            mState = STATE_PREPARED;
        } else if (Player.STATE_ENDED == playbackState) {
            mState = STATE_PLAYBACK_COMPLETED;
        }
    }

    public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
        Log.d(TAG, "onRepeatModeChanged repeatMode:" + repeatMode);
    }

    public void onPlayerError(ExoPlaybackException error) {
        Log.d(TAG, "onPlayerError error:" + error);
    }

    public void onPositionDiscontinuity() {
        Log.d(TAG, "onPositionDiscontinuity");
    }

    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        Log.d(TAG, "onPlaybackParametersChanged playbackParameters:" + playbackParameters);
    }

    /**
     * Called each time there's a change in the size of the video being rendered.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
     *     rotation in degrees that the application should apply for the video for it to be rendered
     *     in the correct orientation. This value will always be zero on API levels 21 and above,
     *     since the renderer will apply all necessary rotations internally. On earlier API levels
     *     this is not possible. Applications that use {@link android.view.TextureView} can apply
     *     the rotation by calling {@link android.view.TextureView#setTransform}. Applications that
     *     do not expect to encounter rotated videos can safely ignore this parameter.
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
     *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     *     content.
     */
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                            float pixelWidthHeightRatio) {
        mVideoWidth = width;
        mVideoHeight = height;
    }

    /**
     * Called when a frame is rendered for the first time since setting the surface, and when a
     * frame is rendered for the first time since a video track was selected.
     */
    public void onRenderedFirstFrame() {

    }

}
