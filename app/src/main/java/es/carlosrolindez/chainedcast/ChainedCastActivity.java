package es.carlosrolindez.chainedcast;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;



public class ChainedCastActivity extends AppCompatActivity {

    private static final String TAG = "ChainedCastActivity";
    public static final String BUNDLE_ARRAY = "es.carlosrolindez.ChainedCastActivity.URLlist";

    private static final int STATE_PAUSED = 0;
    private static final int STATE_PLAYING = 1;

    private int mCurrentState;
    private String mCurrentName;
    private long mCurrentDuration;  // in mSec

    private ArrayList<CastItem> audioList;
    private CastItemAdaptor audioAdapter;
    private SeekBar seekBarProgress;

    private MediaBrowserCompat mMediaBrowserCompat;
    private MediaControllerCompat mMediaControllerCompat;

    private Button mPlayPauseToggleButton;
    private LayoutInflater inflater;

    private MediaBrowserCompat.ConnectionCallback mMediaBrowserCompatConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {

        @Override
        public void onConnected() {
            super.onConnected();
            try {
                mMediaControllerCompat = new MediaControllerCompat(ChainedCastActivity.this, mMediaBrowserCompat.getSessionToken());
                mMediaControllerCompat.registerCallback(mMediaControllerCompatCallback);
                setSupportMediaController(mMediaControllerCompat);
    //            getSupportMediaController().getTransportControls().playFromMediaId(String.valueOf(R.raw.warner_tautz_off_broadway), null);

            } catch( RemoteException e ) {

            }
        }
    };

    private MediaControllerCompat.Callback mMediaControllerCompatCallback = new MediaControllerCompat.Callback() {

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            if( state == null ) {
                return;
            }

            switch( state.getState() ) {
                case PlaybackStateCompat.STATE_PLAYING: {
                    mCurrentState = STATE_PLAYING;
                    break;
                }
                default:
                {
                    mCurrentState = STATE_PAUSED;
                    break;
                }
            }
            long position = state.getPosition();
            if (mCurrentDuration>0) {
                seekBarProgress.setProgress((int) (100*position / mCurrentDuration));

            }


        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            mCurrentDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
            mCurrentName = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            if (audioList!=null) {
                if (audioList.size() > 0) {
                    for (CastItem audio : audioList) {
                        if (audio.getDescription().equals(mCurrentName)) {
                            if (audio.getDuration() != mCurrentDuration) {
                                audio.setDuration(mCurrentDuration);
                                audioAdapter.notifyDataSetChanged();
                            }
                            break;
                        }
                    }
                }
                audioAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chainedcast);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setIcon(R.mipmap.ic_launcher);

 //       actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        if (savedInstanceState != null) {
            audioList = savedInstanceState.getParcelableArrayList(BUNDLE_ARRAY);
        } else {
            Intent myIntent = getIntent();
            if (myIntent.getAction().equals(Intent.ACTION_VIEW)) {
                Uri url = myIntent.getData();
                CastItem item;
                audioList = new ArrayList<>();
                item = new CastItem("Audio 1", url.toString());
                audioList.add(item);
            }
        }

        mCurrentName = null;
        mCurrentDuration = 0;


        inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        audioAdapter = new CastItemAdaptor();

