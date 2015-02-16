package net.binzume.android.flvplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.TextureView.SurfaceTextureListener;

public class MainActivity extends Activity implements SurfaceTextureListener {

	private FlvPlayer player = null;
	private String TEST_FLV = "/sdcard/Movies/test.flv";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		getActionBar().hide();

		final String flvPath;
		Uri data = getIntent().getData();
		if (data != null && "file".equals(data.getScheme())) {
			flvPath = data.getPath();
		} else {
			flvPath = TEST_FLV;
		}

		player = new FlvPlayer();
		player.setDataSource(flvPath);
		// ((SurfaceView)findViewById(R.id.video)).getHolder().setFormat(PixelFormat.RGBA_8888);
		// ((SurfaceView)findViewById(R.id.video)).getHolder().addCallback(this);

		((TextureView) findViewById(R.id.video)).setSurfaceTextureListener(this);
		findViewById(R.id.video).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (player.isPlaying()) {
					player.stop();
				} else {
					player.play();
				}
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
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
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
		player.setSurface(new Surface(surface));
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		player.stop();
		return false;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
		player.setSurface(new Surface(surface));
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {
	}
}
