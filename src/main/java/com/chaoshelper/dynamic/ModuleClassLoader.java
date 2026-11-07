package com.chaoshelper.dynamic;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Dynamic modules may use JDK classes and the chaoshelper API, but are prevented
 * from linking Minecraft, Fabric, Mixin, or host implementation classes.
 */
final class ModuleClassLoader extends URLClassLoader {
	ModuleClassLoader(URL url, ClassLoader parent) {
		super(new URL[]{url}, parent);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (name.startsWith("net.minecraft.") || name.startsWith("net.fabricmc.")
				|| name.startsWith("org.spongepowered.") || name.startsWith("com.mojang.")
				|| name.startsWith("org.lwjgl.") || name.startsWith("io.netty.")) {
			throw new ClassNotFoundException("动态模块不得链接宿主/Minecraft 类: " + name);
		}
		if (name.startsWith("com.chaoshelper.") && !name.startsWith("com.chaoshelper.api.")) {
			throw new ClassNotFoundException("动态模块不得链接宿主实现类: " + name);
		}
		return super.loadClass(name, resolve);
	}
}
