package com.chaoshelper.data;

/**
 * A discovered End Ship. Stored in {@code data.json} so the list survives a
 * restart: the whole point of the finder is to fly back to a ship that was
 * spotted minutes ago from high altitude.
 *
 * <p>{@code frameX/Y/Z} is the item frame that holds the elytra once it has been
 * seen; the finder then uses the treasure room instead of the dragon head as the
 * navigation anchor. {@link Integer#MIN_VALUE} means "not seen yet".
 */
public final class ShipPoint {
	public String dimension = "";
	public int x;
	public int y;
	public int z;
	public long foundAt;
	/** True once the elytra frame has been emptied (by us or by the player). */
	public boolean looted;
	public int frameX = Integer.MIN_VALUE;
	public int frameY = Integer.MIN_VALUE;
	public int frameZ = Integer.MIN_VALUE;

	public ShipPoint() {
	}

	public ShipPoint(String dimension, int x, int y, int z) {
		this.dimension = dimension == null ? "" : dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.foundAt = System.currentTimeMillis();
	}

	public boolean hasFrame() {
		return frameX != Integer.MIN_VALUE;
	}

	public void setFrame(int x, int y, int z) {
		frameX = x;
		frameY = y;
		frameZ = z;
	}

	public double distanceSquaredTo(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dy = y + 0.5 - py;
		double dz = z + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}
}
