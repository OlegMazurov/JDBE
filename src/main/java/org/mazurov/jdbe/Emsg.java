package org.mazurov.jdbe;

public class Emsg {

    enum Cmsg_warn {
        CMSG_NONE(-1),
        CMSG_WARN(0),
        CMSG_ERROR(1),
        CMSG_FATAL(2),
        CMSG_COMMENT(3),
        CMSG_PARSER(4),
        CMSG_ARCHIVE(5);

        int value;
        Cmsg_warn(int value) {
            this.value = value;
        }
    } ;

    Emsg next;       // next message in a queue
    Cmsg_warn warn;   // error/warning/...
    int flavor;       // the message flavor
    String par;        // the input parameter string
    String text;       // The I18N text of the message

    public Emsg(Cmsg_warn w, String i18n_text) {
        warn = w;
        flavor = 0;
        par = null;
        text = i18n_text;
        next = null;
    }


    public String get_msg() {
        return text;
    };

    public Cmsg_warn get_warn() {
        return warn;
    };

    public static String pr_mesgs(Emsg msg, String null_str, String lead) {
        if (msg == null) return null_str;

        StringBuilder sb = new StringBuilder();
        for (Emsg m = msg; m != null; m = m.next) {
            sb.append(lead);
            sb.append(m.get_msg());
            sb.append("\n");
        }
        return sb.toString();
    }

}
