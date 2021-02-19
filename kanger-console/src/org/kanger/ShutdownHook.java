package org.kanger;

import org.kanger.interfaces.IUser;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class ShutdownHook extends Thread {
    User user = null;

    public ShutdownHook(IUser user) {
        this.user = (User) user;
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
