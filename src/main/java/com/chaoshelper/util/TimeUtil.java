package com.chaoshelper.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private TimeUtil() {
	}

	public static String clock(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis));
	}

	public static String dateTime(long millis) {
		return DATE_TIME.format(Instant.ofEpochMilli(millis));
	}

	public static String duration(long millis) {
		long seconds = Math.max(0, millis / 1000L);
		if (seconds < 60) {
			return seconds + "s";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + "m" + (seconds % 60) + "s";
		}
		return (minutes / 60) + "h" + (minutes % 60) + "m";
	}
}
