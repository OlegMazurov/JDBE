package org.mazurov.jdbe;

public class ERPrint extends DbeApplication {

    ERPrint(String[] args) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        new ERPrint(args);
        ERIPC.ipc_mainLoop();
    }
}
