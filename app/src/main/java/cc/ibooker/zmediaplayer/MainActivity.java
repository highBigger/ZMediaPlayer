package cc.ibooker.zmediaplayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private MediaPlayer mediaPlayer;
    private String[] musics = {"http://ibooker.cc/ibooker/musics/1234.mp3",
            "http://ibooker.cc/ibooker/musics/2345.mp3"}; // 设置音频资源（网络）
    private TextView descTv;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isPause = false;
    private int current_item = 0;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();

        // 设置音量，参数分别表示左右声道声音大小，取值范围为0~1
        mediaPlayer.setVolume(0.5f, 0.5f);

        // 设置设备进入锁状态模式-可在后台播放或者缓冲音乐-CPU一直工作
        mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

        // 如果你使用wifi播放流媒体，你还需要持有wifi锁
        WifiManager.WifiLock wifiLock = ((WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "wifilock");
        wifiLock.acquire();

        // 处理音频焦点-处理多个程序会来竞争音频输出设备
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 征对于Android 8.0
            AudioFocusRequest audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            switch (focusChange) {
                                case AudioManager.AUDIOFOCUS_GAIN:
                                    // 获取audio focus
                                    if (mediaPlayer == null)
                                        mediaPlayer = new MediaPlayer();
                                    else if (!mediaPlayer.isPlaying())
                                        mediaPlayer.start();
                                    mediaPlayer.setVolume(1.0f, 1.0f);
                                    break;
                                case AudioManager.AUDIOFOCUS_LOSS:
                                    // 失去audio focus很长一段时间，必须停止所有的audio播放，清理资源
                                    if (mediaPlayer.isPlaying())
                                        mediaPlayer.stop();
                                    mediaPlayer.release();
                                    mediaPlayer = null;
                                    break;
                                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                    // 暂时失去audio focus，但是很快就会重新获得，在此状态应该暂停所有音频播放，但是不能清除资源
                                    if (mediaPlayer.isPlaying())
                                        mediaPlayer.pause();
                                    break;
                                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                    // 暂时失去 audio focus，但是允许持续播放音频(以很小的声音)，不需要完全停止播放。
                                    if (mediaPlayer.isPlaying())
                                        mediaPlayer.setVolume(0.1f, 0.1f);
                                    break;
                            }
                        }
                    }).build();
            audioFocusRequest.acceptsDelayedFocusGain();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            // 小于Android 8.0
            int result = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            // 获取audio focus
                            if (mediaPlayer == null)
                                mediaPlayer = new MediaPlayer();
                            else if (!mediaPlayer.isPlaying())
                                mediaPlayer.start();
                            mediaPlayer.setVolume(1.0f, 1.0f);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            // 失去audio focus很长一段时间，必须停止所有的audio播放，清理资源
                            if (mediaPlayer.isPlaying())
                                mediaPlayer.stop();
                            mediaPlayer.release();
                            mediaPlayer = null;
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            // 暂时失去audio focus，但是很快就会重新获得，在此状态应该暂停所有音频播放，但是不能清除资源
                            if (mediaPlayer.isPlaying())
                                mediaPlayer.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            // 暂时失去 audio focus，但是允许持续播放音频(以很小的声音)，不需要完全停止播放。
                            if (mediaPlayer.isPlaying())
                                mediaPlayer.setVolume(0.1f, 0.1f);
                            break;
                    }
                }
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // could not get audio focus.
            }
        }

        // 处理AUDIO_BECOMING_NOISY意图
        // 当用户插着耳机听音乐突然拔掉耳机，如果没有对以上意图进行处理，音频将会通过外部扬声器来播放音频，这也许不会是用户想要的，所以我们必须对此处理。
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("BR_AUDIO_BECOMING_NOISY".equals(intent.getAction())) {
                    // 拔掉耳机时候进行相应的操作
                }
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter("BR_AUDIO_BECOMING_NOISY"));

        // 设置播放错误监听
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                mediaPlayer.reset();
                return false;
            }
        });

        // 设置播放完成监听
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                nextMusic();
            }
        });

        // 网络流媒体缓冲监听
        mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
                // i 0~100
                Log.d("Progress:", "缓存进度" + i + "%");
            }
        });

        // 异步准备完成监听
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                // 开始播放
                mediaPlayer.start();
                updateDescTv();
            }
        });

