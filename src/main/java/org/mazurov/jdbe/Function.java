/* Copyright (C) 2026 Oleg Mazurov

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses>. */

package org.mazurov.jdbe;

public class Function extends Histable {

    public static final int FUNC_FLAG_PLT = 1;
    public static final int FUNC_FLAG_DYNAMIC = 2;
    public static final int FUNC_FLAG_SIMULATED = 16; // not a real function, e.g. <Total>, <Unknown>

    public int flags;
    public Module module;      // pointer to module containing source
    public long size;          // size of the function in bytes
    public long img_offset;    // file offset of the image

    public Function(long id) {
        this.id = id;
    }

    public Type get_type () {
        return Histable.Type.FUNCTION;
    }

    @Override
    public long get_size() {
        return size;
    }

}
