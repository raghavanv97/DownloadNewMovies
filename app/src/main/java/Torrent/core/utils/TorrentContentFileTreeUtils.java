/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package Torrent.core.utils;

import Torrent.core.BencodeFileItem;
import Torrent.core.filetree.FileTree;
import Torrent.core.filetree.TorrentContentFileTree;
import Torrent.core.filetree.FileNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/*
 * The static class for create and using TorrentContentFileTree objects.
 *
 * TODO: need refactor (with BencodeFileTreeUtils)
 */

public class TorrentContentFileTreeUtils
{
    public static TorrentContentFileTree buildFileTree(List<BencodeFileItem> files)
    {
        TorrentContentFileTree root = new TorrentContentFileTree(FileTree.ROOT, 0L, FileNode.Type.DIR);
        TorrentContentFileTree parentTree = root;
        /* It allows reduce the number of iterations on the paths with equal beginnings */
        String prevPath = "";
        List<BencodeFileItem> filesCopy = new ArrayList<>(files);
        /* Sort reduces the returns number to root */
        Collections.sort(filesCopy);

        for (BencodeFileItem file : filesCopy) {
            String path;
            /*
             * Compare previous path with new path.
             * Example:
             * prev = dir1/dir2/
             * cur  = dir1/dir2/file1
             *        |________|
             *          equal
             *
             * prev = dir1/dir2/
             * cur  = dir3/file2
             *        |________|
             *         not equal
             */
            if (!prevPath.isEmpty() &&
                    file.getPath().regionMatches(true, 0, prevPath, 0, prevPath.length())) {
                /*
                 * Beginning paths are equal, remove previous path from the new path.
                 * Example:
                 * prev = dir1/dir2/
                 * cur  = dir1/dir2/file1
                 * new  = file1
                 */
                path = file.getPath().substring(prevPath.length());
            } else {
                /* Beginning paths are not equal, return to root */
                path = file.getPath();
                parentTree = root;
            }

            String[] nodes = FileIOUtils.parsePath(path);
            /*
             * Remove last node (file) from previous path.
             * Example:
             * cur = dir1/dir2/file1
             * new = dir1/dir2/
             */
            prevPath = file.getPath()
                    .substring(0, file.getPath().length() - nodes[nodes.length - 1].length());

            /* Iterates path nodes */
            for (int i = 0; i < nodes.length; i++) {
                if (!parentTree.contains(nodes[i])) {
                    /* The last leaf item is a file */
                    parentTree.addChild(
                            makeObject(file.getIndex(), nodes[i],
                                    file.getSize(), parentTree,
                                    i == (nodes.length - 1)));
                }

                TorrentContentFileTree nextParent = parentTree.getChild(nodes[i]);
                /* Skipping leaf nodes */
                if (!nextParent.isFile()) {
                    parentTree = nextParent;
                }
            }
        }

        return root;
    }

    private static TorrentContentFileTree makeObject(int index, String name,
                                              long size, TorrentContentFileTree parent,
                                              boolean isFile)
    {
        return (isFile ?
                new TorrentContentFileTree(index, name, size, FileNode.Type.FILE, parent) :
                new TorrentContentFileTree(name, 0L, FileNode.Type.DIR, parent));
    }

    public static List<TorrentContentFileTree> getFiles(TorrentContentFileTree node)
    {
        return new FileTreeDepthFirstSearch<TorrentContentFileTree>().getLeaves(node);
    }

    public static Map<Integer, TorrentContentFileTree> getFilesAsMap(TorrentContentFileTree node)
    {
        return new FileTreeDepthFirstSearch<TorrentContentFileTree>().getLeavesAsMap(node);
    }
}
