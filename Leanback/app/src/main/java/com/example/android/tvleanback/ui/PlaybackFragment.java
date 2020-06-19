/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.tvleanback.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.widget.EditText;
import android.widget.Toast;

import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.example.android.tvleanback.R;
import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.model.Playlist;
import com.example.android.tvleanback.model.Video;
import com.example.android.tvleanback.model.VideoCursorMapper;
import com.example.android.tvleanback.player.VideoPlayerGlue;
import com.example.android.tvleanback.presenter.CardPresenter;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import static com.example.android.tvleanback.ui.PlaybackFragment.VideoLoaderCallbacks.RELATED_VIDEOS_LOADER;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to {@link
 * VideoPlayerGlue}.
 */
public class PlaybackFragment extends VideoSupportFragment {

    private static final int UPDATE_DELAY = 10;

    private VideoPlayerGlue mPlayerGlue;
    private LeanbackPlayerAdapter mPlayerAdapter;
    private SimpleExoPlayer mPlayer;
    private PlaylistActionListener mPlaylistActionListener;


    private Video mVideo;
    private Playlist mPlaylist;
    private VideoLoaderCallbacks mVideoLoaderCallbacks;
    private CursorObjectAdapter mVideoCursorAdapter;
    private SharedPreferences mPreferences;

    private String userAgent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userAgent = Util.getUserAgent(getActivity(), "MultiTrustAndroidDemo");

        mVideo = getActivity().getIntent().getParcelableExtra(VideoDetailsActivity.VIDEO);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mPlaylist = new Playlist();



        mVideoLoaderCallbacks = new VideoLoaderCallbacks(mPlaylist);

        // Loads the playlist.
        Bundle args = new Bundle();
        args.putString(VideoContract.VideoEntry.COLUMN_CATEGORY, mVideo.category);
        getLoaderManager()
                .initLoader(VideoLoaderCallbacks.QUEUE_VIDEOS_LOADER, args, mVideoLoaderCallbacks);

