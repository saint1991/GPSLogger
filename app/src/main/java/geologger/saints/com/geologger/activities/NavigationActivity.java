package geologger.saints.com.geologger.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import geologger.saints.com.geologger.R;
import geologger.saints.com.geologger.map.MapWorker;
import geologger.saints.com.geologger.mapsapi.MapsApiParser;
import geologger.saints.com.geologger.mapsapi.models.MapRouteSearchResult;
import geologger.saints.com.geologger.models.CheckinEntry;
import geologger.saints.com.geologger.models.TrajectoryEntry;
import geologger.saints.com.geologger.mapsapi.MapsApiClient;
import geologger.saints.com.geologger.utils.Position;

@EActivity
public class NavigationActivity extends FragmentActivity {

    private final String TAG = getClass().getSimpleName();
    private ProgressDialog mProgress;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LatLng mDestination;
    private String mPlaceName = null;
    private String mAddress = null;
    private MapRouteSearchResult mSearchResult = null;

    @Bean
    MapWorker mMapWorker;

    @Bean
    MapsApiClient mMapApiClient;

    @ViewById(R.id.destination)
    TextView mDestinationText;

    @ViewById(R.id.distance)
    TextView mDistanceText;

    @ViewById(R.id.duration)
    TextView mDurationText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage(getResources().getString(R.string.position_updating));
        mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgress.show();

    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        mDestination = new LatLng(intent.getDoubleExtra(TrajectoryEntry.LATITUDE, 0.0), intent.getDoubleExtra(TrajectoryEntry.LONGITUDE, 0.0));
        mPlaceName = intent.getStringExtra(CheckinEntry.PLACENAME);
        mAddress = intent.getStringExtra("Address");

        setUpMapIfNeeded();
    }

    protected void onPause() {
        super.onPause();
    }


    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }

        startSearching();
    }

    private void setUpMap() {

        if (mMap == null || mMapWorker == null) {
            return;
        }

        mMapWorker.initMap(mMap, true, true);
        mMapWorker.addDistinationMarker(mDestination, mPlaceName, mAddress);
    }


    private void startSearching() {

        new Thread(new Runnable() {

            @Override
            public void run() {

                if (mDestination == null) {
                    return;
                }

                float[] position = Position.getPosition(getApplicationContext());
                LatLng origin = new LatLng(position[0], position[1]);

                String response = mMapApiClient.query(origin, mDestination);
                if (response == null) {
                    showAlertMessage();
                    dismissProgress();
                } else {

                    //ここでガイドのルートを描画する処理を記述
                    mSearchResult = new MapRouteSearchResult(MapsApiParser.parseRoute(response));
                    if (mSearchResult != null) {
                        afterSearching();
                        dismissProgress();
                    }
                }
            }

        }).start();
    }

    @UiThread
    public void afterSearching() {
        drawNavigationLine();
        setDestinationText();
        setDistanceText();
        setDurationText();
    }

    @UiThread
    public void drawNavigationLine() {
        if (mMap != null && mMapWorker != null && mSearchResult != null) {
            mMapWorker.drawLine(mSearchResult.getPolyLinePoints());
        }
    }

    @UiThread
    public void setDestinationText() {
        String destination = mSearchResult.getDestination();
        if (destination != null) {
            mDestinationText.setText(mSearchResult.getDestination());
        }

    }

    @UiThread
    public void setDistanceText() {
        int distance = mSearchResult.getTotalDistance();
        String distanceStr = distance > 1000 ? distance + " m" : ((double)distance / 1000.0) + " km";
        mDistanceText.setText(distanceStr);
    }

    @UiThread
    public void setDurationText() {
        int minutes = mSearchResult.getTotalMinutes();
        mDurationText.setText(minutes + " min");
    }

    @UiThread
    public void dismissProgress() {
        if (mProgress != null) {
            mProgress.dismiss();
        }
    }

    @UiThread
    public void showAlertMessage() {
        Toast.makeText(getApplicationContext(), getResources().getString(R.string.network_connectivity_alert), Toast.LENGTH_SHORT).show();
    }


}