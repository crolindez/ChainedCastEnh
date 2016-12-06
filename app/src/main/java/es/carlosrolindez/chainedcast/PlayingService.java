package es.carlosrolindez.chainedcast;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class PlayingService extends MediaBrowserServiceCompat implements MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, MediaPlayer.OnPreparedListener  {

    private static String TAG = "PlayingService";

    public static final String SERVICE_COMMAND ="es.carlosrolindez.PlayingService.Command";
    public static final String SERVICE_SONG_ARRAY = "es.carlosrolindez.PlayingService.Songlist";
    private static final String AVRCP_PLAYSTATE_CHANGED = "es.carlosrolindez.chainedcast.playstatechanged";
    private static final String AVRCP_META_CHANGED = "es.carlosrolindez.chainedcast.metachanged";

    public static final String COMMAND_EXAMPLE = "command_example";

    public static final int COMMAND_NEXT = 1;
    public static final int COMMAND_PAUSE = 2;
    public static final int COMMAND_PLAY = 3;
    public static final int COMMAND_STOP = 4;
    public static final int COMMAND_PREVIOUS = 5;
    public static final int COMMAND_INFO = 8;

    private static final int STATE_STOPPED = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_PREPARING = 3;


    private ArrayList<CastItem> arraySong;
    private int currentTrack;
    private int currentState;

    private MediaPlayer mMediaPlayer;
    private MediaSessionCompat mMediaSessionCompat;

    PendingIntent pIntentActivity;
    PendingIntent pIntentServicePlay;
    PendingIntent pIntentServicePause;
    PendingIntent pIntentServiceStop;
//    PendingIntent pIntentServiceNext;
//    PendingIntent pIntentServicePrevious;;

    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( currentState == STATE_PLAYING) {
                pauseSong();
            }
        }
    };

    private MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {


        @Override
        public void onPlay() {
            super.onPlay();

            if( !successfullyRetrievedAudioFocus() ) {
                return;
            }
            if (arraySong== null) {
                return;
            }

            if (arraySong.size()==0)  {
                return;
            }

            if ((currentState==STATE_PREPARING) || (currentState==STATE_PLAYING)) {
                return;
            }

            if (currentState == STATE_STOPPED) {
                if (currentTrack==0)
                    currentTrack = 1;
                startSong();

            } else {
                onPrepared(mMediaPlayer);
            }
        }

        @Override
        public void onPause() {
            super.onPause();

            if( currentState == STATE_PLAYING) {
                pauseSong();
            }
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            if (currentTrack>=arraySong.size()) {
                if (currentState==STATE_STOPPED) return;
                stopSong();
            } else {
                int track = currentTrack+1;
                if (currentState!=STATE_STOPPED) stopSong();
                currentTrack = track;
                startSong();
            }
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            if (currentTrack<=1) {
                if (currentState==STATE_STOPPED) return;
                stopSong();
            } else {
                int track = currentTrack-1;
                if (currentState!=STATE_STOPPED) stopSong();
                currentTrack = track;
                startSong();
            }
        }

        @Override
        public void onSkipToQueueItem(long id) {
            super.onSkipToQueueItem(id);
            if (id>arraySong.size()) {
                if (currentState==STATE_STOPPED) return;
                stopSong();
            } else {
                if (currentState!=STATE_STOPPED) stopSong();
                currentTrack=(int) id;
                startSong();
            }
        }


        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.seekTo((int)(pos));
            }
        }

        @Override
        public void onStop() {
            Log.e(TAG,"onStop");
            super.onStop();
            stopSong();
        }

        // TODO Broadcast

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            Log.e(TAG,"onCommand");
            super.onCommand(command, extras, cb);
            if( COMMAND_EXAMPLE.equalsIgnoreCase(command) ) {
               //TODO explore that way
            }
        }


    };

    @Override
    public void onCreate() {
        super.onCreate();
        currentTrack = 0;

        initMediaPlayer();
        initMediaSession();
        initNoisyReceiver();
        initIntents();

    }


    private void initNoisyReceiver() {
        //Handles headphones coming unplugged. cannot be done through a manifest receiver
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mNoisyReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int command;
        Log.e(TAG,"OnStartCommand");
        if (intent.getExtras() != null) {
            command = intent.getIntExtra(SERVICE_COMMAND, COMMAND_INFO);

            switch (command) {
                case COMMAND_INFO:
                    Log.e(TAG,"COMMAND_INFO");
                    arraySong = intent.getExtras().getParcelableArrayList(SERVICE_SONG_ARRAY);
                    if (arraySong==null) {
                        if (currentState!=STATE_STOPPED) stopSong();
                    }
                    break;
                case COMMAND_PREVIOUS:
                    Log.e(TAG,"COMMAND_PREVIOUS");
                    mMediaSessionCompat.getController().getTransportControls().skipToPrevious();
                    break;
                case COMMAND_PLAY:
                    Log.e(TAG,"COMMAND_PLAY");
                    mMediaSessionCompat.getController().getTransportControls().play();
                    break;
                case COMMAND_PAUSE:
                    Log.e(TAG,"COMMAND_PAUSE");
                    mMediaSessionCompat.getController().getTransportControls().pause();
                    break;
                case COMMAND_NEXT:
                    Log.e(TAG,"COMMAND_NEXT");
                    mMediaSessionCompat.getController().getTransportControls().skipToNext();
                    break;
                case COMMAND_STOP:
                    Log.e(TAG,"COMMAND_STOP");
                    mMediaSessionCompat.getController().getTransportControls().stop();
                    break;
                default:
                    Log.e(TAG,"Default");
                    break;

            }
        }
        MediaButtonReceiver.handleIntent(mMediaSessionCompat, intent);
        return super.onStartCommand(intent,flags,startId);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaPlayer.stop();
        mMediaPlayer.reset();

        audioManager.abandonAudioFocus(this);
        unregisterReceiver(mNoisyReceiver);
        mMediaSessionCompat.release();
        NotificationManagerCompat.from(this).cancel(1);
    }


    public void sendSongPosition() {
        if (mMediaPlayer == null) return;

        if (currentTrack>0) {
            CastItem song = arraySong.get(currentTrack-1);

            int state;
            if (currentState==STATE_PLAYING)
                state =  PlaybackStateCompat.STATE_PLAYING;
            else
                state = PlaybackStateCompat.STATE_PAUSED;

            setMediaPlaybackState(state,  mMediaPlayer.getCurrentPosition());
            setMediaSessionMetadata(song.getDescription(), "", arraySong.size(), currentTrack, song.getDuration());
            sendMetadataBroadcast(song.getDescription());


            Runnable notification = new Runnable() {
                public void run() {
                    sendSongPosition();
                }
            };
            Handler postHandler = new Handler();
            postHandler.postDelayed(notification,1000);

        } else {
            setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
            if (arraySong!=null) {
                setMediaSessionMetadata("", "", arraySong.size(), 0, 0);
            } else {
                setMediaSessionMetadata("", "", 0, 0, 0);
            }
            sendMetadataBroadcast("");
        }

    }


    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setVolume(1.0f, 1.0f);
    }

    private void initIntents() {

        Intent intentActivity = new Intent(this, ChainedCastActivity.class);
        intentActivity.setAction(Intent.ACTION_MAIN);

//        Intent intentServicePrevious = new Intent(this, PlayingService.class);
//        intentServicePrevious.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_PREVIOUS);

        Intent intentServicePlay = new Intent(this, PlayingService.class);
        intentServicePlay.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_PLAY);

        Intent intentServicePause = new Intent(this, PlayingService.class);
        intentServicePause.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_PAUSE);

        Intent intentServiceStop = new Intent(this, PlayingService.class);
        intentServiceStop.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_STOP);

