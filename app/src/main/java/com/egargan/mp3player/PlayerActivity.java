package com.egargan.mp3player;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
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
import android.widget.TextView;

/**
 * Single activity component for player UI. Gets and displays music files from external storage.
 * Also controls audio playback control buttons.
 */
public class PlayerActivity extends AppCompatActivity {

    private static final int READ_EXT_STORAGE_REQCODE = 1;

    // Media store fetch status codes
    private static final int MUSIC_LOAD_FAILURE = 0;
    private static final int MUSIC_LOAD_EMPTY = 1;
    private static final int MUSIC_LOAD_SUCCESS = 2;

    private boolean playerIsBound;
    private int currentSongIndex = -1; // Index in listview

    private PlayerService player;
    private SimpleCursorAdapter musicAdapter;

    private Handler handler; // Handler for song progress polling method
    private boolean isPolling = false; // Prevents duplicate runnables

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
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // called when we bind to a service
            player = ((PlayerService.PlayerBinder)service).getService();
            playerIsBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // only called when not properly disconnected
            player = null;
            playerIsBound = false;
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
                    updateStatusMsg(true, "We need permission to read your music!");
                }
            }
        }
    }

    /** Attempts to load all music files from media store.
     * Instantiates member adapter for ListView if successful.
     *
     * @return non-zero integer if load successful */
    private int loadMusicFromStorage() {

        ContentResolver musicResolver = getContentResolver();

        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String PROJECTION[] = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
        };

        // Exclude audiofiles that aren't music, e.g. ringtones
        // SELECTION = MediaStore.Audio.Media.IS_MUSIC + " <> 0";
        String SELECTION = "";

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

    /** Populates ListView with data from adapter, if possible.
     *  Switches on status code return from music load method + displays appropriate status msg. */
    private void populateList() {

        switch (loadMusicFromStorage()) {

            case MUSIC_LOAD_FAILURE :
                updateStatusMsg(true, "Error loading songs :(");
                break;

            case MUSIC_LOAD_EMPTY :
                updateStatusMsg(true, "Couldn't find any music!");
                break;

            case MUSIC_LOAD_SUCCESS :
                updateStatusMsg(false, null);
                ListView lview = findViewById(R.id.songview);
                lview.setAdapter(musicAdapter);
                lview.setOnItemClickListener(songItemListener);
        }
    }

    // Listener attached to each listview item
    AdapterView.OnItemClickListener songItemListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            changeSongTo(position);
        }
    };

    /** Error checks + updates UI before calling actual song-playing method.
     * @param position Index of song in listview to be played. */
    private void changeSongTo(int position) {

        if (musicAdapter == null) return;

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
        musicAdapter.getCursor().moveToPosition(currentSongIndex);

        playSongAtCursor();

        songview.getChildAt(currentSongIndex).
                setBackgroundColor(getColor(R.color.colorSongItemBgHighlight));

        if (!isPolling) { // Make poller, if one has not already been posted
            handler.post(new ProgressPoller());
        }
    }

    /** Plays song pointed to by the cursor object. */
    private void playSongAtCursor() {

        player.stop(); // New media player instantiated for each file, so can stop instead of pause

        Cursor cursor = musicAdapter.getCursor();
        int pathColIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

        if (cursor.isClosed() || pathColIndex < 0) return;

        if (!player.load(cursor.getString(pathColIndex))) return;

        songProgBar.setMax(player.getSongDuration());
    }

    // ---  GUI Control Methods  --- //

    /** Handler for pause/play button. Will alternate messages according to player state.
     *  @param view Event source */
    public void playPause(View view) {

        if (musicAdapter == null ||
                musicAdapter.isEmpty() ||
                !playerIsBound)
            return;

        switch(player.getState()) {

            case PLAYING : // then pause music
                player.pause();
                break;

            case PAUSED : // then play music
                player.play();
                break;

            case STOPPED : // then just play first in list
                if (musicAdapter == null) return;
                if (currentSongIndex == -1) currentSongIndex = 0;

                changeSongTo(currentSongIndex);
                break;
        }
        updatePlayPauseBtn();
    }

    /** Handler for 'previous' and 'next' buttons - method shared as function is so similar.
     * @param view Event source */
    public void prevNext(View view) {

        // return if not song not playing or paused
        if (musicAdapter == null ||
                musicAdapter.isEmpty() ||
                player.getState() == MP3Player.MP3PlayerState.STOPPED ||
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

    /** Controls visibility + text of UI's central status message. */
    private void updateStatusMsg(boolean show, String msg) {

        TextView statusMsg = findViewById(R.id.statusMsgTxt);

        if (show) {
            statusMsg.setText(msg);
            statusMsg.setVisibility(View.VISIBLE);
        } else {
            statusMsg.setText("");
            statusMsg.setVisibility(View.INVISIBLE);
        }
    }

    /** Sets play/pause button text to represent player state. */
    public void updatePlayPauseBtn() {

        if (!playerIsBound) return;

        switch (player.getState()) {

            case STOPPED: case ERROR: case PAUSED:
                ((Button) findViewById(R.id.pausePlayBtn)).setText(R.string.pausedBtnText);
                break;

            case PLAYING:
                ((Button) findViewById(R.id.pausePlayBtn)).setText(R.string.playingBtnText);
                break;
        }
    }

    /** Runnable for polling player service state, and updating UI.
        Will repost itself until song has stopped. */
    private class ProgressPoller implements Runnable {

        @Override
        public void run() {

            switch (player.getState()) {

                case PAUSED: case PLAYING:
                    songProgBar.setProgress(player.getSongProgress());
                    isPolling = handler.postDelayed(new ProgressPoller(), 50);
                    break;

                case STOPPED:
                    songProgBar.setProgress(0);
                    isPolling = false;
                    break;
            }
            updatePlayPauseBtn();
        }
    }

    @Override
    protected void onDestroy() {

        // Remove any handler posts, just in case any still exist
        handler.removeCallbacksAndMessages(null);

        musicAdapter.getCursor().close();

        if(conn != null) {
            unbindService(conn);
        }

        // Stop service if activity is destroyed when song not playing
        if (player.getState() != MP3Player.MP3PlayerState.PLAYING) {
            Intent intent = new Intent(this, PlayerService.class);
            stopService(intent);
        }

        super.onDestroy();
    }

}
