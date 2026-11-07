package com.chaoshelper.dynamic;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.ChaosApi;
import com.chaoshelper.api.ChaosContext;
import com.chaoshelper.api.DropData;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.api.RenderBox;
import com.chaoshelper.module.Module;
import com.chaoshelper.api.ChaosModule;

import java.net.URLClassLoader;
import java.util.List;

public final class DynamicModule extends Module implements AutoCloseable {
	private final ChaosModule delegate;
	private final URLClassLoader classLoader;
	private final DynamicContext context;
	private final DynamicModuleMetadata metadata;
	private final java.util.List<RenderBox> renderBoxes = new java.util.ArrayList<>();

	public DynamicModule(DynamicModuleMetadata metadata, ChaosModule delegate, URLClassLoader classLoader) {
		super(metadata.effectiveId(),
				metadata.name == null || metadata.name.isBlank() ? metadata.effectiveId() : metadata.name,
				metadata.defaultEnabled, metadata.behavior, true);
		this.metadata = metadata;
		this.delegate = delegate;
		this.classLoader = classLoader;
		this.context = new DynamicContext(metadata.effectiveId(), this);
	}

	public DynamicModuleMetadata metadata() {
		return metadata;
	}

	@Override
	protected void onEnable() {
		delegate.onEnable(context);
	}

	@Override
	protected void onDisable() {
		try {
			delegate.onDisable(context);
		} finally {
			renderBoxes.clear();
		}
	}

	@Override
	protected void tick() {
		renderBoxes.clear();
		delegate.tick(context);
	}

	@Override
	public String status() {
		try {
			return delegate.status(context);
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.warn("动态模块状态读取失败: {}", id(), throwable);
			return "状态读取失败";
		}
	}

	void addRenderBox(RenderBox box) {
		if (box != null && renderBoxes.size() < 256) {
			renderBoxes.add(box);
		}
	}

	public List<RenderBox> renderBoxes() {
		return List.copyOf(renderBoxes);
	}

	public DynamicContext context() {
		return context;
	}

	@Override
	public void close() {
		renderBoxes.clear();
		try {
			classLoader.close();
		} catch (Exception exception) {
			ChaosHelperClient.LOGGER.warn("关闭动态模块 ClassLoader 失败: {}", id(), exception);
		}
	}

	private static final class DynamicContext implements ChaosContext {
		private final String moduleId;
		private final DynamicModule owner;

		DynamicContext(String moduleId, DynamicModule owner) {
			this.moduleId = moduleId;
			this.owner = owner;
		}

		@Override
		public String apiVersion() {
			return Integer.toString(ChaosApi.API_VERSION);
		}

		@Override
		public String moduleId() {
			return moduleId;
		}

		@Override
		public long tick() {
			return ChaosHelperClient.get().tickCount();
		}

		@Override
		public PlayerData self() {
			return ChaosHelperClient.get().playerTracker().self();
		}

		@Override
		public List<EntityData> entities() {
			return ChaosHelperClient.get().entityCache().entities();
		}

		@Override
		public List<PlayerData> players() {
			return ChaosHelperClient.get().playerTracker().players();
		}

		@Override
		public List<DropData> drops() {
			return ChaosHelperClient.get().dropTracker().drops();
		}

		@Override
		public String setting(String key, String defaultValue) {
			var settings = ChaosHelperClient.get().config().dynamicSettings.get(moduleId);
			if (settings == null) {
				return defaultValue;
			}
			return settings.getOrDefault(key, defaultValue);
		}

		@Override
		public void addRenderBox(RenderBox box) {
			owner.addRenderBox(box);
		}

		@Override
		public void notify(String message) {
			ChaosHelperClient.get().notifications().info(message == null ? "" : message);
		}

		@Override
		public void error(String message) {
			String text = message == null ? "" : message;
			ChaosHelperClient.get().notifications().error(text);
			ChaosHelperClient.get().errorLog().add(com.chaoshelper.util.ErrorLog.Level.ERROR,
					"module:" + moduleId, text);
			ChaosHelperClient.LOGGER.error("动态模块 {}: {}", moduleId, text);
		}

		@Override
		public boolean isModuleEnabled(String id) {
			return ChaosHelperClient.get().moduleManager().isEnabled(id);
		}

		@Override
		public void disableModule(String id) {
			ChaosHelperClient.get().moduleManager().setEnabled(id, false);
		}
	}
}
