package com.chaoshelper.gui;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One row in the ClickGUI settings panel.
 *
 * <p>Three shapes cover everything: a boolean toggle, a slider backed by a
 * numeric config field, and a button (cycle an enum, run an action). Values are
 * read live from the config, so there is no duplicate state to keep in sync.
 */
public final class Setting {
	public enum Kind {
		BOOL, SLIDER, BUTTON
	}

	private final String label;
	private final Kind kind;
	private BooleanSupplier boolGetter;
	private Consumer<Boolean> boolSetter;
	private DoubleSupplier sliderGetter;
	private DoubleConsumer sliderSetter;
	private double min;
	private double max;
	private double step;
	private Supplier<String> valueText = () -> "";
	private Runnable action = () -> {
	};

	private Setting(String label, Kind kind) {
		this.label = label;
		this.kind = kind;
	}

	public static Setting bool(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		Setting setting = new Setting(label, Kind.BOOL);
		setting.boolGetter = getter;
		setting.boolSetter = setter;
		setting.valueText = () -> getter.getAsBoolean() ? "开" : "关";
		return setting;
	}

	public static Setting integer(String label, int min, int max, int step, IntSupplier getter, IntConsumer setter) {
		Setting setting = new Setting(label, Kind.SLIDER);
		setting.min = min;
		setting.max = max;
		setting.step = Math.max(1, step);
		setting.sliderGetter = getter::getAsInt;
		setting.sliderSetter = value -> setter.accept((int) Math.round(value));
		setting.valueText = () -> Integer.toString(getter.getAsInt());
		return setting;
	}

	public static Setting decimal(String label, double min, double max, double step,
			DoubleSupplier getter, DoubleConsumer setter) {
		Setting setting = new Setting(label, Kind.SLIDER);
		setting.min = min;
		setting.max = max;
		setting.step = step;
		setting.sliderGetter = getter;
		setting.sliderSetter = setter;
		setting.valueText = () -> {
			double value = getter.getAsDouble();
			return Math.abs(value - Math.rint(value)) < 1.0E-6
					? Long.toString(Math.round(value))
					: String.format(Locale.ROOT, "%.1f", value);
		};
		return setting;
	}

	/** Cycles through labels; the index is stored via the setter. */
	public static Setting option(String label, String[] options, IntSupplier getter, IntConsumer setter) {
		Setting setting = new Setting(label, Kind.BUTTON);
		setting.valueText = () -> options[Math.floorMod(getter.getAsInt(), options.length)];
		setting.action = () -> setter.accept(Math.floorMod(getter.getAsInt() + 1, options.length));
		return setting;
	}

	/** Cycles through an ARGB palette. */
	public static Setting color(String label, IntSupplier getter, IntConsumer setter, int[] palette) {
		Setting setting = new Setting(label, Kind.BUTTON);
		setting.valueText = () -> String.format("#%08X", getter.getAsInt());
		setting.action = () -> {
			int current = getter.getAsInt();
			for (int index = 0; index < palette.length; index++) {
				if (palette[index] == current) {
					setter.accept(palette[(index + 1) % palette.length]);
					return;
				}
			}
			setter.accept(palette[0]);
		};
		return setting;
	}

	public static Setting button(String label, Supplier<String> value, Runnable action) {
		Setting setting = new Setting(label, Kind.BUTTON);
		setting.valueText = value;
		setting.action = action;
		return setting;
	}

	public String label() {
		return label;
	}

	public Kind kind() {
		return kind;
	}

	public String valueText() {
		return valueText.get();
	}

	public boolean boolValue() {
		return boolGetter != null && boolGetter.getAsBoolean();
	}

	public void toggleBool() {
		if (boolSetter != null) {
			boolSetter.accept(!boolGetter.getAsBoolean());
		}
	}

	public double ratio() {
		if (sliderGetter == null || max <= min) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, (sliderGetter.getAsDouble() - min) / (max - min)));
	}

	public void setRatio(double ratio) {
		if (sliderSetter == null) {
			return;
		}
		double clamped = Math.max(0.0, Math.min(1.0, ratio));
		double raw = min + clamped * (max - min);
		if (step > 0) {
			raw = Math.round(raw / step) * step;
		}
		sliderSetter.accept(Math.max(min, Math.min(max, raw)));
	}

	public void press() {
		action.run();
	}

	/** ARGB accent for the current value, used by the renderer. */
	public int valueColor() {
		if (kind == Kind.BOOL) {
			return boolValue() ? 0xFF55FF55 : 0xFF77808F;
		}
		return 0xFF55FFFF;
	}
}
