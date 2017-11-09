package com.egargan.mp3player;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class PlayerService extends Service {

    private MP3Player player;

    @Override
    public void onCreate() {
        player = new MP3Player();
        super.onCreate();
    }


    // Might not need to implement?
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY; // Keep
    }

    private IBinder binder = new PlayerBinder();

    class PlayerBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // expect player to repeatedly bind due to lifecycle,
        // will give same service instance each time
        return binder;
    }

    @Override
    public void onDestroy() {
        player.stop();
        super.onDestroy();
    }

    public MP3Player getPlayer() {
        return player;
    }

    public void stopPlayerService() {
        player.stop();
        stopSelf();
    }


}
