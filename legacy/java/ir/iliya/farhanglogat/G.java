package ir.iliya.farhanglogat;

import android.app.Application;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by Kalmati on 7/6/2016.
 */
public class G extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                        .setDefaultFontPath("fonts/NotoSerif-Regular.ttf")
                        .setFontAttrId(ir.iliya.farhanglogat.R.attr.fontPath)
                        .build()
        );
    }
}
