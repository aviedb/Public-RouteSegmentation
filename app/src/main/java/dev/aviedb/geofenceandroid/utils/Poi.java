package dev.aviedb.geofenceandroid.utils;

import com.google.android.gms.maps.model.LatLng;

public class Poi {
  public int id;
  public String name;
  public String category;
  public LatLng coordinate;

  public Poi(int id, String name, String category, LatLng coordinate) {
    this.id = id;
    this.name = name;
    this.category = category;
    this.coordinate = coordinate;
  }
}
