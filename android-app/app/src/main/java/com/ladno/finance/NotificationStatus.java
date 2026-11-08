package com.ladno.finance;

import java.util.function.Consumer;

/** One monitor protects both the sticky flag and the status publication that depends on it. */
final class NotificationStatus {
    private boolean overflowed;

    synchronized void overflow(Runnable publishWarning) {
        overflowed = true;
        publishWarning.run();
    }

    synchronized void success(Consumer<Boolean> publishStatus) {
        publishStatus.accept(overflowed);
    }
}
