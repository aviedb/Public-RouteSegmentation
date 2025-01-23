package dev.aviedb.geofenceandroid.utils;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class PolylineDecoder {

  private final JSONObject jObject;
  private static final DecimalFormat df = new DecimalFormat("0.00");

  public PolylineDecoder(JSONObject jsonObject) {
    this.jObject = jsonObject;
  }

  public String getDistanceTotal() {
    // in meters
    double distance = 0;

    try {
      JSONObject d = this.jObject
          .getJSONArray("routes")
          .getJSONObject(0)
          .getJSONArray("legs")
          .getJSONObject(0)
          .getJSONObject("distance");

      distance = d.getDouble("value");
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return df.format(distance);
  }

  public String getDistanceStraight() {
    // in meters
    double distance = 0;

    try {
      JSONObject legs = this.jObject
          .getJSONArray("routes")
          .getJSONObject(0)
          .getJSONArray("legs")
          .getJSONObject(0);

      JSONObject sl = legs.getJSONObject("start_location");
      JSONObject el = legs.getJSONObject("end_location");

      LatLng start = new LatLng(sl.getDouble("lat"), sl.getDouble("lng"));
      LatLng end = new LatLng(el.getDouble("lat"), el.getDouble("lng"));

      distance = calculateDistance(start, end);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return df.format(distance);
  }

  public List<LatLng> getBounds() {
    List<LatLng> bounds = new ArrayList<>();

    try {
      JSONObject b = this.jObject
          .getJSONArray("routes")
          .getJSONObject(0)
          .getJSONObject("bounds");

      JSONObject ne = b.getJSONObject("northeast");
      JSONObject sw = b.getJSONObject("southwest");

      bounds.add(new LatLng(ne.getDouble("lat"), ne.getDouble("lng")));
      bounds.add(new LatLng(sw.getDouble("lat"), sw.getDouble("lng")));
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return bounds;
  }

  public List<LatLng> getOverviewPolyline() {
    List<LatLng> polyline = new ArrayList<>();

    try {
      JSONObject overview_polyline = this.jObject
          .getJSONArray("routes")
          .getJSONObject(0)
          .getJSONObject("overview_polyline");

      String str = overview_polyline.getString("points");
      polyline = decodePoly(str);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return polyline;
  }

  public List<LatLng> getDetailedPolyline() {
    List<LatLng> polyline = new ArrayList<>();
    try {
      JSONArray legs = this.jObject
          .getJSONArray("routes")
          .getJSONObject(0)
          .getJSONArray("legs");

      for (int i = 0; i < legs.length(); i++) {
        JSONArray steps = legs
            .getJSONObject(i)
            .getJSONArray("steps");

        for (int j = 0; j < steps.length(); j++) {
          String str = steps
              .getJSONObject(j)
              .getJSONObject("polyline")
              .getString("points");

          List<LatLng> p = decodePoly(str);
          polyline.addAll(p);
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return polyline;
  }

  private List<LatLng> decodePoly(String encoded) {
    List<LatLng> poly = new ArrayList<>();
    int index = 0, len = encoded.length();
    int lat = 0, lng = 0;

    while (index < len) {
      int b, shift = 0, result = 0;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lat += dlat;

      shift = 0;
      result = 0;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lng += dlng;

      LatLng p = new LatLng((((double) lat / 1E5)),
          (((double) lng / 1E5)));
      poly.add(p);
    }
    return poly;
  }

  private double calculateDistance(LatLng start, LatLng end) {
    final double R = 6371008.8; // Radius of the Earth in meters
    final double lat1 = start.latitude;
    final double lat2 = end.latitude;
    final double lon1 = start.longitude;
    final double lon2 = end.longitude;

    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
  }
}
