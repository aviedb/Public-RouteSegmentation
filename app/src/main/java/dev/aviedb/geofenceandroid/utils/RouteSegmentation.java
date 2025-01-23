package dev.aviedb.geofenceandroid.utils;

import com.google.android.gms.maps.model.LatLng;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

public class RouteSegmentation {
  private final List<Point> POLYLINE;
  private final double SEGMENT_WIDTH; // meters
  private final int CIRCLE_STEPS = 16; // number of sides in circle
  private final double EARTH_RADIUS = 6371008.8; // meters
  private final GeometryFactory geometryFactory = new GeometryFactory();

  public RouteSegmentation(List<Point> polyline, int width) {
    this.POLYLINE = polyline;
    this.SEGMENT_WIDTH = (double) width;
  }

  public List<Point> createRoutePolygon() {
    // Initialize the main polygon with a circle from the first point
    Geometry polygon = createCirclePolygon(this.POLYLINE.get(0));

    for (int i = 0, j = 1; j < this.POLYLINE.size(); i = j++) {
      // creating a segment polygon, a combination of block and semi-circle
      if (this.POLYLINE.get(i).latitude == this.POLYLINE.get(j).latitude
          && this.POLYLINE.get(i).longitude == this.POLYLINE.get(j).longitude)
        continue;

      Polygon segmentPolygon = createSegmentPolygon(this.POLYLINE.get(i), this.POLYLINE.get(j));

      // unify with the main polygon
      polygon = polygon.union(segmentPolygon);
    }

    return GeoUtils.GeometryToListPoint(polygon);
  }

  private Polygon createCirclePolygon(Point center) {
    Coordinate[] coordinates = new Coordinate[this.CIRCLE_STEPS + 1];

    for (int i = 0; i < this.CIRCLE_STEPS; i++) {
      double angle = 2 * Math.PI * i / this.CIRCLE_STEPS;
      double lat1 = degreesToRadians(center.latitude);
      double lon1 = degreesToRadians(center.longitude);

      double lat2 = Math.asin(
          Math.sin(lat1) * Math.cos(this.SEGMENT_WIDTH / this.EARTH_RADIUS) +
              Math.cos(lat1) * Math.sin(this.SEGMENT_WIDTH / this.EARTH_RADIUS) * Math.cos(angle)
      );

      double lon2 = lon1 + Math.atan2(
          Math.sin(angle) * Math.sin(this.SEGMENT_WIDTH / this.EARTH_RADIUS) * Math.cos(lat1),
          Math.cos(this.SEGMENT_WIDTH / this.EARTH_RADIUS) - Math.sin(lat1) * Math.sin(lat2)
      );

      coordinates[i] = new Coordinate(radiansToDegrees(lon2), radiansToDegrees(lat2));
    }

    // last coord == first coord, to close the polygon
    coordinates[coordinates.length-1] = coordinates[0];

    return this.geometryFactory.createPolygon(coordinates);
  }

  private Polygon createSegmentPolygon(Point origin, Point dest) {
    Coordinate[] leftOffset = lineOffset(origin, dest, this.SEGMENT_WIDTH);
    Coordinate[] rightOffset = lineOffset(origin, dest, -this.SEGMENT_WIDTH);
    Coordinate[] semiCircle = bearingSemiCircle(dest, leftOffset[1], rightOffset[1]);

    // the coordinates, respectively, starts from left offset, then include all the semi circle
    // and through the right offset (reversely) and finish with the same point as the start
    Coordinate[] result = new Coordinate[semiCircle.length + 5];
    result[0] = leftOffset[0];
    result[1] = leftOffset[1];
    System.arraycopy(semiCircle, 0, result, 2, semiCircle.length);
    result[semiCircle.length + 2] = rightOffset[1];
    result[semiCircle.length + 3] = rightOffset[0];
    result[semiCircle.length + 4] = leftOffset[0];

    return this.geometryFactory.createPolygon(result);
  }

  private Coordinate[] lineOffset(Point origin, Point dest, double distance) {
    double offsetDegrees = radiansToDegrees(distanceToRadians(distance));

    // Thank you!! https://stackoverflow.com/a/2825673
    double L = Math.sqrt(
        Math.pow(origin.latitude - dest.latitude, 2) +
            Math.pow(origin.longitude - dest.longitude, 2)
    );
    double out1lat = origin.latitude + (offsetDegrees * (dest.longitude - origin.longitude)) / L;
    double out2lat = dest.latitude + (offsetDegrees * (dest.longitude - origin.longitude)) / L;
    double out1lng = origin.longitude + (offsetDegrees * (origin.latitude - dest.latitude)) / L;
    double out2lng = dest.longitude + (offsetDegrees * (origin.latitude - dest.latitude)) / L;

    return new Coordinate[]{
        new Coordinate(out1lng, out1lat), // new origin coord
        new Coordinate(out2lng, out2lat) // new dest coord
    };
  }

  private Coordinate[] bearingSemiCircle(Point mid, Coordinate start, Coordinate end) {
    // semi circle is half the circle - 2, because the 2 points will be the same as the start and end point
    final int SEMICIRCLE_STEPS = this.CIRCLE_STEPS/2 - 2;
    Coordinate[] coordinates = new Coordinate[SEMICIRCLE_STEPS];

    double M_lat = degreesToRadians(mid.latitude);
    double M_lon = degreesToRadians(mid.longitude);
    double A_lat = degreesToRadians(start.getY());
    double A_lon = degreesToRadians(start.getX());
    double B_lat = degreesToRadians(end.getY());
    double B_lon = degreesToRadians(end.getX());

    // Calculate bearing
    double y = Math.sin(B_lon - A_lon) * Math.cos(B_lat);
    double x = Math.cos(A_lat) * Math.sin(B_lat) -
        Math.sin(A_lat) * Math.cos(B_lat) * Math.cos(B_lon - A_lon);
    double bearing = Math.atan2(y, x);
    bearing = Math.toDegrees(bearing);
    bearing = (bearing + 180) % 360; // Normalize to 0-360

    // n points create n+1 segments between A and B
    double angleIncrement = 180.0 / (SEMICIRCLE_STEPS+1);

    // Calculate initial bearing from M to B
    double initialBearing = Math.toRadians(bearing);

    // Generate each of the semicircle intermediate points
    for (int i = 0; i < SEMICIRCLE_STEPS; i++) {
      double adjustedBearing = initialBearing + Math.toRadians((i+1) * angleIncrement);
      adjustedBearing = adjustedBearing % (2 * Math.PI); // Normalize the bearing

      double angularDistance = this.SEGMENT_WIDTH / this.EARTH_RADIUS;

      double newLat = Math.asin(Math.sin(M_lat) * Math.cos(angularDistance) +
          Math.cos(M_lat) * Math.sin(angularDistance) * Math.cos(adjustedBearing));
      double newLon = M_lon + Math.atan2(Math.sin(adjustedBearing) * Math.sin(angularDistance) * Math.cos(M_lat),
          Math.cos(angularDistance) - Math.sin(M_lat) * Math.sin(newLat));

      coordinates[i] = new Coordinate(radiansToDegrees(newLon), radiansToDegrees((newLat)));
    }

    return coordinates;
  }

  private double degreesToRadians(double degrees) {
    return degrees * Math.PI / 180;
  }

  private double radiansToDegrees(double radians) {
    return radians * 180 / Math.PI;
  }

  private double distanceToRadians(double distance) {
    return distance / EARTH_RADIUS;
  }

  public static class Point {
    public double latitude, longitude;
    public Point(double latitude, double longitude) {
      this.latitude = latitude;
      this.longitude = longitude;
    }
  }
}