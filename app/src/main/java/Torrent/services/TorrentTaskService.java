/*
 * Copyright (C) 2016, 2017, 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package Torrent.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.swig.settings_pack;

import net.grandcentrix.tray.core.OnTrayPreferenceChangeListener;
import net.grandcentrix.tray.core.TrayItem;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import Torrent.MainActivity;
import com.example.vijayraghavan.monthlynewmovies.R;
import Torrent.core.AddTorrentParams;
import Torrent.core.ProxySettingsPack;
import Torrent.core.Torrent;
import Torrent.core.TorrentDownload;
import Torrent.core.TorrentEngine;
import Torrent.core.TorrentEngineCallback;
import Torrent.core.TorrentFileObserver;
import Torrent.core.TorrentMetaInfo;
import Torrent.core.TorrentStateCode;
import Torrent.core.exceptions.DecodeException;
import Torrent.core.exceptions.FileAlreadyExistsException;
import Torrent.core.exceptions.FreeSpaceException;
import Torrent.core.stateparcel.AdvanceStateParcel;
import Torrent.core.stateparcel.BasicStateParcel;
import Torrent.core.stateparcel.PeerStateParcel;
import Torrent.core.stateparcel.StateParcelCache;
import Torrent.core.stateparcel.TorrentStateMsg;
import Torrent.core.stateparcel.TrackerStateParcel;
import Torrent.core.storage.TorrentStorage;
import Torrent.core.utils.FileIOUtils;
import Torrent.core.utils.TorrentUtils;
import Torrent.core.utils.Utils;
import Torrent.receivers.BootReceiver;
import Torrent.receivers.NotificationReceiver;
import Torrent.receivers.PowerReceiver;
import Torrent.receivers.WifiReceiver;
import Torrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TorrentTaskService extends Service
        implements
        TorrentEngineCallback,
        OnTrayPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentTaskService.class.getSimpleName();

    private static final int SERVICE_STARTED_NOTIFICATION_ID = 1;
    private static final int TORRENTS_MOVED_NOTIFICATION_ID = 2;
    public static final String FOREGROUND_NOTIFY_CHAN_ID = "Torrent.FOREGROUND_NOTIFY_CHAN";
    public static final String DEFAULT_CHAN_ID = "Torrent.DEFAULT_CHAN";
    public static final String SHUTDOWN_ACTION = "Torrent.SHUTDOWN_ACTION";
    private static final int SYNC_TIME = 1000; /* ms */

    private boolean isAlreadyRunning;
    private NotificationManager notifyManager;
    /* For the pause action button of foreground notify */
    private Handler updateForegroundNotifyHandler;
    private NotificationCompat.Builder foregroundNotify;
    private final IBinder binder = new LocalBinder();
    private TorrentStorage repo;
    private SettingsManager pref;
    private PowerManager.WakeLock wakeLock;
    private PowerReceiver powerReceiver = new PowerReceiver();
    private WifiReceiver wifiReceiver = new WifiReceiver();
    boolean powerReceiverRegistered = false;
    boolean wifiReceiverRegistered = false;
    /* Pause torrents (including new added) when in power settings are set power save flags */
    private AtomicBoolean pauseTorrents = new AtomicBoolean(false);
    /* Reduces sending packets due skip cache duplicates */
    private StateParcelCache<BasicStateParcel> basicStateCache = new StateParcelCache<>();
    private AtomicBoolean needsUpdateNotify = new AtomicBoolean(false);
    private Integer torrentsMoveTotal;
    private List<String> torrentsMoveSuccess;
    private List<String> torrentsMoveFailed;
    private boolean shutdownAfterMove = false;
    private boolean isNetworkOnline = false;
    private AtomicBoolean isPauseButton = new AtomicBoolean(true);
    private TorrentFileObserver fileObserver;

    public class LocalBinder extends Binder
    {
        public TorrentTaskService getService()
        {
            return TorrentTaskService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "Start " + TorrentTaskService.class.getSimpleName());
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Context context = getApplicationContext();
        repo = new TorrentStorage(context);
        pref = new SettingsManager(context);
        pref.registerOnTrayPreferenceChangeListener(this);

        makeNotifyChans(notifyManager);
        int autostartState = (pref.getBoolean(getString(R.string.pref_key_autostart),
                                              SettingsManager.Default.autostart) ?
                              PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                              PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        ComponentName bootReceiver = new ComponentName(context, BootReceiver.class);
        getPackageManager().setComponentEnabledSetting(bootReceiver, autostartState,
                                                       PackageManager.DONT_KILL_APP);

        checkPauseControl();

        if (pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                            SettingsManager.Default.cpuDoNotSleep))
            setKeepCpuAwake(true);

        TorrentEngine.getInstance().setContext(context);
        TorrentEngine.getInstance().setCallback(this);
        TorrentEngine.getInstance().setSettings(SettingsManager.readEngineSettings(context));
        TorrentEngine.getInstance().start();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        cleanTemp();
        Log.i(TAG, "Stop " + TorrentTaskService.class.getSimpleName());
    }

    private void cleanTemp()
    {
        try {
            FileIOUtils.cleanTempDir(getBaseContext());
        } catch (Exception e) {
            Log.e(TAG, "Error during setup of temp directory: ", e);
        }
    }

    private void shutdown()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        sendBroadcast(shutdownIntent);
    }

    private void stopService()
    {
        pref.unregisterOnTrayPreferenceChangeListener(this);
        if (powerReceiverRegistered) {
            powerReceiverRegistered = false;
            unregisterReceiver(powerReceiver);
        }
        if (wifiReceiverRegistered) {
            wifiReceiverRegistered = false;
            unregisterReceiver(wifiReceiver);
        }
        stopWatchDir();
        setKeepCpuAwake(false);
        stopUpdateForegroundNotify();
        TorrentEngine.getInstance().stop();
        isAlreadyRunning = false;
        repo = null;
        pref = null;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent == null)
            return START_NOT_STICKY;

        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null)
                manager.cancelAll();
        } catch (SecurityException e) {
            /* Ignore */
        }

        if (isAlreadyRunning) {
            if (intent.getAction() != null) {
                Context context = getApplicationContext();
                boolean batteryControl = pref.getBoolean(getString(R.string.pref_key_battery_control),
                                                         SettingsManager.Default.batteryControl);
                boolean customBatteryControl = pref.getBoolean(getString(R.string.pref_key_custom_battery_control),
                                                         SettingsManager.Default.customBatteryControl);
                int customBatteryControlValue = pref.getInt(getString(
                        R.string.pref_key_custom_battery_control_value), Utils.getDefaultBatteryLowLevel());
                boolean onlyCharging = pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging),
                                                       SettingsManager.Default.onlyCharging);
                boolean wifiOnly = pref.getBoolean(getString(R.string.pref_key_wifi_only),
                                                   SettingsManager.Default.wifiOnly);

                switch (intent.getAction()) {
                    case NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP:
                    case SHUTDOWN_ACTION:
                        new Thread() {
                            @Override
                            public void run()
                            {
                                stopService();
                            }
                        }.start();
                        return START_NOT_STICKY;
                    case Intent.ACTION_BATTERY_LOW:
                        if (batteryControl) {
                            boolean pause = true;
                            if (onlyCharging)
                                pause &= !Utils.isBatteryCharging(context);
                            if (wifiOnly)
                                pause &= !Utils.isWifiEnabled(context);
                            if (pause) {
                                pauseTorrents.set(true);
                                TorrentEngine.getInstance().pauseAll();
                            }
                        }
                        break;
                    case Intent.ACTION_BATTERY_OKAY:
                        if (batteryControl) {
                            boolean resume = true;
                            if (onlyCharging)
                                resume &= Utils.isBatteryCharging(context);
                            if (wifiOnly)
                               resume &= Utils.isWifiEnabled(context);
                            if (resume) {
                                pauseTorrents.set(false);
                                TorrentEngine.getInstance().resumeAll();
                            }
                        }
                        break;
                    case Intent.ACTION_BATTERY_CHANGED:
                        if (customBatteryControl) {
                            boolean pause = Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                            if(onlyCharging)
                                pause &= !Utils.isBatteryCharging(context);
                            if (wifiOnly)
                                pause &= !Utils.isWifiEnabled(context);
                            if (pause) {
                                pauseTorrents.set(true);
                                TorrentEngine.getInstance().pauseAll();
                            } else {
                                pauseTorrents.set(false);
                                TorrentEngine.getInstance().resumeAll();
                            }
                        }
                    case Intent.ACTION_POWER_CONNECTED:
                        if (onlyCharging) {
                            boolean resume = true;
                            if (wifiOnly)
                                resume &= Utils.isWifiEnabled(context);
                            if (customBatteryControl)
                                resume &= !Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                            else if (batteryControl)
                                resume &= !Utils.isBatteryLow(context);
                            if (resume) {
                                pauseTorrents.set(false);
                                TorrentEngine.getInstance().resumeAll();
                            }
                        }
                        break;
                    case Intent.ACTION_POWER_DISCONNECTED:
                        if (onlyCharging) {
                            boolean pause = true;
                            if (wifiOnly)
                                pause &= !Utils.isWifiEnabled(context);
                            if (customBatteryControl)
                                pause &= Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                            else if (batteryControl)
                                pause &= Utils.isBatteryLow(context);
                            if (pause) {
                                pauseTorrents.set(true);
                                TorrentEngine.getInstance().pauseAll();
                            }
                        }
                        break;
                    case WifiReceiver.ACTION_WIFI_ENABLED:
                        if (wifiOnly) {
                            boolean resume = true;
                            if (onlyCharging)
                                resume &= Utils.isBatteryCharging(context);
                            if (customBatteryControl)
                                resume &= !Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
                            else if (batteryControl)
                                resume &= !Utils.isBatteryLow(context);
                            if (resume) {
                                pauseTorrents.set(false);
                                TorrentEngine.getInstance().resumeAll();
                            }
                        }
                        break;
                    case WifiReceiver.ACTION_WIFI_DISABLED:
                        if (wifiOnly) {
                            boolean pause = true;
                            if (onlyCharging)
                                pause &= !Utils.isBatteryCharging(context);
                            if (batteryControl)
                                pause &= Utils.isBatteryLow(context);
                            if (pause) {
                                pauseTorrents.set(true);
                                TorrentEngine.getInstance().pauseAll();
                            }
                        }
                        break;
                    case NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME:
                        boolean pause = isPauseButton.getAndSet(!isPauseButton.get());
                        updateForegroundNotifyActions();
                        if (pause)
                            TorrentEngine.getInstance().pauseAll();
                        else
                            TorrentEngine.getInstance().resumeAll();
                        break;
                }
            }

            return START_STICKY;
        }

        /* The first start */
        isAlreadyRunning = true;

        makeForegroundNotify();
        startUpdateForegroundNotify();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && pref.getBoolean(getString(R.string.pref_key_keep_alive),
                                                                                   SettingsManager.Default.keepAlive)) {
            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());

            PendingIntent restartServicePendingIntent =
                    PendingIntent.getService(
                            getApplicationContext(),
                            1, restartServiceIntent,
                            PendingIntent.FLAG_ONE_SHOT);

            AlarmManager alarmService =
                    (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            alarmService.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1000,
                    restartServicePendingIntent);
        }
    }

    @Override
    public void onEngineStarted()
    {
        loadTorrents(repo.getAll());

        if (pref.getBoolean(getString(R.string.pref_key_use_random_port),
                            SettingsManager.Default.useRandomPort)) {
            TorrentEngine.getInstance().setRandomPort();
            /* Update port */
            pref.put(getString(R.string.pref_key_port), TorrentEngine.getInstance().getPort());
        }

        if (pref.getBoolean(getString(R.string.pref_key_proxy_changed),
                            SettingsManager.Default.proxyChanged)) {
            pref.put(getString(R.string.pref_key_proxy_changed), false);
            setProxy();
        }

        if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering),
                            SettingsManager.Default.enableIpFiltering))
            TorrentEngine.getInstance().enableIpFilter(
                    pref.getString(getString(R.string.pref_key_ip_filtering_file),
                                   SettingsManager.Default.ipFilteringFile));

        if (pref.getBoolean(getString(R.string.pref_key_watch_dir), SettingsManager.Default.watchDir))
            startWatchDir();
    }

    @Override
    public void onTorrentAdded(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null && pauseTorrents.get())
            task.pause();
    }

    @Override
    public void onTorrentStateChanged(String id)
    {
        sendBasicState(TorrentEngine.getInstance().getTask(id));
    }

    @Override
    public void onTorrentRemoved(String id)
    {
        if (basicStateCache.contains(id))
            basicStateCache.remove(id);
        LocalBroadcastManager.getInstance(this).sendBroadcast(TorrentStateMsg.makeTorrentRemovedIntent(id));
    }

    @Override
    public void onTorrentPaused(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        sendBasicState(task);

        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        if (!torrent.isPaused()) {
            torrent.setPaused(true);
            repo.update(torrent);

            if (task != null)
                task.setTorrent(torrent);
        }
    }

    @Override
    public void onTorrentResumed(String id)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null && !task.getTorrent().isPaused())
            return;

        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        torrent.setPaused(false);
        repo.update(torrent);

        if (task != null)
            task.setTorrent(torrent);
    }

    @Override
    public void onTorrentFinished(String id)
    {
        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        if (!torrent.isFinished()) {
            torrent.setFinished(true);
            makeFinishNotify(torrent);

            repo.update(torrent);

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.setTorrent(torrent);

            if (pref.getBoolean(getString(R.string.pref_key_move_after_download),
                                SettingsManager.Default.moveAfterDownload)) {
                String path = pref.getString(getString(R.string.pref_key_move_after_download_in),
                                             torrent.getDownloadPath());

                if (!torrent.getDownloadPath().equals(path))
                    torrent.setDownloadPath(path);

                moveTorrent(torrent);
            }

            if (pref.getBoolean(getString(R.string.pref_key_shutdown_downloads_complete),
                                SettingsManager.Default.shutdownDownloadsComplete) && torrentsFinished()) {
                if (torrentsMoveTotal != null)
                    shutdownAfterMove = true;
                else
                    shutdown();
            }
        }
    }

    private boolean torrentsFinished() {
        List<TorrentStateCode> inProgressStates = Arrays.asList(
                TorrentStateCode.DOWNLOADING, TorrentStateCode.PAUSED, TorrentStateCode.CHECKING,
                TorrentStateCode.DOWNLOADING_METADATA, TorrentStateCode.ALLOCATING);

        for (TorrentDownload torrentDownload : TorrentEngine.getInstance().getTasks()) {
            if (inProgressStates.contains(torrentDownload.getStateCode())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTorrentMoved(String id, boolean success)
    {
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        String name = null;

        if (task != null)
            name = task.getTorrent().getName();

        if (success) {
            if (torrentsMoveSuccess != null && name != null)
                torrentsMoveSuccess.add(name);

        } else {
            if (torrentsMoveFailed != null && name != null)
                torrentsMoveFailed.add(name);
        }

        if (torrentsMoveSuccess != null && torrentsMoveFailed != null) {
            if ((torrentsMoveSuccess.size() + torrentsMoveFailed.size()) == torrentsMoveTotal) {
                makeTorrentsMoveNotify();
                if (shutdownAfterMove)
                    shutdown();
            }
        }
    }

    @Override
    public void onIpFilterParsed(boolean success)
    {
        Toast.makeText(getApplicationContext(),
                (success ? getString(R.string.ip_filter_add_success) :
                           getString(R.string.ip_filter_add_error)),
                Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public void onTorrentMetadataLoaded(String id, Exception err)
    {
        if (err != null) {
            Log.e(TAG, "Load metadata error: ");
            Log.e(TAG, Log.getStackTraceString(err));
            if (err instanceof FreeSpaceException) {
                makeTorrentErrorNotify(repo.getTorrentByID(id).getName(), getString(R.string.error_free_space));
                needsUpdateNotify.set(true);
                LocalBroadcastManager.getInstance(this).sendBroadcast(TorrentStateMsg.makeTorrentRemovedIntent(id));
            }
            repo.delete(id);
        } else {
            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null) {
                Torrent torrent = task.getTorrent();
                repo.update(torrent);
                if (pref.getBoolean(getString(R.string.pref_key_save_torrent_files),
                                    SettingsManager.Default.saveTorrentFiles))
                    saveTorrentFileIn(torrent, pref.getString(getString(R.string.pref_key_save_torrent_files_in),
                                                              torrent.getDownloadPath()));
            }
        }
    }

    @Override
    public void onMagnetLoaded(String hash, byte[] bencode)
    {
        TorrentMetaInfo info = null;
        try {
            info = new TorrentMetaInfo(bencode);
        } catch (DecodeException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(TorrentStateMsg.makeMagnetFetchedIntent(info));
    }

    @Override
    public void onRestoreSessionError(String id)
    {
        if (id == null) {
            return;
        }

        try {
            Torrent torrent = repo.getTorrentByID(id);

            if (torrent != null) {
                makeTorrentErrorNotify(torrent.getName(), getString(R.string.restore_torrent_error));
                repo.delete(torrent);
            }

        } catch (Exception e) {
            /* Ignore */
        }
    }

    @Override
    public void onTrayPreferenceChanged(Collection<TrayItem> items)
    {
        if (pref == null)
            return;

        for (TrayItem item : items) {
            if (item.module().equals(SettingsManager.MODULE_NAME)) {
                if (item.key().equals(getString(R.string.pref_key_battery_control)) ||
                        item.key().equals(getString(R.string.pref_key_custom_battery_control)) ||
                        item.key().equals(getString(R.string.pref_key_custom_battery_control_value)) ||
                        item.key().equals(getString(R.string.pref_key_download_and_upload_only_when_charging)) ||
                        item.key().equals(getString(R.string.pref_key_wifi_only))) {
                    if (checkPauseControl())
                        TorrentEngine.getInstance().pauseAll();
                    else
                        TorrentEngine.getInstance().resumeAll();
                } else if (item.key().equals(getString(R.string.pref_key_max_download_speed))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.downloadRateLimit = pref.getInt(item.key(), SettingsManager.Default.maxDownloadSpeedLimit);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_max_upload_speed))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.uploadRateLimit = pref.getInt(item.key(), SettingsManager.Default.maxUploadSpeedLimit);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_max_connections))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.connectionsLimit = pref.getInt(item.key(), SettingsManager.Default.maxConnections);
                    s.maxPeerListSize = s.connectionsLimit;
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_max_connections_per_torrent))) {
                    TorrentEngine.getInstance().setMaxConnectionsPerTorrent(pref.getInt(item.key(),
                            SettingsManager.Default.maxConnectionsPerTorrent));
                } else if (item.key().equals(getString(R.string.pref_key_max_uploads_per_torrent))) {
                    TorrentEngine.getInstance().setMaxUploadsPerTorrent(pref.getInt(item.key(),
                            SettingsManager.Default.maxUploadsPerTorrent));
                } else if (item.key().equals(getString(R.string.pref_key_max_active_downloads))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.activeDownloads = pref.getInt(item.key(), SettingsManager.Default.maxActiveDownloads);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_max_active_uploads))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.activeSeeds = pref.getInt(item.key(), SettingsManager.Default.maxActiveUploads);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_max_active_torrents))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.activeLimit = pref.getInt(item.key(), SettingsManager.Default.maxActiveTorrents);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_cpu_do_not_sleep))) {
                    setKeepCpuAwake(pref.getBoolean(getString(R.string.pref_key_cpu_do_not_sleep),
                                    SettingsManager.Default.cpuDoNotSleep));
                } else if (item.key().equals(getString(R.string.pref_key_enable_dht))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.dhtEnabled = pref.getBoolean(getString(R.string.pref_key_enable_dht),
                                                   SettingsManager.Default.enableDht);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enable_lsd))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.lsdEnabled = pref.getBoolean(getString(R.string.pref_key_enable_lsd),
                                                   SettingsManager.Default.enableLsd);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enable_utp))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.utpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_utp),
                                                   SettingsManager.Default.enableUtp);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enable_upnp))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.upnpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_upnp),
                                                    SettingsManager.Default.enableUpnp);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enable_natpmp))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.natPmpEnabled = pref.getBoolean(getString(R.string.pref_key_enable_natpmp),
                                                      SettingsManager.Default.enableNatPmp);
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enc_mode))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    s.encryptMode = getEncryptMode();
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enc_in_connections))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    int state = settings_pack.enc_policy.pe_disabled.swigValue();
                    s.encryptInConnections = pref.getBoolean(getString(R.string.pref_key_enc_in_connections),
                                                             SettingsManager.Default.encryptInConnections);
                    if (s.encryptInConnections) {
                        state = getEncryptMode();
                    }
                    s.encryptMode = state;
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_enc_out_connections))) {
                    TorrentEngine.Settings s = TorrentEngine.getInstance().getSettings();
                    int state = settings_pack.enc_policy.pe_disabled.swigValue();
                    s.encryptOutConnections = pref.getBoolean(getString(R.string.pref_key_enc_out_connections),
                                                              SettingsManager.Default.encryptOutConnections);
                    if (s.encryptOutConnections) {
                        state = getEncryptMode();
                    }
                    s.encryptMode = state;
                    TorrentEngine.getInstance().setSettings(s);
                } else if (item.key().equals(getString(R.string.pref_key_use_random_port))) {
                    if (pref.getBoolean(getString(R.string.pref_key_use_random_port),
                                        SettingsManager.Default.useRandomPort))
                        TorrentEngine.getInstance().setRandomPort();
                    else
                        TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                                                            SettingsManager.Default.port));
                } else if (item.key().equals(getString(R.string.pref_key_port))) {
                    TorrentEngine.getInstance().setPort(pref.getInt(getString(R.string.pref_key_port),
                                                                    SettingsManager.Default.port));
                } else if (item.key().equals(getString(R.string.pref_key_enable_ip_filtering))) {
                    if (pref.getBoolean(getString(R.string.pref_key_enable_ip_filtering),
                                        SettingsManager.Default.enableIpFiltering))
                        TorrentEngine.getInstance().enableIpFilter(
                                pref.getString(getString(R.string.pref_key_ip_filtering_file),
                                               SettingsManager.Default.ipFilteringFile));
                    else
                        TorrentEngine.getInstance().disableIpFilter();
                } else if (item.key().equals(getString(R.string.pref_key_ip_filtering_file))) {
                    TorrentEngine.getInstance().enableIpFilter(
                            pref.getString(getString(R.string.pref_key_ip_filtering_file),
                                           SettingsManager.Default.ipFilteringFile));
                } else if (item.key().equals(getString(R.string.pref_key_apply_proxy))) {
                    pref.put(getString(R.string.pref_key_proxy_changed), false);
                    setProxy();
                    Toast.makeText(getApplicationContext(),
                            R.string.proxy_settings_applied,
                            Toast.LENGTH_SHORT)
                            .show();
                } else if (item.key().equals(getString(R.string.pref_key_auto_manage))) {
                    TorrentEngine.getInstance().setAutoManaged(pref.getBoolean(item.key(),
                            SettingsManager.Default.autoManage));
                } else if (item.key().equals(getString(R.string.pref_key_foreground_notify_func_button))) {
                    updateForegroundNotifyActions();
                } else if (item.key().equals(getString(R.string.pref_key_watch_dir))) {
                    if (pref.getBoolean(getString(R.string.pref_key_watch_dir), SettingsManager.Default.watchDir))
                        startWatchDir();
                    else
                        stopWatchDir();
                } else if (item.key().equals(getString(R.string.pref_key_dir_to_watch))) {
                    stopWatchDir();
                    startWatchDir();
                }
            }
        }
    }

    /*
     * Return pause state
     */
    private boolean checkPauseControl()
    {
        Context context = getApplicationContext();
        boolean batteryControl = pref.getBoolean(getString(R.string.pref_key_battery_control),
                SettingsManager.Default.batteryControl);
        boolean customBatteryControl = pref.getBoolean(getString(R.string.pref_key_custom_battery_control),
                SettingsManager.Default.customBatteryControl);
        int customBatteryControlValue = pref.getInt(getString(
                R.string.pref_key_custom_battery_control_value), Utils.getDefaultBatteryLowLevel());
        boolean onlyCharging = pref.getBoolean(getString(R.string.pref_key_download_and_upload_only_when_charging),
                SettingsManager.Default.onlyCharging);
        boolean wifiOnly = pref.getBoolean(getString(R.string.pref_key_wifi_only),
                SettingsManager.Default.wifiOnly);

        boolean registerWifiReceiver = wifiOnly;
        boolean registerPowerReceiver = false;
        if (powerReceiverRegistered)
            unregisterReceiver(powerReceiver);
        if (customBatteryControl) {
            registerReceiver(powerReceiver, PowerReceiver.getCustomFilter());
            registerPowerReceiver = true;
        } else if (batteryControl || onlyCharging) {
            registerReceiver(powerReceiver, PowerReceiver.getFilter());
            registerPowerReceiver = true;
        }

        if (registerWifiReceiver && !wifiReceiverRegistered)
            registerReceiver(wifiReceiver, WifiReceiver.getFilter());
        else if (!registerWifiReceiver && wifiReceiverRegistered)
            unregisterReceiver(wifiReceiver);

        powerReceiverRegistered = registerPowerReceiver;
        wifiReceiverRegistered = registerWifiReceiver;

        boolean pause = false;
        if (wifiOnly)
            pause = !Utils.isWifiEnabled(context);
        if (onlyCharging)
            pause |= !Utils.isBatteryCharging(context);
        if (customBatteryControl)
            pause |= Utils.isBatteryBelowThreshold(context, customBatteryControlValue);
        else if (batteryControl)
            pause |= Utils.isBatteryLow(context);
        pauseTorrents.set(pause);

        return pause;
    }

    private int getEncryptMode()
    {
        int mode = pref.getInt(getString(R.string.pref_key_enc_mode),
                               SettingsManager.Default.encryptMode(getApplicationContext()));

        if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_prefer_value))) {
            return settings_pack.enc_policy.pe_enabled.swigValue();
        } else if (mode == Integer.parseInt(getString(R.string.pref_enc_mode_require_value))) {
            return settings_pack.enc_policy.pe_forced.swigValue();
        } else {
            return settings_pack.enc_policy.pe_disabled.swigValue();
        }
    }

    private void setProxy()
    {
        ProxySettingsPack proxy = new ProxySettingsPack();
        ProxySettingsPack.ProxyType type = ProxySettingsPack.ProxyType.fromValue(
                pref.getInt(getString(R.string.pref_key_proxy_type),
                            SettingsManager.Default.proxyType));
        proxy.setType(type);
        if (type == ProxySettingsPack.ProxyType.NONE)
            TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
        proxy.setAddress(pref.getString(getString(R.string.pref_key_proxy_address),
                                        SettingsManager.Default.proxyAddress));
        proxy.setPort(pref.getInt(getString(R.string.pref_key_proxy_port),
                                  SettingsManager.Default.proxyPort));
        proxy.setProxyPeersToo(pref.getBoolean(getString(R.string.pref_key_proxy_peers_too),
                                               SettingsManager.Default.proxyPeersToo));
        proxy.setForceProxy(pref.getBoolean(getString(R.string.pref_key_force_proxy),
                                            SettingsManager.Default.forceProxy));
        if (pref.getBoolean(getString(R.string.pref_key_proxy_requires_auth),
                            SettingsManager.Default.proxyRequiresAuth)) {
            proxy.setLogin(pref.getString(getString(R.string.pref_key_proxy_login),
                                          SettingsManager.Default.proxyLogin));
            proxy.setPassword(pref.getString(getString(R.string.pref_key_proxy_password),
                                             SettingsManager.Default.proxyPassword));
        }
        TorrentEngine.getInstance().setProxy(getApplicationContext(), proxy);
    }

    private void loadTorrents(Collection<Torrent> torrents)
    {
        if (torrents == null)
            return;

        ArrayList<Torrent> loadList = new ArrayList<>();
        for (Torrent torrent : torrents) {
            if (!torrent.isDownloadingMetadata() &&
                    !TorrentUtils.torrentFileExists(getApplicationContext(), torrent.getId())) {
                Log.e(TAG, "Torrent doesn't exists: " + torrent);
                makeTorrentErrorNotify(torrent.getName(), getString(R.string.torrent_does_not_exists_error));
                repo.delete(torrent);
            } else {
                loadList.add(torrent);
            }
        }

        TorrentEngine.getInstance().restoreDownloads(loadList);
    }

    public synchronized void addTorrent(AddTorrentParams params, boolean removeFile) throws Throwable
    {
        if (params == null)
            return;

        Torrent torrent = new Torrent(params.getSha1hash(), params.getName(), params.getFilePriorities(),
                                      params.getPathToDownload(), System.currentTimeMillis());
        torrent.setTorrentFilePath(params.getSource());
        torrent.setSequentialDownload(params.isSequentialDownload());
        torrent.setPaused(params.addPaused());

        if (params.fromMagnet()) {
            byte[] bencode = TorrentEngine.getInstance().getLoadedMagnet(torrent.getId());
            TorrentEngine.getInstance().removeLoadedMagnet(torrent.getId());
            if (!repo.exists(torrent)) {
                if (bencode == null) {
                    torrent.setDownloadingMetadata(true);
                    repo.add(torrent);
                } else {
                    torrent.setDownloadingMetadata(false);
                    repo.add(torrent, bencode);
                }
            }
        } else if (new File(torrent.getTorrentFilePath()).exists()) {
            if (repo.exists(torrent)) {
                repo.replace(torrent, removeFile);
                throw new FileAlreadyExistsException();
            } else {
                repo.add(torrent, torrent.getTorrentFilePath(), removeFile);
            }
        } else {
            throw new FileNotFoundException(torrent.getTorrentFilePath());
        }
        torrent = repo.getTorrentByID(torrent.getId());
        if (torrent == null)
            throw new IOException("torrent is null");
        if (!torrent.isDownloadingMetadata()) {
            if (pref.getBoolean(getString(R.string.pref_key_save_torrent_files),
                                SettingsManager.Default.saveTorrentFiles))
                saveTorrentFileIn(torrent, pref.getString(getString(R.string.pref_key_save_torrent_files_in),
                                                          torrent.getDownloadPath()));
        }
        if (!torrent.isDownloadingMetadata() && !TorrentUtils.torrentDataExists(getApplicationContext(), torrent.getId())) {
            repo.delete(torrent);
            throw new FileNotFoundException("Torrent doesn't exists: " + torrent.getName());
        }

        TorrentEngine.getInstance().download(torrent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(TorrentStateMsg.makeTorrentAddedIntent(torrent));
    }

    private void saveTorrentFileIn(Torrent torrent, String saveDirPath)
    {
        String torrentFileName = torrent.getName() + ".torrent";
        try {
            if (!TorrentUtils.copyTorrentFile(getApplicationContext(),
                    torrent.getId(),
                    saveDirPath,
                    torrentFileName))
            {
                Log.w(TAG, "Could not save torrent file + " + torrentFileName);
            }

        } catch (Exception e) {
            Log.w(TAG, "Could not save torrent file + " + torrentFileName + ": ", e);
        }
    }

    public synchronized void pauseResumeTorrents(List<String> ids)
    {
        for (String id : ids) {
            if (id == null)
                continue;

            pauseResumeTorrent(id);
        }
    }

    public synchronized void pauseResumeTorrent(String id)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        try {
            if (task.isPaused() && !pauseTorrents.get())
                task.resume();
            else
                task.pause();

        } catch (Exception e) {
            /* Ignore */
        }
    }

    public synchronized void forceRecheckTorrents(List<String> ids)
    {
        if (ids == null) {
            return;
        }

        for (String id : ids) {
            if (id == null) {
                continue;
            }

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.forceRecheck();
        }
    }

    public synchronized void forceAnnounceTorrents(List<String> ids)
    {
        if (ids == null)
            return;

        for (String id : ids) {
            if (id == null)
                continue;

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null)
                task.requestTrackerAnnounce();
        }
    }

    public synchronized void deleteTorrents(List<String> ids, boolean withFiles)
    {
        if (ids != null) {
            for (String id : ids) {
                TorrentDownload task = TorrentEngine.getInstance().getTask(id);
                if (task != null)
                    task.remove(withFiles);
                repo.delete(id);
            }
        }

        needsUpdateNotify.set(true);
    }

    public synchronized void setTorrentName(String id, String name)
    {
        if (id == null || name == null)
            return;

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null)
            return;

        torrent.setName(name);
        repo.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);

            needsUpdateNotify.set(true);
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(TorrentStateMsg.makeUpdateTorrentsIntent(makeBasicStatesList()));
        }
    }

    private void moveTorrent(Torrent torrent)
    {
        if (torrent == null)
            return;

        repo.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(torrent.getId());
        if (task != null) {
            if (torrentsMoveSuccess == null)
                torrentsMoveSuccess = new ArrayList<>();

            if (torrentsMoveFailed == null)
                torrentsMoveFailed = new ArrayList<>();

            if (torrentsMoveTotal == null)
                torrentsMoveTotal = 0;

            ++torrentsMoveTotal;

            task.setTorrent(torrent);
            task.setDownloadPath(torrent.getDownloadPath());
        }
    }

    public synchronized void setTorrentDownloadPath(ArrayList<String> ids, String path)
    {
        if (ids == null || path == null || TextUtils.isEmpty(path))
            return;

        for (String id : ids) {
            Torrent torrent = repo.getTorrentByID(id);

            if (torrent == null)
                return;

            torrent.setDownloadPath(path);
            repo.update(torrent);

            TorrentDownload task = TorrentEngine.getInstance().getTask(id);
            if (task != null) {
                if (torrentsMoveSuccess == null)
                    torrentsMoveSuccess = new ArrayList<>();

                if (torrentsMoveFailed == null)
                    torrentsMoveFailed = new ArrayList<>();

                if (torrentsMoveTotal == null)
                    torrentsMoveTotal = 0;

                ++torrentsMoveTotal;

                task.setTorrent(torrent);
                task.setDownloadPath(path);
            }
        }
    }

    public synchronized void setSequentialDownload(String id, boolean sequential)
    {
        if (id == null)
            return;

        Torrent torrent = repo.getTorrentByID(id);

        if (torrent == null)
            return;

        torrent.setSequentialDownload(sequential);
        repo.update(torrent);

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);
            task.setSequentialDownload(sequential);
        }
    }

    public synchronized void changeFilesPriority(String id, Priority[] priorities)
    {
        if (id == null || (priorities == null || priorities.length == 0))
            return;

        Torrent torrent = repo.getTorrentByID(id);
        if (torrent == null)
            return;

        TorrentInfo ti = new TorrentInfo(new File(torrent.getTorrentFilePath()));
        if (isSelectedFilesTooBig(torrent, ti)) {
            makeTorrentErrorNotify(torrent.getName(), getString(R.string.error_free_space));
            return;
        }
        torrent.setFilePriorities(Arrays.asList(priorities));
        repo.update(torrent);
        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null) {
            task.setTorrent(torrent);
            task.prioritizeFiles(priorities);
        }
    }

    public synchronized void replaceTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.replaceTrackers(new HashSet<>(urls));
    }

    public synchronized void addTrackers(String id, ArrayList<String> urls)
    {
        if (id == null || urls == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.addTrackers(new HashSet<>(urls));
    }

    public String getMagnet(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return task.makeMagnet();
    }

    public void setUploadSpeedLimit(String id, int limit)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.setUploadSpeedLimit(limit);
    }

    public void setDownloadSpeedLimit(String id, int limit)
    {
        if (id == null)
            return;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task != null)
            task.setDownloadSpeedLimit(limit);
    }

    private void setKeepCpuAwake(boolean enable)
    {
        if (enable) {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }

            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }

        } else {
            if (wakeLock == null) {
                return;
            }

            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private BasicStateParcel makeBasicStateParcel(TorrentDownload task)
    {
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();

        return new BasicStateParcel(
                torrent.getId(),
                torrent.getName(),
                task.getStateCode(),
                task.getProgress(),
                task.getTotalReceivedBytes(),
                task.getTotalSentBytes(),
                task.getTotalWanted(),
                task.getDownloadSpeed(),
                task.getUploadSpeed(),
                task.getETA(),
                torrent.getDateAdded(),
                task.getTotalPeers(),
                task.getConnectedPeers());
    }

    public BasicStateParcel makeBasicStateParcel(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);

        return makeBasicStateParcel(task);
    }

    private ArrayList<TrackerStateParcel> makeTrackerStateParcelList(TorrentDownload task)
    {
        if (task == null)
            return null;

        List<AnnounceEntry> trackers = task.getTrackers();
        ArrayList<TrackerStateParcel> states = new ArrayList<>();

        int statusDHT = TorrentEngine.getInstance().isDHTEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusLSD = TorrentEngine.getInstance().isLSDEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;
        int statusPeX = TorrentEngine.getInstance().isPeXEnabled() ?
                TrackerStateParcel.Status.WORKING :
                TrackerStateParcel.Status.NOT_WORKING;

        states.add(new TrackerStateParcel(TrackerStateParcel.DHT_ENTRY_NAME, "", -1, statusDHT));
        states.add(new TrackerStateParcel(TrackerStateParcel.LSD_ENTRY_NAME, "", -1, statusLSD));
        states.add(new TrackerStateParcel(TrackerStateParcel.PEX_ENTRY_NAME, "", -1, statusPeX));

        for (AnnounceEntry entry : trackers) {
            String url = entry.url();
            /* Prevent duplicate */
            if (url.equals(TrackerStateParcel.DHT_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.LSD_ENTRY_NAME) ||
                    url.equals(TrackerStateParcel.PEX_ENTRY_NAME)) {
                continue;
            }

            states.add(new TrackerStateParcel(entry.swig()));
        }

        return states;
    }

    private ArrayList<PeerStateParcel> makePeerStateParcelList(TorrentDownload task)
    {
        if (task == null) {
            return null;
        }

        ArrayList<PeerStateParcel> states = new ArrayList<>();
        ArrayList<PeerInfo> peers = task.getPeers();

        TorrentStatus status = task.getTorrentStatus();

        for (PeerInfo peer : peers) {
            PeerStateParcel state = new PeerStateParcel(peer.swig(), status);
            states.add(state);
        }

        return states;
    }

    public Bundle makeBasicStatesList()
    {
        Bundle states = new Bundle();
        for (TorrentDownload task : TorrentEngine.getInstance().getTasks()) {
            if (task != null) {
                BasicStateParcel state = makeBasicStateParcel(task);
                if (!basicStateCache.contains(state))
                    basicStateCache.put(state);
                states.putParcelable(state.torrentId, state);
            }
        }

        return states;
    }

    private void sendBasicState(TorrentDownload task)
    {
        if (task == null)
            return;
        Torrent torrent = task.getTorrent();
        if (torrent == null)
            return;

        BasicStateParcel state = makeBasicStateParcel(task);
        if (basicStateCache.contains(state)) {
            return;
        } else {
            basicStateCache.put(state);
            /* Update foreground notification only if added a new package to the cache */
            needsUpdateNotify.set(true);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(TorrentStateMsg.makeUpdateTorrentIntent(state));
    }

    public AdvanceStateParcel makeAdvancedState(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        Torrent torrent = task.getTorrent();
        int[] piecesAvail = task.getPiecesAvailability();

        return new AdvanceStateParcel(
                torrent.getId(),
                task.getFilesReceivedBytes(),
                task.getTotalSeeds(),
                task.getConnectedSeeds(),
                task.getNumDownloadedPieces(),
                task.getShareRatio(),
                task.getActiveTime(),
                task.getSeedingTime(),
                task.getAvailability(piecesAvail),
                task.getFilesAvailability(piecesAvail));
    }

    public TorrentMetaInfo getTorrentMetaInfo(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        TorrentInfo ti = task.getTorrentInfo();
        TorrentMetaInfo info = null;
        try {
            if (ti != null)
                info = new TorrentMetaInfo(ti);
            else
                info = new TorrentMetaInfo(task.getTorrent().getName(), task.getInfoHash());

        } catch (DecodeException e) {
            Log.e(TAG, "Can't decode torrent info: ");
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return info;
    }

    public long getActiveTime(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getActiveTime();
    }

    public long getSeedingTime(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getSeedingTime();
    }

    public ArrayList<TrackerStateParcel> getTrackerStatesList(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return makeTrackerStateParcelList(task);
    }

    public ArrayList<PeerStateParcel> getPeerStatesList(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return makePeerStateParcelList(task);
    }

    public boolean[] getPieces(String id)
    {
        if (id == null)
            return null;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return null;

        return task.pieces();
    }

    public int getUploadSpeedLimit(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getUploadSpeedLimit();
    }

    public int getDownloadSpeedLimit(String id)
    {
        if (id == null)
            return -1;

        TorrentDownload task = TorrentEngine.getInstance().getTask(id);
        if (task == null)
            return -1;

        return task.getDownloadSpeedLimit();
    }

    public String fetchMagnet(String uri) throws Exception
    {
        return TorrentEngine.getInstance().fetchMagnet(uri);
    }

    /*
     * Used only for magnets from the magnetList (non added magnets)
     */

    public synchronized void cancelFetchMagnet(String infoHash)
    {
        if (infoHash == null)
            return;

        TorrentEngine.getInstance().cancelFetchMagnet(infoHash);
    }

    private Runnable updateForegroundNotify = new Runnable()
    {
        @Override
        public void run()
        {
            if (isAlreadyRunning) {
                boolean online = TorrentEngine.getInstance().isConnected();
                if (isNetworkOnline != online) {
                    isNetworkOnline = online;
                    needsUpdateNotify.set(true);
                }

                if (needsUpdateNotify.get()) {
                    try {
                        needsUpdateNotify.set(false);
                        if (foregroundNotify != null) {
                            foregroundNotify.setContentText((isNetworkOnline ?
                                    getString(R.string.network_online) :
                                    getString(R.string.network_offline)));
                            if (!TorrentEngine.getInstance().hasTasks())
                                foregroundNotify.setStyle(makeDetailNotifyInboxStyle());
                            else
                                foregroundNotify.setStyle(null);
                            /* Disallow killing the service process by system */
                            startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
                        }

                    } catch (Exception e) {
                            /* Ignore */
                    }
                }
            }
            updateForegroundNotifyHandler.postDelayed(this, SYNC_TIME);
        }
    };

    private void startUpdateForegroundNotify()
    {
        if (updateForegroundNotifyHandler != null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        updateForegroundNotifyHandler = new Handler();
        updateForegroundNotifyHandler.postDelayed(updateForegroundNotify, SYNC_TIME);
    }

    private void stopUpdateForegroundNotify()
    {
        if (updateForegroundNotifyHandler == null)
            return;

        updateForegroundNotifyHandler.removeCallbacks(updateForegroundNotify);
    }

    private void updateForegroundNotifyActions()
    {
        if (foregroundNotify == null)
            return;

        foregroundNotify.mActions.clear();
        foregroundNotify.addAction(makeFuncButtonAction());
        foregroundNotify.addAction(makeShutdownAction());
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    private void makeForegroundNotify() {
        /* For starting main activity */
        Intent startupIntent = new Intent(getApplicationContext(), MainActivity.class);
        startupIntent.setAction(Intent.ACTION_MAIN);
        startupIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        startupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent startupPendingIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        startupIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        foregroundNotify = new NotificationCompat.Builder(getApplicationContext(),
                FOREGROUND_NOTIFY_CHAN_ID)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentIntent(startupPendingIntent)
                .setContentTitle(getString(R.string.app_running_in_the_background))
                .setTicker(getString(R.string.app_running_in_the_background))
                .setContentText((isNetworkOnline ?
                        getString(R.string.network_online) :
                        getString(R.string.network_offline)))
                .setWhen(System.currentTimeMillis());

        foregroundNotify.addAction(makeFuncButtonAction());
        foregroundNotify.addAction(makeShutdownAction());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            foregroundNotify.setCategory(Notification.CATEGORY_SERVICE);

        /* Disallow killing the service process by system */
        startForeground(SERVICE_STARTED_NOTIFICATION_ID, foregroundNotify.build());
    }

    /*
     * For calling add torrent dialog or pause/resume torrents
     */
    private NotificationCompat.Action makeFuncButtonAction()
    {
        Intent funcButtonIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        int type = pref.getInt(getString(R.string.pref_key_foreground_notify_func_button),
                               SettingsManager.Default.funcButton(getApplicationContext()));
        int icon = 0;
        String text = null;
        if (type == Integer.parseInt(getString(R.string.pref_function_button_pause_value))) {
            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_PAUSE_RESUME);
            boolean isPause = isPauseButton.get();
            icon = (isPause ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
            text = (isPause ? getString(R.string.pause_torrent) : getString(R.string.resume_torrent));
        } else if (type == Integer.parseInt(getString(R.string.pref_function_button_add_value))) {
            funcButtonIntent.setAction(NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT);
            icon = R.drawable.ic_add_white_36dp;
            text = getString(R.string.add);
        }
        PendingIntent funcButtonPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        funcButtonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(icon, text, funcButtonPendingIntent).build();
    }

    /*
     * For shutdown activity and service
     */
    private NotificationCompat.Action makeShutdownAction()
    {
        Intent shutdownIntent = new Intent(getApplicationContext(), NotificationReceiver.class);
        shutdownIntent.setAction(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP);
        PendingIntent shutdownPendingIntent =
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        shutdownIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_power_settings_new_white_24dp,
                getString(R.string.shutdown),
                shutdownPendingIntent)
                .build();
    }

    private NotificationCompat.InboxStyle makeDetailNotifyInboxStyle()
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String titleTemplate = getString(R.string.torrent_count_notify_template);

        int downloadingCount = 0;

        for (TorrentDownload task : TorrentEngine.getInstance().getTasks()) {
            if (task == null) {
                continue;
            }

            String template;
            TorrentStateCode code = task.getStateCode();
            
            if (code == TorrentStateCode.DOWNLOADING) {
                ++downloadingCount;
                template =  getString(R.string.downloading_torrent_notify_template);
                inboxStyle.addLine(
                        String.format(
                                template,
                                task.getProgress(),
                                (task.getETA() == -1) ? Utils.INFINITY_SYMBOL :
                                        DateUtils.formatElapsedTime(task.getETA()),
                                Formatter.formatFileSize(this, task.getDownloadSpeed()),
                                task.getTorrent().getName()));

            } else if (code == TorrentStateCode.SEEDING) {
                template = getString(R.string.seeding_torrent_notify_template);
                inboxStyle.addLine(
                        String.format(
                                template,
                                getString(R.string.torrent_status_seeding),
                                Formatter.formatFileSize(this, task.getUploadSpeed()),
                                task.getTorrent().getName()));
            } else {
                String stateString = "";
                
                switch (task.getStateCode()) {
                    case PAUSED:
                        stateString = getString(R.string.torrent_status_paused);
                        break;
                    case STOPPED:
                        stateString = getString(R.string.torrent_status_stopped);
                        break;
                    case CHECKING:
                        stateString = getString(R.string.torrent_status_checking);
                        break;
                    case DOWNLOADING_METADATA:
                        stateString = getString(R.string.torrent_status_downloading_metadata);
                }

                template = getString(R.string.other_torrent_notify_template);
                inboxStyle.addLine(
                        String.format(
                                template,
                                stateString,
                                task.getTorrent().getName()));
            }
        }

        inboxStyle.setBigContentTitle(String.format(
                titleTemplate,
                downloadingCount,
                TorrentEngine.getInstance().tasksCount()));

        inboxStyle.setSummaryText((isNetworkOnline ?
            getString(R.string.network_online) :
            getString(R.string.network_offline)));

        return inboxStyle;
    }

    private void makeFinishNotify(Torrent torrent)
    {
        if (torrent == null || !pref.getBoolean(getString(R.string.pref_key_torrent_finish_notify),
                                                SettingsManager.Default.torrentFinishNotify))
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(getString(R.string.torrent_finished_notify))
                .setTicker(getString(R.string.torrent_finished_notify))
                .setContentText(torrent.getName())
                .setWhen(System.currentTimeMillis());

        if (pref.getBoolean(getString(R.string.pref_key_play_sound_notify),
                            SettingsManager.Default.playSoundNotify)) {
            Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
                                                 SettingsManager.Default.notifySound));
            builder.setSound(sound);
        }

        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify),
                            SettingsManager.Default.vibrationNotify))
            /* TODO: Make the ability to customize vibration */
            builder.setVibrate(new long[] {1000}); /* ms */

        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify),
                            SettingsManager.Default.ledIndicatorNotify)) {
            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
                                    SettingsManager.Default.ledIndicatorColorNotify(getApplicationContext()));
            builder.setLights(color, 1000, 1000); /* ms */
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(torrent.getId().hashCode(), builder.build());
    }

    private void makeTorrentErrorNotify(String name, String message)
    {
        if (name == null || message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_error_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(name)
                .setTicker(getString(R.string.torrent_error_notify_title))
                .setContentText(String.format(getString(R.string.torrent_error_notify_template), message))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_ERROR);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private void makeTorrentInfoNotify(String name, String message)
    {
        if (name == null || message == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_info_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(name)
                .setTicker(message)
                .setContentText(message)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private void makeTorrentAddedNotify(String name)
    {
        if (name == null)
            return;

        String title = getString(R.string.torrent_added_notify);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID)
                .setSmallIcon(R.drawable.ic_done_white_24dp)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary))
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(name)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setCategory(Notification.CATEGORY_STATUS);

        notifyManager.notify(name.hashCode(), builder.build());
    }

    private synchronized void makeTorrentsMoveNotify()
    {
        if (torrentsMoveTotal == null ||
                torrentsMoveSuccess == null ||
                torrentsMoveFailed == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),
                DEFAULT_CHAN_ID);

        String resultTemplate = getString(R.string.torrents_moved_content);
        int successfully = torrentsMoveSuccess.size();
        int failed = torrentsMoveFailed.size();

        builder.setContentTitle(getString(R.string.torrents_moved_title))
                .setTicker(getString(R.string.torrents_moved_title))
                .setContentText(String.format(resultTemplate, successfully, failed));

        builder.setSmallIcon(R.drawable.ic_folder_move_white_24dp)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setStyle(makeTorrentsMoveInboxStyle(torrentsMoveSuccess, torrentsMoveFailed));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_STATUS);
        }

        notifyManager.notify(TORRENTS_MOVED_NOTIFICATION_ID, builder.build());

        torrentsMoveTotal = null;
        torrentsMoveSuccess = null;
        torrentsMoveFailed = null;
    }

    private NotificationCompat.InboxStyle makeTorrentsMoveInboxStyle(List<String> torrentsMoveSuccess,
                                                                     List<String> torrentsMoveFailed)
    {
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        boolean successNotEmpty = !torrentsMoveSuccess.isEmpty();

        if (successNotEmpty) {
            inboxStyle.addLine(getString(R.string.torrents_move_inbox_successfully));
            for (String name : torrentsMoveSuccess) {
                inboxStyle.addLine(name);
            }
        }

        if (!torrentsMoveFailed.isEmpty()) {
            if (successNotEmpty) {
                inboxStyle.addLine("\n");
            }

            inboxStyle.addLine(getString(R.string.torrents_move_inbox_failed));
            for (String name : torrentsMoveFailed) {
                inboxStyle.addLine(name);
            }
        }

        return inboxStyle;
    }

    private TorrentFileObserver makeTorrentFileObserver(final String pathToDir)
    {
        return new TorrentFileObserver(pathToDir) {
            @Override
            public void onEvent(int event, @Nullable String name)
            {
                if (name == null)
                    return;

                File f = new File(pathToDir, name);
                if (!f.exists())
                    return;
                if (f.isDirectory() || !f.getName().endsWith(".torrent"))
                    return;

                addTorrent(f);
            }
        };
    }

    private void addTorrent(File file)
    {
        if (file == null)
            return;

        TorrentInfo ti;
        try {
            ti = new TorrentInfo(file);
        } catch (IllegalArgumentException e) {
            makeTorrentErrorNotify(null, getString(R.string.error_decode_torrent));
            return;
        }
        ArrayList<Priority> priorities = new ArrayList<>(Collections.nCopies(ti.files().numFiles(),Priority.NORMAL));
        String downloadPath = pref.getString(getString(R.string.pref_key_save_torrents_in),
                                             SettingsManager.Default.saveTorrentsIn);
        AddTorrentParams params = new AddTorrentParams(file.getAbsolutePath(), false, ti.infoHash().toHex(),
                                                       ti.name(), priorities, downloadPath, false, false);
        if (isAllFilesTooBig(downloadPath, ti)) {
            makeTorrentErrorNotify(file.getName(), getString(R.string.error_free_space));
            return;
        }
        try {
            addTorrent(params, true);
        } catch (Throwable e) {
            if (e instanceof FileAlreadyExistsException) {
                makeTorrentInfoNotify(params.getName(), getString(R.string.torrent_exist));
                return;
            }
            Log.e(TAG, Log.getStackTraceString(e));
            String message;
            if (e instanceof FileNotFoundException)
                message = getString(R.string.error_file_not_found_add_torrent);
            else if (e instanceof IOException)
                message = getString(R.string.error_io_add_torrent);
            else
                message = getString(R.string.error_add_torrent);
            makeTorrentErrorNotify(params.getName(), message);

            return;
        }

        makeTorrentAddedNotify(params.getName());
    }

    private void startWatchDir()
    {
        String dir = pref.getString(getString(R.string.pref_key_dir_to_watch),
                                    SettingsManager.Default.dirToWatch);
        scanTorrentsInDir(dir);
        fileObserver = makeTorrentFileObserver(dir);
        fileObserver.startWatching();
    }

    private void stopWatchDir()
    {
        if (fileObserver == null)
            return;
        fileObserver.stopWatching();
        fileObserver = null;
    }

    private void scanTorrentsInDir(String pathToDir)
    {
        if (pathToDir == null)
            return;

        File dir = new File(pathToDir);
        if (!dir.exists())
            return;
        for (File file : FileUtils.listFiles(dir, FileFilterUtils.suffixFileFilter(".torrent"), null)) {
            if (!file.exists())
                continue;
            addTorrent(file);
        }
    }

    private boolean isSelectedFilesTooBig(Torrent torrent, TorrentInfo ti)
    {
        long freeSpace = FileIOUtils.getFreeSpace(torrent.getDownloadPath());
        long filesSize = 0;
        List<Priority> priorities = torrent.getFilePriorities();
        if (priorities != null) {
            FileStorage files = ti.files();
            for (int i = 0; i < priorities.size(); i++)
                if (priorities.get(i) != Priority.IGNORE)
                    filesSize += files.fileSize(i);
        }

        return freeSpace < filesSize;
    }

    private boolean isAllFilesTooBig(String downloadPath, TorrentInfo ti)
    {
        return FileIOUtils.getFreeSpace(downloadPath) < ti.totalSize();
    }

    private void makeNotifyChans(NotificationManager notifyManager)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        ArrayList<NotificationChannel> chans = new ArrayList<>();
        NotificationChannel defaultChan = new NotificationChannel(DEFAULT_CHAN_ID, getString(R.string.def),
                                                           NotificationManager.IMPORTANCE_DEFAULT);
        Uri sound = Uri.parse(pref.getString(getString(R.string.pref_key_notify_sound),
                                             SettingsManager.Default.notifySound));
        defaultChan.setSound(sound, getNotifyAudioAttributes());

        if (pref.getBoolean(getString(R.string.pref_key_vibration_notify),
                            SettingsManager.Default.vibrationNotify)) {
            defaultChan.enableVibration(true);
            /* TODO: Make the ability to customize vibration */
            defaultChan.setVibrationPattern(new long[]{1000}); /* ms */
        } else {
            defaultChan.enableVibration(false);
        }

        if (pref.getBoolean(getString(R.string.pref_key_led_indicator_notify),
                            SettingsManager.Default.ledIndicatorNotify)) {
            int color = pref.getInt(getString(R.string.pref_key_led_indicator_color_notify),
                                    SettingsManager.Default.ledIndicatorColorNotify(getApplicationContext()));
            defaultChan.enableLights(true);
            defaultChan.setLightColor(color);
        } else {
            defaultChan.enableLights(false);
        }
        chans.add(defaultChan);
        chans.add(new NotificationChannel(FOREGROUND_NOTIFY_CHAN_ID, getString(R.string.foreground_notification),
                                          NotificationManager.IMPORTANCE_LOW));
        notifyManager.createNotificationChannels(chans);
    }

    private AudioAttributes getNotifyAudioAttributes()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;

        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }
}
