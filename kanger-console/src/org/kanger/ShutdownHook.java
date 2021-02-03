package org.kanger;

import org.kanger.interfaces.IUser;

public class ShutdownHook extends Thread {
    IUser user = null;

    public ShutdownHook(IUser user) {
        this.user = user;
    }

    @Override
    public void run() {
        super.run();
        try {
            if (user != null && !user.isClosed()) {
                user.flush();
                user.close(null);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
