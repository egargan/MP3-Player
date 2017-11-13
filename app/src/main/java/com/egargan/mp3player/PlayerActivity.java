package com.egargan.mp3player;

import android.Manifest;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Single activity component for player UI. Gets and displays music files from external storage.
 * Also controls audio playback control buttons.
 */
public class PlayerActivity extends AppCompatActivity {

    private static final int READ_EXT_STORAGE_REQCODE = 1;

    private static final int MUSIC_LOAD_FAILURE = 0;
    private static final int MUSIC_LOAD_EMPTY = 1;
    private static final int MUSIC_LOAD_SUCCESS = 2;

    private boolean playerIsBound;
    private int currentSongIndex = -1;

    private PlayerService playerService;
    private SimpleCursorAdapter musicAdapter;
    private MP3Player player;

    private Handler handler; // Handler for song progress polling method

    private ProgressBar songProgBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE)) {

            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_EXT_STORAGE_REQCODE);

        } else {
            loadMusicFromStorage();
            populateList();
        }

        handler = new Handler();
        songProgBar = findViewById(R.id.songProgBar);

        Intent intent = new Intent(getApplicationContext(), PlayerService.class);
        startService(intent);

        bindService(intent, conn, 0);
        // check if player service exists
        // or maybe just start + bind to service, and handle already-running state there?
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // called when we bind to a service
            playerService = ((PlayerService.PlayerBinder)service).getService();
            playerIsBound = true;

            player = playerService.getPlayer();
            Log.i("playeract","service connected");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // only called when not properly disconnected
            playerService = null;
            playerIsBound = false;
            Log.i("playeract","service disconnected");
        }
    };

    @Override
    public void onRequestPermissionsResult(int reqcode,
                                           @NonNull String permissions[],
                                           @NonNull int[] granted) {
        switch (reqcode) {
            case READ_EXT_STORAGE_REQCODE: {
                if (granted.length > 0 &&
                        granted[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, perform load + populate
                    loadMusicFromStorage();
                    populateList();
                } else {
                    // Permission denied :(
                    // TODO : show permission denied msg
                }
            }
        }
    }

    /**
     * Attempts to load all music files from media store.
     * Instantiates member adapter for ListView if successful.
     * @return non-zero integer if load successful
     */
    private int loadMusicFromStorage() {

        ContentResolver musicResolver = getContentResolver();

        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String PROJECTION[] = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
        };

        // Exclude audiofiles that aren't music, e.g. ringtones
        String SELECTION = MediaStore.Audio.Media.IS_MUSIC + " <> 0";

        // Get cursor from query - projection applied when adapter is constructed
        Cursor musicCursor = musicResolver.query(musicUri, null,
                SELECTION, null, MediaStore.Audio.Media.TITLE);

        if (musicCursor == null) return MUSIC_LOAD_FAILURE;

        // Construct adapter using Android's 2-item list item layout
        musicAdapter = new SimpleCursorAdapter(
                getApplicationContext(),
                android.R.layout.simple_list_item_2,
                musicCursor,
                PROJECTION,
                new int[] { android.R.id.text1, android.R.id.text2},
                0 );

        return (musicAdapter.isEmpty() ? MUSIC_LOAD_EMPTY : MUSIC_LOAD_SUCCESS);
    }

    // Listener attached to each listview item
    AdapterView.OnItemClickListener songItemListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            changeSongTo(position);
        }
    };

    /**
     * Error checks + updates UI before calling actual song-playing method.
     * @param position Index of song in listview to be played.
     */
    private void changeSongTo(int position) {

        if (musicAdapter == null) return;

        Cursor cursor = musicAdapter.getCursor();
        ListView songview = findViewById(R.id.songview);

        if (currentSongIndex >= 0) { // Reset previously played song's bg colour
            songview.getChildAt(currentSongIndex).setBackgroundColor(Color.WHITE);
        }

        currentSongIndex = position;

        // If erroneous song index received, assume from 'prev' or 'next' btn click, so stop player
        if (position >= musicAdapter.getCount() || position < 0) {
            player.stop();
            currentSongIndex = -1;
            return;
        }

        // Cursor position changes automatically onClick,
        // but we do have to set it on prev/next click.
        cursor.moveToPosition(currentSongIndex);

        playSongAtCursor();

        updatePlayPauseBtn();

        songview.getChildAt(currentSongIndex).
                setBackgroundColor(getColor(R.color.colorSongItemBgHighlight));
    }

    /**
     *  Plays song pointed to by the cursor object.
     */
    private void playSongAtCursor() {

        player.stop(); // New media player instantiated for each file, so can stop instead of pause

        MP3Player player = playerService.getPlayer();
        Cursor cursor = musicAdapter.getCursor();

        int pathColIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

        if (cursor.isClosed() || pathColIndex < 0 || player == null) return;

        player.load(cursor.getString(pathColIndex));

        songProgBar.setMax(player.getDuration());
        handler.post(updateSongProgressBar);
    }

    /**
     *  Populates ListView with data from adapter, if possible.
     */
    private void populateList() {

        switch (loadMusicFromStorage()) {

            case MUSIC_LOAD_FAILURE :
                // load failed, show error
                Log.i("playeract", "music load failed");
                break;
            case MUSIC_LOAD_EMPTY :
                // load return nothing, show msg
                Log.i("playeract", "loaded nothing");
                break;
            case MUSIC_LOAD_SUCCESS :
                Log.i("playeract", "music load succeeded");
                ListView lview = findViewById(R.id.songview);
                lview.setAdapter(musicAdapter);
                lview.setOnItemClickListener(songItemListener);
        }
    }

    // ---  GUI Control Methods  --- //

    /**
     * Handler for pause/play button. Will alternate messages according to player state.
     * @param view Event source
     */
    public void playPause(View view) {

        if (playerIsBound) {
            MP3Player player = playerService.getPlayer();

            switch(player.getState()) {

                case PLAYING : // then pause music
                    player.pause();
                    break;

                case PAUSED : // then play music
                    player.play();
                    break;

                case STOPPED : // then just play first in list
                    if (musicAdapter == null) return;
                    changeSongTo(0);
                    break;
            }
            updatePlayPauseBtn();
        }
    }

    /**
     * Handler for 'previous' and 'next' buttons - method shared as function is so similar
     * @param view Event source
     */
    public void prevNext(View view) {

        // return if not song not playing or paused
        if (musicAdapter == null || player.getState() == MP3Player.MP3PlayerState.STOPPED ||
                player.getState() == MP3Player.MP3PlayerState.ERROR)
            return;

        switch (view.getResources().getResourceEntryName(view.getId())) { // Get Id w/o path

            case "prevBtn":
                if (currentSongIndex == 0) { // If at beginning of list, wrap around to end
                    changeSongTo(musicAdapter.getCount() - 1);
                } else {
                    changeSongTo(currentSongIndex - 1);
                }
                break;

            case "nextBtn":
                if (currentSongIndex == musicAdapter.getCount() - 1) { // If at end, wrap to start
                    changeSongTo(0);
                } else {
                    changeSongTo(currentSongIndex + 1);
                }
                break;
        }
    }

    // Sets play/pause btn text to represent player state
    private void updatePlayPauseBtn() {

        switch (player.getState()) {
            case STOPPED: case ERROR: case PAUSED:
                ((Button) findViewById(R.id.pausePlayBtn)).setText(R.string.pausedBtnText);
                break;

            case PLAYING:
                ((Button) findViewById(R.id.pausePlayBtn)).setText(R.string.playingBtnText);
                break;
        }
    }

    private Runnable updateSongProgressBar = new Runnable() {
        @Override
        public void run() {
            try {
                songProgBar.setProgress(playerService.getPlayer().getProgress());
            } finally {
                if (player.getState() != MP3Player.MP3PlayerState.STOPPED) {
                    handler.postDelayed(updateSongProgressBar, 50);
                }
            }
        }
    };

    @Override
    protected void onStop() {

        super.onStop();

        musicAdapter.getCursor().close();

        // Close player in activity stop for now - but will persist soon
        if(conn != null) {
            unbindService(conn);
        }

        //Intent intent = new Intent(this, PlayerService.class);
        //stopService(intent);
        // if player still playing, leave alone
        // if player paused, stop player + service ... ?
    }

}
