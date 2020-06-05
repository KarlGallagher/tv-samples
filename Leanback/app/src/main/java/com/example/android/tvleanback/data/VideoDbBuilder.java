/*
 * Copyright (c) 2016 The Android Open Source Project
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
package com.example.android.tvleanback.data;

import android.content.ContentValues;
import android.content.Context;
import android.media.Rating;
import androidx.annotation.NonNull;
import android.util.Log;

import com.example.android.tvleanback.R;
import com.example.android.tvleanback.model.Video;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

class TMDbInfo {
    public String description;
    public String backgroundUrl;
    public String posterUrl;
    public String releaseYear;
    public Double averageVote;

}

/**
 * The VideoDbBuilder is used to grab a JSON file from a server and parse the data
 * to be placed into a local database
 */
public class VideoDbBuilder {
    public static final String TAG_MEDIA = "streams";
    public static final String TAG_MT_VIDEOS = "multitrustvideos";
    public static final String TAG_CATEGORY = "category";
    public static final String TAG_STUDIO = "studio";
    public static final String TAG_SOURCES = "dash_address";
    public static final String TAG_DESCRIPTION = "description";
    public static final String TAG_CARD_THUMB = "card";
    public static final String TAG_BACKGROUND = "background";
    public static final String TAG_TITLE = "name";
    public static final String TAG_LICENSE = "wv_license_proxy";
    public static final String TAG_AUTH_TOKEN = "token";
    public static final String TAG_ASSET = "asset";
    public static final String TAG_ENTITLEMENT = "entitlement";
    public static final String TAG_POLICY = "policy";
    public static final String TAG_DRM_SCHEME = "drm_type";
    public static final String TAG_FORMAT = "stream_format";

    private static final String TAG = "VideoDbBuilder";

    /**TMDB consts */
    private static final String TAG_TMDB_RESULTS = "results";
    private static final String TAG_TMDB_VOTE = "vote_average";
    private static final String TAG_TMDB_BACKGROUND_ART = "backdrop_path";
    private static final String TAG_TMDB_POSTER_ART = "poster_path";
    private static final String TAG_TMDB_DESCRIPTION = "overview";
    private static final String TAG_TMDB_RELEASE_DATE = "release_date";
    private static final String TMDB_POSTER_BASE_PATH =  "https://image.tmdb.org/t/p/w500";
    private static final String TMDB_BACKDROP_BASE_PATH =  "https://image.tmdb.org/t/p/original";
    private static final String TMDB_SEARCH_URL  = "https://api.themoviedb.org/3/search/movie?api_key=0f99c73a164f775c5a0e060a16cd9c76";

    private Context mContext;

    /**
     * Default constructor that can be used for tests
     */
    public VideoDbBuilder() {

    }

    public VideoDbBuilder(Context mContext) {
        this.mContext = mContext;
    }

    /**
     * Fetches JSON data representing videos from a server and populates that in a database
     * @param url The location of the video list
     */
    public @NonNull List<ContentValues> fetch(String url)
            throws IOException, JSONException {
        JSONObject videoData = fetchJSON(url);
        return buildMedia(videoData);
    }

