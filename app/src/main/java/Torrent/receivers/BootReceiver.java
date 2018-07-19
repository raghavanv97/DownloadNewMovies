/*
 * Copyright (C) 2016-2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package Torrent.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.vijayraghavan.monthlynewmovies.R;
import Torrent.services.TorrentTaskService;
import Torrent.settings.SettingsManager;

/*
 * The receiver for autostart service.
 */

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SettingsManager pref = new SettingsManager(context.getApplicationContext());
            if (pref.getBoolean(context.getString(R.string.pref_key_autostart), SettingsManager.Default.autostart) &&
                pref.getBoolean(context.getString(R.string.pref_key_keep_alive), SettingsManager.Default.keepAlive)) {
                /*
                 * Workaround for start service in Android 8+ after BOOT_COMPLETED.
                 * We have a window of time to get around to calling startForeground() before we get ANR,
                 * if work is longer than a millisecond but less than a few seconds.
                 */
                Intent i = new Intent(context, TorrentTaskService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(i);
                else
                    context.startService(i);
            }
        }
    }
}
