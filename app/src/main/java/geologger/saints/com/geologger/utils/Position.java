package geologger.saints.com.geologger.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by Seiya on 2015/01/01.
 */
public class Position {

    private static final String PREFNAME = "Position";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";

    public static void savePosition(Context context, float latitude, float longitude) {
        Log.i(PREFNAME, "savePosition");
        SharedPreferences preference = context.getSharedPreferences(PREFNAME, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = preference.edit();
        editor.putFloat(LATITUDE, latitude);
        editor.putFloat(LONGITUDE, longitude);
        editor.commit();
    }

    public static float[] getPosition(Context context) {
        Log.i(PREFNAME, "getPosition");
        SharedPreferences preference = context.getSharedPreferences(PREFNAME, Context.MODE_MULTI_PROCESS);
        float[] position = new float[2];
        position[0] = preference.getFloat(LATITUDE, 0.0f);
        position[1] = preference.getFloat(LONGITUDE, 0.0f);
        return position;
    }

}
