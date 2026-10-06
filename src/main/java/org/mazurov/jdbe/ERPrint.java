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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mazurov.jdbe.Command.CmdType;
import static org.mazurov.jdbe.DbeStructs.*;
import static org.mazurov.jdbe.Enums.*;
import static org.mazurov.jdbe.FilterSet.*;
import static org.mazurov.jdbe.Print.Print_mode;

public class ERPrint extends DbeApplication {

    String error_msg;
    DbeView dbev;
    String out_fname = "<stdout>";
    InputStream inp_file = System.in;
    PrintStream out_file = System.out;
    PrintStream dis_file = System.out;
    int dbevindex;
    String cov_string = null;
    int limit = 0;
    ArrayList<Histable> cstack = new ArrayList<>();
    boolean was_QQUIT = false;

    static final int MAXARGS = 20;

    ERPrint(String[] args) {
        super(args);
    }

    static void main(String[] args) throws Exception {
        if (!Arrays.asList(args).contains("-IPC")) {
            new ERPrint(args).start(args);
            return;
        }
        // Mirrors native's reexec()-driven restart (gp-display-text.cc:54-64), but in
        // place of actually replacing the OS process image, ERIPC.reExec() throws
        // RestartRequestedException: it unwinds the stack all the way back out of
        // ipc_mainLoop() to here, where we rebuild the world exactly as a fresh process
        // would (new ERPrint -- which re-self-assigns both the DbeApplication and
        // DbeSession singletons, see their constructors) and re-enter ipc_mainLoop().
        // This keeps the same OS process/PID (and, critically, the same debugger
        // session) across a "restart", unlike the execv()-based approach it replaces.
        for (;;) {
            ERPrint erPrint = new ERPrint(args);
            erPrint.rdtMode = false;
            try {
                ERIPC.ipc_mainLoop();
                return; // normal exit: NULL command / EOF on the wire
            } catch (ERIPC.RestartRequestedException e) {
                // fall through and loop: rebuild everything, re-enter ipc_mainLoop()
            }
        }
    }

    void abort() {
        System.exit(2);
    }

    void close() {
        try {
            inp_file.close();
        } catch (IOException _) {
        }
    }

    static InputStream fopen(String path, String mode) {
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    static void usage() {
        System.out.print(
                "Usage: gprofng display text [OPTION(S)] [COMMAND(S)] [-script <script_file>] EXPERIMENT(S)\n");

        System.out.print(
                "\n"
                + "Print a plain text version of the various displays supported by gprofng.\n"
                + "\n"
                + "Options:\n"
                + "\n"
                + " --version           print the version number and exit.\n"
                + " --help              print usage information and exit.\n"
                + " --verbose {on|off}  enable (on) or disable (off) verbose mode; the default is \"off\".\n"
                + "\n"
                + " -script <script-file>  execute the commands stored in the script file;\n"
                + "                        this feature may be combined with commands specified\n"
                + "                        at the command line.\n"
                + "\n"
                + "Commands:\n"
                + "\n"
                + "This tool supports a rich set of commands to control the display of the\n"
                + "data; instead of, or in addition to, including these commands in a script\n"
                + "file, it is also allowed to include such commands at the command line;\n"
                + "in this case, the commands need to be prepended with the \"-\" symbol; the\n"
                + "commands are processed and interpreted left from right, so the order matters;\n"
                + "The gprofng manual documents the commands that are supported.\n"
                + "\n"
                + "If this tool is invoked without options, commands, or a script file, it starts\n"
                + "in interpreter mode. The user can then issue the commands interactively; the\n"
                + "session is terminated with the \"exit\" command in the interpreter.\n"
                + "\n"
                + "Documentation:\n"
                + "\n"
                + "A getting started guide for gprofng is maintained as a Texinfo manual. If the info and\n"
                + "gprofng programs are properly installed at your site, the command \"info gprofng\"\n"
                + "should give you access to this document.\n"
                + "\n"
                + "See also:\n"
                + "\n"
                + "gprofng(1), gprofng-archive(1), gprofng-collect-app(1), "
                + "gprofng-display-html(1), gprofng-display-src(1)\n"
                + "\nReport bugs to <https://sourceware.org/bugzilla/>\n");
    }

    private record FnameParse(String name, String fcontext) {}

    // parse a file name of the form name`context`; returns null if the string is not
    // properly formatted
    private static FnameParse parse_fname(String in_str) {
        if (in_str == null)
            return null;
        int p = in_str.indexOf('`');
        if (p < 0)
            return new FnameParse(in_str, null);
        int p1 = in_str.indexOf('`', p + 1);
        if (p1 < 0)
            // if we don't have the closing `, the format is incorrect
            return null;
        if (p1 + 1 != in_str.length())
            // there's something following the closing `; error in format
            return null;
        return new FnameParse(in_str.substring(0, p), in_str.substring(p + 1, p1));
    }

    void start(String[] argv)  {
        set_name("ERPrint");
        initApplication(null, null, null);

        // Create a view on the session
        DbeSession dbeSession = DbeSession.getInstance();
        dbevindex = dbeSession.createView(0, -1);
        dbev = dbeSession.getView(dbevindex);
        limit = dbev.get_limit();
        check_args(argv);
        int ngood = dbeSession.ngoodexps();
        if (ngood == 0) {
            System.err.println("No valid experiments loaded; exiting");
            return;
        }
        DBE.dbeDetectLoadMachineModel(dbevindex);
        run(argv);
    }

    void check_args(String[] argv) {
        DbeSession dbeSession = DbeSession.getInstance();
        Command.CmdType cmd_type;
        int arg_count;
        int cparam;
        int exp_no;
        error_msg = null;

        Emsg rcmsg = fetch_comments();
        while (rcmsg != null) {
            System.err.printf("%s: %s%n", prog_name, rcmsg.get_msg());
            rcmsg = rcmsg.next;
        }
        delete_comments();

        // Set up the list of experiments to add after checking the args
        ArrayList<ArrayList<String>> exp_list = new ArrayList<>();

        // Prescan the command line arguments, processing only a few
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].charAt(0) != '-') {
                // we're at the end -- get the list of experiments
                //  Build the list of experiments, and set the searchpath
                ArrayList<String> list = dbeSession.get_group_or_expt(argv[i]);
                for (String path : list) {
                    if (path.length() == 0 || path.equals("\\"))
                        continue;
                    int idx = path.lastIndexOf('/');
                    if (idx >= 0) {
                        // there's a directory in front of the name; add it to search path
                        dbeSession.set_search_path(path.substring(0, idx), false);
                    }
                }
                list.clear();
                list.add(argv[i]);
                exp_list.add(list);
                continue;
            }

