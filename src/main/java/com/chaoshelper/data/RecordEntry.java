package com.chaoshelper.data;

public final class RecordEntry {
	public RecordType type = RecordType.LOG;
	public long timeMillis;
	public String world = "";
	public double x;
	public double y;
	public double z;
	public String message = "";
	/** Player name for log-off points; empty for deaths and chat lines. */
	public String subject = "";

	public RecordEntry() {
	}

	public RecordEntry(RecordType type, long timeMillis, String world, double x, double y, double z, String message) {
		this(type, timeMillis, world, x, y, z, message, "");
	}

	public RecordEntry(RecordType type, long timeMillis, String world, double x, double y, double z, String message,
			String subject) {
		this.type = type == null ? RecordType.LOG : type;
		this.timeMillis = timeMillis;
		this.world = world == null ? "" : world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.message = message == null ? "" : message;
		this.subject = subject == null ? "" : subject;
	}
}
