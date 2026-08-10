package ru.gran.edge2e;

import android.content.Context;

/** Cheap process-local cache so sheet/combat calculations use the same resolved rule graph. */
public final class RuntimeBridge {
    private static int lastKey = Integer.MIN_VALUE;
    private static RuleRuntime.Snapshot lastSnapshot;

    private RuntimeBridge() { }

    public static synchronized RuleRuntime.Snapshot snapshot(CharacterState character, StatsState stats) {
        if (character == null || stats == null) return null;
        Context context = stats.context();
        if (context == null) return null;
        int key = character.toJson().toString().hashCode();
        for (String ability : new String[]{"str","dex","con","int","wis","cha"}) key = key * 31 + stats.ability(ability);
        if (lastSnapshot != null && key == lastKey) return lastSnapshot;
        RuleStore store = new RuleStore(context);
        try {
            lastSnapshot = RuleRuntime.resolve(store, character, stats);
            lastKey = key;
            return lastSnapshot;
        } finally {
            store.close();
        }
    }

    public static synchronized void invalidate() {
        lastKey = Integer.MIN_VALUE;
        lastSnapshot = null;
    }
}
