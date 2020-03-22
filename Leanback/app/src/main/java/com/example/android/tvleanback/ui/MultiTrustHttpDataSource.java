package com.example.android.tvleanback.ui;

import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

public class MultiTrustHttpDataSource {

  private static final int DEFAULT_NETWORK_TIMEOUT = 10000;

  private final String proxyUrl;
  private final String authToken;

  public MultiTrustHttpDataSource(String url, String token) {
    proxyUrl = url;
    authToken = token;
  }

  public String getProxyUrl() {
    return proxyUrl;
  }

  //put logs in to show what's happening.
  /**
   * Posts a MultiTrust License Request to Widevine License Proxy
   *
   * @param payload The request body (from Widevine CDM)
   */
  Pair<Integer, byte[]> postRequest(byte[] payload) throws IOException {
    //this might be wrong, test how this works although I'm never using the payload. Does this get set somewhere or should I be making claims?
    //TODO - replace throw with a working implementation

    URL url = new URL(proxyUrl);
    HttpURLConnection con = (HttpURLConnection)url.openConnection();
    setHttpPostHeaders(con);

    //might need to embed it as an asset or download off the internet (Similar to the media.exolist.json)
    //Dynamically have the content list instead of having it hard coded like the exolist in this application.
    //Potential to have it with an already created list (Might have to make my own though)
    //need buffered output stream
    //leanback mode controlling functions with a remote rather than touch screen
    //If as a leanback wont work on a phone.
    //Figure out via the interface how do I make it so
    //WHere does leanback get its content - will have to change the leanback app. WOn't be extensible. Swap out source of data for another
    //Will use exoplayer but need to be configured with the multitrust stuff with toeksn etc
    //Won't be the same token everytime etc from content list.

    //Need to do this myself mostly think methodically etc and present my findings.

    BufferedOutputStream output = new BufferedOutputStream(con.getOutputStream());
    output.write(payload);
    output.flush();
    output.close();

    Integer statusCode = con.getResponseCode();

//    Pair<Integer, byte[]> data; //never used

    try{
        InputStream inputStream = new BufferedInputStream(con.getInputStream());
        return Pair.create(statusCode, convertInputStreamToByteArray(inputStream));
    } catch(IOException e){
//        e.printStackTrace();
        return Pair.create(statusCode, convertInputStreamToByteArray(con.getErrorStream()));

    } finally{
      con.disconnect();
    }
  }

  /**
   * Posts a generic request with an empty body to arbitrary url
   *
   * @param url The request end-point
   */
  Pair<Integer, byte[]> postRequest(String url) throws IOException {
    //Not quite sure what to put in this method. (also need to know if my above implementation is in the right direction)
    //TODO - replace throw with a working implementation
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    setHttpPostHeaders(con);
    con.connect();

    Integer statusCode = con.getResponseCode();

    try{
      InputStream inputStream = new BufferedInputStream(con.getInputStream());
      return Pair.create(statusCode, convertInputStreamToByteArray(inputStream));

    } catch(IOException e){
//      e.printStackTrace();
      return Pair.create(statusCode, convertInputStreamToByteArray(con.getErrorStream()));
    } finally{
      con.disconnect();
    }
  }


  //Private helper functions

  private byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
    byte[] bytes = null;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte data[] = new byte[1024];
    int count;
    while ((count = inputStream.read(data)) != -1) {
      bos.write(data, 0, count);
    }
    bos.flush();
    bos.close();
    inputStream.close();
    bytes = bos.toByteArray();
    return bytes;
  }

  private void setHttpPostHeaders(HttpURLConnection connection) throws ProtocolException {
    connection.setConnectTimeout(DEFAULT_NETWORK_TIMEOUT);
    connection.setRequestMethod("POST");
//    connection.setRequestProperty("Host", "192.168.6.151");
//    connection.setRequestProperty("User-Agent", "curl/7.64.1");
    connection.setRequestProperty("Authorization", "Bearer " + authToken);
    Log.d("AUTH TOKEN MTDS", authToken);
//    String token = "Basic " + Base64.encodeToString(authToken.getBytes(), Base64.DEFAULT);
//    connection.setRequestProperty("Authorization", "Bearer " + token);
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setDoInput(true);
    connection.setDoOutput(true);

  }
}
