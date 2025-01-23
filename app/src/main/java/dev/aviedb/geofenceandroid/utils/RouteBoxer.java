package dev.aviedb.geofenceandroid.utils;

import java.util.ArrayList;

public class RouteBoxer {

  private ArrayList<RouteSegmentation.Point> route;
  private int distance;
  private double degree;
  private Boolean simplify = false;
  private Boolean runBoth = false;
  private LatLngBounds bounds;
  private RouteBoxer.IRouteBoxer iRouteBoxer;
  private ArrayList<RouteBoxer.Box> boxes = new ArrayList<>();
  private ArrayList<RouteBoxer.Box> routeBoxesH;
  private ArrayList<RouteBoxer.Box> routeBoxesV;

  public RouteBoxer(ArrayList<RouteSegmentation.Point> route, int distance) {
    this.route = route;
    this.distance = distance;
    this.degree = this.distance / 1.1132 * 0.00001;
  }

  public RouteBoxer(ArrayList<RouteSegmentation.Point> route, int distance, Boolean simplify, Boolean runBoth) {
    this(route, distance);
    this.simplify = simplify;
    this.runBoth = runBoth;
  }

  public void setRouteBoxerInterface(RouteBoxer.IRouteBoxer iRouteBoxer) {
    this.iRouteBoxer = iRouteBoxer;
  }

  public static ArrayList<RouteBoxer.Box> box(ArrayList<RouteSegmentation.Point> path, int distance) {
    RouteBoxer routeBoxer = new RouteBoxer(path, distance);
    return routeBoxer.box();
  }

  public static ArrayList<RouteBoxer.Box> box(ArrayList<RouteSegmentation.Point> path, int distance, RouteBoxer.IRouteBoxer iRouteBoxer) {
    RouteBoxer routeBoxer = new RouteBoxer(path, distance);
    routeBoxer.setRouteBoxerInterface(iRouteBoxer);
    return routeBoxer.box();
  }

  public ArrayList<RouteBoxer.Box> box() {

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Initializing...");

    // Getting bounds

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Calculating bounds...");

    this.bounds = new LatLngBounds();
    //LatLngBounds.Builder builder = LatLngBounds.builder();
    for (RouteSegmentation.Point point : this.route) {
      this.bounds.include(point);
    }

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Expanding bounds...");

    // Expanding bounds

    this.bounds.build();
    RouteSegmentation.Point southwest = new RouteSegmentation.Point(this.bounds.southwest.latitude - this.degree,
        this.bounds.southwest.longitude - this.degree);
    RouteSegmentation.Point northeast = new RouteSegmentation.Point(this.bounds.northeast.latitude + this.degree,
        this.bounds.northeast.longitude + this.degree);
    this.bounds = this.bounds.include(southwest).include(northeast).build();

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Bounds obtained...");
      this.iRouteBoxer.onBoundsObtained(this.bounds);
    }

    // Laying out grids

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Overlaying grid...");

    RouteSegmentation.Point sw = this.bounds.southwest;
    RouteSegmentation.Point ne = new RouteSegmentation.Point(sw.latitude + this.degree, sw.longitude + this.degree);
    int x = 0, y = 0;
    RouteBoxer.Box gridBox;

    do {

      do {
        gridBox = new RouteBoxer.Box(sw, ne, x, y);
        this.boxes.add(gridBox); //box.draw(mMap, Color.BLUE);
        sw = new RouteSegmentation.Point(sw.latitude, ne.longitude);
        ne = new RouteSegmentation.Point(sw.latitude + degree, sw.longitude + degree);
        x++;
      } while (gridBox.ne.longitude < this.bounds.northeast.longitude);

      if (gridBox.ne.latitude < this.bounds.northeast.latitude) {
        x = 0;
        sw = new RouteSegmentation.Point(sw.latitude + degree, this.bounds.southwest.longitude);
        ne = new RouteSegmentation.Point(sw.latitude + degree, sw.longitude + degree);
      }
      y++;

    } while (gridBox.ne.latitude < this.bounds.northeast.latitude);


