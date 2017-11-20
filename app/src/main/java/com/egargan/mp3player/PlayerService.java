package com.egargan.mp3player;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 *  Service wrapper for MP3Player class. Exists as a foreground service, whose status bar
 *  notification can be used to navigate back to / relaunch PlayerActivity.
 */
public class PlayerService extends Service {

    private static final int ID_NOTI_PLAYER = 0;
    private static final int REQCODE_NOTI_INTENT = 0;

    private NotificationCompat.Builder notiBuilder;

    private MP3Player player;

    private Handler handler;
    private boolean isPolling = false; // Bool to prevent duplicate runnables

    @Override
    public void onCreate() {
        player = new MP3Player();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        handler = new Handler();

        updateNotification();
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    private IBinder binder = new PlayerBinder();

    class PlayerBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {

        // Destroy any posted runnables, just in case
        handler.removeCallbacksAndMessages(null);

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID_NOTI_PLAYER);

        player.stop();
        player = null;

        super.onDestroy();
    }


    /** MP3Player does not 'stop' itself when song ends - this runnable reposts itself
        until the song is found to have stopped. */
    private class ProgressPoller implements Runnable {
        @Override
        public void run() {
            if (player.getState() == MP3Player.MP3PlayerState.PLAYING &&
                    player.getProgress() >= player.getDuration()) {
                player.stop();
                isPolling = false;
                updateNotification();
            } else {
                handler.postDelayed(new ProgressPoller(), 100);
                isPolling = true;
            }
        }
    }

    /** Creating notification takes a good bit of code, so this method
     *  generates and returns the notification object.
     *
     *  The notification returns the user to player activity onclick.
     *
     *  @param message Dscription text for notification. */
    private Notification makeNotification(String message) {

        Intent notiIntent = new Intent(this, PlayerActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this,
                        REQCODE_NOTI_INTENT,
                        notiIntent,
                        0);

        if (notiBuilder == null) {
            notiBuilder = new NotificationCompat.Builder(this, "playerservice")
                .setSmallIcon(R.drawable.ic_play_arrow_black_24dp)
                .setContentTitle("MP3 Player")
                .setContentText(message)
                .setOngoing(true) // 'ongoing' notifications are pushed left ahead of others
                .setContentIntent(pendingIntent);
        }

        notiBuilder.setContentText(message);

        Notification noti = notiBuilder.build();
        noti.flags = Notification.FLAG_ONGOING_EVENT;

        return noti;
    }


    /** Check player state and update service's status bar notification. */
    private void updateNotification() {

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        switch (player.getState()) {
            case PLAYING:
                manager.notify(ID_NOTI_PLAYER, makeNotification("Playing your tune..."));
                break;
            case PAUSED:
                manager.notify(ID_NOTI_PLAYER, makeNotification("Player paused."));
                break;
            case STOPPED:
                manager.notify(ID_NOTI_PLAYER, makeNotification("Player stopped."));
                break;
            case ERROR:
            default:
                manager.notify(ID_NOTI_PLAYER, makeNotification("Player error!"));
        }
    }


    // -- Proxy player methods -- //

    public MP3Player.MP3PlayerState getState() {
        return player.getState();
    }

    /** @return True if load successful, false otherwise. */
    public boolean load(String path) {

        player.load(path);

        // Start polling player for song status - if some poller doesn't exist already
        if (!isPolling) {
            handler.postDelayed(new ProgressPoller(), 100);
        }
        updateNotification();
        return player.getState() != MP3Player.MP3PlayerState.ERROR;
    }

    public String getLoadedFilePath() { return player.getFilePath(); }

    public int getSongDuration() { return player.getDuration(); }

    public int getSongProgress() { return player.getProgress(); }

    public void play() {
        player.play();
        updateNotification();
    }

    public void pause() {
        player.pause();
        updateNotification();
    }

    public void stop() {
        player.stop();
        updateNotification();
    }

}
