package com.chaoshelper.dynamic;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.ChaosApi;
import com.chaoshelper.api.ChaosModule;
import com.chaoshelper.api.RenderBox;
import com.chaoshelper.util.ErrorLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads pure-logic jars from config/chaoshelper/modules. Each jar is isolated,
 * validated, and failure is reported to the HUD and log instead of crashing.
 */
public final class DynamicModuleLoader {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String METADATA_ENTRY = "META-INF/chaoshelper-module.json";
	private static final int MAX_METADATA_BYTES = 64 * 1024;

	private final ChaosHelperClient mod;
	private final Path modulesDirectory;
	private final List<DynamicModule> loaded = new ArrayList<>();

	public DynamicModuleLoader(ChaosHelperClient mod) {
		this.mod = mod;
		this.modulesDirectory = mod.configManager().directory().resolve("modules");
	}

	public LoadResult reload() {
		mod.errorLog().clearSource("dynamic");
		List<String> errors = new ArrayList<>();
		List<DynamicModule> loadedNow = new ArrayList<>();
		try {
			Files.createDirectories(modulesDirectory);
			List<Path> jars;
			try (Stream<Path> stream = Files.list(modulesDirectory)) {
				jars = stream.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
						.sorted(Comparator.comparing(path -> path.getFileName().toString()))
						.toList();
			}
			Set<String> ids = new HashSet<>();
			int maxModules = Math.max(1, mod.config().dynamic.maxModules);
			for (Path jar : jars) {
				try {
					DynamicModule module = loadOne(jar);
					if (module == null) {
						continue;
					}
					if (loadedNow.size() >= maxModules) {
						closeQuietly(module);
						fail(errors, jar, "超过动态模块上限 dynamic.maxModules=" + maxModules);
						continue;
					}
					if (!ids.add(module.id())) {
						closeQuietly(module);
						fail(errors, jar, "动态模块 ID 重复: " + module.id());
						continue;
					}
					if (mod.moduleManager().isProtected(module.id())) {
						closeQuietly(module);
						fail(errors, jar, "不能覆盖受保护的内置模块: " + module.id());
						continue;
					}
					Map<String, String> settings = module.metadata().settings;
					if (settings != null && !settings.isEmpty()) {
						mod.config().dynamicSettings.putIfAbsent(module.id(), new LinkedHashMap<>(settings));
					}
					loadedNow.add(module);
				} catch (Throwable throwable) {
					fail(errors, jar, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
				}
			}
		} catch (Throwable throwable) {
			fail(errors, modulesDirectory, "扫描模块目录失败: " + throwable.getMessage());
		}
		mod.moduleManager().replaceDynamicModules(new ArrayList<>(loadedNow));
		loaded.clear();
		loaded.addAll(loadedNow);
		if (!errors.isEmpty()) {
			mod.notifications().error("动态模块加载有 " + errors.size() + " 个错误，见 HUD 红框");
		}
		ChaosHelperClient.LOGGER.info("动态模块加载: {} 成功, {} 错误", loaded.size(), errors.size());
		return new LoadResult(loaded.size(), List.copyOf(errors));
	}

	private DynamicModule loadOne(Path jarPath) throws Exception {
		DynamicModuleMetadata metadata;
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			JarEntry metadataEntry = jar.getJarEntry(METADATA_ENTRY);
			if (metadataEntry == null) {
				throw new IllegalArgumentException("缺少 " + METADATA_ENTRY);
			}
			if (metadataEntry.getSize() > MAX_METADATA_BYTES) {
				throw new IllegalArgumentException("元数据过大");
			}
			String json;
			try (InputStream input = jar.getInputStream(metadataEntry)) {
				json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			}
			metadata = GSON.fromJson(json, DynamicModuleMetadata.class);
			if (metadata == null) {
				throw new IllegalArgumentException("元数据为空");
			}
			validateMetadata(metadata);
			validateEntries(jar);
		}

		URLClassLoader classLoader = new ModuleClassLoader(jarPath.toUri().toURL(),
				ChaosHelperClient.class.getClassLoader());
		try {
			Class<?> type = Class.forName(metadata.entrypoint, true, classLoader);
			if (!ChaosModule.class.isAssignableFrom(type)) {
				throw new IllegalArgumentException("入口类未实现 ChaosModule: " + metadata.entrypoint);
			}
			ChaosModule delegate = (ChaosModule) type.getDeclaredConstructor().newInstance();
			return new DynamicModule(metadata, delegate, classLoader);
		} catch (Throwable throwable) {
			try {
				classLoader.close();
			} catch (Exception ignored) {
				// Preserve the original failure.
			}
			throw throwable;
		}
	}

	private void validateMetadata(DynamicModuleMetadata metadata) {
		if (metadata.schema != 1) {
			throw new IllegalArgumentException("元数据 schema 不支持: " + metadata.schema + " (需要 1)");
		}
		if (metadata.id == null || !metadata.id.matches("[a-z][a-z0-9_-]{1,31}")) {
			throw new IllegalArgumentException("id 必须为 2-32 位小写字母/数字/_/-，且以字母开头");
		}
		if (metadata.entrypoint == null || metadata.entrypoint.isBlank()
				|| !metadata.entrypoint.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
			throw new IllegalArgumentException("entrypoint 不是合法 Java 类名");
		}
		if (metadata.entrypoint.startsWith("com.chaoshelper.")
				|| metadata.entrypoint.startsWith("net.minecraft.")
				|| metadata.entrypoint.startsWith("net.fabricmc.")) {
			throw new IllegalArgumentException("动态模块不得使用宿主/Minecraft 包名");
		}
		if (metadata.overrides != null && !metadata.overrides.isBlank()
				&& !metadata.overrides.matches("[a-z][a-z0-9_-]{1,31}")) {
			throw new IllegalArgumentException("overrides 不是合法模块 id");
		}
		if (metadata.apiVersion != ChaosApi.API_VERSION) {
			throw new IllegalArgumentException("API 版本不兼容: 需要 " + ChaosApi.API_VERSION + "，实际 " + metadata.apiVersion);
		}
	}

	private void validateEntries(JarFile jar) {
		var entries = jar.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				continue;
			}
			String name = entry.getName();
			if (name.equals(METADATA_ENTRY) || name.equals("META-INF/MANIFEST.MF")
					|| name.startsWith("META-INF/LICENSE") || name.startsWith("META-INF/NOTICE")
					|| name.endsWith(".class")) {
				continue;
			}
			throw new IllegalArgumentException("包含不允许的资源: " + name + " (动态模块不得带 Fabric/资源文件)");
		}
	}

	private void fail(List<String> errors, Path source, String message) {
		String text = source.getFileName() + ": " + (message == null ? "未知错误" : message);
		errors.add(text);
		mod.errorLog().add(ErrorLog.Level.ERROR, "dynamic", text);
		ChaosHelperClient.LOGGER.error("动态模块加载失败: {}", text);
	}

	private void closeQuietly(DynamicModule module) {
		try {
			module.close();
		} catch (Exception ignored) {
			// Best effort.
		}
	}

	public List<RenderBox> renderBoxes() {
		List<RenderBox> boxes = new ArrayList<>();
		for (DynamicModule module : loaded) {
			if (module.isEnabled()) {
				boxes.addAll(module.renderBoxes());
			}
		}
		return boxes;
	}

	public int loadedCount() {
		return loaded.size();
	}

	public Path modulesDirectory() {
		return modulesDirectory;
	}

	public record LoadResult(int loaded, List<String> errors) {
	}
}
