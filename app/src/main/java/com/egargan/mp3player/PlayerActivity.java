package com.egargan.mp3player;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class PlayerActivity extends AppCompatActivity {

    private PlayerService playerService;
    private MP3Player player;

    private boolean playerIsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // check if player service exists
        // or maybe just start + bind to service, and handle alread-running state there?

    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            // called when we bound to a service and it returns an
            playerService = ((PlayerService.PlayerBinder)service).getService();
            playerIsBound = true;

            player = playerService.getPlayer();
            Log.i("playeract","service connected");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {

            playerService = null;
            playerIsBound = false;

            Log.i("playeract","service disconnected");
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        // if player still playing, leave alone
        // if player paused, stop player + service ... ?

    }

    // called when central pause/play button clicked
    public void playPause(Button btn) {

        if (playerIsBound) {

            MP3Player player = playerService.getPlayer();

            switch(player.getState()) {

                case PLAYING : // then pause music
                    player.pause();
                    btn.setText(R.string.pausedBtnText);
                    break;

                case PAUSED : // then play music
                    player.play();
                    btn.setText(R.string.playingBtnText);
                    break;
            }
        }
    }


}
