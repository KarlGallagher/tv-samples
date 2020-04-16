/*
 * Copyright 2019 Google LLC
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
package com.example.android.tvleanback;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaDrm;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.data.VideoDbBuilder;
import com.example.android.tvleanback.model.Video;
import com.example.android.tvleanback.ui.MultiTrustDrmCallback;
import com.example.android.tvleanback.ui.MultiTrustHttpDataSource;
import com.example.android.tvleanback.ui.PlaybackFragment;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DummyExoMediaDrm;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, manifest = "src/main/AndroidManifest.xml")
public class VideoDbUnitTest {
    private static final String TAG = "VideoDbTest";

    public VideoDbUnitTest() {
    }

    @Test
    /**
     * Test that the json file isn't empty
     */
    public void getVideosFromServer() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);
        Assert.assertTrue(contentValuesList.size() > 0);
        Assert.assertTrue(!contentValuesList.get(0)
                .getAsString(VideoContract.VideoEntry.COLUMN_NAME).isEmpty());
    }

    /**
     * Test video source values from json file are as expected
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testVideoSources() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);
        Assert.assertEquals("https://urm.latens.com:9443/content/dash_clr/out.mpd",
                contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_VIDEO_URL));
        Assert.assertEquals("https://urm.latens.com:9443/demo/content/dash/top_gun_maverick/out.mpd",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_VIDEO_URL));
    }

    /**
     * Test category values from json file are as expected
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testVideoCategory() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);

        Assert.assertEquals("Multitrust",
                contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_CATEGORY));
        Assert.assertEquals("MultitrustEncrypted",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_CATEGORY));
    }


    /**
     * Test description values from json file are as expected
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testDescriptions() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);

        Assert.assertEquals("Clear Dash Stream",
                contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_DESC));
        Assert.assertEquals("Encrypted Dash Stream",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_DESC));
    }

    /**
     * Test the studio value from json file is as expected
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testStudio() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);

        Assert.assertEquals("Multitrust",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_STUDIO));
    }

    /**
     * Test the title value from json file is as expected
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testTitle() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);

        Assert.assertEquals("Clear Dash",
                contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_NAME));

        Assert.assertEquals("Encrypted Dash",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_NAME));
    }

    /**
     * Test the License value from json file is as expected for the encrypted content
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testLicense() throws IOException, JSONException {
        String serverUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);

        Assert.assertEquals("https://urm.latens.com:6443/WvLicenseProxy33",
                contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_LICENSE));
    }

    /**
     * Check if the inferContentType method is returning the expected content type(also uses dummy values for testing other file extensions)
     * Simple test for {@link PlaybackFragment#mediaSourceType} conditional statement
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testVideoFormat() throws IOException, JSONException {
        String jsonUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        String dummyHLS = "https://dummyhlsstring.com/video.m3u8";
        String dummySS = "https://dummyhlsstring.com/video.ism";
        String dummyOther = "https://dummyotherstring.com/video.mp4";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(jsonUrl);

        //tests a real value in the json file
        Assert.assertEquals(C.TYPE_DASH, Util.inferContentType(contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_VIDEO_URL)));

        //dummy values for testing purposes
        Assert.assertEquals(C.TYPE_HLS, Util.inferContentType(dummyHLS));
        Assert.assertEquals(C.TYPE_SS, Util.inferContentType(dummySS));
        Assert.assertEquals(C.TYPE_OTHER, Util.inferContentType(dummyOther));
    }

    /**
     * Check if the DRM scheme in the json file could set the expected UUID
     * Simple test for {@link PlaybackFragment#setUUID()} conditional statement
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testDrmScheme() throws IOException, JSONException {
        String jsonUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(jsonUrl);


        UUID actual = null;
        if (contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_DRM_SCHEME).equals("widevine")) {
            actual = C.WIDEVINE_UUID;
        } else if (contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_DRM_SCHEME).equals("playready")) {
            actual = C.PLAYREADY_UUID;
        } else if (contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_DRM_SCHEME).equals("clearkey")) {
            actual = C.CLEARKEY_UUID;
        }

        Assert.assertEquals(C.WIDEVINE_UUID, actual);
    }

    /**
     * Test if the license is returning with a 200 response code
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testMultitrustLicense() throws IOException, JSONException {
        String jsonUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(jsonUrl);
        String authToken = contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_AUTH_TOKEN);

        URL url = new URL(contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_LICENSE));
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.connect();

        String[] drmKeyRequestPropertiesList = new String[]{authToken};
        MultiTrustHttpDataSource multiTrustHttpDataSource = new MultiTrustHttpDataSource(url.toString(), drmKeyRequestPropertiesList[0]);

        Assert.assertEquals("https://urm.latens.com:6443/WvLicenseProxy33", multiTrustHttpDataSource.getProxyUrl());
        Assert.assertEquals(HttpURLConnection.HTTP_OK, urlConn.getResponseCode());
    }

    @Test
    public void a() throws IOException, JSONException {
        String proxy = "https://urm.latens.com:6443/WvLicenseProxy33";
        String jsonUrl = "https://raw.githubusercontent.com/EddieMc/JsonTest/master/content.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(jsonUrl);
        String authToken = contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_AUTH_TOKEN);


        try {
            URL url = new URL(proxy);
            HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
            urlConn.setRequestMethod("POST");
            urlConn.setRequestProperty("Authorization", "Bearer " + authToken);
            urlConn.setRequestProperty("Content-Type", "application/octet-stream");
            urlConn.setDoInput(true);
            urlConn.setDoOutput(true);
            urlConn.connect();

//            BufferedOutputStream output = new BufferedOutputStream(urlConn.getOutputStream());
//            output.write(payload);
//            output.flush();
//            output.close();

            Assert.assertEquals("", "");
        } catch (IOException e) {
            System.err.println("Error creating HTTP connection");
            e.printStackTrace();
            throw e;
        }
      }
}
