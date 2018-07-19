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

package Torrent.dialogs.filemanager;

import Torrent.core.filetree.FileNode;


/*
 * The class encapsulates a node and properties, that determine whether he is a file or directory.
 */

public class FileManagerNode implements FileNode<FileManagerNode> {
    public static final String PARENT_DIR = "..";
    public static final String ROOT_DIR = "/";

    private String node;
    private int nodeType;

    public FileManagerNode(String item, int itemType)
    {
        node = item;
        nodeType = itemType;
    }

    @Override
    public String getName()
    {
        return node;
    }

    @Override
    public void setName(String name)
    {
        node = name;
    }

    @Override
    public int getType()
    {
        return nodeType;
    }

    @Override
    public void setType(int type)
    {
        nodeType = type;
    }

    @Override
    public int compareTo(FileManagerNode another)
    {
        return node.compareTo(another.getName());
    }

    @Override
    public String toString()
    {
        return "FileManagerNode{" +
                "node='" + node + '\'' +
                ", nodeType=" + nodeType +
                '}';
    }
}
