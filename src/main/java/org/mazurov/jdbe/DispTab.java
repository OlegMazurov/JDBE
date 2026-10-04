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

public class DispTab {
    Enums.FuncListDisp_type type;             // Display type
    int order;            // Order in which tabs should appear in GUI
    boolean visible;         // Is Tab visible
    boolean available;       // Is tab available for this experiment
    Command.CmdType cmdtoken;     // command token
    int param;            // command parameter (used for memory space)

    public DispTab(Enums.FuncListDisp_type ntype, int num, boolean vis, Command.CmdType token)
    {
        type = ntype;
        order = num;
        visible = vis;
        available = true;
        cmdtoken = token;
    }

    void setAvailability (boolean val) {
        available = val;
    }

}
