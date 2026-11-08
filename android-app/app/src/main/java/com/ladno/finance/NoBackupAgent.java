package com.ladno.finance;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Defense in depth: no super/fullBackupFile calls for any full-backup transport. */
public final class NoBackupAgent extends BackupAgent {
    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) { }
    @Override public void onRestore(BackupDataInput data, int version, ParcelFileDescriptor newState) { }
    @Override public void onFullBackup(FullBackupDataOutput data) { }

    @Override public void onRestoreFile(ParcelFileDescriptor data, long size, File destination, int type,
                                        long mode, long mtime) throws IOException {
        // The platform default writes destination; consume bytes without opening any output file.
        try (FileInputStream input = new FileInputStream(data.getFileDescriptor())) {
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) break;
                remaining -= count;
            }
        }
    }
}
