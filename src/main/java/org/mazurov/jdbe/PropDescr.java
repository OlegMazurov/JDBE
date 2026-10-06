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

import static org.mazurov.jdbe.Enums.VType_type;

import java.util.ArrayList;
import java.util.List;

public class PropDescr {
    Enums.Prop_type propID;
    String name;
    String uname;
    VType_type vtype;
    int flags;

    private List<String> stateNames;
    private List<String> stateUNames;

    public PropDescr(Enums.Prop_type propID, String name) {
        this.propID = propID;
        this.name = name != null ? name : "";
        this.uname = null;
        this.vtype = VType_type.TYPE_NONE;
        this.flags = 0;
        this.stateNames = null;
        this.stateUNames = null;
    }

    public void addState(int idx, String stname, String stuname) {
        if (idx < 0 || stname == null)
            return;
        if (stateNames == null) {
            stateNames = new ArrayList<>();
        }
        while (stateNames.size() <= idx) stateNames.add(null);
        stateNames.set(idx, stname);

        if (stateUNames == null) {
            stateUNames = new ArrayList<>();
        }
        while (stateUNames.size() <= idx) stateUNames.add(null);
        stateUNames.set(idx, stuname);
    }

    public String getStateName(int idx) {
        if (stateNames != null && idx >= 0 && idx < stateNames.size())
            return stateNames.get(idx);
        return null;
    }

    public String getStateUName(int idx) {
        if (stateUNames != null && idx >= 0 && idx < stateUNames.size())
            return stateUNames.get(idx);
        return null;
    }

    public int getMaxState() {
        return stateNames != null ? stateNames.size() : 0;
    }
}