//        // 检索媒体文件
//        ContentResolver contentResolver = getContentResolver();
//        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
//        Cursor cursor = contentResolver.query(uri, null, null, null, null);
//        if (cursor == null) {
//            // query failed, handle error.
//        } else if (!cursor.moveToFirst()) {
//            // no media on the device
//        } else {
//            int titleColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
//            int idColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
//            do {
//                long thisId = cursor.getLong(idColumn);
//                String thisTitle = cursor.getString(titleColumn);
//                // ...process entry...
//            } while (cursor.moveToNext());
//        }
//        try {
//            long id = /* retrieve it from somewhere */;
//            Uri contentUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, thisId);
//            mediaPlayer = new MediaPlayer();
//            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//            mediaPlayer.setDataSource(getApplicationContext(), contentUri);
//            // ...prepare and start...
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    // 初始化控件
    private void initView() {
        descTv = (TextView) findViewById(R.id.tv_music);
        TextView preTv = (TextView) findViewById(R.id.tv_pre);
        preTv.setOnClickListener(this);
        TextView pauseTv = (TextView) findViewById(R.id.tv_pause);
        pauseTv.setOnClickListener(this);
        TextView startTv = (TextView) findViewById(R.id.tv_start);
        startTv.setOnClickListener(this);
        TextView stopTv = (TextView) findViewById(R.id.tv_stop);
        stopTv.setOnClickListener(this);
        TextView nextTv = (TextView) findViewById(R.id.tv_next);
        nextTv.setOnClickListener(this);
    }

    // 点击事件监听
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_pre:// 上一首
                descTv.setText("上一首");
                preMusic();
                break;
            case R.id.tv_pause:// 暂停
                descTv.setText("暂停");
                if (mediaPlayer.isPlaying()) {
                    isPause = true;
                    mediaPlayer.pause();
                }
                break;
            case R.id.tv_start:// 播放
                descTv.setText("播放");
                play();
                break;
            case R.id.tv_stop:// 停止
                descTv.setText("停止");
                if (mediaPlayer.isPlaying())
                    mediaPlayer.reset();
                break;
            case R.id.tv_next:// 下一首
                descTv.setText("下一首");
                nextMusic();
                break;
        }
    }

    // 播放
    private void play() {
        try {
            if (isPause) {
                mediaPlayer.start();
                updateDescTv();
            } else {
                // 重置mediaPlayer
                mediaPlayer.reset();
                // 重新加载音频资源
//                Uri uri = Uri.parse(musics[current_item]);
//                mediaPlayer.setDataSource(this, uri);
                mediaPlayer.setDataSource(musics[current_item]);
                // 准备播放（同步）-预期准备，因为setDataSource()方法之后，MediaPlayer并未真正的去装载那些音频文件，需要调用prepare()这个方法去准备音频
//                mediaPlayer.prepare();
                // 准备播放（异步）
                mediaPlayer.prepareAsync();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 下一首
    private void nextMusic() {
        current_item++;
        if (current_item >= musics.length)
            current_item = 0;
        play();
    }

    // 上一首
    private void preMusic() {
        current_item--;
        if (current_item < 0)
            current_item = musics.length - 1;
        play();
    }

    // 开启线程，修改descTv
    private void updateDescTv() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mediaPlayer != null && mediaPlayer.isPlaying()) {// 判断音频是否正在播放
                        myHandler.sendEmptyMessage(100);
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        if (executorService == null || executorService.isShutdown())
            executorService = Executors.newSingleThreadExecutor();
        executorService.execute(thread);
    }

    MyHandler myHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private WeakReference<Activity> mActivity;

        MyHandler(Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity currentActivity = (MainActivity) mActivity.get();

            switch (msg.what) {
                case 100:
                    if (currentActivity.mediaPlayer != null)
                        currentActivity.descTv.setText("播放进度：" + currentActivity.mediaPlayer.getCurrentPosition() * 100 / currentActivity.mediaPlayer.getDuration() + "%");
                    break;
            }
        }
    }

    // 开启服务播放音乐
    private void startMusicService(boolean isPlay) {
        Intent intent = new Intent(MainActivity.this, MediaPlayerService.class);
        intent.putExtra("playing", isPlay);
        startService(intent);

        // 停止服务
        // stopService(new Intent(MainActivity.this, MediaPlayerService.class));
    }
}
