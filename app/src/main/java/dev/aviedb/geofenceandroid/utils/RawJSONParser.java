package dev.aviedb.geofenceandroid.utils;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RawJSONParser {

  private static String readJSON(Context context, int resourceId) {
    StringBuilder jsonString = new StringBuilder();
    try {
      InputStream inputStream = context.getResources().openRawResource(resourceId);
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;
      while ((line = reader.readLine()) != null) {
        jsonString.append(line);
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return jsonString.toString();
  }

  public static List<Poi> parseJSONPoi(Context context, int resourceId) {
    List<Poi> pois = new ArrayList<>();

    try {
      String jsonString = readJSON(context, resourceId);
      JSONArray jsonArray = new JSONArray(jsonString);

      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject jsonObject = jsonArray.getJSONObject(i);
        int id = jsonObject.getInt("id");
        String name = jsonObject.getString("name");
        String category = jsonObject.getString("category");
        double lat = jsonObject.getJSONObject("coordinate").getDouble("latitude");
        double lng = jsonObject.getJSONObject("coordinate").getDouble("longitude");

        Poi poi = new Poi(id, name, category, new LatLng(lat, lng));
        pois.add(poi);
      }

      return pois;
    } catch (JSONException e) {
      e.printStackTrace();
      return pois;
    }
  }
}