        ListView mListView = (ListView)findViewById(R.id.List);
        mListView.setAdapter(audioAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                sendArrayToService(audioList);
                getSupportMediaController().getTransportControls().skipToQueueItem(position+1);
            }
        });

        seekBarProgress = (SeekBar)findViewById(R.id.SeekBar);
        seekBarProgress.setMax(99); // It means 100% .0-99
        seekBarProgress.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                SeekBar sb = (SeekBar) v;
                sendArrayToService(audioList);
                getSupportMediaController().getTransportControls().seekTo(mCurrentDuration*sb.getProgress()/100);
                return false;
            }
        });

        ImageButton playButton = (ImageButton) findViewById(R.id.play_pause);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendArrayToService(audioList);
                if (mCurrentState == STATE_PLAYING) {
                    getSupportMediaController().getTransportControls().pause();
                } else {
                    getSupportMediaController().getTransportControls().play();
                }

            }
        });

        ImageButton previousButton = (ImageButton) findViewById(R.id.previous);
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendArrayToService(audioList);
                getSupportMediaController().getTransportControls().skipToPrevious();
            }
        });

        ImageButton nextButton = (ImageButton) findViewById(R.id.next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendArrayToService(audioList);
                getSupportMediaController().getTransportControls().skipToNext();
            }
        });

        mMediaBrowserCompat = new MediaBrowserCompat(this, new ComponentName(this, PlayingService.class),
                mMediaBrowserCompatConnectionCallback, getIntent().getExtras());

        mMediaBrowserCompat.connect();


    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction().equals(Intent.ACTION_VIEW)) {
            Uri url = intent.getData();
            CastItem item;
            if (audioList == null) {
                audioList = new ArrayList<>();
                item = new CastItem("Audio 1", url.toString());
                audioList.add(item);
            } else if (!CastItem.isIncluded(audioList,url.toString())){
                int length = audioList.size();
                item = new CastItem("Audio " + (length + 1), url.toString());
                audioList.add(item);
            }
            else {
                return;
            }
            sendArrayToService(audioList);
            audioAdapter.notifyDataSetChanged();

        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaBrowserCompat.disconnect();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BUNDLE_ARRAY,audioList);

    }

    public void sendArrayToService(ArrayList<CastItem> list) {
        Intent intent = new Intent(this, PlayingService.class);
        intent.putExtra(PlayingService.SERVICE_COMMAND, PlayingService.COMMAND_INFO);
        intent.putParcelableArrayListExtra(PlayingService.SERVICE_SONG_ARRAY, list);
        startService(intent);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.chainedcast, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        switch (item.getItemId())
        {
            case R.id.add:

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Source");

                final EditText input = new EditText(this);

                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip()) {
                    if (clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN)) {
                        CharSequence pasteData = "";
                        ClipData.Item clipItem = clipboard.getPrimaryClip().getItemAt(0);
                        pasteData = clipItem.getText();
                        input.setText(pasteData);

                    }

                }

                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    String URL;
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        URL = input.getText().toString();
                        if (URL.isEmpty()) return;
                        CastItem item;
                        if (audioList == null) {
                            audioList = new ArrayList<>();
                            item = new CastItem("Audio 1", URL);


                        } else if (!CastItem.isIncluded(audioList, URL)){
                            int length = audioList.size();
                            item = new CastItem("Audio " + (length + 1), URL);
                        } else
                            return;
                        audioList.add(item);
                        audioAdapter.notifyDataSetChanged();
                        sendArrayToService(audioList);
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();

                return true;

            case R.id.scan:
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_BROWSER);
                startActivity(i);

                return true;

            case R.id.reset:
                audioList.clear();
                audioList = null;
                audioAdapter.notifyDataSetChanged();
                sendArrayToService(audioList);

                return true;

            case R.id.action_settings:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class CastItemAdaptor extends BaseAdapter {

        @Override
        public int getCount()
        {
            if (audioList == null)
                return 0;
            else
                return audioList.size();
        }

        @Override
        public Object getItem(int position)
        {
            if (audioList == null)
                return null;
            else
                return audioList.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            final CastItem audio =  audioList.get(position);

            if (audioList == null)
                return null;

            View localView = convertView;

            if (localView==null)
            {

                localView = inflater.inflate(R.layout.list_row, parent, false);
            }

            TextView audioName = (TextView)localView.findViewById(R.id.audio_name);
            TextView audioURL = (TextView)localView.findViewById(R.id.audio_url);
            TextView audioDuration = (TextView)localView.findViewById(R.id.duration);
            RelativeLayout layout = (RelativeLayout)localView.findViewById(R.id.main_layout);

            audioName.setText(audio.getDescription());
            audioURL.setText(audio.getUrl());
            if(audio.getDuration() == 0) {
                audioDuration.setText("");
            } else {
                audioDuration.setText(CastItem.formatedTime(audio.getDuration()));
            }

            if (mCurrentName==null) {
                layout.setBackgroundResource(R.drawable.notconnected_selector);
                audioName.setTextColor(0xff040404);
                audioURL.setTextColor(0xff040404);
                audioDuration.setTextColor(0xff040404);
            } else if (!mCurrentName.equals(audio.getDescription())) {
                layout.setBackgroundResource(R.drawable.notconnected_selector);
                audioName.setTextColor(0xff040404);
                audioURL.setTextColor(0xff040404);
                audioDuration.setTextColor(0xff040404);
            } else if (mCurrentState==STATE_PLAYING){
                layout.setBackgroundColor(Color.BLACK);
                audioName.setTextColor(Color.WHITE);
                audioURL.setTextColor(Color.WHITE);
                audioDuration.setTextColor(Color.WHITE);
            }

            return localView;
        }
    }
}
