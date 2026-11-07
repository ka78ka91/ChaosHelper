package com.chaoshelper.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded in-memory error list. Errors are also written to the log by callers.
 * The HUD reads this list even when the normal HUD module is disabled.
 */
public final class ErrorLog {
	public enum Level {
		INFO,
		WARN,
		ERROR
	}

	public record Entry(long timeMillis, Level level, String source, String message) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private static final int MAX_ENTRIES = 32;

	public synchronized void add(Level level, String source, String message) {
		entries.add(new Entry(System.currentTimeMillis(), level, source == null ? "" : source,
				message == null ? "" : message));
		while (entries.size() > MAX_ENTRIES) {
			entries.removeFirst();
		}
	}

	public synchronized List<Entry> recent(int seconds) {
		if (seconds <= 0) {
			// 0 means "keep until cleared".
			return List.copyOf(entries);
		}
		long cutoff = System.currentTimeMillis() - seconds * 1000L;
		List<Entry> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.timeMillis() >= cutoff) {
				result.add(entry);
			}
		}
		return result;
	}

	public synchronized void clearSource(String source) {
		entries.removeIf(entry -> entry.source().equals(source));
	}

	public synchronized void clear() {
		entries.clear();
	}
}
