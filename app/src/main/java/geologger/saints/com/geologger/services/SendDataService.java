package geologger.saints.com.geologger.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;

import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.SystemService;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import geologger.saints.com.geologger.activities.SettingsActivity;
import geologger.saints.com.geologger.database.CheckinFreeFormSQLite;
import geologger.saints.com.geologger.database.CheckinSQLite;
import geologger.saints.com.geologger.database.CompanionSQLite;
import geologger.saints.com.geologger.database.SentTrajectorySQLite;
import geologger.saints.com.geologger.database.TrajectorySQLite;
import geologger.saints.com.geologger.database.TrajectorySpanSQLite;
import geologger.saints.com.geologger.models.CheckinEntry;
import geologger.saints.com.geologger.models.CheckinFreeFormEntry;
import geologger.saints.com.geologger.models.CompanionEntry;
import geologger.saints.com.geologger.models.TrajectoryEntry;
import geologger.saints.com.geologger.utils.BaseHttpClient;
import geologger.saints.com.geologger.utils.SendDataUtil;


@EService
public class SendDataService extends Service {

    private final String TAG = getClass().getSimpleName();
    private final String SERVERURL = "http://";

    @SystemService
    ConnectivityManager mConnectivityManager;

    @Bean
    BaseHttpClient mHttpClient;

    @Bean
    CheckinFreeFormSQLite mCheckinFreeFormDbHandler;

    @Bean
    CheckinSQLite mCheckinDbHandler;

    @Bean
    CompanionSQLite mCompanionDbHandler;

    @Bean
    SentTrajectorySQLite mSentTrajectoryDbHandler;

    @Bean
    TrajectorySpanSQLite mTrajectorySpanDbHandler;

    @Bean
    TrajectorySQLite mTrajectoryDbHandler;

    public SendDataService() {
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        super.onStartCommand(intent, flags, startId);
        if (!isReadyToSendData(intent)) {
            return START_NOT_STICKY;
        }

        Log.i(TAG, "onStartCommand");

        JSONArray sendData = new JSONArray();
        List<String> tidListToSend = makeTidListToSend();
        for (String tid : tidListToSend) {
            JSONObject entry = makeJsonEntry(tid);
            if (entry != null) {
                sendData.put(entry);
            }
        }

        if (sendData == null) {
            return START_NOT_STICKY;
        }

        Log.i(TAG, "[Send Data] " + sendData.toString());
        List<NameValuePair> sendParams = new ArrayList<>();
        sendParams.add(new BasicNameValuePair("Data", sendData.toString()));


        /**
         * TODO 要実装
         * サーバセットアップ後URL設定
         */
        //まずはPreferenceで設定されたSecond URLに対する送信
        //設定されていなければスキップ
        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String secondUrl = preference.getString(SettingsActivity.SECONDURL, null);
        if (secondUrl != null && secondUrl.length() > 7) {
            mHttpClient.sendHttpPostRequest(secondUrl, sendParams);
        }

        //送信済みのTIDを記録する
        String result = mHttpClient.sendHttpPostRequest(SERVERURL, sendParams);
        if (result != null) {
            mSentTrajectoryDbHandler.insertSentTidList(tidListToSend);
        }

        Log.i(TAG,"response: " + result);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }



    // Check if connected to WIFI or WIMAX
    private boolean isReadyToSendData(Intent intent) {

        NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (netInfo == null) {
            Log.i(TAG, "NetInfo is null");
            return false;
        }

        if (!(netInfo.getType() == ConnectivityManager.TYPE_WIFI || netInfo.getType() == ConnectivityManager.TYPE_WIMAX)) {
            return false;
        }

        NetworkInfo.State state = netInfo.getState();
        if (!state.equals(NetworkInfo.State.CONNECTED)) {
            return false;
        }

        return true;
    }


    private JSONArray prepareJsonDataToSend() {

        //Prepare the data to send as a JSONArray
        JSONArray sendData = new JSONArray();
        List<String> tidListToSend = makeTidListToSend();
        for (String tid : tidListToSend) {
            JSONObject entry = makeJsonEntry(tid);
            if (entry != null) {
                sendData.put(entry);
            }
        }

        return sendData;
    }

    // Get TID List to send whose trajectory is finished logging and has not been sent
    private List<String> makeTidListToSend() {

        List<String> sentList = mSentTrajectoryDbHandler.getSentTrajectoryList();
        List<String> tidList = mTrajectorySpanDbHandler.getLoggingFinishedTidList();

        List<String> ret = new ArrayList<>();
        for (String tid : tidList) {

            if (!sentList.contains(tid)) {
                ret.add(tid);
            }
        }

        return ret;
    }

    private JSONObject makeJsonEntry(String tid) {

        List<TrajectoryEntry> trajectory = mTrajectoryDbHandler.getTrajectory(tid);
        List<CheckinFreeFormEntry> checkinFreeForm = mCheckinFreeFormDbHandler.getCheckinFreeFormList(tid);
        List<CheckinEntry> checkin = mCheckinDbHandler.getCheckinList(tid);
        List<CompanionEntry> companion = mCompanionDbHandler.getCompanionList(tid);

        JSONObject entry = SendDataUtil.makeJsonData(trajectory, checkinFreeForm, checkin, companion);

        return entry;
    }

}
