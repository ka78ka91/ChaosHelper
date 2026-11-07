package com.chaoshelper.data;

public final class Waypoint {
	public String name = "";
	public double x;
	public double y;
	public double z;
	public String dimension = "";
	public String note = "";
	public long createdAt;

	public Waypoint() {
	}

	public Waypoint(String name, double x, double y, double z, String dimension, String note, long createdAt) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension == null ? "" : dimension;
		this.note = note == null ? "" : note;
		this.createdAt = createdAt;
	}
}
