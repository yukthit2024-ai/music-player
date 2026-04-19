package com.vypeensoft.vypeenmusicplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int PERMISSION_REQUEST_CODE = 100;

    // UI Components
    private DrawerLayout drawerLayout;
    private View barFolder, barFile, barPlaylist;
    private TextView txtFolder, txtFile, txtPlaylist, txtNowPlaying;
    
    // Playback and Data
    private PlaybackManager playbackManager;
    private List<Folder> folders = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    
    private int currentFolderIndex = 0;
    private int currentFileIndex = 0;
    private int currentPlaylistIndex = 0;
    
    // Selection state: 0=Folder, 1=File, 2=Playlist
    private int activeBarIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupToolbar();
        setupNavigationDrawer();
        initViews();
        setupGestureDetectors();
        setupButtonListeners();

        playbackManager = new PlaybackManager(new PlaybackManager.PlaybackListener() {
            @Override
            public void onTrackFinished() {
                playNextAuto();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });

        checkPermissions();
        
        // Setup dummy playlists
        setupDummyPlaylists();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.bar_folder, R.string.bar_folder);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void initViews() {
        barFolder = findViewById(R.id.bar_folder_container);
        barFile = findViewById(R.id.bar_file_container);
        barPlaylist = findViewById(R.id.bar_playlist_container);

        txtFolder = findViewById(R.id.txt_folder_name);
        txtFile = findViewById(R.id.txt_file_name);
        txtPlaylist = findViewById(R.id.txt_playlist_name);
        txtNowPlaying = findViewById(R.id.txt_now_playing);

        // Set initial selection
        updateActiveBar(0);
    }

    private void setupGestureDetectors() {
        // Folder Bar Gestures
        GestureDetector folderDetector = new GestureDetector(this, new GestureListener(new GestureListener.SwipeCallback() {
            @Override
            public void onSwipeLeft() { nextFolder(); }
            @Override
            public void onSwipeRight() { prevFolder(); }
        }));
        barFolder.setOnTouchListener((v, event) -> {
            setActiveBar(0);
            return folderDetector.onTouchEvent(event);
        });

        // File Bar Gestures
        GestureDetector fileDetector = new GestureDetector(this, new GestureListener(new GestureListener.SwipeCallback() {
            @Override
            public void onSwipeLeft() { nextFile(); }
            @Override
            public void onSwipeRight() { prevFile(); }
        }));
        barFile.setOnTouchListener((v, event) -> {
            setActiveBar(1);
            return fileDetector.onTouchEvent(event);
        });

        // Playlist Bar Gestures
        GestureDetector playlistDetector = new GestureDetector(this, new GestureListener(new GestureListener.SwipeCallback() {
            @Override
            public void onSwipeLeft() { nextPlaylist(); }
            @Override
            public void onSwipeRight() { prevPlaylist(); }
        }));
        barPlaylist.setOnTouchListener((v, event) -> {
            setActiveBar(2);
            return playlistDetector.onTouchEvent(event);
        });

        // Clicks for play selection
        barFolder.setOnClickListener(v -> playCurrentFolder());
        barFile.setOnClickListener(v -> playCurrentFile());
        barPlaylist.setOnClickListener(v -> playCurrentPlaylist());
    }

    private void setupButtonListeners() {
        findViewById(R.id.btn_folder_left).setOnClickListener(v -> prevFolder());
        findViewById(R.id.btn_folder_right).setOnClickListener(v -> nextFolder());
        
        findViewById(R.id.btn_file_left).setOnClickListener(v -> prevFile());
        findViewById(R.id.btn_file_right).setOnClickListener(v -> nextFile());
        
        findViewById(R.id.btn_playlist_left).setOnClickListener(v -> prevPlaylist());
        findViewById(R.id.btn_playlist_right).setOnClickListener(v -> nextPlaylist());
    }

    private void setActiveBar(int index) {
        if (activeBarIndex != index) {
            updateActiveBar(index);
        }
    }

    private void updateActiveBar(int index) {
        activeBarIndex = index;
        barFolder.setSelected(index == 0);
        barFile.setSelected(index == 1);
        barPlaylist.setSelected(index == 2);
    }

    // --- Navigation Logic ---

    private void nextFolder() {
        if (folders.isEmpty()) return;
        currentFolderIndex = (currentFolderIndex + 1) % folders.size();
        currentFileIndex = 0;
        updateUI();
        playCurrentFolder();
    }

    private void prevFolder() {
        if (folders.isEmpty()) return;
        currentFolderIndex = (currentFolderIndex - 1 + folders.size()) % folders.size();
        currentFileIndex = 0;
        updateUI();
        playCurrentFolder();
    }

    private void nextFile() {
        if (folders.isEmpty()) return;
        Folder currentFolder = folders.get(currentFolderIndex);
        if (currentFileIndex < currentFolder.getTracks().size() - 1) {
            currentFileIndex++;
        } else {
            // Move to next folder
            currentFolderIndex = (currentFolderIndex + 1) % folders.size();
            currentFileIndex = 0;
        }
        updateUI();
        playCurrentFile();
    }

    private void prevFile() {
        if (folders.isEmpty()) return;
        if (currentFileIndex > 0) {
            currentFileIndex--;
        } else {
            // Move to prev folder's last file
            currentFolderIndex = (currentFolderIndex - 1 + folders.size()) % folders.size();
            currentFileIndex = folders.get(currentFolderIndex).getTracks().size() - 1;
        }
        updateUI();
        playCurrentFile();
    }

    private void nextPlaylist() {
        if (playlists.isEmpty()) return;
        currentPlaylistIndex = (currentPlaylistIndex + 1) % playlists.size();
        updateUI();
    }

    private void prevPlaylist() {
        if (playlists.isEmpty()) return;
        currentPlaylistIndex = (currentPlaylistIndex - 1 + playlists.size()) % playlists.size();
        updateUI();
    }

    // --- Playback Logic ---

    private void playCurrentFolder() {
        if (folders.isEmpty()) return;
        Folder folder = folders.get(currentFolderIndex);
        if (!folder.getTracks().isEmpty()) {
            playbackManager.play(folder.getTracks().get(0).getPath());
            updateNowPlaying(folder.getTracks().get(0).getTitle());
        }
    }

    private void playCurrentFile() {
        if (folders.isEmpty()) return;
        Track track = folders.get(currentFolderIndex).getTracks().get(currentFileIndex);
        playbackManager.play(track.getPath());
        updateNowPlaying(track.getTitle());
    }

    private void playCurrentPlaylist() {
        if (playlists.isEmpty()) return;
        Playlist playlist = playlists.get(currentPlaylistIndex);
        if (!playlist.getTracks().isEmpty()) {
            playbackManager.play(playlist.getTracks().get(0).getPath());
            updateNowPlaying(playlist.getTracks().get(0).getTitle());
        }
    }

    private void playNextAuto() {
        // Logic for auto-play next. For now, just next file.
        nextFile();
    }

    private void updateUI() {
        if (!folders.isEmpty()) {
            Folder f = folders.get(currentFolderIndex);
            txtFolder.setText(f.getName());
            if (!f.getTracks().isEmpty()) {
                txtFile.setText(f.getTracks().get(currentFileIndex).getTitle());
            } else {
                txtFile.setText("No Files");
            }
        }
        if (!playlists.isEmpty()) {
            txtPlaylist.setText(playlists.get(currentPlaylistIndex).getName());
        }
    }

    private void updateNowPlaying(String title) {
        txtNowPlaying.setText("Now Playing: " + title);
    }

    // --- Permissions ---

    private void checkPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU 
                ? Manifest.permission.READ_MEDIA_AUDIO 
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        } else {
            loadMedia();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMedia();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadMedia() {
        MediaScanner.scanMedia(this, folderList -> {
            runOnUiThread(() -> {
                this.folders = folderList;
                if (!folders.isEmpty()) {
                    updateUI();
                } else {
                    Toast.makeText(this, R.string.no_media_found, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setupDummyPlaylists() {
        playlists.add(new Playlist("Favorites"));
        playlists.add(new Playlist("Recent"));
        playlists.add(new Playlist("Road Trip"));
        updateUI();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.nav_settings) {
             startActivity(new Intent(this, SettingsActivity.class));
        }
        drawerLayout.closeDrawers();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackManager != null) {
            playbackManager.release();
        }
    }
}
