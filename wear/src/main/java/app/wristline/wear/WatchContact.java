package app.wristline.wear;

import java.util.List;

/** A person the watch can call, and the apps (packages) that reach them. */
final class WatchContact {
    final long id;
    final String name;
    final List<String> apps;

    WatchContact(long id, String name, List<String> apps) {
        this.id = id;
        this.name = name;
        this.apps = apps;
    }
}
