package org.mazurov.jdbe;

public class Application {

    private String prog_name;
    private String cur_dir;

    public void set_name(String name) {
        prog_name = name; //get_realpath(name);
    }

    public String get_cur_dir() {
        if (cur_dir == null) {
            cur_dir = System.getProperty("user.dir");
        }
        return cur_dir;
    }
}
