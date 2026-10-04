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

import static org.mazurov.jdbe.Command.*;
import static org.mazurov.jdbe.Enums.*;

import java.util.ArrayList;
import java.util.List;

public class Settings {

    private int compare_mode;         // compare mode
    private DispTab[] tab_list;
    private List<Integer> indx_tab_order = new ArrayList<>();
    private List<Boolean> indx_tab_state = new ArrayList<>();
    private List<Boolean> mem_tab_state = new ArrayList<>();


    private boolean tabs_processed;

    public Settings() {
        // construct the master list of tabs
        buildMasterTabList ();
    }

    public Settings(Settings _settings) {
        compare_mode = _settings.compare_mode;

        tab_list = new DispTab[_settings.tab_list.length];
        for (int i = 0; i < tab_list.length; ++i) {
            DispTab dsptab = _settings.tab_list[i];
            DispTab ntab = new DispTab(dsptab.type, dsptab.order, dsptab.visible, dsptab.cmdtoken);
            ntab.setAvailability(dsptab.available);
            tab_list[i] = ntab;
        }

        indx_tab_order.addAll(_settings.indx_tab_order);
    }

    public int get_compare_mode() {
        return compare_mode;
    }
    public void set_compare_mode(int mode) {
        compare_mode = mode;
    }

    DispTab[] get_TabList() {        // Get the list of tabs for this view
        return tab_list;
    }

    private void buildMasterTabList() {
        List<DispTab> tabs = new ArrayList<>();
        int i = -1;

        // Add tabs for all the known reports
        tabs.add(new DispTab(FuncListDisp_type.DSP_DEADLOCKS, i, false, CmdType.DEADLOCK_EVNTS));
        tabs.add(new DispTab(FuncListDisp_type.DSP_FUNCTION, i, false, CmdType.FUNCS));
        tabs.add(new DispTab(FuncListDisp_type.DSP_TIMELINE, i, false, CmdType.TIMELINE));
        tabs.add(new DispTab(FuncListDisp_type.DSP_CALLTREE, i, false, CmdType.CALLTREE));
        tabs.add(new DispTab(FuncListDisp_type.DSP_CALLFLAME, i, false, CmdType.CALLFLAME));
        tabs.add(new DispTab(FuncListDisp_type.DSP_DUALSOURCE, i, false, CmdType.DUALSOURCE));
        tabs.add(new DispTab(FuncListDisp_type.DSP_SOURCE_DISASM, i, false, CmdType.SOURCEDISAM));
        tabs.add(new DispTab(FuncListDisp_type.DSP_SOURCE, i, false, CmdType.SOURCE));
        tabs.add(new DispTab(FuncListDisp_type.DSP_LINE, i, false, CmdType.HOTLINES));
        tabs.add(new DispTab(FuncListDisp_type.DSP_DISASM, i, false, CmdType.DISASM));
        tabs.add(new DispTab(FuncListDisp_type.DSP_PC, i, false, CmdType.HOTPCS));
        tabs.add(new DispTab(FuncListDisp_type.DSP_LEAKLIST, i, false, CmdType.LEAKS));
        tabs.add(new DispTab(FuncListDisp_type.DSP_IOACTIVITY, i, false, CmdType.IOACTIVITY));
        tabs.add(new DispTab(FuncListDisp_type.DSP_HEAPCALLSTACK, i, false, CmdType.HEAP));
        tabs.add(new DispTab(FuncListDisp_type.DSP_IFREQ, i, false, CmdType.IFREQ));
        tabs.add(new DispTab(FuncListDisp_type.DSP_CALLER, i, false, CmdType.GPROF));
        tabs.add(new DispTab(FuncListDisp_type.DSP_STATIS, i, false, CmdType.STATISTICS));
        tabs.add(new DispTab(FuncListDisp_type.DSP_EXP, i, false, CmdType.HEADER));

        tab_list = tabs.toArray(new DispTab[0]);
    }

    public List<Integer> get_IndxTabOrder() {
        return indx_tab_order;
    }

    public List<Boolean> get_IndxTabState() {  // Get the list and order of index tabs for this view
        return indx_tab_state;
    }

    public List<Boolean> get_MemTabState () {   // Get the list and order of memory tabs for this view
        return mem_tab_state;
    }

    public /*Cmd_status*/ void proc_tabs(boolean _rdtMode) {
//        int arg_cnt, cparam;
//        int count = 0;
//        int index;
//        DispTab *dsptab;
//        char *cmd;
        if (tabs_processed == true)
            return /*CMD_OK*/;
        tabs_processed = true;
//        if (_rdtMode == true) {
//            if (str_rtabs == NULL)
//                str_rtabs = strdup ("header");
//            cmd = str_rtabs;
//        } else {
//            if (str_tabs == NULL)
//                str_tabs = strdup ("header");
//            cmd = str_tabs;
//        }
//        if (strcmp (cmd, NTXT ("none")) == 0)
//            return CMD_OK;
//        Vector <char *> *tokens = split_str (cmd, ':');
//        for (long j = 0, sz = VecSize (tokens); j < sz; j++)
//        {
//            char *tabname = tokens->get(j);
//            // search for this tab command token
//            CmdType c = Command::get_command (tabname, arg_cnt, cparam);
//            if (c == INDXOBJ) {
//                // set the bit for this subtype
//                indx_tab_state->store (cparam, true);
//                indx_tab_order->store (cparam, count++);
//            } else {
//                // search for this tab type in the regular tabs
//                Vec_loop (DispTab*, tab_list, index, dsptab) {
//                    if (dsptab->cmdtoken == c) {
//                        dsptab->visible = true;
//                        dsptab->order = count++;
//                        break;
//                    }
//                }
//            }
//            free (tabname);
//        }
//        delete tokens;
//        return CMD_OK;
    }

    public void indxobj_define(int type, boolean state) {
        indx_tab_state.set(type, state);
        indx_tab_order.set(type, -1);
    }

}
