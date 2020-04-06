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
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.leanback.app.VideoFragment;
import androidx.leanback.app.VideoFragmentGlueHost;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.media.PlaybackGlue;
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
import com.example.android.tvleanback.Utils;
import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.data.VideoDbBuilder;
import com.example.android.tvleanback.model.Playlist;
import com.example.android.tvleanback.model.Video;
import com.example.android.tvleanback.model.VideoCursorMapper;
import com.example.android.tvleanback.player.VideoPlayerGlue;
import com.example.android.tvleanback.presenter.CardPresenter;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static com.example.android.tvleanback.ui.PlaybackFragment.VideoLoaderCallbacks.RELATED_VIDEOS_LOADER;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to {@link
 * VideoPlayerGlue}.
 */
public class PlaybackFragment extends VideoSupportFragment {

    private static final int UPDATE_DELAY = 16;

    private VideoPlayerGlue mPlayerGlue;
    private LeanbackPlayerAdapter mPlayerAdapter;
    private SimpleExoPlayer mPlayer;
    private TrackSelector mTrackSelector;
    private PlaylistActionListener mPlaylistActionListener;
    private DefaultBandwidthMeter mBandwidthMeter;
    private DrmSessionManager drmSessionManager;
    private DataSource.Factory dataSourceFactory;
//    private UUID drmScheme;

    private Video mVideo;
    private Playlist mPlaylist;
    private VideoLoaderCallbacks mVideoLoaderCallbacks;
    private CursorObjectAdapter mVideoCursorAdapter;

    String userAgent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userAgent = Util.getUserAgent(getContext(), "TVsample");

        mVideo = getActivity().getIntent().getParcelableExtra(VideoDetailsActivity.VIDEO);
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

    private void initializePlayer() {
        mBandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(mBandwidthMeter);
        mTrackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        dataSourceFactory = new DefaultDataSourceFactory(getContext(), Util.getUserAgent(getContext(),"ExoPlayer"));

        mPlayer = ExoPlayerFactory.newSimpleInstance(getActivity(), mTrackSelector);
        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);
        mPlaylistActionListener = new PlaylistActionListener(mPlaylist);
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
            mTrackSelector = null;
            mPlayerGlue = null;
            mPlayerAdapter = null;
            mPlaylistActionListener = null;
        }
    }

    private void play(Video video) {
        mPlayerGlue.setTitle(video.title);
        mPlayerGlue.setSubtitle(video.description);
        prepareMediaForPlaying(Uri.parse(video.videoUrl));
        mPlayerGlue.play();
    }

    private void prepareMediaForPlaying(Uri mediaSourceUri) {
//        commented this part out and thinking of moving it around

        createDrm();

//        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getContext(), Util.getUserAgent(getContext(),"ExoPlayer"));

//        DashMediaSource dashMediaSource = new DashMediaSource(mediaSourceUri, dataSourceFactory,
//                new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);

        mediaSourceType(mediaSourceUri, drmSessionManager);

        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(mediaSourceUri);

        mPlayer.prepare(dashMediaSource);
    }

    //TODO CREATION OF THE DRM SESSION MANAGER (needs fixing)
    //currently this is pretty badly done. Created a local String[] to pass into my drmcallback with the token (token taken from json file)
    //Also hard coding my uuid in the drm session manager
    private DrmSessionManager createDrm() {
        String[] drmKeyRequestPropertiesList = new String[] {mVideo.authtoken};
        MultiTrustDrmCallback multiTrustDrmCallback = createMultiTrustDrmCallback(mVideo.license, drmKeyRequestPropertiesList);

        drmSessionManager =
                new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(setUUID(), FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(false)
                        .build(multiTrustDrmCallback);

        return drmSessionManager;
    }


//    TODO SWITCH STATEMENT FROM EXOPLAYER TO CHECK CONTENT TYPE, SET UP CORRECT MEDIA PLAYER FOR THAT TYPE
    //Need to add an extension piece to the json file to determine what case should be taken.
    //Will also need some sort of logic to set the extension string to one of the C.TYPE's
    //currently I check the file extension to determine the media source\.
    private MediaSource mediaSourceType(
            Uri uri, DrmSessionManager<ExoMediaCrypto> drmSessionManager) {
        @C.ContentType int type = Util.inferContentType(mVideo.videoUrl); //checks the file extension to infer the type.
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(uri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    //Set the UUID from the json file "drmscheme" value. If there is no value, the application will crash which isn't great
    private UUID setUUID(){
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

    private MultiTrustDrmCallback createMultiTrustDrmCallback(String licenseUrl, String[] keyRequestPropertiesArray){
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
}