    // Center the grids
    // and converts to 2-D array

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onGridOverlaid(this.boxes);
      this.iRouteBoxer.onProcess("Aligning grid...");
    }

    double latDif = this.boxes.get(this.boxes.size() - 1).ne.latitude - this.bounds.northeast.latitude;
    double lngDif = this.boxes.get(this.boxes.size() - 1).ne.longitude - this.bounds.northeast.longitude;

    RouteBoxer.Box alignedBoxes[][] = new RouteBoxer.Box[x][y];
    for (RouteBoxer.Box bx : this.boxes) {
      bx.sw = new RouteSegmentation.Point(bx.sw.latitude - (latDif / 2), bx.sw.longitude - (lngDif / 2));
      bx.ne = new RouteSegmentation.Point(bx.ne.latitude - (latDif / 2), bx.ne.longitude - (lngDif / 2));
      bx.updateNWSE();
      alignedBoxes[bx.x][bx.y] = bx;
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onGridObtained(alignedBoxes);
      this.iRouteBoxer.onProcess("Traversing...");
    }

    // step 2: Traverse all points and mark grid which contains it.
    if(!this.runBoth) {
      if (!this.simplify)
        alignedBoxes = this.traversePointsAndMarkGrids(alignedBoxes, this.route);
      else {
        ArrayList<RouteSegmentation.Point> complexRoute = new ArrayList<>();
        for (RouteSegmentation.Point latLng : this.route) {
          complexRoute.add(new RouteSegmentation.Point(latLng.latitude, latLng.longitude));
        }
        ArrayList<RouteSegmentation.Point> simplifiedRoute = new DouglasPeucker().simplify(complexRoute, this.distance);
        ArrayList<RouteSegmentation.Point> newSimplifiedRoute = new ArrayList<>();
        for (RouteSegmentation.Point point : simplifiedRoute)
          newSimplifiedRoute.add(new RouteSegmentation.Point(point.latitude, point.longitude));
        if (this.iRouteBoxer != null)
          this.iRouteBoxer.onRouteSimplified(newSimplifiedRoute);
        alignedBoxes = this.traversePointsAndMarkGrids(alignedBoxes, newSimplifiedRoute);
      }
    } else {
      // run both
      ArrayList<RouteSegmentation.Point> complexRoute = new ArrayList<>();
      for (RouteSegmentation.Point latLng : this.route) {
        complexRoute.add(new RouteSegmentation.Point(latLng.latitude, latLng.longitude));
      }
      ArrayList<RouteSegmentation.Point> simplifiedRoute = new DouglasPeucker().simplify(complexRoute, this.distance);
      ArrayList<RouteSegmentation.Point> newSimplifiedRoute = new ArrayList<>();
      for (RouteSegmentation.Point point : simplifiedRoute)
        newSimplifiedRoute.add(new RouteSegmentation.Point(point.latitude, point.longitude));
      if (this.iRouteBoxer != null)
        this.iRouteBoxer.onRouteSimplified(newSimplifiedRoute);
      alignedBoxes = this.traversePointsAndMarkGridsBothRoute(alignedBoxes, this.route, newSimplifiedRoute);

    }

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Expanding cell marks...");

    // step 3: Expand marked cells
    alignedBoxes = this.expandMarks(alignedBoxes);

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Duplicating array...");

    int length = alignedBoxes.length;
    RouteBoxer.Box[][] boxArrayCopy = new RouteBoxer.Box[length][alignedBoxes[0].length];
    for (int i = 0; i < length; i++) {
      System.arraycopy(alignedBoxes[i], 0, boxArrayCopy[i], 0, alignedBoxes[i].length);
    }

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Merging horizontally...");
    // step 4: Merge cells and generate boxes
    // 1st Approach: merge cells horizontally
    this.horizontalMerge(x, y, alignedBoxes);

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Merging vertically...");
    // 2nd Approach: Merge cells vertically
    this.verticalMerge(x, y, boxArrayCopy);

    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onProcess("Obtaining results...");
    // Step 5: return boxes with the least count from both approach
    ArrayList<RouteBoxer.Box> boxes = (this.routeBoxesV.size() >= routeBoxesH.size()) ? this.routeBoxesH : this.routeBoxesV;
    if(this.iRouteBoxer != null)
      this.iRouteBoxer.onBoxesObtained(boxes);

    return boxes;

  }

  private RouteBoxer.Box[][] traversePointsAndMarkGrids(RouteBoxer.Box[][] boxArray,
                                                        ArrayList<RouteSegmentation.Point> route) {
    int sizeX = boxArray.length;
    int sizeY = boxArray[0].length;

    int i=0;
    RouteSegmentation.Point origin = null, destination;
    //ArrayList<Line> lines = new ArrayList<>();

    for (RouteSegmentation.Point point : route) {

      Line l = null;
      double ay1 = 0, ay2 = 0, ax2 = 0, ax1 = 0;

      if (i == 0) {
        origin = point;
      } else {
        destination = point;
        // finding line bounding box
        l = new Line(origin, destination);
        //lines.add(l);
        origin = destination;
      }

      if (l != null) {
        ay1 = Math.abs((l.origin.latitude > l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
        ay2 = Math.abs((l.origin.latitude < l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
        ax2 = Math.abs((l.origin.longitude > l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
        ax1 = Math.abs((l.origin.longitude < l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
      }

      i++;

      for (int x = 0; x < sizeX; x++) {
        for (int y = 0; y < sizeY; y++) {
          RouteBoxer.Box bx = boxArray[x][y];
          if (bx.marked) continue;
          if (point.latitude > bx.sw.latitude
              && point.latitude < bx.ne.latitude
              && point.longitude > bx.sw.longitude
              && point.longitude < bx.ne.longitude) {
            bx.mark();
                        /* // previous algorithm to mark boxes in between marked boxes
                        if (lastBox == null)
                            lastBox = bx;
                        else {

                            int lastX = lastBox.x;
                            int lastY = lastBox.y;
                            int diffX = bx.x - lastX;
                            int diffY = bx.y - lastY;
                            if(diffX < 1) {
                                for(int dx = bx.x - diffX - 1; dx > bx.x; dx--)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffX > 1) {
                                for(int dx = bx.x - diffX + 1; dx < bx.x; dx++)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffY < 1){
                                for(int dy = bx.y - diffY - 1; dy > bx.y; dy--)
                                    boxArray[lastBox.x][dy].mark();
                            }
                            if(diffY > 1) {
                                for(int dy = bx.y - diffY + 1; dy < bx.y; dy++)
                                    boxArray[lastBox.x][dy].mark();
                            }

                            lastBox = bx;
                        }
                        */
          }

          // if a line exists

          if (l != null) {
            // find box bounds
            double bx2 = Math.abs(bx.se.longitude);
            double bx1 = Math.abs(bx.nw.longitude);
            double by2 = Math.abs(bx.se.latitude);
            double by1 = Math.abs(bx.nw.latitude);

            // if box of line and grid box intersect
            if (!(ax1 <= bx2 && ax2 >= bx1 && ay1 <= by2 && ay2 >= by1)) continue;

            // if the line intersect with current box, mark it!
            if (l.intersect(bx.sw, bx.ne)) bx.mark();
          }

        }
      }
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Traversing and marking complete...");
      this.iRouteBoxer.onGridMarked(this.boxes);
    }

    return boxArray;

  }

  private RouteBoxer.Box[][] traversePointsAndMarkGridsBothRoute(RouteBoxer.Box[][] boxArray,
                                                                 ArrayList<RouteSegmentation.Point> route,
                                                                 ArrayList<RouteSegmentation.Point> simplifiedRoute) {
    int sizeX = boxArray.length;
    int sizeY = boxArray[0].length;

    int i=0;
    RouteSegmentation.Point origin = null, destination;
    //ArrayList<Line> lines = new ArrayList<>();

    for (RouteSegmentation.Point point : route) {

      Line l = null;
      double ay1 = 0, ay2 = 0, ax2 = 0, ax1 = 0;

      if (i == 0) {
        origin = point;
      } else {
        destination = point;
        // finding line bounding box
        l = new Line(origin, destination);
        //lines.add(l);
        origin = destination;
      }

      if (l != null) {
        ay1 = Math.abs((l.origin.latitude > l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
        ay2 = Math.abs((l.origin.latitude < l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
        ax2 = Math.abs((l.origin.longitude > l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
        ax1 = Math.abs((l.origin.longitude < l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
      }

      i++;

      for (int x = 0; x < sizeX; x++) {
        for (int y = 0; y < sizeY; y++) {
          RouteBoxer.Box bx = boxArray[x][y];
          if (bx.marked) continue;
          if (point.latitude > bx.sw.latitude
              && point.latitude < bx.ne.latitude
              && point.longitude > bx.sw.longitude
              && point.longitude < bx.ne.longitude) {
            bx.mark();
                        /* // previous algorithm to mark boxes in between marked boxes
                        if (lastBox == null)
                            lastBox = bx;
                        else {

                            int lastX = lastBox.x;
                            int lastY = lastBox.y;
                            int diffX = bx.x - lastX;
                            int diffY = bx.y - lastY;
                            if(diffX < 1) {
                                for(int dx = bx.x - diffX - 1; dx > bx.x; dx--)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffX > 1) {
                                for(int dx = bx.x - diffX + 1; dx < bx.x; dx++)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffY < 1){
                                for(int dy = bx.y - diffY - 1; dy > bx.y; dy--)
                                    boxArray[lastBox.x][dy].mark();
                            }
                            if(diffY > 1) {
                                for(int dy = bx.y - diffY + 1; dy < bx.y; dy++)
                                    boxArray[lastBox.x][dy].mark();
                            }

                            lastBox = bx;
                        }
                        */
          }

          // if a line exists
          if (l != null) {
            // find box bounds
            double bx2 = Math.abs(bx.se.longitude);
            double bx1 = Math.abs(bx.nw.longitude);
            double by2 = Math.abs(bx.se.latitude);
            double by1 = Math.abs(bx.nw.latitude);

            // if box of line and grid box intersect
            if (!(ax1 <= bx2 && ax2 >= bx1 && ay1 <= by2 && ay2 >= by1)) continue;

            // if the line intersect with current box, mark it!
            if (l.intersect(bx.sw, bx.ne)) bx.mark();
          }
        }
      }
    }


    if(simplifiedRoute != null) {
      i = 0;

      for (RouteSegmentation.Point point : simplifiedRoute) {

        Line l = null;
        double ay1 = 0, ay2 = 0, ax2 = 0, ax1 = 0;

        if (i == 0) {
          origin = point;
        } else {
          destination = point;
          // finding line bounding box
          l = new Line(origin, destination);
          //lines.add(l);
          origin = destination;
        }

        if (l != null) {
          ay1 = Math.abs((l.origin.latitude > l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
          ay2 = Math.abs((l.origin.latitude < l.destination.latitude) ? l.origin.latitude : l.destination.latitude);
          ax2 = Math.abs((l.origin.longitude > l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
          ax1 = Math.abs((l.origin.longitude < l.destination.longitude) ? l.origin.longitude : l.destination.longitude);
        }

        i++;

        for (int x = 0; x < sizeX; x++) {
          for (int y = 0; y < sizeY; y++) {
            RouteBoxer.Box bx = boxArray[x][y];
            if (bx.simpleMarked) continue;
            if (point.latitude > bx.sw.latitude
                && point.latitude < bx.ne.latitude
                && point.longitude > bx.sw.longitude
                && point.longitude < bx.ne.longitude) {
              bx.simpleMark();
                        /* // previous algorithm to mark boxes in between marked boxes
                        if (lastBox == null)
                            lastBox = bx;
                        else {

                            int lastX = lastBox.x;
                            int lastY = lastBox.y;
                            int diffX = bx.x - lastX;
                            int diffY = bx.y - lastY;
                            if(diffX < 1) {
                                for(int dx = bx.x - diffX - 1; dx > bx.x; dx--)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffX > 1) {
                                for(int dx = bx.x - diffX + 1; dx < bx.x; dx++)
                                    boxArray[dx][lastBox.y].mark();
                            }
                            if(diffY < 1){
                                for(int dy = bx.y - diffY - 1; dy > bx.y; dy--)
                                    boxArray[lastBox.x][dy].mark();
                            }
                            if(diffY > 1) {
                                for(int dy = bx.y - diffY + 1; dy < bx.y; dy++)
                                    boxArray[lastBox.x][dy].mark();
                            }

                            lastBox = bx;
                        }
                        */
            }

            // if a line exists
            if (l != null) {
              // find box bounds
              double bx2 = Math.abs(bx.se.longitude);
              double bx1 = Math.abs(bx.nw.longitude);
              double by2 = Math.abs(bx.se.latitude);
              double by1 = Math.abs(bx.nw.latitude);

              // if box of line and grid box intersect
              if (!(ax1 <= bx2 && ax2 >= bx1 && ay1 <= by2 && ay2 >= by1)) continue;

              // if the line intersect with current box, mark it!
              if (l.intersect(bx.sw, bx.ne)) bx.simpleMark();
            }
          }
        }
      }
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Traversing and marking complete...");
      this.iRouteBoxer.onGridMarked(this.boxes);
    }

    return boxArray;

  }

  private RouteBoxer.Box[][] expandMarks(RouteBoxer.Box[][] boxArray) {
    for(RouteBoxer.Box b:this.boxes) {
      if(b.marked) {

        // Mark all surrounding cells
        boxArray[b.x-1][b.y-1].expandMark();    //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x - 1][b.y].expandMark();      //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x - 1][b.y + 1].expandMark();    //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x][b.y+1].expandMark();      //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x+1][b.y+1].expandMark();    //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x+1][b.y].expandMark();      //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x + 1][b.y - 1].expandMark();    //.redraw(mMap, Color.BLACK, Color.GREEN);
        boxArray[b.x][b.y - 1].expandMark();      //.redraw(mMap, Color.BLACK, Color.GREEN);
      }
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Cell marks expanded");
      this.iRouteBoxer.onGridMarksExpanded(boxArray);
    }

    return boxArray;
  }

  private void verticalMerge(int x, int y, RouteBoxer.Box[][] boxArray) {
    ArrayList<RouteBoxer.Box> mergedBoxes = new ArrayList<>();
    RouteBoxer.Box vBox = null;
    for(int cx = 0; cx < x; cx++) {
      for (int cy = 0; cy < y; cy++) {
        RouteBoxer.Box b = new RouteBoxer.Box(boxArray[cx][cy].sw, boxArray[cx][cy].ne, cx, cy);
        if(boxArray[cx][cy].marked || boxArray[cx][cy].expandMarked)
          b.mark();
        if ((b.marked || b.expandMarked)) {
          if (vBox == null)
            vBox = b;
          else vBox.ne = b.ne;
          if(cy == y-1) {
            vBox.unexpandMark().unmark();
            mergedBoxes.add(vBox);
            vBox = null;
          }
        } else {
          if(vBox != null) {
            vBox.unexpandMark().unmark();
            mergedBoxes.add(vBox);
            vBox = null;
          }
        }
      }
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Adjoint cells merged.");
      this.iRouteBoxer.onMergedAdjointVertically(mergedBoxes);
    }



    this.routeBoxesV = new ArrayList<>();
    RouteBoxer.Box rBox = null;
    for(int i = 0; i < mergedBoxes.size(); i++) {
      RouteBoxer.Box bx = mergedBoxes.get(i);
      if(bx.merged)
        continue;
      rBox = bx;
      for (int j = i + 1; j < mergedBoxes.size(); j++) {
        RouteBoxer.Box b = mergedBoxes.get(j);
        if (b.sw.latitude == rBox.sw.latitude
            && b.ne.latitude == rBox.ne.latitude
            && b.sw.longitude == rBox.ne.longitude) {
          rBox.ne = b.ne;
          b.merged = true;
        }
      }
      routeBoxesV.add(rBox);

    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Adjoint boxes merged.");
      this.iRouteBoxer.onMergedVertically(routeBoxesV);
    }
  }

  private void horizontalMerge(int x, int y, RouteBoxer.Box[][] boxArray) {
    ArrayList<RouteBoxer.Box> mergedBoxes = new ArrayList<>();
    RouteBoxer.Box hBox = null;
    for(int cy = 0; cy < y; cy++) {
      for (int cx = 0; cx < x; cx++) {
        RouteBoxer.Box b = new RouteBoxer.Box(boxArray[cx][cy].sw, boxArray[cx][cy].ne, cx, cy);
        if(boxArray[cx][cy].marked || boxArray[cx][cy].expandMarked)
          b.mark();
        if ((b.marked || b.expandMarked)) {
          if (hBox == null)
            hBox = b;
          else hBox.ne = b.ne;
          if(cx == x-1) {
            hBox.unexpandMark().unmark();
            mergedBoxes.add(hBox);
            hBox = null;
          }
        } else {
          if(hBox != null) {
            hBox.unexpandMark().unmark();
            mergedBoxes.add(hBox);
            hBox = null;
          }
        }
      }
    }

    if(this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Adjoint cells merged.");
      this.iRouteBoxer.onMergedAdjointHorizontally(mergedBoxes);
    }

    this.routeBoxesH = new ArrayList<>();
    RouteBoxer.Box rBox = null;
    for(int i = 0; i < mergedBoxes.size(); i++) {
      RouteBoxer.Box bx = mergedBoxes.get(i);
      if(bx.merged)
        continue;
      rBox = bx;
      for (int j = i + 1; j < mergedBoxes.size(); j++) {
        RouteBoxer.Box b = mergedBoxes.get(j);
        if (b.sw.longitude == rBox.sw.longitude
            && b.sw.latitude == rBox.ne.latitude
            && b.ne.longitude == rBox.ne.longitude) {
          rBox.ne = b.ne;
          b.merged = true;
        }
      }
      routeBoxesH.add(rBox);
    }

    if (this.iRouteBoxer != null) {
      this.iRouteBoxer.onProcess("Adjoint boxes merged.");
      this.iRouteBoxer.onMergedHorizontally(routeBoxesH);
    }
  }

  public ArrayList<RouteBoxer.Box> getRouteBoxesH() {
    return this.routeBoxesH;
  }
  public ArrayList<RouteBoxer.Box> getRouteBoxesV() {
    return this.routeBoxesV;
  }

  /**
   * Created by aryo on 30/1/16.
   */
  public static interface IRouteBoxer {

    void onBoxesObtained(ArrayList<RouteBoxer.Box> boxes);
    void onBoundsObtained(LatLngBounds bounds);
    void onGridOverlaid(ArrayList<RouteBoxer.Box> boxes);
    void onGridObtained(RouteBoxer.Box[][] boxArray);
    void onGridMarked(ArrayList<RouteBoxer.Box> boxes);
    void onGridMarksExpanded(Box[][] boxArray);
    void onMergedAdjointVertically(ArrayList<RouteBoxer.Box> boxes);
    void onMergedAdjointHorizontally(ArrayList<RouteBoxer.Box> boxes);
    void onMergedVertically(ArrayList<RouteBoxer.Box> mergedBoxes);
    void onMergedHorizontally(ArrayList<RouteBoxer.Box> mergedBoxes);
    void onProcess(String processInfo);
    void onRouteSimplified(ArrayList<RouteSegmentation.Point> simplifiedRoute);
    void drawLine(RouteSegmentation.Point origin, RouteSegmentation.Point destination, int color);
    void drawBox(RouteSegmentation.Point origin, RouteSegmentation.Point destination, int yellow);
    void clearPolygon();
  }

  public class Box {

    private int x;
    private int y;

    public Boolean marked = false;
    public Boolean simpleMarked = false;
    public Boolean expandMarked = false;
    public Boolean merged = false;

    public RouteSegmentation.Point ne;
    public RouteSegmentation.Point sw;
    private RouteSegmentation.Point nw;
    private RouteSegmentation.Point se;


    private Box() {}

    private Box(RouteSegmentation.Point sw, RouteSegmentation.Point ne, int x, int y) {
      this.sw = sw;
      this.ne = ne;
      this.x = x;
      this.y = y;
      updateNWSE();
    }

    private void updateNWSE() {
      this.nw = new RouteSegmentation.Point(this.ne.latitude, this.sw.longitude);
      this.se = new RouteSegmentation.Point(this.sw.latitude, this.ne.longitude);
    }

    private RouteBoxer.Box mark() { this.marked = true; return this; }
    private RouteBoxer.Box simpleMark() { this.simpleMarked = true; return this; }
    private RouteBoxer.Box unmark() { this.marked = false; return this; }
    private RouteBoxer.Box expandMark() { this.expandMarked = true; return this; }
    private RouteBoxer.Box unexpandMark() { this.expandMarked = false; return this; }

    public RouteBoxer.Box copy(RouteBoxer.Box box) {
      RouteBoxer.Box b = new RouteBoxer.Box();
      b.x = box.x;
      b.y = box.y;
      b.marked = box.marked;
      b.expandMarked = box.expandMarked;
      b.merged = box.merged;
      b.ne = new RouteSegmentation.Point(box.ne.latitude, box.ne.longitude);
      b.sw = new RouteSegmentation.Point(box.sw.latitude, box.sw.latitude);
      return b;
    }

  }

  public class LatLngBounds {

    private ArrayList<RouteSegmentation.Point> latLngs = new ArrayList<>();

    private RouteSegmentation.Point southwest;
    private RouteSegmentation.Point northeast;

    private LatLngBounds include(RouteSegmentation.Point latLng) {
      this.latLngs.add(latLng);
      return this;
    }

    private LatLngBounds build() {
      this.southwest = null;
      this.northeast = null;
      double maxLat = 0;
      double maxLng = 0;
      double minLat = 0;
      double minLng = 0;
      for ( RouteSegmentation.Point latLng:
          this.latLngs) {

        if(maxLat == 0 && maxLng == 0 && minLat == 0 && minLng == 0) {
          maxLat = minLat = latLng.latitude;
          maxLng = minLng = latLng.longitude;
          continue;
        }

        if(latLng.latitude > maxLat) maxLat = latLng.latitude;
        if(latLng.longitude > maxLng) maxLng = latLng.longitude;
        if(latLng.latitude < minLat) minLat = latLng.latitude;
        if(latLng.longitude < minLng) minLng = latLng.longitude;
      }

      this.southwest = new RouteSegmentation.Point(minLat, minLng);
      this.northeast = new RouteSegmentation.Point(maxLat, maxLng);

      return this;
    }
  }

  private class Line
  {

    private RouteSegmentation.Point origin;
    private RouteSegmentation.Point destination;
    private double m, c; // y = mx + c;

    private Line (RouteSegmentation.Point origin, RouteSegmentation.Point destination) {

      this.origin = origin;
      this.destination = destination;
      this.m = (destination.latitude - origin.latitude) / (destination.longitude - origin.longitude);
      this.c = origin.latitude - (this.m * origin.longitude);

    }

    // y = mx + c; x = (y - c) / m
    private double getY(double x) {
      return this.m * x + c;
    }
    private double getX(double y) {
      return (y - c) / this.m;
    }

    private boolean intersect(RouteSegmentation.Point to, RouteSegmentation.Point td) {

      double y = this.getY(to.longitude);
      if(y <= td.latitude && y >= to.latitude) return true;

      y = this.getY(td.longitude);
      if(y <= td.latitude && y >= to.latitude) return true;

      double x = this.getX(td.latitude);
      if(x >= to.longitude && x <= td.longitude) return true;

      x = this.getX(to.latitude);
      return (x >= to.longitude && x <= td.longitude);

    }

  }

}
