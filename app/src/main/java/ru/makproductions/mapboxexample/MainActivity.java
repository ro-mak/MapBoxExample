package ru.makproductions.mapboxexample;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.BubbleLayout;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAnchor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

public class MainActivity extends AppCompatActivity implements PermissionsListener {

    public static final String MY_MARKER_IMAGE = "my-marker-image";
    public static final String MARKER_LAYER = "marker-layer";
    public static final String MARKER_SOURCE = "marker-source";
    public static final String NAME = "name";
    public static final String PROPERTY_SELECTED = "PROPERTY_SELECTED";
    public static final String BUBBLE_LAYER = "bubble";

    @BindView(R.id.map_view)
    MapView mapView;
    @BindView(R.id.speedDial)
    SpeedDialView speedDialView;
    int featureIndex = 0;
    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private GeoJsonSource geoJsonSource;
    private List<Feature> markerCoordinates;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        ButterKnife.bind(this);
        mapView.onCreate(savedInstanceState);
        initMapView();
        permissionsManager = new PermissionsManager(this);
        addActionItems();
        setActionSelectedListener();
    }

    private void initMapView() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                initStyle(mapboxMap);
                initBubbles(mapboxMap);
                MainActivity.this.mapboxMap = mapboxMap;
                initMapClickListener(mapboxMap);
            }
        });
    }

    private void initStyle(MapboxMap mapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                Timber.e("OnMapReady");
                markerCoordinates = new ArrayList<>();
                initMarkerCoordinates(markerCoordinates, style);
                //MarkerLayer
                style.addImage(MY_MARKER_IMAGE, Objects.requireNonNull(getDrawable(R.drawable.ic_place)));
                style.addLayer(new SymbolLayer(MARKER_LAYER, MARKER_SOURCE)
                        .withProperties(PropertyFactory.iconImage(MY_MARKER_IMAGE),
                                iconOffset(new Float[]{0f, -9f}),
                                iconAllowOverlap(true)
                        ));

                //BubbleLayer
                style.addLayer(new SymbolLayer(BUBBLE_LAYER, MARKER_SOURCE)
                        .withProperties(
                                iconImage("{name}"),
                                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                                iconAllowOverlap(true),
                                iconOffset(new Float[]{-2f, -25f})
                        )
                        .withFilter(eq((get(PROPERTY_SELECTED)), literal(true))));
                enableLocationComponent();
            }
        });
    }

    private void initBubbles(MapboxMap mapboxMap) {
        mapboxMap.getStyle(new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                Timber.e("OnStyleLoaded");
                addBubbles(style);
            }
        });
    }

    private void initMapClickListener(MapboxMap mapboxMap) {
        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {
                Timber.e("OnMapClick");
                return handleClickOnIcon(point);
            }
        });
    }

    private boolean handleClickOnIcon(LatLng point) {
        List<Feature> features = mapboxMap.queryRenderedFeatures(mapboxMap.getProjection().toScreenLocation(point), MARKER_LAYER);
        if (!features.isEmpty()) {
            onPlaceClick(features);
            return true;
        } else {
            onEmptySpaceClick(point);
            return false;
        }
    }

    private void onPlaceClick(List<Feature> features) {
        String featureName = features.get(0).getStringProperty(NAME);
        List<Feature> featureList = markerCoordinates;
        if (featureList != null) {
            for (int i = 0; i < featureList.size(); i++) {
                Feature feature = featureList.get(i);
                String name;
                if (feature != null && (name = feature.getStringProperty(NAME)) != null) {
                    if (name.equals(featureName)) {
                        if (featureSelectStatus(i)) {
                            Timber.e("Feature" + name + " status selected = true");
                            setFeatureSelectState(feature, false);
                        } else {
                            Timber.e("Feature " + name + " status selected = false");
                            setFeatureSelectState(feature, true);
                        }
                    }
                }
            }
        }
    }

    private void onEmptySpaceClick(LatLng point) {
        Feature feature = Feature.fromGeometry(Point.fromLngLat(point.getLongitude(), point.getLatitude()));
        feature.addStringProperty(NAME, "name" + featureIndex);
        featureIndex++;
        feature.addBooleanProperty(PROPERTY_SELECTED, false);
        markerCoordinates.add(feature);
        Style style = mapboxMap.getStyle();
        if (style != null) {
            addBubbles(style);
        }
        geoJsonSource.setGeoJson(FeatureCollection.fromFeatures(markerCoordinates));
    }

    private void initMarkerCoordinates(List<Feature> markerCoordinates, Style style) {
        markerCoordinates.add(Feature.fromGeometry(
                Point.fromLngLat(37.62, 55.74))); // Болотный
        markerCoordinates.add(Feature.fromGeometry(
                Point.fromLngLat(37.63, 55.76))); // Басманный
        Timber.e("Features created");
        for (Feature feature : markerCoordinates) {
            feature.addStringProperty(NAME, "name" + featureIndex);
            feature.addBooleanProperty(PROPERTY_SELECTED, false);
            featureIndex++;
        }
        geoJsonSource = new GeoJsonSource(MARKER_SOURCE,
                FeatureCollection.fromFeatures(markerCoordinates));
        style.addSource(geoJsonSource);
        Timber.e("Source Added");
    }

    private void addBubbles(Style style) {
        HashMap<String, Bitmap> bubbles = new HashMap<>();
        for (Feature feature : markerCoordinates) {
            bubbles.put(feature.getStringProperty(NAME), generateBubble(feature));
        }
        style.addImages(bubbles);
    }

    private Bitmap generateBubble(Feature feature) {
        BubbleLayout bubbleLayout = (BubbleLayout)
                getLayoutInflater().inflate(R.layout.info_layout, null);
        ImageView imageView = bubbleLayout.findViewById(R.id.infowindow_image);
        imageView.setImageDrawable(getDrawable(R.drawable.ic_my_location));

        TextView descriptionTextView = bubbleLayout.findViewById(R.id.infowindow_description);
        descriptionTextView.setText("Description " + feature.getStringProperty(NAME));

        int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        bubbleLayout.measure(measureSpec, measureSpec);

        int measuredWidth = bubbleLayout.getMeasuredWidth();

        bubbleLayout.setArrowPosition(measuredWidth / 2 - 5);

        return BubbleBitmapGenerator.generate(bubbleLayout);
    }

    private void refreshSource() {
        if (geoJsonSource != null && markerCoordinates != null) {
            Timber.e("Refreshing");
            geoJsonSource.setGeoJson(FeatureCollection.fromFeatures(markerCoordinates));
        }
    }

    private void setFeatureSelectState(Feature feature, boolean selectedState) {
        feature.addBooleanProperty(PROPERTY_SELECTED, selectedState);
        refreshSource();
    }

    private boolean featureSelectStatus(int index) {
        if (markerCoordinates == null) {
            return false;
        }
        return markerCoordinates.get(index).getBooleanProperty(PROPERTY_SELECTED);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationComponent();
        }
    }

    @SuppressLint("MissingPermission")
    private void enableLocationComponent() {
        if (PermissionsManager.areLocationPermissionsGranted(this) && mapboxMap != null) {
            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(this, Objects.requireNonNull(mapboxMap.getStyle()));
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager.requestLocationPermissions(this);
        }
    }

    //SpeedDial
    private void addActionItems() {
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.speedDial, R.drawable.ic_my_location)
                        .create()
        );
    }

    private void setActionSelectedListener() {
        speedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                switch (speedDialActionItem.getId()) {
                    case R.id.speedDial:
                        Snackbar.make(findViewById(R.id.speedDial), "Looking for your position...", Snackbar.LENGTH_LONG).show();
                        enableLocationComponent();
                        return false; // true to keep the Speed Dial open
                    default:
                        return false;
                }
            }
        });
    }
}
