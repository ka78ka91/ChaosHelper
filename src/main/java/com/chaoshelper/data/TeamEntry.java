package com.chaoshelper.data;

public final class TeamEntry {
	public String name = "";
	public String uuid = "";
	public String note = "";
	public long addedAt;

	public TeamEntry() {
	}

	public TeamEntry(String name, String uuid, String note, long addedAt) {
		this.name = name;
		this.uuid = uuid == null ? "" : uuid;
		this.note = note == null ? "" : note;
		this.addedAt = addedAt;
	}
}
