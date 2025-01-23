package dev.aviedb.geofenceandroid.utils;

import java.util.ArrayList;

/**
 * Created by Aryo on 2/22/2018.
 */

public class DouglasPeucker {
  private ArrayList<LatLng> points = new ArrayList<>();
  private double toleranceDistance = 200; // meter
  private static final double oneDegreeInMeter = 111319.9; // 1 degree in meter

  public ArrayList<RouteSegmentation.Point> simplify(ArrayList<RouteSegmentation.Point> points, int toleranceDistance) {

    if(toleranceDistance == 0) this.toleranceDistance = 200;
    else this.toleranceDistance = toleranceDistance;

    for (RouteSegmentation.Point point : points)
      this.points.add(new LatLng(point.latitude, point.longitude));

    this.douglasPeucker(this.points);

    ArrayList<RouteSegmentation.Point> simplifiedPoints = new ArrayList<>();
    simplifiedPoints.add(this.points.get(0));
    for (LatLng point :
        this.points) {
      if (point.isKeep())
        simplifiedPoints.add(point);
    }
    simplifiedPoints.add(this.points.get(this.points.size() - 1));
    return simplifiedPoints;

  }


  private void douglasPeucker(ArrayList<LatLng> points) {


    int length = points.size();

    double maxDistance = 0;
    int maxIndex = 0;
    double distanceDeg = toleranceDistance / DouglasPeucker.oneDegreeInMeter;

    for (int i = 1; i < length - 2; i++) {

      LatLng origin = points.get(0);
      LatLng destination = points.get(length - 1);

      double pDistance = perpendicularDistance(points.get(i), origin, destination);
      if (pDistance > maxDistance) {
        maxDistance = pDistance;
        maxIndex = i;
      }

    }

    if (maxDistance > distanceDeg) {
      points.get(maxIndex).keep();
      douglasPeucker(new ArrayList<>(points.subList(0, maxIndex)));
      douglasPeucker(new ArrayList<>(points.subList(maxIndex, length - 1)));
    }

    // return points;
  }

  private double perpendicularDistance(LatLng point, LatLng origin, LatLng destination) {

    // (py – qy)x + (qx – px)y + (pxqy – qxpy) = 0

    double a = origin.latitude - destination.latitude;
    double b = destination.longitude - origin.longitude;
    double c = (origin.longitude * destination.latitude)
        - (destination.longitude * origin.latitude);

    // d = |Am + Bn + C| / sqrt (A^2 + B^2);

    return Math.abs(a * point.longitude + b * point.latitude + c) /
        (Math.sqrt(a * a + b * b));

  }


  private class LatLng extends RouteSegmentation.Point {

    private boolean keep = false;

    private LatLng(double latitude, double longitude) {
      super(latitude, longitude);
    }

    private void keep() {
      this.keep = true;
    }
    private boolean isKeep() {
      return this.keep;
    }

  }
}
