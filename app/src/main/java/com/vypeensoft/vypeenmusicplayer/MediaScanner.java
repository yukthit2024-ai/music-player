package com.vypeensoft.vypeenmusicplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans the device storage for MP3 files using MediaStore.
 */
public class MediaScanner {

    public interface ScanCallback {
        void onScanComplete(List<Folder> folders);
    }

    public static void scanMedia(Context context, ScanCallback callback) {
        new Thread(() -> {
            android.content.SharedPreferences prefs = context.getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE);
            String savedPaths = prefs.getString("scan_folders", "");
            String[] rawPaths = savedPaths.split("\n");
            final java.util.List<String> allowedPaths = new java.util.ArrayList<>();
            for (String p : rawPaths) {
                if (!p.trim().isEmpty()) {
                    allowedPaths.add(p.trim());
                }
            }

            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

            Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);
            Map<String, Folder> folderMap = new HashMap<>();

            if (cursor != null && cursor.moveToFirst()) {
                int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

                do {
                    String thisTitle = cursor.getString(titleColumn);
                    String thisPath = cursor.getString(pathColumn);

                    boolean isAllowed = true;
                    if (!allowedPaths.isEmpty()) {
                        isAllowed = false;
                        for (String allowedPath : allowedPaths) {
                            if (thisPath.startsWith(allowedPath)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }

                    if (!isAllowed) {
                        continue;
                    }

                    File file = new File(thisPath);
                    String folderPath = file.getParent();
                    String folderName = "Unknown";
                    if (folderPath != null) {
                        folderName = new File(folderPath).getName();
                    }

                    Track track = new Track(thisPath, thisTitle, folderPath);

                    Folder folder = folderMap.get(folderPath);
                    if (folder == null) {
                        folder = new Folder(folderPath, folderName);
                        folderMap.put(folderPath, folder);
                    }
                    folder.addTrack(track);

                } while (cursor.moveToNext());
                cursor.close();
            }

            List<Folder> folderList = new ArrayList<>(folderMap.values());
            // Sort folders by name
            Collections.sort(folderList, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

            callback.onScanComplete(folderList);
        }).start();
    }
}