    /**
     * Takes the contents of a JSON object and populates the database
     * @param jsonObj The JSON object of videos
     * @throws JSONException if the JSON object is invalid
     */
    public List<ContentValues> buildMedia(JSONObject jsonObj) throws JSONException, IOException {

        //JSONArray categoryArray = jsonObj.getJSONArray(TAG_MT_VIDEOS);
        List<ContentValues> videosToInsert = new ArrayList<>();

        //for (int i = 0; i < categoryArray.length(); i++) {
            //JSONArray videoArray;

            //JSONObject category = categoryArray.getJSONObject(i);
           // String categoryName = category.getString(TAG_CATEGORY);
            String categoryName = "Trailers";
            //videoArray = category.getJSONArray(TAG_MEDIA);
            JSONArray videoArray = jsonObj.getJSONArray(TAG_MEDIA);


        for (int j = 0; j < videoArray.length(); j++) {
                JSONObject video = videoArray.getJSONObject(j);

                String format = video.optString(TAG_FORMAT);

                if (!format.equals("dash")){
                    continue;
                }

                String title = video.optString(TAG_TITLE);
                TMDbInfo tmdb_video_info = fetchMovieInfo(title);

                String description = video.optString(TAG_DESCRIPTION) + "\n" + tmdb_video_info.description;
                String videoUrl = video.optString(TAG_SOURCES);
                String bgImageUrl = tmdb_video_info.backgroundUrl;
                String cardImageUrl = tmdb_video_info.posterUrl;
                String studio = video.optString(TAG_STUDIO);
                String license = video.optString(TAG_LICENSE);
                String authtoken = video.optString(TAG_AUTH_TOKEN);
                String asset = video.optString(TAG_ASSET, "asset");
                String entitlement = video.optString(TAG_ENTITLEMENT, "");
                String policy = video.optString(TAG_POLICY, "");
                String drmScheme = "widevine";


                ContentValues videoValues = new ContentValues();
                videoValues.put(VideoContract.VideoEntry.COLUMN_CATEGORY, categoryName);
                videoValues.put(VideoContract.VideoEntry.COLUMN_NAME, title);
                videoValues.put(VideoContract.VideoEntry.COLUMN_DESC, description);
                videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL, videoUrl);
                videoValues.put(VideoContract.VideoEntry.COLUMN_CARD_IMG, cardImageUrl);
                videoValues.put(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL, bgImageUrl);
                videoValues.put(VideoContract.VideoEntry.COLUMN_STUDIO, studio);
                videoValues.put(VideoContract.VideoEntry.COLUMN_LICENSE, license);
                videoValues.put(VideoContract.VideoEntry.COLUMN_AUTH_TOKEN, authtoken);
                videoValues.put(VideoContract.VideoEntry.COLUMN_MULTITRUST_ASSET, asset);
                videoValues.put(VideoContract.VideoEntry.COLUMN_MULTITRUST_ENTITLEMENT, entitlement);
                videoValues.put(VideoContract.VideoEntry.COLUMN_MULTITRUST_POLICY, asset);
                videoValues.put(VideoContract.VideoEntry.COLUMN_DRM_SCHEME, drmScheme);

                // Fixed defaults.
                videoValues.put(VideoContract.VideoEntry.COLUMN_CONTENT_TYPE, "application/dash+xml");                        //
                videoValues.put(VideoContract.VideoEntry.COLUMN_IS_LIVE, false);
                videoValues.put(VideoContract.VideoEntry.COLUMN_AUDIO_CHANNEL_CONFIG, "2.0");
                videoValues.put(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR, tmdb_video_info.releaseYear);
                videoValues.put(VideoContract.VideoEntry.COLUMN_DURATION, 0);
                videoValues.put(VideoContract.VideoEntry.COLUMN_RATING_STYLE, Rating.RATING_THUMB_UP_DOWN);
                videoValues.put(VideoContract.VideoEntry.COLUMN_RATING_SCORE, tmdb_video_info.averageVote);
                if (mContext != null) {
                    videoValues.put(VideoContract.VideoEntry.COLUMN_PURCHASE_PRICE,
                            mContext.getResources().getString(R.string.buy_2));
                    videoValues.put(VideoContract.VideoEntry.COLUMN_RENTAL_PRICE,
                            mContext.getResources().getString(R.string.rent_2));
                    videoValues.put(VideoContract.VideoEntry.COLUMN_ACTION,
                            mContext.getResources().getString(R.string.global_search));
                }

                // TODO: Get these dimensions.
                videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_WIDTH, 1280);
                videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_HEIGHT, 720);

                videosToInsert.add(videoValues);
            }
        //}
        return videosToInsert;
    }

    /**
     * Fetch JSON movie info from TMDB
     *
     * @return the TMDbInfo mapped from JSON response
     * @throws JSONException
     * @throws IOException
     */
    private TMDbInfo fetchMovieInfo(String movie) throws JSONException, IOException {
        TMDbInfo tmdb = new TMDbInfo();
        String url  = TMDB_SEARCH_URL + "&query=" + movie;
        JSONObject info = fetchJSON(url);

        JSONArray results = info.getJSONArray(TAG_TMDB_RESULTS);

        for (int j = 0; j < results.length(); j++) {
            JSONObject result = results.getJSONObject(j);
            tmdb.description = result.optString(TAG_TMDB_DESCRIPTION);
            tmdb.backgroundUrl = TMDB_BACKDROP_BASE_PATH + result.optString(TAG_TMDB_BACKGROUND_ART);
            tmdb.posterUrl = TMDB_POSTER_BASE_PATH + result.optString(TAG_TMDB_POSTER_ART);

            //leanback wants year only but TMDB returns yyyy-mm-dd
            tmdb.releaseYear = result.optString(TAG_TMDB_RELEASE_DATE).split("-")[0];

            try {
                tmdb.averageVote = Double.valueOf(result.optString(TAG_TMDB_VOTE));
            }
            catch (NullPointerException e) {
                tmdb.averageVote = 5.0;
            }
            //only care about first result
            break;
        }

        return tmdb;
    }

    /**
     * Fetch JSON object from a given URL.
     *
     * @return the JSONObject representation of the response
     * @throws JSONException
     * @throws IOException
     */
    private JSONObject fetchJSON(String urlString) throws JSONException, IOException {
        BufferedReader reader = null;
        java.net.URL url = new java.net.URL(urlString);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(),
                    "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            return new JSONObject(json);
        } finally {
            urlConnection.disconnect();
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "JSON feed closed", e);
                }
            }
        }
    }
}