//        Intent intentServiceNext = new Intent(this, PlayingService.class);
//        intentServiceNext.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_NEXT);

        pIntentActivity = PendingIntent.getActivity(this, 0, intentActivity, PendingIntent.FLAG_UPDATE_CURRENT);
        pIntentServicePlay = PendingIntent.getService(this, 1, intentServicePlay, PendingIntent.FLAG_UPDATE_CURRENT);
        pIntentServicePause = PendingIntent.getService(this, 2, intentServicePause, PendingIntent.FLAG_UPDATE_CURRENT);
        pIntentServiceStop = PendingIntent.getService(this, 3, intentServiceStop, PendingIntent.FLAG_UPDATE_CURRENT);
//        pIntentServiceNext = PendingIntent.getService(this, 4, intentServiceNext, PendingIntent.FLAG_UPDATE_CURRENT);
//        pIntentServicePrevious = PendingIntent.getService(this, 5, intentServicePrevious, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    private void showPlayNotification() {
        MediaControllerCompat controller = mMediaSessionCompat.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle("Chained Cast")
                .setContentText(description.getTitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(pIntentActivity)
                .setDeleteIntent(pIntentServiceStop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(new NotificationCompat.Action(R.mipmap.ic_play_pause, "Pause",pIntentServicePause))
                .setStyle(new NotificationCompat.MediaStyle().setShowActionsInCompactView(0).setMediaSession(mMediaSessionCompat.getSessionToken()))
                .setSmallIcon(R.mipmap.ic_playing);
        NotificationManagerCompat.from(this).notify(1, builder.build());

    }

    private void showPauseNotification() {
        MediaControllerCompat controller = mMediaSessionCompat.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle("Chained Cast")
                .setContentText(description.getTitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(pIntentActivity)
                .setDeleteIntent(pIntentServiceStop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(new NotificationCompat.Action(R.mipmap.ic_play_pause, "Play",pIntentServicePlay))
                .setStyle(new NotificationCompat.MediaStyle().setShowActionsInCompactView(0).setMediaSession(mMediaSessionCompat.getSessionToken()))
                .setSmallIcon(R.mipmap.ic_playing);
        NotificationManagerCompat.from(this).notify(1, builder.build());
    }

    private void initMediaSession() {
        ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
        mMediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "Tag", mediaButtonReceiver, null);

        mMediaSessionCompat.setCallback(mMediaSessionCallback);
        mMediaSessionCompat.setFlags( MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS );

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        mMediaSessionCompat.setMediaButtonReceiver(pendingIntent);

        setSessionToken(mMediaSessionCompat.getSessionToken());

        mMediaSessionCompat.setActive(true);
    }



    public void sendMetadataBroadcast(String name) {
        Intent intent1 = new Intent();
        intent1.setAction(AVRCP_META_CHANGED);
        intent1.putExtra("track", name );
        sendBroadcast(intent1);

        Intent intent2 = new Intent();
        intent2.setAction(AVRCP_PLAYSTATE_CHANGED);
        intent2.putExtra("isplaying", currentState == STATE_PLAYING);
        intent2.putExtra("track",name);
        sendBroadcast(intent2);
    }

    private void setMediaPlaybackState(int state,long position) {
        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        playbackstateBuilder.setActions(state);
        if (position <0)
            playbackstateBuilder.setState(state, position, 0);
        else
            playbackstateBuilder.setState(state, position, 1);
        mMediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
    }

    private void setMediaSessionMetadata(String title, String subtitle, long numberOfTracks, long trackNumber, long songDuration) {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
        //Notification icon in card
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));

        //lock screen icon for pre lollipop
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "CR");
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber);
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, numberOfTracks);
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, songDuration);

        mMediaSessionCompat.setMetadata(metadataBuilder.build());
    }

    private boolean successfullyRetrievedAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        return result == AudioManager.AUDIOFOCUS_GAIN;
    }


    //Not important for general audio service, required for class
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        if(TextUtils.equals(clientPackageName, getPackageName())) {
            return new BrowserRoot(getString(R.string.app_name), null);
        }

        return null;
    }

    //Not important for general audio service, required for class
    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch( focusChange ) {
            case AudioManager.AUDIOFOCUS_LOSS: {
                if( mMediaPlayer != null ) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.reset();
                    }
                }
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                if( mMediaPlayer != null ) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                    }
                }
                break;
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                if( mMediaPlayer != null ) {
                    mMediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
            }
            case AudioManager.AUDIOFOCUS_GAIN: {
                if( mMediaPlayer != null ) {
                    mMediaPlayer.setVolume(1.0f, 1.0f);
                }
                break;
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (currentState>=arraySong.size()) {
            stopSong();
        } else {
            currentTrack++;
            startSong();
        }
    }

    private void startSong() {
        String URL = arraySong.get(currentTrack-1).getUrl();
        try {
            mMediaPlayer.setDataSource(URL); // setup song from URL to mediaplayer data source
            mMediaPlayer.prepareAsync(); //
            currentState = STATE_PREPARING;
        } catch (Exception e) {
            stopSong();
        }
    }

    private void stopSong() {
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        currentTrack = 0;
        currentState = STATE_STOPPED;
        NotificationManagerCompat.from(this).cancel(1);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        currentState = STATE_PLAYING;
        arraySong.get(currentTrack-1).setDuration(mp.getDuration());
        sendSongPosition();
        showPlayNotification();

    }
    private void pauseSong() {
        mMediaPlayer.pause();
        currentState = STATE_PAUSED;
        showPauseNotification();
    }
}


