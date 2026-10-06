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

import java.util.Arrays;

public class DbeApplication extends Application {

    boolean rdtMode;
    private static DbeApplication INSTANCE;
    private String[] args;
    private Settings settings = new Settings();

    protected DbeApplication(String[] args) {
        INSTANCE = this;
        this.args = args;
        // Matches native's DbeSession::DbeSession(ipc_mode || rdt_mode) (DbeSession.cc:
        // 379, called from main()'s own argv scan): read_rc() restricts .gprofng.rc
        // processing to ADDPATH/PATHMAP when run under the Analyzer GUI's "-IPC" mode.
        // rdtMode (a genuine interactive non-IPC er_print session) is never set true
        // anywhere in this port yet -- see the field's own declaration -- so "-IPC" is
        // the only case reachable here today.
        boolean ipcMode = Arrays.asList(args).contains("-IPC");
        DbeSession.createSession(settings, ipcMode || rdtMode);
    }

    public static DbeApplication getInstance() {
        return INSTANCE;
    }

    public String[] getArgs() {
        return args;
    }

    public String[] initApplication(String fdhome, String licpath, ProgressUpdater func) {
        String[] data = new String[2];
        data[0] = "OK";
        data[1] = null;
        return data;
    }
}