        mVideoCursorAdapter = setupRelatedVideosCursor();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer();
        }
    }

    /** Pauses the player. */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onPause() {
        super.onPause();

        if (mPlayerGlue != null && mPlayerGlue.isPlaying()) {
            mPlayerGlue.pause();
        }
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onError(int errorCode, CharSequence errorMessage) {
        Log.d("Error", "Playback error" + errorMessage.toString() );


        /*AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle("Playback Error");

        alert.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
        alert.setMessage(errorMessage);
        alert.show();*/

        BrowseErrorFragment errorFragment = BrowseErrorFragment.newInstance(errorMessage.toString().replaceAll("\\s",""));
        getFragmentManager().beginTransaction().replace(R.id.playback_fragment_background, errorFragment).addToBackStack(null).commit();


}


        /** Returns a {@link DataSource.Factory}. */
    private DataSource.Factory buildDataSourceFactory() {
        DefaultDataSourceFactory upstreamFactory =
                new DefaultDataSourceFactory(getActivity(), buildHttpDataSourceFactory());
        return buildReadOnlyCacheDataSource(upstreamFactory, ((MultiTrustDemo)getActivity().getApplication()).getDownloadCache());
    }

    /** Returns a {@link HttpDataSource.Factory}. */
    private HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSourceFactory(userAgent);
    }

    private static CacheDataSourceFactory buildReadOnlyCacheDataSource(
            DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSourceFactory(
                cache,
                upstreamFactory,
                new FileDataSource.Factory(),
                /* cacheWriteDataSinkFactory= */ null,
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                /* eventListener= */ null);
    }

    private TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(/* context= */ getActivity(), new AdaptiveTrackSelection.Factory());
        DefaultTrackSelector.ParametersBuilder builder = new DefaultTrackSelector.ParametersBuilder(/* context= */ getActivity());
        builder.setExceedRendererCapabilitiesIfNecessary(true);
        DefaultTrackSelector.Parameters params = builder.build();
        trackSelector.setParameters(params);
        return trackSelector;


    }

    private LoadControl buildLoadControl() {
        DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
        builder.setBufferDurationsMs(10000,65000, 1000, 1000);
        return builder.createDefaultLoadControl();

    }

    private RenderersFactory buildRendersFactory() {
        return new DefaultRenderersFactory(/* context= */ getActivity())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
    }


    private void initializePlayer() {

        mPlayer = new SimpleExoPlayer.Builder(/* context= */ getActivity(), buildRendersFactory())
                        .setTrackSelector(buildTrackSelector())
                        .setLoadControl(buildLoadControl())
                        .build();

        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);
        mPlaylistActionListener = new PlaylistActionListener(mPlaylist);
        mPlayerAdapter.setErrorMessageProvider(new PlayerErrorMessageProvider());
        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter, mPlaylistActionListener);
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        mPlayerGlue.playWhenPrepared();

        play(mVideo);

        ArrayObjectAdapter mRowsAdapter = initializeRelatedVideosRow();
        setAdapter(mRowsAdapter);
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mPlayerGlue = null;
            mPlayerAdapter = null;
            mPlaylistActionListener = null;
        }
    }

    private void play(Video video) {
        if(video==null) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            getActivity().startActivity(intent);
            Toast.makeText(getContext(), "No next or previous video. Returned to main menu", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }else {

            mPlayerGlue.setTitle(video.title);
            mPlayerGlue.setSubtitle(video.description);

            //calls prepareMediaForPlaying when completed
            Log.d("PlaybackFragment", "Requesting Token");
            String user = mPreferences.getString(getString(R.string.pref_title_username), getString(R.string.user));
            String pass = mPreferences.getString(getString(R.string.pref_title_password), getString(R.string.pass));
            String url = mPreferences.getString(getString(R.string.pref_title_portal), getString(R.string.portal_url));

            //If setting was added and reverted the pref may be an empty string and default needs defined manually
            if (user.isEmpty()){
                user = getString(R.string.user);
            }
            if (pass.isEmpty()){
                pass = getString(R.string.pass);
            }
            if (url.isEmpty()) {
                url = getString(R.string.portal_url);
            }

            new GetTokenTask(url, user, pass, video).execute();


        }
    }

    //Called on completion of GetTokenTask
    private void prepareMediaForPlaying(Video video, String token) {
        mPlayer.prepare(buildMediaSource(video, token));
        mPlayerGlue.play();
    }


    private DrmSessionManager buildDrmSessionManager(Video video, String authtoken) {
        String[] drmKeyRequestPropertiesList = new String[] {authtoken};

        String proxy = video.license;
        if (proxy.isEmpty()){
            proxy = mPreferences.getString(getString(R.string.pref_title_proxy), getString(R.string.proxy_url));
        }

        //Handle case were preference is empty
        if (proxy.isEmpty()) {
            proxy = getString(R.string.proxy_url);
        }


        MultiTrustDrmCallback multiTrustDrmCallback = createMultiTrustDrmCallback(proxy, drmKeyRequestPropertiesList);
        DefaultLoadErrorHandlingPolicy pol = new DefaultLoadErrorHandlingPolicy(0);
        DrmSessionManager drmSessionManager = new DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(setUUID(), FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .setLoadErrorHandlingPolicy(pol)
                .build(multiTrustDrmCallback);

        return drmSessionManager;
    }

    public MediaSource buildMediaSource(Video video, String token) {
        @C.ContentType int type = Util.inferContentType(video.videoUrl); //checks the file extension to infer the type.
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(buildDataSourceFactory())
                        .setDrmSessionManager(buildDrmSessionManager(video, token))
                        .createMediaSource(Uri.parse(video.videoUrl));
            case C.TYPE_SS:
                return new SsMediaSource.Factory(buildDataSourceFactory())
                        .setDrmSessionManager(buildDrmSessionManager(video, token))
                        .createMediaSource(Uri.parse(video.videoUrl));
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(buildDataSourceFactory())
                        .setDrmSessionManager(buildDrmSessionManager(video, token))
                        .createMediaSource(Uri.parse(video.videoUrl));
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(buildDataSourceFactory())
                        .createMediaSource(Uri.parse(video.videoUrl));
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    //Set the UUID from the json file "drmscheme" value
    public UUID setUUID(){
        if(mVideo.drmScheme.equals("widevine")){
            return C.WIDEVINE_UUID;
        }else if(mVideo.drmScheme.equals("playready")){
            return C.PLAYREADY_UUID;
        }else if(mVideo.drmScheme.equals("clearkey")){
            return C.CLEARKEY_UUID;
        }
        else{
            throw new IllegalStateException("Drm scheme is null or unsupported, " + mVideo.drmScheme);
        }
    }

    public MultiTrustDrmCallback createMultiTrustDrmCallback(String licenseUrl, String[] keyRequestPropertiesArray){
        MultiTrustHttpDataSource httpDataSource = new MultiTrustHttpDataSource(licenseUrl, keyRequestPropertiesArray[0]);
        MultiTrustDrmCallback mtdrmCallback = new MultiTrustDrmCallback(httpDataSource);
        return mtdrmCallback;
    }

    private ArrayObjectAdapter initializeRelatedVideosRow() {
        /*
         * To add a new row to the mPlayerAdapter and not lose the controls row that is provided by the
         * glue, we need to compose a new row with the controls row and our related videos row.
         *
         * We start by creating a new {@link ClassPresenterSelector}. Then add the controls row from
         * the media player glue, then add the related videos row.
         */
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(
                mPlayerGlue.getControlsRow().getClass(), mPlayerGlue.getPlaybackRowPresenter());
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());

        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(presenterSelector);

        rowsAdapter.add(mPlayerGlue.getControlsRow());

        HeaderItem header = new HeaderItem(getString(R.string.related_movies));
        ListRow row = new ListRow(header, mVideoCursorAdapter);
        rowsAdapter.add(row);

        setOnItemViewClickedListener(new ItemViewClickedListener());

        return rowsAdapter;
    }

    private CursorObjectAdapter setupRelatedVideosCursor() {
        CursorObjectAdapter videoCursorAdapter = new CursorObjectAdapter(new CardPresenter());
        videoCursorAdapter.setMapper(new VideoCursorMapper());

        Bundle args = new Bundle();
        args.putString(VideoContract.VideoEntry.COLUMN_CATEGORY, mVideo.category);
        getLoaderManager().initLoader(RELATED_VIDEOS_LOADER, args, mVideoLoaderCallbacks);

        return videoCursorAdapter;
    }

    public void skipToNext() {
        mPlayerGlue.next();
    }

    public void skipToPrevious() {
        mPlayerGlue.previous();
    }

    public void rewind() {
        mPlayerGlue.rewind();
    }

    public void fastForward() {
        mPlayerGlue.fastForward();
    }

    /** Opens the video details page when a related video has been clicked. */
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;

                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                        getActivity(),
                                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                        VideoDetailsActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }

    /** Loads a playlist with videos from a cursor and also updates the related videos cursor. */
    protected class VideoLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        static final int RELATED_VIDEOS_LOADER = 1;
        static final int QUEUE_VIDEOS_LOADER = 2;

        private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();

        private final Playlist playlist;

        private VideoLoaderCallbacks(Playlist playlist) {
            this.playlist = playlist;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            // When loading related videos or videos for the playlist, query by category.
            String category = args.getString(VideoContract.VideoEntry.COLUMN_CATEGORY);
            return new CursorLoader(
                    getActivity(),
                    VideoContract.VideoEntry.CONTENT_URI,
                    null,
                    VideoContract.VideoEntry.COLUMN_CATEGORY + " = ?",
                    new String[] {category},
                    null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int id = loader.getId();
            if (id == QUEUE_VIDEOS_LOADER) {
                playlist.clear();
                do {
                    Video video = (Video) mVideoCursorMapper.convert(cursor);

                    // Set the current position to the selected video.
                    if (video.id == mVideo.id) {
                        playlist.setCurrentPosition(playlist.size());
                    }

                    playlist.add(video);

                } while (cursor.moveToNext());
            } else if (id == RELATED_VIDEOS_LOADER) {
                mVideoCursorAdapter.changeCursor(cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mVideoCursorAdapter.changeCursor(null);
        }
    }

    class PlaylistActionListener implements VideoPlayerGlue.OnActionClickedListener {

        private Playlist mPlaylist;

        PlaylistActionListener(Playlist playlist) {
            this.mPlaylist = playlist;
        }

        @Override
        public void onPrevious() {
            play(mPlaylist.previous());
        }

        @Override
        public void onNext() {
            play(mPlaylist.next());
        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

        @Override
        public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
            String errorString = getString(R.string.error_generic);
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                Exception cause = e.getRendererException();
                if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                            (MediaCodecRenderer.DecoderInitializationException) cause;
                    if (decoderInitializationException.codecInfo == null) {
                        if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                            errorString = getString(R.string.error_querying_decoders);
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            errorString = getString(R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                        } else {
                            errorString = getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                        }
                    } else {
                        errorString = getString(R.string.error_no_secure_decoder, decoderInitializationException.codecInfo.mimeType);
                    }
                }
            }
            //DRMSession exceptions are of TYPE_SOURCE
            else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
                Exception cause = e.getSourceException();
                if (cause instanceof DrmSession.DrmSessionException) {
                    Throwable underlyingCause = cause.getCause();
                    if (underlyingCause instanceof MultiTrustDrmException)
                    {
                        errorString = ((MultiTrustDrmException)underlyingCause).error;
                    }

                }
            }
            return Pair.create(0, errorString);
        }
    }

    private class GetTokenTask extends AsyncTask<Void,Void,String> {

        private final String Url;
        private final String Username;
        private final String Password;
        private final String Path;
        private final String Asset;
        private final String Entitlement;
        private final String Policy;
        private final Video  Video;


        GetTokenTask(String url, String user, String pass, Video video) {
            this.Url = url;
            this.Username = user;
            this.Password = pass;
            this.Asset =  video.asset;
            this.Path = "gettoken";
            this.Entitlement = video.entitlement;
            this.Policy = video.policy;
            this.Video = video;
        }

        @Override
        protected void onPreExecute() {
            Log.d("Token Request","Getting Token for :" + this.Asset);
        }
        @Override
        protected String doInBackground(Void... voids) {
            String ent  = this.Entitlement.isEmpty() ? "" : "&entitlement="+this.Entitlement;
            String pol  = this.Policy.isEmpty() ? "" : "&policy="+this.Policy;
            String asset = this.Asset.isEmpty() ? "test" : this.Asset;
            String request = this.Url + "/" + this.Path + "?username=" + this.Username + "&password=" + this.Password + "&asset=" + asset + "&duration=3600" + ent + pol;

            try {
                java.net.URL url = new java.net.URL(request);
                URLConnection conn;
                conn = url.openConnection();
                conn.setReadTimeout(3000);

                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = bufferedReader.readLine()) != null) {
                    response.append(inputLine);
                }
                bufferedReader.close();

                return response.toString();

            } catch (IOException e ){
                e.printStackTrace();
                if (e.getMessage().isEmpty())
                {
                    return "TOKEN REQUEST FAILED: " + e.getCause().toString();
                }
                else
                {
                    return "TOKEN REQUEST FAILED: " + e.getMessage();
                }
            }

        }
        @Override
        protected void onPostExecute(String result) {
            if(result.contains("FAILED"))
            {
                onError(500, result);

            }
            else{
                //result is Auth Token
                prepareMediaForPlaying(this.Video, result);
            }

        }
    }
}
