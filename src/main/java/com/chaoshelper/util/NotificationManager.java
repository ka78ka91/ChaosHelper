package com.chaoshelper.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Short HUD notices used for toggles and command results. Not persisted.
 */
public final class NotificationManager {
	public record Notice(long timeMillis, String message, boolean error) {
	}

	private final List<Notice> notices = new ArrayList<>();

	public synchronized void info(String message) {
		add(message, false);
	}

	public synchronized void error(String message) {
		add(message, true);
	}

	private void add(String message, boolean error) {
		notices.add(new Notice(System.currentTimeMillis(), message, error));
		while (notices.size() > 24) {
			notices.removeFirst();
		}
	}

	public synchronized List<Notice> recent(int seconds) {
		long cutoff = System.currentTimeMillis() - Math.max(0, seconds) * 1000L;
		List<Notice> result = new ArrayList<>();
		for (Notice notice : notices) {
			if (notice.timeMillis() >= cutoff) {
				result.add(notice);
			}
		}
		return result;
	}

	public synchronized void clear() {
		notices.clear();
	}
}
