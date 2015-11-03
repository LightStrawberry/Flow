package com.shokill.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.Volley;
import com.shokill.download.util.Player;
import com.shokill.net.download.DownloadProgressListener;
import com.shokill.net.download.FileDownloader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

public class MainActivity extends Activity {
	private static final int PROCESSING = 1;
	private static final int FAILURE = -1;
    private static final int IMAGE = 0;

	private EditText pathText;
	private TextView resultView;
	private Button downloadButton;
	private Button stopButton;
    private TextView music_name;
	private ProgressBar progressBar;
	private Button playBtn;
	private Player player;
	private SeekBar musicProgress;
	private Handler handler = new UIHandler();
    private NetworkImageView networkImageView;

    private final class UIHandler extends Handler {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case PROCESSING:
				progressBar.setProgress(msg.getData().getInt("size"));
				float num = (float) progressBar.getProgress()
						/ (float) progressBar.getMax();
				int result = (int) (num * 100);
				resultView.setText(result + "%");
				if (progressBar.getProgress() == progressBar.getMax()) {
					Toast.makeText(getApplicationContext(), R.string.success,
							Toast.LENGTH_LONG).show();
				}
				break;
			case FAILURE:
				Toast.makeText(getApplicationContext(), R.string.error,
						Toast.LENGTH_LONG).show();
				break;

                case IMAGE:
                    Bundle bundle = msg.getData();
                    String cover = bundle.getString("SongCover");
                    String name = bundle.getString("SongName");
                    music_name.setText(name);
                    RequestQueue mQueue = Volley.newRequestQueue(getBaseContext());
                    ImageLoader imageLoader = new ImageLoader(mQueue, new ImageLoader.ImageCache() {
                        @Override
                        public void putBitmap(String url, Bitmap bitmap) {
                        }
                        @Override
                        public Bitmap getBitmap(String url) {
                            return null;
                        }
                    });
                    networkImageView.setImageUrl(cover, imageLoader);
                    break;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		pathText = (EditText) findViewById(R.id.path);
		resultView = (TextView) findViewById(R.id.resultView);
		downloadButton = (Button) findViewById(R.id.downloadbutton);
		stopButton = (Button) findViewById(R.id.stopbutton);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		ButtonClickListener listener = new ButtonClickListener();
		downloadButton.setOnClickListener(listener);
		stopButton.setOnClickListener(listener);
		playBtn = (Button) findViewById(R.id.btn_online_play);
		playBtn.setOnClickListener(listener);
		musicProgress = (SeekBar) findViewById(R.id.music_progress);
		player = new Player(musicProgress);
        music_name = (TextView) findViewById(R.id.music_name);
		musicProgress.setOnSeekBarChangeListener(new SeekBarChangeEvent());
        networkImageView = (NetworkImageView) findViewById(R.id.network_image_view);
    }

	private final class ButtonClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			System.out.println("xxx");
			switch (v.getId()) {
			case R.id.downloadbutton:
				String path = pathText.getText().toString();
				String filename = path.substring(path.lastIndexOf('/') + 1);

				try {
					filename = URLEncoder.encode(filename, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

				path = path.substring(0, path.lastIndexOf("/") + 1) + filename;
				if (Environment.getExternalStorageState().equals(
						Environment.MEDIA_MOUNTED)) {
					File savDir = Environment.getExternalStorageDirectory();
					download(path, savDir);
				} else {
					Toast.makeText(getApplicationContext(),
							R.string.sdcarderror, Toast.LENGTH_LONG).show();
				}
				downloadButton.setEnabled(false);
				stopButton.setEnabled(true);
				break;
			case R.id.stopbutton:
				exit();
				Toast.makeText(getApplicationContext(),
						"Now thread is Stopping!!", Toast.LENGTH_LONG).show();
				downloadButton.setEnabled(true);
				stopButton.setEnabled(false);
				break;
			case R.id.btn_online_play:
                playMusic();
			}
		}

		private DownloadTask task;

		private void exit() {
			if (task != null)
				task.exit();
		}

		private void download(String path, File savDir) {
			task = new DownloadTask(path, savDir);
			new Thread(task).start();
		}

		private final class DownloadTask implements Runnable {
			private String path;
			private File saveDir;
			private FileDownloader loader;

			public DownloadTask(String path, File saveDir) {
				this.path = path;
				this.saveDir = saveDir;
			}

			public void exit() {
				if (loader != null)
					loader.exit();
			}

			DownloadProgressListener downloadProgressListener = new DownloadProgressListener() {
				@Override
				public void onDownloadSize(int size) {
					Message msg = new Message();
					msg.what = PROCESSING;
					msg.getData().putInt("size", size);
					handler.sendMessage(msg);
				}
			};

			public void run() {
				try {
					loader = new FileDownloader(getApplicationContext(), path, saveDir, 3);
					progressBar.setMax(loader.getFileSize());
					loader.download(downloadProgressListener);
				} catch (Exception e) {
					e.printStackTrace();
					handler.sendMessage(handler.obtainMessage(FAILURE));
				}
			}
		}
    }

	class SeekBarChangeEvent implements OnSeekBarChangeListener {
		int progress;

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			this.progress = progress * player.mediaPlayer.getDuration()
					/ seekBar.getMax();
            if(progress == 99)
            {
                playMusic();
            }
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			player.mediaPlayer.seekTo(progress);
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (player != null) {
            player.stop();
            player = null;
        }
    }

    private JSONObject getSong() {
        HttpClient client = new DefaultHttpClient();
        StringBuilder builder = new StringBuilder();
        String musicUrl;
        HttpGet get = new HttpGet("http://hr.hgdonline.net/music/player.php");
        try {
            HttpResponse response = client.execute(get);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            for (String s = reader.readLine(); s != null; s = reader.readLine()) {
                builder.append(s);
            }
            JSONObject myJsonObject = new JSONObject(builder.toString());
            return myJsonObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void playMusic()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject json = getSong();
                String SongUrl = json.optString("mp3");
                String SongCover = json.optString("cover");
                String SongName = json.optString("music_name");

                player.playUrl(SongUrl);
                Message msg = handler.obtainMessage();
                Bundle data = new Bundle();
                data.putString("SongCover", SongCover);
                data.putString("SongName", SongName);
                msg.what = IMAGE;
                msg.setData(data);
                handler.sendMessage(msg);
                //player.playUrl(SongUrl);
            }
        }).start();
    }
}