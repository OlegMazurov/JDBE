package org.mazurov.jdbe;

public class DbeApplication extends Application {

    boolean rdtMode;
    private static DbeApplication INSTANCE;
    private String[] args;
    private Settings settings = new Settings();

    protected DbeApplication(String[] args) {
        INSTANCE = this;
        this.args = args;
        DbeSession.createSession(settings);
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