            // Not at the end yet, treat the next argument as a command
            Command.CommandLookup lookup = Command.get_command(argv[i].substring(1));
            cmd_type = lookup.type();
            arg_count = lookup.arg_count();
            cparam = lookup.cparam();
            switch (cmd_type) {
                case WHOAMI:
                    whoami = argv[i] + 1 + cparam;
                    break;
                case HELP:
                    if (i + 1 + arg_count == argv.length) {
                        usage();
                        System.exit(0);
                    }
                    break;
                case HHELP:
                    Command.print_help(whoami, true, false, System.out);
                    System.out.println();
                    indxo_list (false, System.out);
                    System.out.println();
                    mo_list(false, System.out);
                    System.out.print("\nSee gprofng(1) for more details\n");
                    System.exit(0);
                case ADD_EXP:
                case DROP_EXP:
                case OPEN_EXP:
                    System.err.printf("Error: command %s can not appear on the command line\n", argv[i]);
                    System.exit (2);
                case VERSION_cmd:
                    Application.print_version_info();
                    System.exit (0);
                case AMBIGUOUS_CMD:
                    System.err.printf("Error: Ambiguous command: %s\n", argv[i]);
                    System.exit (2);
                case UNKNOWN_CMD:
                    System.err.printf("Error: Invalid command: %s\n", argv[i]);
                    System.exit (2);
                    // it's a plausible argument; see if we process now or later
                case SOURCE:
                case DISASM:
                case CSINGLE:
                case CPREPEND:
                case CAPPEND:
                case FSINGLE:
                case SAMPLE_DETAIL:
                case STATISTICS:
                case HEADER:
                    //skip the arguments to that command
                    i += arg_count;
                    if (i >= argv.length || end_command(argv[i]))
                        i--;
                    break;
                case PRINTMODE:
                case INDXOBJDEF:
                case ADDPATH:
                case SETPATH:
                case PATHMAP:
                case OBJECT_SHOW:
                case OBJECT_HIDE:
                case OBJECT_API:
                case OBJECTS_DEFAULT:
                case EN_DESC:
                    // these are processed in the initial pass over the arguments
                    proc_cmd(cmd_type, cparam, (arg_count > 0) ? argv[i + 1] : null,
                            (arg_count > 1) ? argv[i + 2] : null,
                            (arg_count > 2) ? argv[i + 3] : null,
                            (arg_count > 3) ? argv[i + 4] : null);
                    i += arg_count;
                    break;
                default:
                    // any others, we skip for now
                    i += arg_count;
                    break;
            }
        }

        // Make sure some experiments were specified
        if (exp_list.isEmpty()) { // no experiment name
            System.err.printf("%s: Missing experiment directory (use the --help option to get a usage overview)", whoami);
            System.exit(1);
        }

        // add the experiments to the session
        String[][] groups = new String[exp_list.size()][];
        for (int i = 0; i < exp_list.size(); i++)
            groups[i] = exp_list.get(i).toArray(new String[0]);
        String errstr = DBE.dbeOpenExperimentList(0, groups, false);
        if (errstr != null) {
            System.err.println(errstr);
        }
    }

    boolean end_command(String cmd) {
        if (cmd == null || cmd.isBlank() || cmd.charAt(0) == '-')
            return true;
        return cmd.endsWith(".er") || cmd.endsWith(".er/") ||
                cmd.endsWith(".erg") || cmd.endsWith(".erg/");
    }

    // Now actually start processing the arguments
    void run(String[] argv) {
        Command.CmdType cmd_type;
        int arg_count, cparam, i;
        boolean got = false;
        String arg1, arg2;
        for (i = 0; i < argv.length; i++) {
            if (argv[i].charAt(0) != '-') { // open experiment pointer files
                continue;
            }

            Command.CommandLookup lookup = Command.get_command(argv[i]);
            cmd_type = lookup.type();
            arg_count = lookup.arg_count();
            cparam = lookup.cparam();
            switch (cmd_type) {
                case WHOAMI:
                    whoami = argv[i] + 1 + cparam;
                    break;
                case SCRIPT:
                    got = true;
                    inp_file = fopen (argv[++i], "r");
                    if (inp_file == null) {
                        System.err.printf("Error: Script file cannot be opened: %s\n", argv[i]);
                        System.exit(3);
                    }
                    proc_script();
                    break;
                case STDIN:
                    got = true;
                    inp_file = System.in;
                    proc_script();
                    break;
                case SOURCE: // with option arg_count == 2
                case DISASM:
                    got = true;
                    i += arg_count;
                    if ((i >= argv.length) || end_command(argv[i]))
                    {
                        i--;
                        arg1 = argv[i];
                        arg2 = "";
                    }
                    else
                    {
                        arg1 = argv[i - 1];
                        arg2 = argv[i];
                    }
                    proc_cmd(cmd_type, cparam, arg1, arg2);
                    break;
                case CSINGLE:
                case CPREPEND:
                case CAPPEND:
                case FSINGLE:
                    got = true;
                    i += arg_count;
                    if ((i >= argv.length) || end_command(argv[i])) {
                        i--;
                        proc_cmd(cmd_type, cparam, argv[i],"1");
                    }
                    else
                        proc_cmd (cmd_type, cparam, argv[i - 1], argv[i]);
                    break;
                case SAMPLE_DETAIL: // with option arg_count == 1
                case STATISTICS:
                case HEADER:
                case COMPARE:
                    got = true;
                    i += arg_count;
                    if ((i >= argv.length) || end_command (argv[i])) {
                        i--;
                        proc_cmd (cmd_type, cparam, null, null);
                    } else {
                        proc_cmd(cmd_type, cparam, argv[i], null);
                    }
                    break;
                case PRINTMODE:
                case INDXOBJDEF:
                case ADDPATH:
                case SETPATH:
                case PATHMAP:
                case OBJECT_SHOW:
                case OBJECT_HIDE:
                case OBJECT_API:
                case OBJECTS_DEFAULT:
                case EN_DESC:
                    got = true;
                    // these have been processed already
                    i += arg_count;
                    break;
                case LIMIT:
                    got = true;
                    proc_cmd (cmd_type, cparam, (arg_count > 0) ? argv[i + 1] : null,
                            (arg_count > 1) ? argv[i + 2] : null);
                    i += arg_count;
                    break;
                default:
                    got = true;
                    proc_cmd (cmd_type, cparam, (arg_count > 0) ? argv[i + 1] : null,
                            (arg_count > 1) ? argv[i + 2] : null);
                    i += arg_count;
                    break;
            }
        }
        if (!got) { // no command has been specified
            proc_script();
        }
    }

    // one quoted or unquoted token starting at str.charAt(pos) (leading blanks/tabs are
    // skipped); endIndex is where the next token search should resume from
    private record QString(String value, int endIndex) {}

    // get quoted string; mirrors util.cc's parse_qstring(), except that a `\<digit>`
    // numeric escape (e.g. octal/hex char codes) is not supported and is passed through
    // as a literal character instead -- not worth the extra complexity for something
    // essentially never used in gprofng scripts
    private static QString parse_qstring(String str, int pos) {
        int len = str.length();
        while (pos < len && (str.charAt(pos) == ' ' || str.charAt(pos) == '\t'))
            pos++;
        if (pos >= len)
            return null;

        boolean gtxt = str.startsWith("GTXT(", pos);
        if (gtxt)
            pos += 5;

        char term;
        if (pos < len && str.charAt(pos) == '"')
            term = '"';
        else if (pos < len && str.charAt(pos) == '\'')
            term = '\'';
        else {
            // non-quoted string
            int start = pos;
            while (pos < len && str.charAt(pos) != ' ' && str.charAt(pos) != '\t')
                pos++;
            return new QString(str.substring(start, pos), pos);
        }

        StringBuilder sb = new StringBuilder();
        pos++; // skip the opening quote
        char c = 0;
        while (pos < len) {
            c = str.charAt(pos++);
            if (c == term) // the closing quote
                break;
            if (c == '\\' && pos < len) {
                char c2 = str.charAt(pos++);
                switch (c2) {
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    default -> sb.append(c2);
                }
            } else {
                sb.append(c);
            }
        }
        if (c == term && gtxt && pos < len && str.charAt(pos) == ')')
            pos++;
        return new QString(sb.toString(), pos);
    }

    void proc_script() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inp_file));
        int lineno = 0;
        try {
            for (;;) {
                if (inp_file == System.in) {
                    int slash = prog_name.lastIndexOf('/');
                    System.out.printf("(%s) ", slash >= 0 ? prog_name.substring(slash + 1) : prog_name);
                }
                String line = reader.readLine();
                if (line == null)
                    break;
                lineno++;

                // extract the command
                int len = line.length();
                int pos = 0;
                while (pos < len && (line.charAt(pos) == ' ' || line.charAt(pos) == '\t'))
                    pos++;
                int cmdStart = pos;
                while (pos < len && line.charAt(pos) != ' ' && line.charAt(pos) != '\t')
                    pos++;
                if (cmdStart == pos)
                    continue;
                String cmd = line.substring(cmdStart, pos);
                if (cmd.charAt(0) == '#') {
                    System.err.println(line);
                    continue;
                }

                // now extract the arguments
                String[] arglist = new String[MAXARGS];
                int nargs = 0;
                for (;;) {
                    if (nargs >= MAXARGS)
                        System.err.printf("Warning: more than %d arguments to %s command, line %d\n",
                                MAXARGS, cmd, lineno);
                    QString nextarg = parse_qstring(line, pos);
                    if (nextarg == null || nextarg.value().startsWith("#"))
                        // either the end of the line, or a comment indicator
                        break;
                    if (nargs < MAXARGS)
                        arglist[nargs] = nextarg.value();
                    nargs++;
                    pos = nextarg.endIndex();
                }

                Command.CommandLookup lookup = Command.get_command(cmd);
                Command.CmdType cmd_type = lookup.type();
                int cparam = lookup.cparam();
                int arg_count = lookup.arg_count();

                // check for extra arguments
                if (cmd_type != CmdType.UNKNOWN_CMD && cmd_type != CmdType.INDXOBJDEF && nargs > arg_count)
                    System.err.printf("Warning: extra arguments to %s command, line %d\n", cmd, lineno);

                switch (cmd_type) {
                    case SOURCE:
                    case DISASM:
                        // ignore any third parameter; if there was, we have written a
                        // warning. xdefault (whether input is interactive) is not
                        // threaded through -- find_obj, its only consumer, is still a
                        // stub
                        proc_cmd(cmd_type, cparam, arglist[0], arglist[1], null, null);
                        break;
                    case QUIT:
                        System.exit(0);
                        break;
                    case QQUIT:
                        was_QQUIT = true;
                        return;
                    case STDIN:
                        break;
                    case COMMENT:
                        dis_file.println(line);
                        break;
                    case AMBIGUOUS_CMD:
                        System.err.printf("Error: Ambiguous command: %s\n", cmd);
                        break;
                    case UNKNOWN_CMD:
                        System.err.printf("Error: Invalid command: %s\n", cmd);
                        break;
                    default:
                        proc_cmd(cmd_type, cparam, arglist[0], arglist[1]);
                        break;
                }
            }
        } catch (IOException e) {
            // treat as end of input
        }
    }

    void proc_cmd(Command.CmdType cmd_type, int cparam, String arg1, String arg2) {
        proc_cmd(cmd_type, cparam, arg1, arg2, null, null);
    }

    void proc_cmd(Command.CmdType cmd_type, int cparam,
                  String arg1, String arg2, String arg3, String arg4) {
        DbeSession dbeSession = DbeSession.getInstance();
        Print.er_print_common_display cd;
        InputStream ck_file, save_file;
        Command.Cmd_status status;
        String scratch, scratch1;

        switch (cmd_type) {
            case FUNCS:
                print_func(Histable.Type.FUNCTION, Print_mode.MODE_LIST,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_list(MetricType.MET_NORMAL), null, null);
                break;
            case FDETAIL:
                print_func(Histable.Type.FUNCTION, Print_mode.MODE_DETAIL,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_ref(MetricType.MET_NORMAL), null, null);
                break;
            case FSINGLE:
                print_func(Histable.Type.FUNCTION, Print_mode.MODE_DETAIL,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_ref(MetricType.MET_NORMAL),
                        arg1, arg2);
                break;
            case HOTPCS:
                print_func(Histable.Type.INSTR, Print_mode.MODE_LIST,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_list(MetricType.MET_NORMAL), null, null);
                break;
            case PDETAIL:
                print_func(Histable.Type.INSTR, Print_mode.MODE_DETAIL,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_ref(MetricType.MET_NORMAL), null, null);
                break;
            case HOTLINES:
                print_func(Histable.Type.LINE, Print_mode.MODE_LIST,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_list(MetricType.MET_NORMAL), null, null);
                break;
            case LDETAIL:
                print_func(Histable.Type.LINE, Print_mode.MODE_DETAIL,
                        dbev.get_metric_list(MetricType.MET_NORMAL), dbev.get_metric_ref(MetricType.MET_NORMAL), null, null);
                break;
            case OBJECTS:
                print_objects();
                break;
            case OVERVIEW_NEW:
                print_overview();
                break;
            case LOADOBJECT:
                print_segments();
                break;
            case GPROF:
                print_func (Histable.Type.FUNCTION, Print_mode.MODE_GPROF,
                        dbev.get_metric_list(MetricType.MET_CALL), dbev.get_metric_list(MetricType.MET_NORMAL), null, null);
                break;
            case CALLTREE:
                if (dbev.comparingExperiments ()) {
                    out_file.println("\nNot available when comparing experiments\n");
                    break;
                }
                print_ctree (cmd_type);
                break;
            case CSINGLE:
            case CPREPEND:
            case CAPPEND:
            case CRMFIRST:
            case CRMLAST:
                print_gprof(cmd_type, arg1, arg2);
                break;
            case EXP_LIST:
                exp_list();
                break;
            case DESCRIBE:
                describe();
                break;
            case SCOMPCOM:
                status = dbev.proc_compcom(arg1, true, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                break;
            case STHRESH:
                status = dbev.proc_thresh (arg1, true, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                break;
            case DCOMPCOM:
                status = dbev.proc_compcom (arg1, false, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                break;
            case COMPCOM:
                status = dbev.proc_compcom (arg1, true, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                status = dbev.proc_compcom (arg1, false, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string(status), arg1);
                break;
            case DTHRESH:
                status = dbev.proc_thresh(arg1, false, false);
                if (status != Command.Cmd_status.CMD_OK)
                    System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                break;
            case SOURCE:
            case DISASM:
            {
                if (arg3 != null) {
                    abort();
                }
                if (arg1 == null) {
                    System.err.printf("Error: Invalid function/file setting: \n");
                    break;
                }
                FnameParse parsed = parse_fname(arg1);
                if (parsed == null) {
                    System.err.printf("Error: Invalid function/file setting: %s\n", arg1);
                    break;
                }
                String arg = parsed.name();
                String fcontext = parsed.fcontext();
                if (arg2 != null && arg2.isBlank()) {
                    arg2 = null;
                }
                print_anno_file(arg, arg2, fcontext, cmd_type == CmdType.DISASM,
                        dis_file, inp_file, out_file, dbev, true);
                break;
            }
            case METRIC_LIST:
                proc_cmd(CmdType.METRICS, cparam, null, null);  // TODO: METRICS?
                dbev.get_metric_ref(MetricType.MET_NORMAL).print_metric_list(dis_file,
                    "Available metrics:\n", false);
                break;
            case METRICS:
                if (arg1 != null) {
                    String ret = dbev.setMetrics(arg1, false);
                    if (ret != null) {
                        System.err.printf("Error: %s\n", ret);
                        proc_cmd(CmdType.METRIC_LIST, cparam, null, null); // TODO: METRIC_LIST?
                        break;
                    }
                }
                scratch = dbev.get_metric_list(MetricType.MET_NORMAL).get_metrics();
                dis_file.printf("Current metrics: %s\n", scratch);
                proc_cmd(CmdType.SORT, cparam, null, null);
                break;
            case GMETRIC_LIST:
                scratch = dbev.get_metric_list(MetricType.MET_CALL).get_metrics();
                dis_file.printf("Current caller-callee metrics: %s\n", scratch);
                dis_file.printf("Current caller-callee sort Metric: %s\n", dbev.getSort(MetricType.MET_DATA));
                break;
            case INDX_METRIC_LIST:
                scratch = dbev.get_metric_list(MetricType.MET_INDX).get_metrics ();
                dis_file.printf("Current index-object metrics: %s\n", scratch);
                scratch = dbev.getSort(MetricType.MET_INDX);
                dis_file.printf("Current index-object sort Metric: %s\n", scratch);
                break;
            case SORT:
                if (arg1 != null) {
                    String ret = dbev.setSort(arg1, MetricType.MET_NORMAL, false);
                    if (ret != null) {
                        System.err.printf("Error: %s\n", ret);
                        proc_cmd(CmdType.METRICS, cparam, null, null);
                        break;
                    }
                    dbev.setSort(arg1, MetricType.MET_SRCDIS, false);
                    dbev.setSort(arg1, MetricType.MET_CALL, false);
                    dbev.setSort(arg1, MetricType.MET_DATA, false);
                    dbev.setSort(arg1, MetricType.MET_INDX, false);
                    dbev.setSort(arg1, MetricType.MET_CALL_AGR, false);
                    dbev.setSort(arg1, MetricType.MET_IO, false);
                    dbev.setSort(arg1, MetricType.MET_HEAP, false);
                }
                scratch = dbev.getSort(MetricType.MET_NORMAL);
                scratch1 = dbev.getSortCmd(MetricType.MET_NORMAL);
                dis_file.printf("Current Sort Metric: %s ( %s )\n", scratch, scratch1);
                break;
            case OBJECT_SHOW:
                if (arg1 != null)
                    set_libexpand(arg1, LibExpand.LIBEX_SHOW);
                obj_list();
                break;
            case OBJECT_HIDE:
                if (arg1 != null)
                    set_libexpand(arg1, LibExpand.LIBEX_HIDE);
                obj_list();
                break;
            case OBJECT_API:
                if (arg1 != null)
                    set_libexpand(arg1, LibExpand.LIBEX_API);
                obj_list();
                break;
            case OBJECTS_DEFAULT:
                set_libdefaults();
                obj_list();
                break;
            case OBJECT_LIST:
                obj_list();
                break;
            case OBJECT_SELECT:
                if (arg1 != null) {
                    if (process_object_select(arg1) != -1)
                        proc_cmd(CmdType.OBJECT_LIST, cparam, null, null);
                    else
                        System.err.printf("Error: Type \"object_list\" for a list of all load objects.\n");
                }
                else
                    System.err.printf("Error: No load object has been specified.\n");
                break;
            case LOADOBJECT_LIST:
                seg_list();
                break;
            case LOADOBJECT_SELECT:
                if (arg1 != null) {
                    if (process_object_select(arg1) != -1)
                        proc_cmd(CmdType.LOADOBJECT_LIST, cparam, null, null);
                    else
                        System.err.printf("Error: Type \"segment_list\" for a list of all segments.\n");
                }
                else
                    System.err.printf("Error: No segment has been specified.\n");
                break;
            case SAMPLE_LIST:
                filter_list(CmdType.SAMPLE_LIST);
                break;
            case SAMPLE_SELECT:
                if (arg1 != null && !dbev.set_pattern(SAMPLE_FILTER_IDX, arg1))
                    System.err.printf("Error: Invalid filter pattern specification %s\n", arg1);
                proc_cmd(CmdType.SAMPLE_LIST, cparam, null, null);
                break;
            case THREAD_LIST:
                filter_list(CmdType.THREAD_LIST);
                break;
            case THREAD_SELECT:
                if (arg1 != null && !dbev.set_pattern(THREAD_FILTER_IDX, arg1))
                    System.err.printf("Error: Invalid filter pattern specification %s\n", arg1);
                proc_cmd(CmdType.THREAD_LIST, cparam, null, null);
                break;
            case LWP_LIST:
                filter_list(CmdType.LWP_LIST);
                break;
            case LWP_SELECT:
                if (arg1 != null && !dbev.set_pattern(LWP_FILTER_IDX, arg1))
                    System.err.printf("Error: Invalid filter pattern specification %s\n", arg1);
                proc_cmd(CmdType.LWP_LIST, cparam, null, null);
                break;
            case CPU_LIST:
                filter_list(CmdType.CPU_LIST);
                break;
            case CPU_SELECT:
                if (arg1 != null && !dbev.set_pattern(CPU_FILTER_IDX, arg1))
                    System.err.printf("Error: Invalid filter pattern specification %s\n", arg1);
                proc_cmd(CmdType.CPU_LIST, cparam, null, null);
                break;
            case FILTERS:
                if (arg1 != null) {
                    if (arg1.equals("True"))
                        scratch = dbev.set_filter(null);
                    else
                        scratch = dbev.set_filter(arg1);
                    if (scratch != null)
                        System.err.printf("Error: %s\n", scratch);
                }
                scratch = dbev.get_filter();
                dis_file.printf("current filter setting: \"%s\"\n", scratch == null ? "<none>" : scratch);
                break;
            case OUTFILE:
                if (arg1 != null) {
//                    set_outfile (arg1, out_file, false);
                    if (inp_file != System.in)
                        dis_file = out_file;
                }
                break;
            case APPENDFILE:
                if (arg1 != null) {
//                    set_outfile (arg1, out_file, true);
                    if (inp_file != System.in)
                        dis_file = out_file;
                }
                break;
            case LIMIT:
                if (arg1 != null) {
                    limit = Integer.parseInt(arg1);
                    String res = DBE.dbeSetPrintLimit(dbevindex, limit);
                    if (res != null) {
                        System.err.println(res);
                    }
                }

                limit = DBE.dbeGetPrintLimit(dbevindex);
                System.err.printf("Print limit set to %d\n", limit);
                break;
            case NAMEFMT:
                if (arg1 != null) {
                    status = dbev.set_name_format(arg1);
                    if (status != Command.Cmd_status.CMD_OK)
                        System.err.printf("Error: %s: %s\n", Command.get_err_string(status), arg1);
                }
                else
                    System.err.printf("Error: No format has been specified.\n");
                break;
            case VIEWMODE:
            {
                if (arg1 != null) {
                    status = dbev.set_view_mode(arg1, false);
                    if (status != Command.Cmd_status.CMD_OK)
                        System.err.printf("Error: %s: %s\n", Command.get_err_string (status), arg1);
                }
                String vname = "unknown";
                VMode vm = dbev.get_view_mode();
                vname = switch (vm) {
                    case VMODE_USER -> "user";
                    case VMODE_EXPERT -> "expert";
                    case VMODE_MACHINE -> "machine";
                };
                System.err.printf("Viewmode set to %s\n", vname);
            }
            break;

            // EN_DESC does not make sense after experiments are read, but it does make sense on the command line,
            //	processed before the experiments are read.
            case EN_DESC:
                if (arg1 != null) {
                    status = dbev.set_en_desc(arg1, false);
                    if (status != Command.Cmd_status.CMD_OK)
                        System.err.printf("Error: %s: %s\n", Command.get_err_string(status), arg1);
                }
                else
                    System.err.printf("Error: No descendant processing has been specified.\n");
                break;
            case SETPATH:
            case ADDPATH:
                if (arg1 != null)
                    dbeSession.set_search_path(arg1, (cmd_type == Command.CmdType.SETPATH));
                dis_file.printf("search path:\n");
                for (String s : dbeSession.get_search_path()) {
                    dis_file.printf("\t%s\n", s);
                }
                break;
            case PATHMAP:
            {
                List<PathMap> pathMaps = dbeSession.get_pathmaps();
                if (arg1 != null) {
                    if (arg2 == null) {
                        System.err.printf("Error: No replacement path prefix has been specified.\n");
                        break;
                    }
                    // add this mapping to the session
                    String err = Settings.add_pathmap(pathMaps, arg1, arg2);
                    if (err != null) {
                        System.err.printf("%s", err);
                    }
                }
                dis_file.printf("Path mappings: from -> to\n");
                for (PathMap thismap : pathMaps) {
                    dis_file.printf("\t`%s' -> `%s'\n", thismap.old_prefix(), thismap.new_prefix());
                }
            }
            break;
            case SAMPLE_DETAIL:
            {
                int[] range = get_exp_id(arg1);
                if (range != null) {
                    cd = new Print.er_print_experiment(dbev, range[0], range[1], false,
                            false, false, true, true);
                    print_cmd(cd);
                }
                break;
            }
            case STATISTICS: {
                int[] range = get_exp_id(arg1);
                if (range != null) {
                    cd = new Print.er_print_experiment(dbev, range[0], range[1], false,
                            false, true, true, false);
                    print_cmd(cd);
                }
                break;
            }
            case PRINTMODE:
            {
                if (arg1 == null) {
                    System.err.printf("printmode is set to `%s'\n\n", DBE.dbeGetPrintModeString(dbevindex));
                    break;
                }
                String s = DBE.dbeSetPrintMode(dbevindex, arg1);
                if (s != null) {
                    System.err.printf("%s\n", s);
                    break;
                }
                System.err.printf("printmode is set to `%s'\n\n", DBE.dbeGetPrintModeString(dbevindex));
            }
            break;
            case HEADER: {
                int[] range = get_exp_id(arg1);
                if (range != null) {
                    cd = new Print.er_print_experiment(dbev, range[0], range[1], false,
                            true, false, false, false);
                    print_cmd(cd);
                }
                break;
            }
            case COMPARE:
                if (arg1 == null) {
                    out_file.println("The argument to `compare' must be `on', `off', `delta', or `ratio'\n");
                    break;
                }
                else
                {
                    CmpMode cmp;
                    if (arg1.equals("OFF") || arg1.equals("0"))
                        cmp = CmpMode.CMP_DISABLE;
                    else if (arg1.equals("ON") || arg1.equals("1"))
                        cmp = CmpMode.CMP_ENABLE;
                    else if (arg1.equals("DELTA"))
                        cmp = CmpMode.CMP_DELTA;
                    else if (arg1.equals("RATIO"))
                        cmp = CmpMode.CMP_RATIO;
                    else {
                        out_file.println("The argument to `compare' must be `on', `off', `delta', or `ratio'\n");
                        break;
                    }
                    CmpMode oldMode = dbev.get_compare_mode();
                    dbev.set_compare_mode(cmp);
                    if (oldMode != cmp) {
                        dbev.reset_data(false);
                        dbeSession.reset_data();
                    }
                }
                break;
            case LEAKS:
                if (!dbeSession.is_leaklist_available ()) {
                    out_file.println("\nHeap trace information was not requested when recording experiments\n");
                    break;
                }
                if (dbev.comparingExperiments ()) {
                    // XXXX show warning for compare
                    out_file.println("\nNot available when comparing experiments\n");
                    break;
                }
                cd = new Print.er_print_leaklist (dbev, true, false, dbev.get_limit ());
                print_cmd(cd);
                break;
            case ALLOCS:
                if (!dbeSession.is_leaklist_available ()) {
                    out_file.println("\nHeap trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_leaklist (dbev, false, true, dbev.get_limit ());
                print_cmd(cd);
                break;
            case HEAP:
                if (!dbeSession.is_heapdata_available ())
                {
                    out_file.println("Heap trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_heapactivity (dbev, Histable.Type.HEAPCALLSTACK, false, dbev.get_limit());
                print_cmd(cd);
                break;
            case HEAPSTAT:
                if (!dbeSession.is_heapdata_available ())
                {
                    out_file.println("Heap trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_heapactivity (dbev, Histable.Type.HEAPCALLSTACK, true, dbev.get_limit());
                print_cmd (cd);
                break;
            case IOACTIVITY:
                if (!dbeSession.is_iodata_available ()) {
                    out_file.println("I/O trace information was not requested when recording experiments\n");
                    break;
                }
                if (dbev.comparingExperiments ()) {
                    // XXXX show warning for compare
                    out_file.println("\nNot available when comparing experiments\n");
                    break;
                }
                cd = new Print.er_print_ioactivity (dbev, Histable.Type.IOACTFILE, false, dbev.get_limit());
                print_cmd (cd);
                break;
            case IOVFD:
                if (!dbeSession.is_iodata_available ())
                {
                    out_file.println("I/O trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_ioactivity (dbev, Histable.Type.IOACTVFD, false, dbev.get_limit());
                print_cmd (cd);
                break;
            case IOCALLSTACK:
                if (!dbeSession.is_iodata_available ())
                {
                    out_file.println("I/O trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_ioactivity (dbev, Histable.Type.IOCALLSTACK, false, dbev.get_limit());
                print_cmd (cd);
                break;
            case IOSTAT:
                if (!dbeSession.is_iodata_available ()) {
                    out_file.println("I/O trace information was not requested when recording experiments\n");
                    break;
                }
                cd = new Print.er_print_ioactivity (dbev, Histable.Type.IOACTVFD, true, dbev.get_limit());
                print_cmd (cd);
                break;
            case HELP:
                Command.print_help(whoami, false, true, out_file);
                break;
            case VERSION_cmd:
                Application.print_version_info();
                break;
            case SCRIPT:
                if (arg1 != null) {
                    ck_file = fopen(arg1, "r");
                    if (ck_file == null) {
                        System.err.printf("Error: Script file cannot be opened: %s\n", arg1);
                    } else {
                        save_file = inp_file;
                        inp_file = ck_file;
                        proc_script();
                        inp_file = save_file;
                    }
                }
                else {
                    System.err.printf("Error: No filename has been specified.\n");
                }
                break;
            case QUIT:
                System.exit(0);
                break;

            // commands relating to index Objects
            case INDXOBJ:
                if ((cparam == -1) && (arg1 == null))
                {
                    System.err.printf("Error: No index object name has been specified.\n");
                    break;
                }
                // automatically load machine model if applicable
                DBE.dbeDetectLoadMachineModel(dbevindex);
                indxobj(arg1, cparam);
                break;
            case INDXOBJLIST:
                // automatically load machine model if applicable
                DBE.dbeDetectLoadMachineModel(dbevindex);
                indxo_list(false, out_file);
                break;

            // define a new IndexObject type
            case INDXOBJDEF:
                if (arg1 == null)
                {
                    System.err.printf("Error: No index object name has been specified.\n");
                    break;
                }
                if (arg2 == null)
                {
                    System.err.printf("Error: No index-expr has been specified.\n");
                    break;
                }
                indxo_define(arg1, arg2, arg3, arg4);
                break;

            // the commands following this are unsupported/hidden
            case IFREQ:
                if (!dbeSession.is_ifreq_available()) {
                    out_file.println("\nInstruction frequency data was not requested when recording experiments\n");
                    break;
                }
                ifreq();
                break;
            case DUMPNODES:
                dump_nodes();
                break;
            case DUMPSTACKS:
                dump_stacks();
                break;
            case DUMPUNK:
                dump_unk_pcs();
                break;
            case DUMPFUNC:
                dump_funcs(arg1);
                break;
            case DUMPDOBJS:
                dump_dataobjects(arg1);
                break;
            case DUMPMAP:
                dump_map();
                break;
            case DUMPENTITIES:
                dump_entities();
                break;
            case DUMP_PROFILE:
                dbev.dump_profile(out_file);
                break;
            case DUMP_SYNC:
                dbev.dump_sync(out_file);
                break;
            case DUMP_HWC:
                dbev.dump_hwc(out_file);
                break;
            case DUMP_HEAP:
                if (!dbeSession.is_leaklist_available ()) {
                    out_file.println("\nHeap trace information was not requested when recording experiments\n");
                    break;
                }
                dbev.dump_heap(out_file);
                break;
            case DUMP_IOTRACE:
                if (!dbeSession.is_iodata_available ()) {
                    out_file.println("\nI/O trace information was not requested when recording experiments\n");
                    break;
                }
                dbev.dump_iotrace(out_file);
                break;
            case DMEM:
                if (arg1 == null) {
                    System.err.printf("Error: No sample has been specified.\n");
                } else {
                    Experiment exp = dbeSession.get_exp(0);
                    if (exp != null) {
                        exp.DBG_memuse(arg1);
                    }
                }
                break;
            case DUMP_GC:
                if (!dbeSession.has_java ()) {
                    out_file.println("\nJava garbage collection information was not requested when recording experiments\n");
                    break;
                }
                dbev.dump_gc_events (out_file);
                break;
            case DKILL:
            {
                if (arg1 == null) {
                    System.err.printf("Error: No process has been specified.\n");
                    break;
                }
                if (arg2 == null) {
                    System.err.printf("Error: No signal has been specified.\n");
                    break;
                }
                int p = Integer.parseInt(arg1);
                int signum = Integer.parseInt(arg2);
                String ret = DBE.dbeSendSignal(p, signum);
                if (ret != null) {
                    System.err.printf("Error: %s", ret);
                }
            }
            break;
            case PROCSTATS:
                dump_stats();
                break;
            case ADD_EXP:
            case OPEN_EXP:
                if (arg1 == null) {
                    System.err.printf("Error: No experiment name has been specified.\n");
                } else {
                    String[][] groups = new String[][] {{arg1}};
                    String res = DBE.dbeOpenExperimentList(dbevindex, groups, cmd_type == CmdType.OPEN_EXP);
                    if (cmd_type == CmdType.OPEN_EXP)
                        System.err.printf("Previously loaded experiment have been dropped.\n");
                    if (res != null)
                        System.err.printf("%s", res);
                    else
                        System.err.printf("Experiment %s has been loaded\n", arg1);
                }
                break;
            case DROP_EXP:
            {
                if (arg1 == null)
                    System.err.printf("Error: No experiment name has been specified.\n");
                else
                {
                    int exp_index = dbeSession.find_experiment(arg1);
                    if (exp_index < 0) {
                        System.err.printf("Error: experiment %s has not been opened.\n", arg1);
                    }
                    else {
                        int[] expid = new int[] {exp_index};
                        String res = DBE.dbeDropExperiment(dbevindex, expid);
                        if (res != null) {
                            System.err.printf("%s\n", res);
                        } else {
                            System.err.printf("Experiment %s has been dropped\n", arg1);
                        }
                    }
                }
            }
            break;
            case HHELP:
                // automatically load machine model if applicable
                DBE.dbeDetectLoadMachineModel(dbevindex);
                Command.print_help(whoami, false, false, out_file);
                out_file.println();
                indxo_list (false, out_file);
                out_file.println();
                mo_list (false, out_file);
                out_file.println("\nSee gprofng(1) for more details");
                break;
            case QQUIT:
                was_QQUIT = true;
                return;
            default:
                System.err.printf("Error: Invalid option\n");
                break;
        }

        // check for any processing error messages
        dump_proc_warnings ();
        out_file.flush();
    }

    void print_func(Histable.Type type, Print.Print_mode mode, MetricList mlist1,
                    MetricList mlist2, String func_name, String sel) {
        DbeSession dbeSession = DbeSession.getInstance();
        Hist_data hist_data;
        Hist_data.HistItem hitem;
        String errstr;
        int list_limit = limit;
        Histable sobj = null;
        MetricList mlist;
        StringBuilder sb = new StringBuilder();
        String sname = dbev.getSort(MetricType.MET_NORMAL);
        sb.append (sname);

        switch (mode) {
            case MODE_DETAIL:
            {
                // The first metric list, mlist1, is only used to pick out the sort
                //    mlist2 is the one used to generate the data
                String prevsort = null;
                // if specified, find the function from the function name
                if (!"<All>".equals(func_name)) {
                    if ((!dbeSession.find_obj(dis_file, inp_file, sobj, func_name,
                            sel, Histable.Type.FUNCTION, (inp_file != System.in)) || (sobj == null)) &&
                            !dbeSession.find_obj(dis_file, inp_file, sobj, func_name,
                                    sel, Histable.Type.LOADOBJECT, (inp_file != System.in)))
                        return;
                    if (sobj == null) { // function/segment object not found
                        System.err.printf("Error: No function with given name `%s' found.\n", func_name);
                        return;
                    }
                    list_limit = 1;
                }
                else
                {
                    // find the sort metric from the reference list
                    prevsort = mlist2.get_sort_cmd();

                    // find the current sort metric from the current list
                    String cursort = mlist1.get_sort_cmd ();

                    // find the corresponding metric in the reference list
                    mlist2.set_sort(cursort, false);
                    // if it fails, nothing is needed
                }
                hist_data = dbev.get_hist_data(mlist2, type, 0, Hist_data.Mode.ALL);

                // restore
                if (sobj == null) {
                    if (prevsort == null) {
                        abort();
                    }
                    mlist2.set_sort(prevsort, false);
                }
                mlist = mlist2;
                break;
            }
            case MODE_GPROF:
                // if specified, find the function from the function name
                if (func_name != null && !func_name.equals("<All>")) {
                    if (!dbeSession.find_obj(dis_file, inp_file, sobj, func_name,
                            sel, Histable.Type.FUNCTION, (inp_file != System.in)))
                        return;
                    if (sobj == null) { // function/segment object not found
                        System.err.printf("Error: No function with given name `%s' found.\n", func_name);
                        return;
                    }
                    list_limit = 1;
                    sb.setLength(0);
                }
                sb.append ("\nCallers and callees sorted by metric: ");
                sname = dbev.getSort(MetricType.MET_CALL);
                sb.append(sname);

                // Use mlist2 to generate the sort order.
                // mlist1 is used to generate the data.
                hist_data = dbev.get_hist_data(mlist2, type, 0, Hist_data.Mode.ALL);
                mlist = mlist1;
                break;
            default:
                hist_data = dbev.get_hist_data(mlist1, type, 0, Hist_data.Mode.ALL);
                mlist = mlist1;
        }

        if (hist_data.get_status() != Hist_data.Hist_status.SUCCESS) {
            errstr = DbeView.status_str(DbeView.DbeView_status.DBEVIEW_NO_DATA);
            if (errstr != null) {
                System.err.printf("Error: %s\n", errstr);
            }
            return;
        }

        if (type == Histable.Type.FUNCTION) {
            for (int index = 0; index < hist_data.size(); index++) {
                hitem = hist_data.fetch(index);
                if (hitem.obj.get_type() == Histable.Type.FUNCTION)
                    // fetch the name, since that will force a format conversion
                    hitem.obj.get_name();
            }
        }

        String name = sb.toString();
        Print.er_print_histogram cd = new Print.er_print_histogram(dbev, hist_data,
                mlist, mode, list_limit, name, sobj, false, false);
        print_cmd(cd);
    }

    void print_cmd(Print.er_print_common_display cd) {
        cd.set_out_file(out_file);
        cd.data_dump();
    }

    void disp_list(int[] align, String[] header, String[][] lists) {
        int size = lists[0].length;
        int num_header = header.length;
        int[] maxlen = new int[num_header];
        String[] fmt = new String[num_header];
        for (int i = 0; i < num_header; i++) {
            maxlen[i] = header[i].length();
            for (int j = 0; j < size; j++) {
                int len = lists[i][j].length();
                maxlen[i] = Math.max(maxlen[i], len);
            }

            // get format string
            if ((align[i] == -1) && (i == num_header - 1))
                fmt[i] = "%s ";
            else
                fmt[i] = "%" + (align[i] * maxlen[i]) + "s ";

            // write header
            out_file.printf(fmt[i], header[i]);
        }
        out_file.println();

        // write separator "==="
        int np = 0;
        for (int i = 0; (i < num_header) && (np < 132); i++) {
            int nc = maxlen[i];
            if (nc + np > 132) {
                nc = 132 - np;
            }
            for (int j = 0; j < nc; j++) {
                out_file.print('=');
            }
            out_file.print(' ');
            np += nc + 1;
        }
        out_file.println();

        // write lists
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < num_header; i++) {
                out_file.printf(fmt[i], lists[i][j]);
            }
            out_file.println();
        }
    }

    // prev is the loadobject segment index that was last returned; the search starts
    // following that loadobject. Returns the matching seg_idx, or -1.
    int is_valid_seg_name(String lo_name, int prev) {
        String p_lo_name = lo_name;
        // strip angle brackets from all but <Unknown> and <Total>
        if (!lo_name.equals("<Unknown>") && !lo_name.equals("<Total>") && lo_name.startsWith("<")) {
            int gt = lo_name.indexOf('>');
            p_lo_name = gt >= 0 ? lo_name.substring(1, gt) : lo_name.substring(1);
        }
        for (LoadObject lo : DbeSession.getInstance().get_text_segments()) {
            if (prev > 0) {
                if (lo.seg_idx == prev) // this is where we left off
                    prev = -1;
                continue;
            }
            if (cmp_seg_name(lo.get_pathname(), p_lo_name)) {
                if (lo_name.endsWith(".class>") || lo_name.endsWith(".class")) {
                    System.err.printf("Error: Java class `%s' is not selectable\n", lo_name);
                    return -1;
                }
                return lo.seg_idx;
            }
        }
        return -1;
    }

    boolean cmp_seg_name(String full_name, String lo_name) {
        String cmp_name;
        if (!lo_name.contains("/") && full_name.contains("/"))
            cmp_name = full_name.substring(full_name.lastIndexOf('/') + 1); // basename
        else
            cmp_name = full_name; // full path name
        return lo_name.equals(cmp_name);
    }

    // Note that this does not affect the strings in Settings, unlike object_show,
    // object_hide, and object_api. Returns -1 on error, otherwise the number of
    // load objects made visible.
    int process_object_select(String names) {
        int no_lobj = 0;
        boolean got_err = false;
        List<LoadObject> lobjs = DbeSession.getInstance().get_text_segments();
        if (names == null || names.equalsIgnoreCase(Command.ALL_CMD)) {
            // full coverage
            for (LoadObject lo : lobjs)
                dbev.set_lo_expand(lo.seg_idx, LibExpand.LIBEX_SHOW);
        } else {
            // parsing coverage: first, hide functions from all loadobjects except the
            // java ones
            for (LoadObject lo : lobjs) {
                String lo_name = lo.get_name();
                if (lo_name != null && (lo_name.endsWith(".class>") || lo_name.endsWith(".class")))
                    continue;
                dbev.set_lo_expand(lo.seg_idx, LibExpand.LIBEX_HIDE);
            }

            // loop over the provided names
            for (String lo_name : names.split(",")) {
                int matched = 0;
                int seg_idx = is_valid_seg_name(lo_name, -1);
                while (seg_idx != -1) {
                    dbev.set_lo_expand(seg_idx, LibExpand.LIBEX_SHOW);
                    no_lobj++;
                    matched++;
                    seg_idx = is_valid_seg_name(lo_name, seg_idx);
                }
                if (matched == 0) {
                    got_err = true;
                    System.err.printf("Error: Unknown load object: `%s'\n", lo_name);
                }
            }
        }

        if (!got_err) {
            // good coverage string
            cov_string = names;
        } else {
            // bad, restore original coverage
            no_lobj = -1;
            process_object_select(cov_string);
        }
        return no_lobj;
    }

    void set_libexpand(String cov, LibExpand expand) {
        boolean changed = dbev.set_libexpand(cov, expand);
        // update_lo_expands() is only skipped by callers that set_libexpand() many
        // times in a loop and call it once at the end; we call it directly every time.
        if (changed)
            dbev.update_lo_expands();
    }

    void set_libdefaults() {
        dbev.set_libdefaults();
    }

    void ifreq() {
        // instruction-frequency report; depends on DbeView.ifreq(PrintStream), not yet
        // ported.
        throw new RuntimeException("ERPrint.ifreq not implemented");
    }

    // The following dump_* methods print raw debug/internal data for the corresponding
    // "d..." commands (dnodes, dstacks, dunkpc, dfuncs, ddobjs, dmap, dentities);
    // dump_stats/dump_proc_warnings print PathTree processing statistics/warnings.
    // None of this is on the -functions critical path; depends on PathTree (still a
    // stub) and other unported machinery, so these are left as stubs for now.
    void dump_nodes() {
        throw new RuntimeException("ERPrint.dump_nodes not implemented");
    }

    void dump_stacks() {
        throw new RuntimeException("ERPrint.dump_stacks not implemented");
    }

    void dump_unk_pcs() {
        throw new RuntimeException("ERPrint.dump_unk_pcs not implemented");
    }

    void dump_funcs(String arg1) {
        throw new RuntimeException("ERPrint.dump_funcs not implemented");
    }

    void dump_dataobjects(String arg1) {
        throw new RuntimeException("ERPrint.dump_dataobjects not implemented");
    }

    void dump_map() {
        throw new RuntimeException("ERPrint.dump_map not implemented");
    }

    void dump_entities() {
        throw new RuntimeException("ERPrint.dump_entities not implemented");
    }

    void dump_stats() {
        throw new RuntimeException("ERPrint.dump_stats not implemented");
    }

    void dump_proc_warnings() {
//        TODO: restore once PAthTree is implemented
//        PathTree p = dbev.get_path_tree();
//        if (p == null)
//            return;
//        Emsg m = p.fetch_warnings();
//        while (m != null) {
//            out_file.printf("%s\n", m.get_msg());
//            m = m.next;
//        }
//        dbev.get_path_tree().delete_warnings();
    }

    void exp_list() {
        int[] align = new int[] {1, 1, 1, -1};
        String[] header = new String[] {"ID", "Sel", "PID", "Experiment"};

        int size = DbeSession.getInstance().nexps();
        String[][] lists = new String[][] {new String[size], new String[size], new String[size], new String[size]};

        for (int index = 0; index < size; index++) {
            Experiment exp = DbeSession.getInstance().get_exp(index);
            lists[0][index] = Integer.toString(index + 1);
            lists[1][index] = dbev.get_exp_enable(index) ? "yes" : "no";
            lists[2][index] = Integer.toString(exp.getPID());
            lists[3][index] = exp.get_expt_name();
        }
        disp_list(align, header, lists);
    }

    void obj_list() {
        List<LoadObject> text_segments = DbeSession.getInstance().get_text_segments();
        if (text_segments.isEmpty()) {
            dis_file.print("There are no load objects in this experiment\n");
            return;
        }
        int[] align = new int[] {-1, -1, -1, -1};
        String[] header = new String[] {"Sel", "Load Object", "Index", "Path"};

        int size = text_segments.size();
        String[][] lists = new String[][] {new String[size], new String[size], new String[size], new String[size]};

        int new_index = 0;
        for (LoadObject lo : text_segments) {
            String lo_name = lo.get_name();
            if (lo_name != null && lo_name.endsWith(".class>")) {
                continue;
            }
            LibExpand expand = dbev.get_lo_expand(lo.seg_idx);
            switch (expand) {
                case LIBEX_SHOW:
                    lists[0][new_index] = "show";
                    break;
                case LIBEX_HIDE:
                    lists[0][new_index] = "hide";
                    break;
                case LIBEX_API:
                    lists[0][new_index] = "API-only";
                    break;
            }
            lists[1][new_index] = lo_name;
            lists[2][new_index] = Integer.toString(lo.seg_idx);
            lists[3][new_index] = lo.get_pathname();
            new_index++;
        }
        disp_list(align, header, lists);
    }

    void seg_list() {
        // XXX seg_list only prints text segments; should extend to all
        List<LoadObject> lobjs = DbeSession.getInstance().get_text_segments();
        if (lobjs.isEmpty()) {
            dis_file.print("There are no segments in this experiment\n");
            return;
        }
        int[] align = new int[] {-1, 1, -1};
        String[] header = new String[] {"Sel", "Size", "Segment"};

        int size = lobjs.size();
        String[][] lists = new String[][] {new String[size], new String[size], new String[size]};

        int new_index = 0;
        for (LoadObject lo : lobjs) {
            String lo_name = lo.get_name();
            if (lo_name != null && lo_name.endsWith(".class>")) {
                continue;
            }
            boolean expand = dbev.get_lo_expand(lo.seg_idx) == LibExpand.LIBEX_SHOW;
            lists[0][new_index] = expand ? "yes" : "no";
            lists[1][new_index] = Long.toString(lo.get_size());
            lists[2][new_index] = lo.get_pathname();
            new_index++;
        }
        disp_list(align, header, lists);
    }

    void filter_list(CmdType cmd_type) {
        // first ensure that the data has been read
        MetricList mlist = dbev.get_metric_list(MetricType.MET_INDX);
        dbev.get_hist_data(mlist, Histable.Type.INDEXOBJ, 0, Hist_data.Mode.ALL);

        int[] align = new int[] {1, -1, 1, 1};
        String[] header = new String[] {"Exp", "Sel", "Total", "Status"};

        int size = DbeSession.getInstance().nexps();
        String[][] lists = new String[][] {new String[size], new String[size], new String[size], new String[size]};

        for (int index = 0; index < size; index++) {
            Filter.FilterNumeric select;
            switch (cmd_type) {
                case SAMPLE_LIST:
                    select = dbev.get_FilterNumeric(index, SAMPLE_FILTER_IDX);
                    break;
                case THREAD_LIST:
                    select = dbev.get_FilterNumeric(index, THREAD_FILTER_IDX);
                    break;
                case LWP_LIST:
                    select = dbev.get_FilterNumeric(index, LWP_FILTER_IDX);
                    break;
                case CPU_LIST:
                    select = dbev.get_FilterNumeric(index, CPU_FILTER_IDX);
                    break;
                default:
                    abort(); // internal error
                    return;
            }
            if (select == null)
                continue;
            lists[0][index] = Integer.toString(index + 1);
            String pattern = dbev.get_exp_enable(index) ? select.get_pattern() : null;
            lists[1][index] = (pattern != null && !pattern.isEmpty()) ? pattern : "none";
            lists[2][index] = Long.toString(select.nelem());
            lists[3][index] = select.get_status();
        }
        disp_list(align, header, lists);
    }

    int check_exp_id (int exp_id, String sel) {
        if (exp_id < 0 || exp_id >= DbeSession.getInstance().nexps()) {
            System.err.printf("Error: Invalid number entered: %s\nType \"exp_list\" for a list of all experiments.\n", sel);
            return -1;
        }
        return exp_id;
    }

    int[] get_exp_id(String sel) {
        if (sel == null || "all".equals(sel)) {
            // loop over all experiments
            return new int[] {0, DbeSession.getInstance().nexps() - 1};
        } else {
            int id = Integer.parseInt(sel) - 1;
            int exp_id = check_exp_id(id, sel);
            if (exp_id != -1)
                return new int[] {exp_id, exp_id};
        }
        return null;
    }

    void print_objects() {
        List<LoadObject> lobjs = DbeSession.getInstance().get_text_segments();
        String msg = Print.pr_load_objects(lobjs, "");
        out_file.printf("%s\n", msg);
    }

    void print_segments() {
        // matches native gp-display-text.cc: print_segments() is itself unimplemented
        // upstream (it computes the load-object list but never uses it, always
        // printing this literal message)
        dis_file.print("Not implemented yet!\n");
    }

    void print_overview() {
        // depends on dbeGetOverviewText()/dbeGetRefMetricTreeValues() (missing),
        // print_overview_nodes()/print_overview_tree() tree-rendering helpers (not
        // ported), and several Experiment fields (hostname, architecture, os_version,
        // start_sec) that don't exist yet either; a subsystem of its own, not yet
        // ported.
        throw new RuntimeException("ERPrint.print_overview not implemented");
    }

    void print_ctree(Command.CmdType cmd_type) {
        // depends on the calltree/path-tree rendering machinery (PathTree, still a
        // stub); not yet ported.
        throw new RuntimeException("ERPrint.print_ctree not implemented");
    }

    void print_gprof(Command.CmdType cmd_type, String func_name, String sel) {
        DbeSession dbeSession = DbeSession.getInstance();
        Histable sobj = null;
        if (func_name != null) {
            if ((!dbeSession.find_obj(dis_file, inp_file, sobj, func_name, sel,
                    Histable.Type.FUNCTION, (inp_file != System.in)) || sobj == null)
                    && !dbeSession.find_obj(dis_file, inp_file, sobj, func_name, sel,
                    Histable.Type.LOADOBJECT, (inp_file != System.in)))
                return;
            if (sobj == null) {
                System.err.printf("Error: No function with given name `%s' found.\n", func_name);
                return;
            }
        }
        if (cmd_type == CmdType.CPREPEND) {
            if (sobj == null) {
                System.err.printf("Error: No function name has been specified.\n");
                return;
            }
            cstack.add(0, sobj);
        } else if (cmd_type == CmdType.CAPPEND) {
            if (sobj == null) {
                System.err.printf("Error: No function name has been specified.\n");
                return;
            }
            cstack.add(sobj);
        } else if (cmd_type == CmdType.CSINGLE) {
            if (sobj != null) {
                cstack.clear();
                cstack.add(sobj);
            } else if (cstack.isEmpty()) {
                System.err.printf("Error: No function name has been specified.\n");
                return;
            }
        } else if (cmd_type == CmdType.CRMFIRST) {
            if (cstack.size() <= 1) {
                System.err.printf("Warning: there is only one function in the stack segment; cannot remove it.\n");
                return;
            }
            cstack.remove(0);
        } else if (cmd_type == CmdType.CRMLAST) {
            if (cstack.size() <= 1) {
                System.err.printf("Warning: there is only one function in the stack segment; cannot remove it.\n");
                return;
            }
            cstack.remove(cstack.size() - 1);
        }

        Print.er_print_gprof cd = new Print.er_print_gprof(dbev, cstack);
        print_cmd(cd);
    }

    void indxobj(String name, int cparam) {
        int type;
        if (name != null) {
            // find the index object index for the name
            type = DbeSession.getInstance().findIndexSpaceByName(name);
            if (type < 0) {
                System.err.printf("Error: Unknown Index Object type: %s\n", name);
                return;
            }
        } else {
            String indxname = DbeSession.getInstance().getIndexSpaceName(cparam);
            if (indxname == null) {
                System.err.printf("Error: Unknown Index Object type: %d\n", cparam);
                return;
            }
            type = cparam;
        }
        DBE.dbePrintData(0, FuncListDisp_type.DSP_INDXOBJ.value, type, null, null, out_file);
    }

    void indxo_define(String ioname, String io_index_exp, String sdesc, String ldesc) {
        String ret = DBE.dbeDefineIndxObj(ioname, io_index_exp, sdesc, ldesc);
        if (ret != null)
            System.err.printf("indxobj_define for %s failed: %s\n", ioname, ret);
    }

    void indxo_list(boolean showtab, PrintStream outf) {
        outf.print(" Index Object Types Available:\n");
        Object[] res = DBE.dbeGetIndxObjDescriptions(0);
        if (res == null) // if none is defined
            return;
        List<Boolean> indxtab = showtab ? dbev.get_IndxTabState() : null;
        String[] indxo_names = (String[]) res[1];
        String[] indxo_i18nnames = (String[]) res[3];
        String[] indxo_exprlist = (String[]) res[5];
        for (int i = 0; i < indxo_names.length; i++) {
            String name = indxo_names[i];
            String i18n_name = indxo_i18nnames[i];
            boolean hasI18n = i18n_name != null && !i18n_name.equals(name);
            if (indxtab != null) {
                if (hasI18n)
                    outf.printf("  %c %s (%s)\n", indxtab.get(i) ? 'T' : 'F', i18n_name, name);
                else
                    outf.printf("  %c %s\n", indxtab.get(i) ? 'T' : 'F', name);
            } else {
                if (hasI18n)
                    outf.printf("  %s (%s)", i18n_name, name);
                else
                    outf.printf("  %s", name);
            }
            String exprs = indxo_exprlist[i];
            outf.print(exprs != null ? " \t" + exprs + "\n" : "\n");
        }
    }

    void mo_list(boolean showtab, PrintStream outf) {
        // depends on MemorySpace.getMemObjects(); the MemorySpace subsystem (memory
        // object type definitions) is not yet ported.
        throw new RuntimeException("ERPrint.mo_list not implemented");
    }

    void print_anno_file(String name, String sel, String srcFile, boolean isDisasm,
                          PrintStream dis_file, InputStream inp_file, PrintStream out_file,
                          DbeView dbev, boolean xdefault) {
        // annotated source/disassembly rendering; depends on the Function/Module/
        // SourceFile source hierarchy, none of which exist yet.
        throw new RuntimeException("ERPrint.print_anno_file not implemented");
    }

    void describe() {
        // depends on dbeGetFilterKeywords() (filter/query-language keyword metadata);
        // not yet ported.
        throw new RuntimeException("ERPrint.describe not implemented");
    }
}
