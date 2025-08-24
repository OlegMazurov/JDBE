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
