package ir.iliya.farhanglogat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Window;
import android.view.WindowManager;

import ir.adad.client.Adad;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;


/**
 * Created by ThangTB on 09/02/2015.
 */
public class SplashActivity extends Activity{

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    //The countdown to control the time that wil be this acivity
    CountDownTimer countdown = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        Adad.initialize(getApplicationContext());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(ir.iliya.farhanglogat.R.layout.wordbook_splash);

        //Initializing the countdawn. Its duration is 3 seconds
        countdown = new CountDownTimer(3000, 500) {

            public void onTick(long millisUntilFinished) {

            }
            //When the countdown finishes
            @Override
            public void onFinish() {
                // TODO Auto-generated method stub
                //Calling the menu activity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                //Cancelling the countdown
                countdown.cancel();
                //Finishing this activity
                finish();
            }


        };
        //Starting the countdown
        countdown.start();



    }

    @Override
    public void onBackPressed() {

    }
}
