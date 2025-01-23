package dev.aviedb.geofenceandroid;

import static java.lang.System.currentTimeMillis;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.aviedb.geofenceandroid.utils.GeoUtils;
import dev.aviedb.geofenceandroid.utils.Poi;
import dev.aviedb.geofenceandroid.utils.PolylineDecoder;
import dev.aviedb.geofenceandroid.utils.RawJSONParser;
import dev.aviedb.geofenceandroid.utils.RouteBoxer;
import dev.aviedb.geofenceandroid.utils.RouteBoxerTask;
import dev.aviedb.geofenceandroid.utils.RouteSegmentation;

public class MapActivity extends AppCompatActivity
    implements RouteBoxerTask.IRouteBoxerTask,
    OnMapReadyCallback, GoogleMap.OnMapClickListener, View.OnClickListener, TextView.OnEditorActionListener, PopupMenu.OnMenuItemClickListener, GoogleMap.OnMapLongClickListener {

  private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
  private static final int EPSILON = 100;
  private static final String STR_ROUTE_BOXER = "RouteBoxer";
  private static final String STR_ROUTE_SEGMENTATION = "RouteSegmentation";
  
  private FusedLocationProviderClient fusedLocationClient;
  private GoogleMap googleMap;
  private Marker markerOrigin, markerDestination;
  private Polyline polylineRoute;
  private Polygon polygonRoute;
  private LatLng locCurrent, locOrigin, locDestination;
  private List<LatLng> pointsPolyline, pointsPolygon;

  private List<Marker> markersSearchResult;
  private List<Poi> listMalangPoi;

  private ConstraintLayout lytFloatingButtonContainer, lytSearchBarContainer;
  private MaterialButton btnClear, btnStartNavigation;
  private ImageButton btnSearch;
  private TextInputEditText etSearch;

  private Handler handlerGetDirections;

  private ArrayList<Polygon> boxPolygons;
  private ArrayList<Polygon> gridBoxes;

  private String algorithmToUse;
  private long beforeRuntime;
  private long afterRuntime;
  private long beforeMemoryUsage;
  private long afterMemoryUsage;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_map);

    SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
    assert mapFragment != null;
    mapFragment.getMapAsync(this);

    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{
          android.Manifest.permission.ACCESS_FINE_LOCATION,
          android.Manifest.permission.ACCESS_COARSE_LOCATION
      }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    this.lytFloatingButtonContainer = findViewById(R.id.floatingButtonContainer);
    this.lytSearchBarContainer = findViewById(R.id.searchBarContainer);

    this.btnClear = findViewById(R.id.clearButton);
    this.btnStartNavigation = findViewById(R.id.startNavigationButton);
    this.btnSearch = findViewById(R.id.searchButton);
    this.etSearch = findViewById(R.id.searchInputText);

    this.btnClear.setOnClickListener(this);
    this.btnStartNavigation.setOnClickListener(this);
    this.btnSearch.setOnClickListener(this);
    this.etSearch.setOnEditorActionListener(this);

    this.markersSearchResult = new ArrayList<>();
    this.listMalangPoi = RawJSONParser.parseJSONPoi(getApplicationContext(), R.raw.malang_poi);
  }

  @Override
  public void onMapReady(@NonNull GoogleMap googleMap) {
    this.googleMap = googleMap;

    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
      this.googleMap.getUiSettings().setMyLocationButtonEnabled(true);
      this.googleMap.getUiSettings().setZoomControlsEnabled(true);
      this.googleMap.getUiSettings().setCompassEnabled(true);
      this.googleMap.getUiSettings().setMapToolbarEnabled(true);
      this.googleMap.setBuildingsEnabled(false);
      this.googleMap.setPadding(0, 220, 0, 0);
      this.googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getApplicationContext(), R.raw.map_style));
      fetchLastLocation();
    }

    this.googleMap.setOnMapClickListener(this);
    this.googleMap.setOnMapLongClickListener(this);
  }

  @Override
  public void onMapLongClick(@NonNull LatLng latLng) {
    this.locOrigin = latLng;

    if (this.markerOrigin != null) this.markerOrigin.remove();
    MarkerOptions marker = new MarkerOptions()
        .position(this.locOrigin)
        .title("Origin")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
    this.markerOrigin = this.googleMap.addMarker(marker);
  }

  @Override
  public void onMapClick(@NonNull LatLng latLng) {
    this.locDestination = latLng;

    if (this.markerDestination != null) this.markerDestination.remove();
    MarkerOptions marker = new MarkerOptions()
        .position(this.locDestination)
        .title("Destination");
    this.markerDestination = this.googleMap.addMarker(marker);
    this.updateFloatingButtonVisibility();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      if (this.googleMap != null) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          return;
        }
        this.googleMap.setMyLocationEnabled(true);
      }
    }
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.clearButton) {
      handleClearState();
    } else if (v.getId() == R.id.startNavigationButton) {
      showAlgorithmMenu(v);
    } else if (v.getId() == R.id.searchButton) {
      handleSearch(v);
    }
  }

  private void handleClearState() {
    this.locOrigin = this.locCurrent;
    this.locDestination = null;
    this.etSearch.setText("");
    this.googleMap.clear();
    handleClear();
    updateFloatingButtonVisibility();
    updateSearchBarVisibility(false);
  }

  private void showAlgorithmMenu(View v) {
    this.algorithmToUse = null;
    PopupMenu popupMenu = new PopupMenu(this, v);
    popupMenu.getMenuInflater().inflate(R.menu.algorithm_menu, popupMenu.getMenu());
    popupMenu.setOnMenuItemClickListener(this);
    popupMenu.show();
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (item.getItemId() == R.id.useRouteBoxerMenu) {
      this.algorithmToUse = STR_ROUTE_BOXER;
    } else if (item.getItemId() == R.id.useRouteSegmentationMenu) {
      this.algorithmToUse = STR_ROUTE_SEGMENTATION;
    }

    this.handleClear();
    this.updateLoadingState(true);
    new Thread(this::requestDirection).start();
    this.handlerGetDirections = new Handler(Looper.getMainLooper());
    return true;
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
      handleSearch(v);
      return true;
    }
    return false;
  }

  private void handleSearch(View v) {
    InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

    markersSearchResult.forEach(Marker::remove);
    markersSearchResult.clear();

    List<Poi> filtered = listMalangPoi.stream()
        .filter(poi -> poi.name.toLowerCase().contains(Objects.requireNonNull(etSearch.getText()).toString().toLowerCase().trim()))
        .filter(poi -> GeoUtils.isCoordInsidePolygon(poi.coordinate, this.pointsPolygon))
        .collect(Collectors.toList());

    Toast.makeText(MapActivity.this, "Search result: " + filtered.size(), Toast.LENGTH_SHORT).show();

    filtered.forEach(poi -> markersSearchResult.add(this.googleMap.addMarker(new MarkerOptions()
        .position(poi.coordinate)
        .title(poi.name)
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))))
    );
  }

  private void handleClear() {
    this.etSearch.setText("");

    if (this.pointsPolyline != null) this.pointsPolyline.clear();
    if (this.pointsPolygon != null) this.pointsPolygon.clear();

    if (this.polylineRoute != null) this.polylineRoute.remove();
    if (this.polygonRoute != null) this.polygonRoute.remove();

    markersSearchResult.forEach(Marker::remove);
    markersSearchResult.clear();

    if (this.boxPolygons != null) {
      boxPolygons.forEach(Polygon::remove);
      boxPolygons.clear();
    }

    if (this.gridBoxes != null) {
      gridBoxes.forEach(Polygon::remove);
      gridBoxes.clear();
    }
  }

  private void requestDirection() {
    HttpURLConnection urlConnection = null;
    try {
      URL url = new URL(getDirectionsUrl(this.locOrigin, this.locDestination));
      urlConnection = (HttpURLConnection) url.openConnection();
      InputStream in = new BufferedInputStream(urlConnection.getInputStream());

      String responseString = new BufferedReader(new InputStreamReader(in))
          .lines().collect(Collectors.joining("\n"));

      JSONObject jObject = new JSONObject(responseString);
      PolylineDecoder pd = new PolylineDecoder(jObject);

      List<LatLng> points = pd.getOverviewPolyline();
      List<LatLng> bounds = pd.getBounds();
      String dTotal = pd.getDistanceTotal();
      String dStraight = pd.getDistanceStraight();

      handlerGetDirections.post(() -> {
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        bounds.forEach(boundsBuilder::include);
        this.googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150));

        if (Objects.equals(algorithmToUse, STR_ROUTE_BOXER)) {
          createRouteBoxer(points);
        } else if (Objects.equals(algorithmToUse, STR_ROUTE_SEGMENTATION)) {
          logRouteParameters(dStraight, dTotal, points);
          createRouteSegmentation(points);
        }
      });
    } catch (Exception e) {
      Log.e("threadGetDirections", e.toString());
    } finally {
      if (urlConnection != null) urlConnection.disconnect();
    }
  }

  private void logRouteParameters(String dStraight, String dTotal, List<LatLng> points) {
    Log.d("Test Parameter", "origin lat        : " + locOrigin.latitude);
    Log.d("Test Parameter", "origin lng        : " + locOrigin.longitude);
    Log.d("Test Parameter", "destination lat   : " + locDestination.latitude);
    Log.d("Test Parameter", "destination lng   : " + locDestination.longitude);
    Log.d("Test Parameter", "number of path    : " + points.size() + " points");
    Log.d("Test Parameter", "distance straight : " + dStraight + " meters");
    Log.d("Test Parameter", "distance route    : " + dTotal + " meters");
  }

  private void collectDataBeforeProcess(){
    try {
      System.gc();
      Thread.sleep(100);
      this.beforeMemoryUsage = getUsedMemory();
      this.beforeRuntime = currentTimeMillis();
    } catch (InterruptedException e) {
      Log.e("CollectData", "Interrupted", e);
    }
  }

  private void collectDataAfterProcess(){
    try {
      this.afterRuntime = currentTimeMillis();
      this.afterMemoryUsage = getUsedMemory();
      System.gc();
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Log.e("CollectData", "Interrupted", e);
    }
  }

  private long getUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
  }

  private void createRouteSegmentation(List<LatLng> points) {
    collectDataBeforeProcess();

    this.pointsPolyline = points;
    PolylineOptions lineOptions = new PolylineOptions()
        .addAll(pointsPolyline)
        .zIndex(101)
        .width(8)
        .color(ContextCompat.getColor(this, R.color.blue));

    RouteSegmentation rs = new RouteSegmentation(pointsPolyline, EPSILON);
    this.pointsPolygon = rs.createRoutePolygon();
    PolygonOptions polygonOptions = new PolygonOptions()
        .addAll(pointsPolygon)
        .zIndex(100)
        .strokeWidth(4)
        .strokeColor(ContextCompat.getColor(this, R.color.transparent50_green))
        .fillColor(ContextCompat.getColor(this, R.color.transparent25_green));

    this.polylineRoute = googleMap.addPolyline(lineOptions);
    this.polygonRoute = googleMap.addPolygon(polygonOptions);

    updateLoadingState(false);
    collectDataAfterProcess();
    logPerformance("RouteSegmentation");
  }

  private void createRouteBoxer(List<LatLng> points) {
    collectDataBeforeProcess();

    RouteBoxerTask routeBoxerTask = new RouteBoxerTask(
        new ArrayList<>(points),
        EPSILON,
        false, 
        false, 
        this
    );
    routeBoxerTask.execute();

    this.polylineRoute = googleMap.addPolyline(new PolylineOptions()
        .addAll(points)
        .zIndex(101)
        .width(8)
        .color(ContextCompat.getColor(this, R.color.blue)));
  }

  private void logPerformance(String algorithm) {
    Log.v("Test " + algorithm, algorithm + " : runtime " + (afterRuntime - beforeRuntime) + " ms");
    Log.v("Test " + algorithm, algorithm + " : memory  " + (afterMemoryUsage - beforeMemoryUsage) + " MB");
  }

  @Override
  public void onRouteBoxerTaskComplete(ArrayList<RouteBoxer.Box> boxes) {
    if (this.boxPolygons == null) this.boxPolygons = new ArrayList<>();
    else this.boxPolygons.clear();

    boxes.forEach(box -> {
      LatLng sw = new LatLng(box.sw.latitude, box.sw.longitude);
      LatLng nw = new LatLng(box.ne.latitude, box.sw.longitude);
      LatLng ne = new LatLng(box.ne.latitude, box.ne.longitude);
      LatLng se = new LatLng(box.sw.latitude, box.ne.longitude);

      PolygonOptions polygonOptions = new PolygonOptions()
          .add(sw, nw, ne, se, sw)
          .zIndex(100)
          .strokeWidth(5);

      if (box.marked) {
        polygonOptions.strokeColor(Color.DKGRAY).fillColor(Color.argb(96, 0, 0, 0));
      } else if (box.expandMarked) {
        polygonOptions.strokeColor(Color.DKGRAY).fillColor(Color.argb(72, 0, 0, 0));
      } else {
        polygonOptions.strokeColor(ContextCompat.getColor(this, R.color.transparent50_green))
            .fillColor(ContextCompat.getColor(this, R.color.transparent25_green));
      }

      boxPolygons.add(googleMap.addPolygon(polygonOptions));
    });

    updateLoadingState(false);
    collectDataAfterProcess();
    logRouteBoxerStats(boxes);
  }

  private void logRouteBoxerStats(ArrayList<RouteBoxer.Box> boxes) {
    long filteredCount = boxes.stream()
        .filter(box -> !box.marked && !box.expandMarked)
        .count();
    Log.v("Test RouteBoxer", "RouteBoxer        : result  " + filteredCount + " polygons");
    logPerformance("RouteBoxer");
  }

  @Override
  public void onRouteBoxerMessage(String message) {}

  @Override
  public void onRouteBoxerGrid(ArrayList<RouteBoxer.Box> boxes, int boxBorderColor, int markedColor, int simpleMarkedColor) {
    if (this.gridBoxes == null) this.gridBoxes = new ArrayList<>();
    else this.gridBoxes.clear();

    boxes.forEach(box -> {
      LatLng sw = new LatLng(box.sw.latitude, box.sw.longitude);
      LatLng nw = new LatLng(box.ne.latitude, box.sw.longitude);
      LatLng ne = new LatLng(box.ne.latitude, box.ne.longitude);
      LatLng se = new LatLng(box.sw.latitude, box.ne.longitude);

      PolygonOptions polygonOptions = new PolygonOptions()
          .add(sw, nw, ne, se, sw)
          .zIndex(99)
          .strokeColor(boxBorderColor)
          .strokeWidth(3);

      if (box.simpleMarked) {
        polygonOptions.fillColor(simpleMarkedColor);
      } else if (box.marked) {
        polygonOptions.fillColor(ContextCompat.getColor(this, R.color.transparent25_green));
      } else {
        polygonOptions.fillColor(Color.TRANSPARENT);
      }

      gridBoxes.add(googleMap.addPolygon(polygonOptions));
    });
  }

  @Override
  public void onRouteBoxerBoxes(ArrayList<RouteBoxer.Box> boxes, int boxBorderColor, int boxFillColor) {}

  @Override
  public void onRouteBoxerSimplifiedRoute(ArrayList<LatLng> simplifiedRoute, int lineColor) {}

  private void fetchLastLocation() {
    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return;
    }

    this.fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
      if (location != null) {
        this.locCurrent = new LatLng(location.getLatitude(), location.getLongitude());
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locCurrent, 15));
      }
    });
  }

  private void updateFloatingButtonVisibility() {
    boolean showButtons = (locOrigin != null || locCurrent != null) && locDestination != null;
    lytFloatingButtonContainer.setVisibility(showButtons ? View.VISIBLE : View.GONE);
    googleMap.setPadding(0, 220, 0, showButtons ? 200 : 0);
  }

  private void updateSearchBarVisibility(boolean visible) {
    lytSearchBarContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  private void updateLoadingState(boolean isLoading) {
    btnStartNavigation.setText(isLoading ? R.string.loading : R.string.btn_start_navigation);
    Drawable icon = isLoading ? 
        new CircularProgressIndicator(this).getIndeterminateDrawable() :
        ContextCompat.getDrawable(this, R.drawable.icon_navigation);
    
    btnStartNavigation.setIcon(icon);
    btnStartNavigation.setEnabled(!isLoading);
    btnClear.setEnabled(!isLoading);
    btnSearch.setEnabled(!isLoading);
    etSearch.setEnabled(!isLoading);
  }

  private String getDirectionsUrl(LatLng origin, LatLng dest) {
    return "https://maps.googleapis.com/maps/api/directions/json?"
        + "origin=" + origin.latitude + "," + origin.longitude
        + "&destination=" + dest.latitude + "," + dest.longitude
        + "&key=" + getString(R.string.google_maps_key);
  }
}