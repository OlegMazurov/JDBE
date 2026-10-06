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
import static org.mazurov.jdbe.DbeStructs.*;
import static org.mazurov.jdbe.Enums.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Settings {

    private int limit;                // print limit
    public PrintMode print_mode;// print mode
    public String str_printmode;
    public char print_delim;         // the delimiter, if print mode = PM_DELIM_SEP_LIST
    private CmpMode compare_mode;         // compare mode

    private VMode view_mode = VMode.VMODE_USER;

    private boolean en_desc;          // whether descendant processes are enabled
    private String en_desc_usr;       // the raw string used to set en_desc
    private Pattern en_desc_cmp;      // compiled match specification, if any (from "=...")

    private boolean ignore_fs_warn;        // ignore file-system recording warning
    private boolean ignore_no_xhwcprof;    // ignore no -xhwcprof data in dataspace

    private List<PathMap> pathmaps = new ArrayList<>();

    public Histable.NameFormat name_format = Histable.NameFormat.NA; // long/short/mangled naming for C++/Java

    private LibExpand lo_expand_default = LibExpand.LIBEX_SHOW;  // default show/hide/API-only setting
    private List<LoExpand> lo_expands = new ArrayList<>();       // per-library overrides of the default
    private boolean is_loexpand_default = true;                  // whether the above still match the .rc file

    private DispTab[] tab_list;
    private List<Integer> indx_tab_order = new ArrayList<>();
    private List<Boolean> indx_tab_state = new ArrayList<>();
    private List<Boolean> mem_tab_state = new ArrayList<>();


    private boolean tabs_processed;
    private String str_tabs;   // colon-separated default tab list (CLI/rc "tabs" command)
    private String str_rtabs;  // same, for rdtMode

    // Mirrors native's Settings.h accumulated-rc-string fields (Settings.cc:121-140),
    // restricted to the ones this port's read_rc()/set_rc() actually recognize --
    // see set_rc()'s doc comment for which .gprofng.rc commands are (and are not)
    // acted on. Each is also just the "has this already been set by an earlier .rc
    // file" guard set_rc() checks before honoring a later file's directive (matching
    // native's own "!str_x || override" pattern), except str_dmetrics/str_dsort, which
    // accumulate (colon-joined) across every .rc file read, same as native.
    public String str_search_path;
    public String str_dmetrics;
    public String str_dsort;
    private String str_vmode;
    private String str_limit;
    private String str_compare;

    public Settings() {
        // construct the master list of tabs
        buildMasterTabList ();
    }

    public Settings(Settings settings) {
        compare_mode = settings.compare_mode;
        view_mode = settings.view_mode;
        en_desc = settings.en_desc;
        en_desc_usr = settings.en_desc_usr;
        en_desc_cmp = settings.en_desc_cmp;
        ignore_fs_warn = settings.ignore_fs_warn;
        ignore_no_xhwcprof = settings.ignore_no_xhwcprof;
        name_format = settings.name_format;
        lo_expand_default = settings.lo_expand_default;
        lo_expands = new ArrayList<>(settings.lo_expands);
        is_loexpand_default = settings.is_loexpand_default;
        tabs_processed = settings.tabs_processed;

        // Mirrors native's Settings copy ctor (Settings.cc:209-266) also copying these
        // (limit/print_mode/pathmaps were previously missing here -- a real, if latent,
        // gap: without them, a .gprofng.rc-configured print limit/pathmap/search-path
        // would never reach a per-view Settings clone).
        limit = settings.limit;
        print_mode = settings.print_mode;
        print_delim = settings.print_delim;
        str_printmode = settings.str_printmode;
        pathmaps = new ArrayList<>(settings.pathmaps);
        str_search_path = settings.str_search_path;
        str_dmetrics = settings.str_dmetrics;
        str_dsort = settings.str_dsort;
        str_vmode = settings.str_vmode;
        str_limit = settings.str_limit;
        str_compare = settings.str_compare;
        str_tabs = settings.str_tabs;
        str_rtabs = settings.str_rtabs;

        tab_list = new DispTab[settings.tab_list.length];
        for (int i = 0; i < tab_list.length; ++i) {
            DispTab dsptab = settings.tab_list[i];
            DispTab ntab = new DispTab(dsptab.type, dsptab.order, dsptab.visible, dsptab.cmdtoken);
            ntab.setAvailability(dsptab.available);
            tab_list[i] = ntab;
        }

        indx_tab_order.addAll(settings.indx_tab_order);
        indx_tab_state.addAll(settings.indx_tab_state);
    }

    public CmpMode get_compare_mode() {
        return compare_mode;
    }
    public void set_compare_mode(CmpMode mode) {
        compare_mode = mode;
    }

    public VMode get_view_mode() {
        return view_mode;
    }

    public Command.Cmd_status set_view_mode(String arg, boolean rc) {
        if (arg.equalsIgnoreCase("user"))
            view_mode = VMode.VMODE_USER;
        else if (arg.equalsIgnoreCase("expert"))
            view_mode = VMode.VMODE_EXPERT;
        else if (arg.equalsIgnoreCase("machine"))
            view_mode = VMode.VMODE_MACHINE;
        else if (!rc)
            return Command.Cmd_status.CMD_BAD_ARG;
        return Command.Cmd_status.CMD_OK;
    }

    public Command.Cmd_status set_en_desc(String arg, boolean rc) {
        Pattern regexDesc = null;
        if (arg.equalsIgnoreCase("on")) {
            en_desc = true;
        } else if (arg.equalsIgnoreCase("off")) {
            en_desc = false;
        } else if (arg.length() > 1 && arg.charAt(0) == '=') {
            // user has specified a string matching specification
            try {
                regexDesc = Pattern.compile("^" + arg.substring(1) + "$");
            } catch (PatternSyntaxException e) {
                // syntax error in parsing string
                return rc ? Command.Cmd_status.CMD_OK : Command.Cmd_status.CMD_BAD_ARG;
            }
            en_desc = true;
        } else {
            return rc ? Command.Cmd_status.CMD_OK : Command.Cmd_status.CMD_BAD_ARG;
        }
        en_desc_usr = arg;
        en_desc_cmp = regexDesc;
        return Command.Cmd_status.CMD_OK;
    }

    // See if a descendant matches either the lineage or the executable name
    public boolean check_en_desc(String lineage, String targname) {
        if (en_desc_cmp == null)
            return en_desc; // no specification was set, use the binary on/off value
        if (lineage == null)
            return en_desc; // user doesn't care about specification
        if (en_desc_cmp.matcher(lineage).matches())
            return true;
        if (targname == null)
            return false;
        return en_desc_cmp.matcher(targname).matches();
    }

    public void set_ignore_fs_warn(boolean v) {
        ignore_fs_warn = v;
    }

    public boolean get_ignore_fs_warn() {
        return ignore_fs_warn;
    }

    public void set_ignore_no_xhwcprof(boolean v) {
        ignore_no_xhwcprof = v;
    }

    public boolean get_ignore_no_xhwcprof() {
        return ignore_no_xhwcprof;
    }

    public List<PathMap> get_pathmaps() {
        return pathmaps;
    }

    public void set_name_format(int fname_fmt, boolean soname_fmt) {
        name_format = Histable.make_fmt(fname_fmt, soname_fmt);
    }

    public Histable.NameFormat get_name_format() {
        return name_format;
    }

    public Command.Cmd_status set_name_format(String arg) {
        int colon = arg.indexOf(':');
        String fmt = colon >= 0 ? arg.substring(0, colon) : arg;
        int len = fmt.length();
        Histable.NameFormat fname_fmt;
        if ("long".regionMatches(true, 0, fmt, 0, len))
            fname_fmt = Histable.NameFormat.LONG;
        else if ("short".regionMatches(true, 0, fmt, 0, len))
            fname_fmt = Histable.NameFormat.SHORT;
        else if ("mangled".regionMatches(true, 0, fmt, 0, len))
            fname_fmt = Histable.NameFormat.MANGLED;
        else
            return Command.Cmd_status.CMD_BAD_ARG;

        boolean soname_fmt = false;
        if (colon >= 0) {
            String soname = arg.substring(colon + 1);
            if (soname.equalsIgnoreCase("soname"))
                soname_fmt = true;
            else if (soname.equalsIgnoreCase("nosoname"))
                soname_fmt = false;
            else
                return Command.Cmd_status.CMD_BAD_ARG;
        }
        name_format = Histable.make_fmt(fname_fmt.value, soname_fmt);
        return Command.Cmd_status.CMD_OK;
    }

    // the last path component
    private static String get_basename(String name) {
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    public LibExpand get_lo_setting(String name) {
        String lo_name = get_basename(name);
        for (LoExpand loe : lo_expands) {
            if (loe.libname().equals(lo_name))
                return loe.expand();
        }
        return lo_expand_default;
    }

    // returns true if any change was made
    public boolean set_libexpand(String cov, LibExpand expand, boolean rc) {
        boolean change = false;
        if (cov == null || cov.equalsIgnoreCase(Command.ALL_CMD)) {
            // set all libraries
            if (lo_expand_default != expand) {
                lo_expand_default = expand;
                change = true;
                is_loexpand_default = false;
            }
            // and force any explicit settings to match, too
            for (int i = 0; i < lo_expands.size(); i++) {
                LoExpand loe = lo_expands.get(i);
                if (loe.expand() != expand) {
                    lo_expands.set(i, new LoExpand(loe.libname(), expand));
                    change = true;
                    is_loexpand_default = false;
                }
            }
        } else {
            // parsing coverage
            for (String lo_name : cov.split(",")) {
                String newname = get_basename(lo_name);
                boolean found = false;
                for (int i = 0; i < lo_expands.size(); i++) {
                    LoExpand loe = lo_expands.get(i);
                    if (loe.libname().equals(newname)) {
                        if (loe.expand() != expand && !rc) {
                            lo_expands.set(i, new LoExpand(newname, expand));
                            change = true;
                            is_loexpand_default = false;
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    lo_expands.add(new LoExpand(newname, expand));
                    change = true;
                    is_loexpand_default = false;
                }
            }
        }
        return change;
    }

    // reset all shared object expands back to .rc file defaults, as stored in the
    // DbeSession's Settings; returns true if any change was made
    public boolean set_libdefaults() {
        if (is_loexpand_default)
            return false; // no change
        Settings sessionSettings = DbeSession.getInstance().get_settings();
        lo_expand_default = sessionSettings.lo_expand_default;
        lo_expands = new ArrayList<>(sessionSettings.lo_expands);
        is_loexpand_default = true;
        return true;
    }

    // strip trailing '/' characters
    private static String get_canonical_name(String fname) {
        int len = fname.length();
        while (len > 0 && fname.charAt(len - 1) == '/')
            len--;
        return fname.substring(0, len);
    }

    public static String add_pathmap(List<PathMap> v, String from, String to) {
        if (from == null || to == null)
            return "Pathmap can have neither from nor to as NULL\n";
        if (from.equals(to))
            return "Pathmap from must differ from to\n";
        String old_prefix = get_canonical_name(from);
        String new_prefix = get_canonical_name(to);

        for (PathMap pmp : v) {
            if (pmp.old_prefix().equals(old_prefix) && pmp.new_prefix().equals(new_prefix))
                return String.format("Pathmap from `%s' to `%s' already exists\n", old_prefix, new_prefix);
        }
        PathMap thismap = new PathMap(old_prefix, new_prefix);
        v.add(thismap);
        return null;
    }

    public int get_limit () {
        return limit;
    }

    public String set_limit(int limit) {
        this.limit = limit;
        return null;
    }

    public String set_printmode(String arg) {
        String errStr = "The argument to 'printmode' must be 'text' or 'html' or a single-character";
        if (arg == null)
            return errStr;
        if (arg.length() == 1) {
            print_mode = PrintMode.PM_DELIM_SEP_LIST;
            print_delim = arg.charAt(0);
        }
        else if (arg.equalsIgnoreCase("text"))
            print_mode = PrintMode.PM_TEXT;
        else if (arg.equalsIgnoreCase("html"))
            print_mode = PrintMode.PM_HTML;
        else
            return errStr;
        str_printmode = arg;
        return null;
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

    // Mirrors native's Settings::proc_tabs (Settings.cc:1336-1389): resolves the
    // colon-separated default tab list (set via a "tabs"/"rtabs" command, not ported --
    // no CLI/rc-file directive sets str_tabs/str_rtabs in this port, so they're always
    // null) into which DispTab/index-object entries start visible. With nothing to set
    // them, this always falls through to native's own "header" fallback, matching
    // native's actual default behavior when no tabs command is configured either.
    public /*Cmd_status*/ void proc_tabs(boolean _rdtMode) {
        if (tabs_processed)
            return /*CMD_OK*/;
        tabs_processed = true;
        String cmd;
        if (_rdtMode) {
            if (str_rtabs == null)
                str_rtabs = "header";
            cmd = str_rtabs;
        } else {
            if (str_tabs == null)
                str_tabs = "header";
            cmd = str_tabs;
        }
        if (cmd.equals("none"))
            return /*CMD_OK*/;
        int count = 0;
        for (String tabname : cmd.split(":")) {
            CommandLookup lookup = Command.get_command(tabname);
            if (lookup.type() == CmdType.INDXOBJ) {
                indx_tab_state.set(lookup.cparam(), true);
                indx_tab_order.set(lookup.cparam(), count++);
            } else {
                for (DispTab dsptab : tab_list) {
                    if (dsptab.cmdtoken == lookup.type()) {
                        dsptab.visible = true;
                        dsptab.order = count++;
                        break;
                    }
                }
            }
        }
    }

    // Mirrors native's Settings::updateTabAvailability (Settings.cc:1302-1332). Notably,
    // DSP_LEAKLIST is unconditionally marked unavailable (matching native exactly --
    // it never calls is_leaklist_available() here despite that method existing).
    public void updateTabAvailability() {
        DbeSession session = DbeSession.getInstance();
        for (DispTab dsptab : tab_list) {
            switch (dsptab.type) {
                case DSP_DATAOBJ, DSP_DLAYOUT -> dsptab.setAvailability(session.is_datamode_available());
                case DSP_LEAKLIST -> dsptab.setAvailability(false);
                case DSP_IOACTIVITY -> dsptab.setAvailability(session.is_iodata_available());
                case DSP_HEAPCALLSTACK -> dsptab.setAvailability(session.is_heapdata_available());
                case DSP_TIMELINE -> dsptab.setAvailability(session.is_timeline_available());
                case DSP_IFREQ -> dsptab.setAvailability(session.is_ifreq_available());
                case DSP_RACES -> dsptab.setAvailability(session.is_racelist_available());
                case DSP_DEADLOCKS -> dsptab.setAvailability(session.is_deadlocklist_available());
                case DSP_DUALSOURCE -> dsptab.setAvailability(
                        session.is_racelist_available() || session.is_deadlocklist_available());
                default -> { }
            }
        }
    }

    public void indxobj_define(int type, boolean state) {
        if (type == indx_tab_state.size()) {
            indx_tab_state.add(state);
        }
        if (type == indx_tab_order.size()) {
            indx_tab_order.add(-1);
        }
    }

    // Default --sysconfdir value baked into native's own build (Makefile.am:
    // "SYSCONFDIR = @sysconfdir@", autotools' default $prefix/etc with the default
    // $prefix=/usr/local); this port has no configure/install step of its own, so
    // there's no real equivalent -- GPROFNG_SYSCONFDIR still overrides it, matching
    // native, and a missing file here just produces the same warning comment native
    // does rather than failing.
    private static final String SYSCONFDIR = "/opt/javascope/etc";

    // Mirrors native's Settings::read_rc(char*) (Settings.cc:362-381): reads a single
    // explicit .gprofng.rc-format file (not the automatic cwd/home/sysconfdir search --
    // see read_rc(boolean) for that), returning any comments/warnings produced as one
    // string. override=true: a directive in this file always takes effect even if
    // something else already set that setting.
    public String read_rc(String path) {
        if (path == null)
            return "Error: empty file name";
        Emsgqueue commentq = new Emsgqueue("setting_commentq");
        set_rc(path, true, commentq, true, false);
        StringBuilder sb = new StringBuilder();
        for (Emsg m = commentq.fetch(); m != null; m = m.next)
            sb.append(m.get_msg());
        return sb.toString();
    }

    // Mirrors native's Settings::read_rc(bool) (Settings.cc:383-431): reads, in order,
    // ./.gprofng.rc (cwd), $HOME/.gprofng.rc, and the system-wide gprofng.rc
    // ($GPROFNG_SYSCONFDIR or SYSCONFDIR above). Comments/warnings go to the
    // application's own comment queue (shown in the "-header" output), matching native.
    // ipcOrRdtMode restricts the cwd/home files to ADDPATH/PATHMAP only (see set_rc's
    // doc comment) -- the system file is always processed in full, matching native's
    // own set_rc(..., /*ipc_or_rdt_mode=*/false) call for it.
    public void read_rc(boolean ipcOrRdtMode) {
        boolean override = false;

        // Read file from the current working directory
        String rcPath = realpath("./.gprofng.rc");
        if (rcPath != null)
            set_rc(rcPath, true, DbeApplication.getInstance().commentq, override, ipcOrRdtMode);

        // Read file from the user's home directory
        String home = System.getenv("HOME");
        if (home == null) {
            home = System.getProperty("user.home");
        }
        if (home != null) {
            String homeRcPath = realpath(home + "/.gprofng.rc");
            if (homeRcPath != null && !homeRcPath.equals(rcPath))
                set_rc(homeRcPath, true, DbeApplication.getInstance().commentq, override, ipcOrRdtMode);
        }

        String sysconfdir = System.getenv("GPROFNG_SYSCONFDIR");
        if (sysconfdir == null) {
            sysconfdir = SYSCONFDIR;
        }
        String sysRcPath = sysconfdir + "/gprofng.rc";
        if (!new File(sysRcPath).canRead()) {
            DbeApplication.getInstance().queue_comment(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                    String.format("Warning: Default gprofng.rc file (%s) missing; configuration error ", sysRcPath)));
        } else {
            set_rc(sysRcPath, false, DbeApplication.getInstance().commentq, override, false);
        }
        is_loexpand_default = true;
        if (str_printmode == null) {
            // only if there's none set
            print_mode = PrintMode.PM_TEXT;
            str_printmode = "text";
        }
    }

    private static String realpath(String path) {
        File f = new File(path);
        if (!f.exists())
            return null;
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
    }

    private static final int MAXARGS = 20;

    // Tokenizes one logical .gprofng.rc line into whitespace-separated words, honoring
    // '"'/'\'' quoting with backslash escapes (\\, \", \', \t, \r, \b, \f, \n) -- mirrors
    // native's parse_qstring()/strtok_r() combination (util.cc:339-521), minus the rare
    // numeric-escape (\NNN) form and the GTXT(...) i18n-macro unwrap, neither of which
    // is meaningful for a hand-written rc file. A token starting with '#' ends the line
    // (the rest is a comment), matching native's own "*nextarg == '#'" check.
    private static List<String> tokenizeArgs(String line) {
        List<String> toks = new ArrayList<>();
        int i = 0, n = line.length();
        while (i < n) {
            while (i < n && (line.charAt(i) == ' ' || line.charAt(i) == '\t'))
                i++;
            if (i >= n || line.charAt(i) == '#')
                break;
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                char term = c;
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && line.charAt(i) != term) {
                    char ch = line.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        char c2 = line.charAt(++i);
                        sb.append(switch (c2) {
                            case '"' -> '"';
                            case '\'' -> '\'';
                            case '\\' -> '\\';
                            case 't' -> '\t';
                            case 'r' -> '\r';
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case 'n' -> '\n';
                            default -> c2;
                        });
                    } else {
                        sb.append(ch);
                    }
                    i++;
                }
                if (i < n)
                    i++; // skip closing quote
                toks.add(sb.toString());
            } else {
                int start = i;
                while (i < n && line.charAt(i) != ' ' && line.charAt(i) != '\t')
                    i++;
                toks.add(line.substring(start, i));
            }
        }
        return toks;
    }

    // Mirrors native's Settings::set_rc (Settings.cc:443-787): parses one .gprofng.rc
    // file and acts on each recognized directive. Restricted to the directives this
    // port actually has a backing feature for -- ADDPATH/PATHMAP (search path/pathmap
    // resolution), TABS/RTABS (default tab selection, Settings.proc_tabs), NAMEFMT/
    // VIEWMODE/EN_DESC/LIMIT/PRINTMODE/COMPARE/OBJECT_SHOW/OBJECT_HIDE/OBJECT_API
    // (all already-ported Settings setters), DMETRICS/DSORT (accumulated into
    // str_dmetrics/str_dsort only -- same as native, which also just stores the string
    // here and applies it later, elsewhere, a step this port hasn't wired up yet; see
    // DbeView.setMetrics/setSort), and INDXOBJDEF (DbeSession.indxobj_define). SCOMPCOM/
    // DCOMPCOM/COMPCOM/STHRESH/DTHRESH (compiler-commentary filtering), TLMODE/TLDATA
    // (thread-list/timeline display), and LIBDIRS (dynamic-function preload dirs) are
    // recognized (so they don't trigger the "unrecognized command" warning) but are
    // otherwise no-ops: this port has no disassembly/compiler-commentary or
    // thread-list/timeline view for them to configure.
    public void set_rc(String path, boolean msg, Emsgqueue commentq, boolean override, boolean ipcOrRdtMode) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            return;
        }

        if (msg) {
            commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                    String.format("Processed %s for default settings", path)));
        }
        int lineNo = 0;
        int li = 0;
        while (li < lines.size()) {
            // Join backslash-continued physical lines into one logical line.
            StringBuilder full = new StringBuilder();
            while (li < lines.size()) {
                String raw = lines.get(li++);
                lineNo++;
                if (raw.endsWith("\\")) {
                    full.append(raw, 0, raw.length() - 1);
                    continue;
                }
                full.append(raw);
                break;
            }

            List<String> toks = tokenizeArgs(full.toString());
            if (toks.isEmpty())
                continue;
            String cmd = toks.get(0);
            if (cmd.isEmpty() || cmd.charAt(0) == '#')
                continue;
            List<String> arglist = toks.subList(1, toks.size());
            if (arglist.size() > MAXARGS) {
                if (!msg) {
                    msg = true;
                    commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                            "Processed system gprofng.rc file for default settings"));
                }
                commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                        String.format("Warning: more than %d arguments to %s command, line %d", MAXARGS, cmd, lineNo)));
                continue;
            }

            Command.CommandLookup lookup = Command.get_command(cmd);
            Command.CmdType cmdType = lookup.type();
            int argCount = lookup.arg_count();

            if (cmdType != Command.CmdType.UNKNOWN_CMD && cmdType != Command.CmdType.INDXOBJDEF
                    && arglist.size() > argCount) {
                if (!msg) {
                    msg = true;
                    commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                            "Processed system gprofng.rc file for default settings"));
                }
                commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                        String.format("Warning: extra arguments to %s command, line %d", cmd, lineNo)));
            }
            if (arglist.size() < argCount) {
                if (!msg) {
                    msg = true;
                    commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                            "Processed system gprofng.rc file for default settings"));
                }
                commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                        String.format("Error: missing arguments to %s command, line %d", cmd, lineNo)));
                continue; // ignore this command
            }
            if (ipcOrRdtMode && cmdType != Command.CmdType.ADDPATH && cmdType != Command.CmdType.PATHMAP)
                continue;

            String arg0 = arglist.isEmpty() ? null : arglist.get(0);
            String arg1 = arglist.size() > 1 ? arglist.get(1) : null;
            switch (cmdType) {
                case DMETRICS -> {
                    // append new settings to old, same splice-before-":name" logic as
                    // native (Settings.cc:593-616) -- irrelevant here since this port's
                    // DbeView.setMetrics never consumes str_dmetrics yet, but kept for
                    // fidelity against the day it does.
                    if (str_dmetrics != null) {
                        int name = str_dmetrics.indexOf(":name");
                        if (name < 0)
                            str_dmetrics = str_dmetrics + ":" + arg0;
                        else {
                            int next = str_dmetrics.indexOf(":", name + 1);
                            str_dmetrics = (next < 0 ? str_dmetrics.substring(0, name) : str_dmetrics) + ":" + arg0
                                    + (next < 0 ? ":name" : "");
                        }
                    } else {
                        str_dmetrics = arg0;
                    }
                }
                case DSORT -> str_dsort = str_dsort != null ? str_dsort + ":" + arg0 : arg0;
                case TABS -> {
                    if (str_tabs == null || override)
                        str_tabs = arg0;
                }
                case RTABS -> {
                    if (str_rtabs == null || override)
                        str_rtabs = arg0;
                }
                case ADDPATH -> str_search_path = str_search_path != null
                        ? str_search_path + ":" + arg0 : arg0;
                case PATHMAP -> add_pathmap(pathmaps, arg0, arg1); // error (if any) not reported, matches native
                case NAMEFMT -> {
                    if (name_format == Histable.NameFormat.NA)
                        set_name_format(arg0);
                }
                case VIEWMODE -> {
                    if (str_vmode == null || override) {
                        str_vmode = arg0;
                        set_view_mode(arg0, true);
                    }
                }
                case EN_DESC -> {
                    if (en_desc_usr == null || override)
                        set_en_desc(arg0, true);
                }
                case LIMIT -> {
                    if (str_limit == null || override) {
                        str_limit = arg0;
                        try {
                            set_limit(Integer.parseInt(arg0.trim()));
                        } catch (NumberFormatException e) {
                            set_limit(0);
                        }
                    }
                }
                case PRINTMODE -> {
                    if (str_printmode == null || override)
                        set_printmode(arg0);
                }
                case COMPARE -> {
                    if (str_compare == null || override) {
                        str_compare = arg0;
                        String s = arg0 != null ? arg0 : "";
                        if (s.equalsIgnoreCase("off") || s.equals("0"))
                            set_compare_mode(CmpMode.CMP_DISABLE);
                        else if (s.equalsIgnoreCase("on") || s.equals("1"))
                            set_compare_mode(CmpMode.CMP_ENABLE);
                        else if (s.equalsIgnoreCase("delta"))
                            set_compare_mode(CmpMode.CMP_DELTA);
                        else if (s.equalsIgnoreCase("ratio"))
                            set_compare_mode(CmpMode.CMP_RATIO);
                        else
                            commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT, String.format(
                                    "   .er.rc:%d The argument of 'compare' should be 'on', 'off', 'delta', or 'ratio'",
                                    lineNo)));
                    }
                }
                case INDXOBJDEF -> {
                    String ret = DbeSession.getInstance().indxobj_define(arg0, null, arg1,
                            arglist.size() >= 3 ? arglist.get(2) : null,
                            arglist.size() >= 4 ? arglist.get(3) : null);
                    if (ret != null)
                        commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                                String.format("   %s: line %d `%s %s %s'", ret, lineNo, cmd, arg0, arg1)));
                }
                case OBJECT_SHOW -> set_libexpand(arg0, LibExpand.LIBEX_SHOW, true);
                case OBJECT_HIDE -> set_libexpand(arg0, LibExpand.LIBEX_HIDE, true);
                case OBJECT_API -> set_libexpand(arg0, LibExpand.LIBEX_API, true);
                case COMMENT -> { /* ignore the line */ }
                // Recognized, but no backing feature in this port (see doc comment above):
                case SCOMPCOM, STHRESH, DCOMPCOM, COMPCOM, DTHRESH, TLMODE, TLDATA, LIBDIRS -> { }
                default -> {
                    if (!msg) {
                        msg = true;
                        commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                                "Processed system gprofng.rc file for default settings"));
                    }
                    commentq.append(new Emsg(Emsg.Cmsg_warn.CMSG_COMMENT,
                            String.format("   Unrecognized .gprofng.rc command on line %d: `%s'",
                                    lineNo, cmd.substring(0, Math.min(64, cmd.length())))));
                }
            }
        }
    }

}
