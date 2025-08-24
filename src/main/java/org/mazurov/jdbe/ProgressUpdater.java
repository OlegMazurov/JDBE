package org.mazurov.jdbe;

public interface ProgressUpdater {
    void setProgress(int percentage, String str);
}
