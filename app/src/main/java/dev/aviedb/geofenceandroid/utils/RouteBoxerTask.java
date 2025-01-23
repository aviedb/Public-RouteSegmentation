package dev.aviedb.geofenceandroid.utils;

import android.graphics.Color;
import android.os.AsyncTask;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class RouteBoxerTask extends AsyncTask<Void, RouteBoxerTask.RouteBoxerData, ArrayList<RouteBoxer.Box>>
    implements RouteBoxer.IRouteBoxer {

  private final ArrayList<LatLng> route = new ArrayList<>();
  private final int distance;
  private final IRouteBoxerTask iRouteBoxerTask;
  private int step;
  private Boolean simplify = false;
  private Boolean runBoth = false;

  public RouteBoxerTask(ArrayList<LatLng> route, int distance, IRouteBoxerTask iRouteBoxerTask) {
    for (LatLng point:
        route) {
      LatLng latLng = new LatLng(point.latitude, point.longitude);
      this.route.add(latLng);
    }
    this.distance = distance;
    this.iRouteBoxerTask = iRouteBoxerTask;
  }

  public RouteBoxerTask(ArrayList<LatLng> route, int distance, boolean simplifyRoute, boolean runBoth, IRouteBoxerTask iRouteBoxerTask) {
    this(route, distance, iRouteBoxerTask);
    this.simplify = simplifyRoute;
    this.runBoth = runBoth;
        /*
        for (LatLng point:
                route) {
            LatLng latLng = new LatLng(point.latitude, point.longitude);
            this.route.add(latLng);
        }
        this.distance = distance;
        this.simplify = simplifyRoute;
        this.iRouteBoxerTask = iRouteBoxerTask;
        */
  }

  @Override
  protected ArrayList<RouteBoxer.Box> doInBackground(Void... params) {
    RouteBoxer routeBoxer = new RouteBoxer(route, distance, this.simplify, this.runBoth);
    routeBoxer.setRouteBoxerInterface(this);
    return routeBoxer.box();
  }

  @Override
  protected void onPostExecute(ArrayList<RouteBoxer.Box> boxes) {
    if(this.iRouteBoxerTask != null)
      this.iRouteBoxerTask.onRouteBoxerTaskComplete(boxes);
  }

  @Override
  public void onBoxesObtained(ArrayList<RouteBoxer.Box> boxes) {}

  @Override
  public void onBoundsObtained(RouteBoxer.LatLngBounds bounds) {

  }

  @Override
  public void onGridOverlaid(ArrayList<RouteBoxer.Box> boxes) {}

  @Override
  public void onGridObtained(RouteBoxer.Box[][] boxArray) {}

  @Override
  public void onGridMarked(ArrayList<RouteBoxer.Box> boxes) {
    RouteBoxerData data = new RouteBoxerData(boxes, Color.LTGRAY, Color.GRAY, Color.argb(128, 77, 129, 214));
    int mark = 0, simpleMark = 0, bothMark = 0, expandMark = 0;
    for(RouteBoxer.Box box : boxes) {
      if (box.marked) mark++;
      if (box.simpleMarked) simpleMark++;
      if (box.marked && box.simpleMarked) bothMark++;
      if (box.expandMarked) expandMark++;
    }
//    Log.d("RouteBoxer", "Marked: " + mark + ", SimpleMark: " + simpleMark + ", OverlapMark: " + bothMark + ", expandMark: " + expandMark );
    this.publishProgress(data);
  }

  @Override
  public void onGridMarksExpanded(RouteBoxer.Box[][] boxArray) {}

  @Override
  public void onMergedAdjointVertically(ArrayList<RouteBoxer.Box> boxes) {}

  @Override
  public void onMergedAdjointHorizontally(ArrayList<RouteBoxer.Box> boxes) {}

  @Override
  public void onMergedVertically(ArrayList<RouteBoxer.Box> mergedBoxes) {}

  @Override
  public void onMergedHorizontally(ArrayList<RouteBoxer.Box> mergedBoxes) {}

  @Override
  public void onProcess(String processInfo) {
    RouteBoxerData data = new RouteBoxerData(processInfo);
    publishProgress(data);
  }

  @Override
  public void onRouteSimplified(ArrayList<LatLng> simplifiedRoute) {
    RouteBoxerData data = new RouteBoxerData(simplifiedRoute, Color.argb(128, 0, 0, 200));
    publishProgress(data);
  }

  @Override
  public void drawLine(LatLng origin, LatLng destination, int color) {}

  @Override
  public void drawBox(LatLng origin, LatLng destination, int color) {}

  @Override
  public void clearPolygon() {}

  @Override
  protected void onProgressUpdate(RouteBoxerData... values) {
    if(this.iRouteBoxerTask != null) {
      RouteBoxerData data = values[0];

      if (data.type == DataType.Message) {
        if (data.message != null)
          this.iRouteBoxerTask.onRouteBoxerMessage(data.message);
      } else if(data.type == DataType.Grid) {
        if(data.boxes != null) {
          this.iRouteBoxerTask.onRouteBoxerGrid(data.boxes, data.lineColor, data.markedColor, data.simpleMarkedColor);
        }
      } else if(data.type == DataType.Boxes){
        if(data.boxes != null)
          this.iRouteBoxerTask.onRouteBoxerBoxes(data.boxes, data.boxBorderColor, data.boxFillColor);
      } else if(data.type == DataType.Route) {
        if(data.simplifiedRoute != null) {
          ArrayList<LatLng> route = new ArrayList<>();
          for (LatLng point: data.simplifiedRoute) {
            route.add(new LatLng(point.latitude, point.longitude));
          }
          this.iRouteBoxerTask.onRouteBoxerSimplifiedRoute(route, data.lineColor);
        }
      }

    }
  }

  public class RouteBoxerData {



    private DataType type = DataType.Message;
    private ArrayList<RouteBoxer.Box> boxes;
    private String message;
    private int boxBorderColor = Color.DKGRAY;
    private int boxFillColor = Color.GRAY;
    private int lineColor = Color.BLUE;
    private int markedColor;
    private int simpleMarkedColor;
    private ArrayList<LatLng> simplifiedRoute;

    private RouteBoxerData(String message) {
      this.message = message;
    }

    private RouteBoxerData(ArrayList<RouteBoxer.Box> boxes, int boxBorderColor, int boxFillColor) {
      this.boxes = boxes;
      this.type = DataType.Boxes;
      this.boxBorderColor = boxBorderColor;
      this.boxFillColor = boxFillColor;
    }

    public RouteBoxerData(ArrayList<LatLng> simplifiedRoute, int lineColor) {
      this.type = DataType.Route;
      this.simplifiedRoute = simplifiedRoute;
      this.lineColor = lineColor;
    }

    public RouteBoxerData(ArrayList<RouteBoxer.Box> boxes, int lineColor, int markedColor, int simpleMarkedColor) {
      this.type = DataType.Grid;
      this.boxes = boxes;
      this.lineColor = lineColor;
      this.markedColor = markedColor;
      this.simpleMarkedColor = simpleMarkedColor;
    }
  }

  private enum DataType {
    Message, Boxes, Route, Grid
  }

  public interface IRouteBoxerTask {

    void onRouteBoxerTaskComplete(ArrayList<RouteBoxer.Box> boxes);
    void onRouteBoxerMessage(String message);
    void onRouteBoxerGrid(ArrayList<RouteBoxer.Box> boxes, int boxBorderColor, int markedColor, int simpleMarkedColor);
    void onRouteBoxerBoxes(ArrayList<RouteBoxer.Box> boxes, int boxBorderColor, int boxFillColor);
    void onRouteBoxerSimplifiedRoute(ArrayList<LatLng> simplifiedRoute, int lineColor);

  }
}