/*
 * Copyright (C) 2017 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package Torrent.core;

import android.os.FileObserver;
import android.support.annotation.Nullable;

/*
 * Watch torrent files in the specified directory and download them.
 */

public abstract class TorrentFileObserver extends FileObserver
{
    private String pathToDir;
    private static final int mask = FileObserver.CREATE | FileObserver.MOVED_TO |
                                    FileObserver.MODIFY | FileObserver.ATTRIB;

    public TorrentFileObserver(String pathToDir)
    {
        super(pathToDir, mask);

        this.pathToDir = pathToDir;
    }

    public abstract void onEvent(int event, @Nullable String name);
}
