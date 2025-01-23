package dev.aviedb.geofenceandroid.utils;

import com.google.android.gms.maps.model.LatLng;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

public class GeoUtils {
  private static final GeometryFactory geometryFactory = new GeometryFactory();

  public static Polygon listLatLngToPolygon(List<LatLng> latLngList) {
    Coordinate[] coordinates = new Coordinate[latLngList.size() + 1];
    for (int i = 0; i < latLngList.size(); i++) {
      LatLng latLng = latLngList.get(i);
      coordinates[i] = new Coordinate(latLng.longitude, latLng.latitude);
    }
    // Ensuring the polygon is closed by adding the first point at the end
    coordinates[latLngList.size()] = coordinates[0];
    return geometryFactory.createPolygon(coordinates);
  }

  public static List<LatLng> GeometryToListLatLng(Geometry geometry) {
    List<LatLng> latLngList = new ArrayList<>();

    if (geometry instanceof Polygon) {
      Polygon polygon = (Polygon) geometry;
      Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();

      for (Coordinate coord : coordinates) {
        latLngList.add(new LatLng(coord.y, coord.x));
      }
    }

    return latLngList;
  }

  public static List<LatLng> PolygonToListLng(Polygon polygon) {
    List<LatLng> latLngList = new ArrayList<>();
    Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();

    for (Coordinate coord : coordinates) {
      latLngList.add(new LatLng(coord.y, coord.x));
    }

    return latLngList;
  }

  public static boolean isCoordInsidePolygon(LatLng coord, List<LatLng> polygon) {
    double x = coord.longitude;
    double y = coord.latitude;
    boolean inside = false;

    for (int i = 0, j = 1; j < polygon.size(); i = j++) {
      double xi = polygon.get(i).longitude;
      double yi = polygon.get(i).latitude;

      double xj = polygon.get(j).longitude;
      double yj = polygon.get(j).latitude;

      boolean intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
      if (intersect) inside = !inside;
    }

    return inside;
  }
}
