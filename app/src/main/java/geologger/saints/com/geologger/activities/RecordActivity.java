package geologger.saints.com.geologger.activities;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.identity.intents.AddressConstants;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.ViewById;

import java.util.List;
import java.util.UUID;

import geologger.saints.com.geologger.R;
import geologger.saints.com.geologger.database.CompanionSQLite;
import geologger.saints.com.geologger.database.SQLiteModelDefinition;
import geologger.saints.com.geologger.models.TableDefinitions;
import geologger.saints.com.geologger.models.TrajectoryEntry;
import geologger.saints.com.geologger.services.GPSLoggingService;
import geologger.saints.com.geologger.services.GPSLoggingService_;
import geologger.saints.com.geologger.services.PositioningService_;
import geologger.saints.com.geologger.utils.Position;


@EActivity
public class RecordActivity extends FragmentActivity {

    private final String TAG = getClass().getSimpleName();
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    @SystemService
    ActivityManager mActivityManager;

    @Bean
    CompanionSQLite mCompanionDbHandler;

    @ViewById(R.id.loggingStartButton)
    Button mLoggingStartButton;

    @ViewById(R.id.loggingStopButton)
    Button mLoggingStopButton;

    @ViewById(R.id.checkInButton)
    Button mCheckinButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        setUpMapIfNeeded();
    }

    @Click(R.id.loggingStartButton)
    public void onLoggingStart(View clicked) {

        Log.i(TAG, "onLoggingStart");
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AddressConstants.Themes.THEME_DARK);
        builder.setTitle(getResources().getString(R.string.companion));

        final String[] candidates = getResources().getStringArray(R.array.companion_candidate_list);
        final boolean[] checks = new boolean[candidates.length];
        checks[0] = true;
        builder.setMultiChoiceItems(R.array.companion_candidate_list, checks, new DialogInterface.OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                checks[which] = isChecked;
            }
        });

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                int count = 0;
                final StringBuilder companions = new StringBuilder();
                for (int i = 0; i < checks.length; i++) {
                    if (checks[i]) {
                        count++;
                        companions.append(candidates[i] + ",");
                    }
                }

                String toastMessage = getResources().getString(R.string.start_logging);
                if (count == 0) {
                    toastMessage = getResources().getString(R.string.companion_alert);
                    Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_SHORT).show();
                    return;
                }

                //Companionが正しく選択されていれば Trajectory IDを生成し，
                //別スレッドでデータベースに格納
                final String tid = UUID.randomUUID().toString();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                       mCompanionDbHandler.insert(tid, companions.substring(0, companions.length() - 1));
                    }
                }).start();

                //Trajectory IDを渡してGPSLoggingServiceを起動
                Intent intent = new Intent(getApplicationContext(), GPSLoggingService_.class);
                intent.putExtra(TrajectoryEntry.TID, tid);
                startService(intent);

                //Viewを更新し開始した旨を通知
                setLoggingStateOnView();
                Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_SHORT).show();
            }


        });

        builder.show();

    }

    @Click(R.id.loggingStopButton)
    public void onLoggingStop(View clicked) {

        Log.i(TAG, "onLoggingStop");

        Intent serviceIntent = new Intent(this.getApplicationContext(), GPSLoggingService_.class);
        stopService(serviceIntent);

        setLoggingStateOnView();
    }

    //ロギング中かどうかによってボタンの表示非表示の状態を制御する
    private void setLoggingStateOnView() {

        if (isLogging()) {
            mLoggingStartButton.setVisibility(View.GONE);
            mLoggingStopButton.setVisibility(View.VISIBLE);
            mCheckinButton.setVisibility(View.VISIBLE);
        } else {
            mLoggingStopButton.setVisibility(View.GONE);
            mCheckinButton.setVisibility(View.GONE);
            mLoggingStartButton.setVisibility(View.VISIBLE);
        }
    }

    //ロギング中かどうかを判定する
    //ロギング中ならtrueを返す
    private boolean isLogging() {

        List<ActivityManager.RunningServiceInfo> runningServiceList = mActivityManager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo info : runningServiceList) {
            String serviceName = info.service.getClassName();
            if (serviceName.equals(GPSLoggingService.class.getName()) || serviceName.equals(GPSLoggingService_.class.getName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
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
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        float[] position = Position.getPosition(getApplicationContext());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(position[0], position[1]), 10));
        mMap.addMarker(new MarkerOptions().position(new LatLng(position[0], position[1])).title("Marker"));
    }


}
