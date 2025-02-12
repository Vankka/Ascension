/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2025 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.common;

import com.discordsrv.api.events.lifecycle.DiscordSRVConnectedEvent;
import com.discordsrv.api.events.lifecycle.DiscordSRVReadyEvent;
import com.discordsrv.api.events.lifecycle.DiscordSRVReloadedEvent;
import com.discordsrv.api.events.lifecycle.DiscordSRVShuttingDownEvent;
import com.discordsrv.api.module.Module;
import com.discordsrv.api.reload.ReloadFlag;
import com.discordsrv.api.reload.ReloadResult;
import com.discordsrv.common.abstraction.bootstrap.IBootstrap;
import com.discordsrv.common.command.discord.DiscordCommandModule;
import com.discordsrv.common.command.game.GameCommandModule;
import com.discordsrv.common.config.configurate.manager.ConnectionConfigManager;
import com.discordsrv.common.config.configurate.manager.MainConfigManager;
import com.discordsrv.common.config.configurate.manager.MessagesConfigManager;
import com.discordsrv.common.config.configurate.manager.MessagesConfigSingleManager;
import com.discordsrv.common.config.connection.BotConfig;
import com.discordsrv.common.config.connection.ConnectionConfig;
import com.discordsrv.common.config.connection.HttpProxyConfig;
import com.discordsrv.common.config.connection.UpdateConfig;
import com.discordsrv.common.config.main.MainConfig;
import com.discordsrv.common.config.main.linking.LinkedAccountConfig;
import com.discordsrv.common.config.messages.MessagesConfig;
import com.discordsrv.common.core.component.ComponentFactory;
import com.discordsrv.common.core.component.translation.TranslationLoader;
import com.discordsrv.common.core.dependency.DiscordSRVDependencyManager;
import com.discordsrv.common.core.eventbus.EventBusImpl;
import com.discordsrv.common.core.logging.Logger;
import com.discordsrv.common.core.logging.impl.DependencyLoggingHandler;
import com.discordsrv.common.core.logging.impl.DiscordSRVLogger;
import com.discordsrv.common.core.module.ModuleManager;
import com.discordsrv.common.core.module.type.AbstractModule;
import com.discordsrv.common.core.placeholder.PlaceholderServiceImpl;
import com.discordsrv.common.core.placeholder.context.*;
import com.discordsrv.common.core.placeholder.format.DiscordMarkdownFormatImpl;
import com.discordsrv.common.core.placeholder.result.ComponentResultStringifier;
import com.discordsrv.common.core.storage.Storage;
import com.discordsrv.common.core.storage.StorageType;
import com.discordsrv.common.core.storage.impl.MemoryStorage;
import com.discordsrv.common.discord.api.DiscordAPIEventModule;
import com.discordsrv.common.discord.api.DiscordAPIImpl;
import com.discordsrv.common.discord.connection.DiscordConnectionManager;
import com.discordsrv.common.discord.connection.details.DiscordConnectionDetailsImpl;
import com.discordsrv.common.discord.connection.jda.JDAConnectionManager;
import com.discordsrv.common.exception.StorageException;
import com.discordsrv.common.feature.DiscordInviteModule;
import com.discordsrv.common.feature.PresenceUpdaterModule;
import com.discordsrv.common.feature.bansync.BanSyncModule;
import com.discordsrv.common.feature.channel.ChannelLockingModule;
import com.discordsrv.common.feature.channel.TimedUpdaterModule;
import com.discordsrv.common.feature.channel.global.GlobalChannelLookupModule;
import com.discordsrv.common.feature.console.ConsoleModule;
import com.discordsrv.common.feature.customcommands.CustomCommandModule;
import com.discordsrv.common.feature.debug.data.VersionInfo;
import com.discordsrv.common.feature.groupsync.GroupSyncModule;
import com.discordsrv.common.feature.linking.LinkProvider;
import com.discordsrv.common.feature.linking.LinkingModule;
import com.discordsrv.common.feature.linking.impl.MinecraftAuthenticationLinker;
import com.discordsrv.common.feature.linking.impl.StorageLinker;
import com.discordsrv.common.feature.mention.MentionCachingModule;
import com.discordsrv.common.feature.mention.MentionGameRenderingModule;
import com.discordsrv.common.feature.messageforwarding.discord.DiscordChatMessageModule;
import com.discordsrv.common.feature.messageforwarding.discord.DiscordMessageMirroringModule;
import com.discordsrv.common.feature.messageforwarding.game.*;
import com.discordsrv.common.feature.nicknamesync.NicknameSyncModule;
import com.discordsrv.common.feature.profile.ProfileManager;
import com.discordsrv.common.feature.update.UpdateChecker;
import com.discordsrv.common.helper.ChannelConfigHelper;
import com.discordsrv.common.helper.DestinationLookupHelper;
import com.discordsrv.common.helper.TemporaryLocalData;
import com.discordsrv.common.logging.adapter.DependencyLoggerAdapter;
import com.discordsrv.common.util.ApiInstanceUtil;
import com.discordsrv.common.util.UUIDUtil;
import com.discordsrv.common.util.function.CheckedFunction;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDAInfo;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * DiscordSRV's implementation's common code.
 * Implementations of this class must call {@link #load()} at the end of their constructors.
 * @param <C> the config type
 * @param <CC> the connections config type
 */
public abstract class AbstractDiscordSRV<
        B extends IBootstrap,
        C extends MainConfig,
        CC extends ConnectionConfig,
        MC extends MessagesConfig
> implements DiscordSRV {

    private final AtomicReference<Status> status = new AtomicReference<>(Status.INITIALIZED);
    private final AtomicReference<Boolean> beenReady = new AtomicReference<>(false);
    private boolean serverStarted = false;
    private final Object reloadLock = new Object();

    // DiscordSRVApi
    private EventBusImpl eventBus;
    private ProfileManager profileManager;
    private PlaceholderServiceImpl placeholderService;
    private DiscordMarkdownFormatImpl discordMarkdownFormat;
    private ComponentFactory componentFactory;
    private DiscordAPIImpl discordAPI;
    private DiscordConnectionDetailsImpl discordConnectionDetails;

    // DiscordSRV
    protected final B bootstrap;
    private final Logger platformLogger;
    private final Path dataDirectory;
    private final DiscordSRVLogger logger;
    private DiscordSRVDependencyManager dependencyManager;
    private ModuleManager moduleManager;
    private JDAConnectionManager discordConnectionManager;
    private ChannelConfigHelper channelConfig;
    private DestinationLookupHelper destinationLookupHelper;
    private TemporaryLocalData temporaryLocalData;
    private TranslationLoader translationLoader;

    private Storage storage;
    private LinkProvider linkProvider;

    // Version
    private UpdateChecker updateChecker;
    protected VersionInfo versionInfo;

    private final ZonedDateTime initializeTime = ZonedDateTime.now();

    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AbstractDiscordSRV(B bootstrap) {
        ApiInstanceUtil.setInstance(this);
        this.bootstrap = bootstrap;
        this.platformLogger = bootstrap.logger();
        this.dataDirectory = bootstrap.dataDirectory();
        this.logger = new DiscordSRVLogger(this);

        DependencyLoggerAdapter.setAppender(new DependencyLoggingHandler(this));
    }

    /**
     * Method that should be called at the end of implementors constructors.
     */
    protected final void load() {
        this.dependencyManager = new DiscordSRVDependencyManager(this, bootstrap.lifecycleManager() != null ? bootstrap.lifecycleManager().getDependencyLoader() : null);
        this.eventBus = new EventBusImpl(this);
        this.moduleManager = new ModuleManager(this);
        this.profileManager = new ProfileManager(this);
        this.placeholderService = new PlaceholderServiceImpl(this);
        this.discordMarkdownFormat = new DiscordMarkdownFormatImpl();
        this.componentFactory = new ComponentFactory(this);
        this.discordAPI = new DiscordAPIImpl(this);
        this.discordConnectionDetails = new DiscordConnectionDetailsImpl(this);
        this.discordConnectionManager = new JDAConnectionManager(this);
        this.channelConfig = new ChannelConfigHelper(this);
        this.destinationLookupHelper = new DestinationLookupHelper(this);
        this.temporaryLocalData = new TemporaryLocalData(this);
        this.updateChecker = new UpdateChecker(this);
        readManifest();

        ///////////////////////////////////////////////////////////////
        logger.warning("");
        logger.warning("+-----------------------------------------+");
        logger.warning("This is a testing version of DiscordSRV");
        logger.warning("Limited or no support will be provided");
        logger.warning("EVERYTHING is subject to change.");
        logger.warning("+-----------------------------------------+");
        logger.warning("");
        ///////////////////////////////////////////////////////////////

        createHttpClient();
    }

    private HttpProxyConfig usedProxyConfig = null;
    private void createHttpClient() {
        HttpProxyConfig proxyConfig = connectionConfig() != null ? connectionConfig().httpProxy : null;
        if (httpClient != null && Objects.equals(usedProxyConfig, proxyConfig)) {
            // Skip recreating client, if proxy is the same
            return;
        }
        usedProxyConfig = proxyConfig;

        OkHttpClient.Builder builder =  new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String host = original.url().host();
                    boolean isDiscord = host.matches("(.*\\.|^)(?:discord\\.(?:com|gg)|(discordapp\\.com))");

                    String userAgent = isDiscord
                                       ? "DiscordBot (https://github.com/DiscordSRV/DiscordSRV, " + versionInfo().version() + ")"
                                               + " (" + JDAInfo.GITHUB + ", " + JDAInfo.VERSION + ")"
                                       : "DiscordSRV/" + versionInfo().version();

                    return chain.proceed(
                            original.newBuilder()
                                    .removeHeader("User-Agent")
                                    .addHeader("User-Agent", userAgent)
                                    .build()
                    );
                })
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(20); // Set maximum amount of requests at a time (to something more reasonable than 64)
        dispatcher.setMaxRequestsPerHost(16); // Most requests are to discord.com
        builder.dispatcher(dispatcher);

        builder.connectionPool(new ConnectionPool(5, 10, TimeUnit.SECONDS));

        if (proxyConfig != null && proxyConfig.enabled) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.host, proxyConfig.port)));

            HttpProxyConfig.BasicAuthConfig basicAuthConfig = proxyConfig.basicAuth;
            if (basicAuthConfig != null && basicAuthConfig.enabled) {
                // https://stackoverflow.com/questions/41806422/java-web-start-unable-to-tunnel-through-proxy-since-java-8-update-111
                String disabledSchemes = "jdk.http.auth.tunneling.disabledSchemes";

                String oldDisabledSchemes = System.getProperty(disabledSchemes);
                if (oldDisabledSchemes != null) {
                    String newDisabledSchemes = Arrays.stream(oldDisabledSchemes.split(","))
                            .filter(disabledScheme -> !disabledScheme.equalsIgnoreCase("Basic"))
                            .collect(Collectors.joining(","));
                    if (!oldDisabledSchemes.equals(newDisabledSchemes)) {
                        System.setProperty(disabledSchemes, newDisabledSchemes);
                    }
                }

                String credential = Credentials.basic(basicAuthConfig.username, basicAuthConfig.password);
                builder.proxyAuthenticator((route, response) -> response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build());
            }
        }

        this.httpClient = builder.build();
    }

    protected URL getManifest() {
        ClassLoader classLoader = bootstrap.classLoader();
        if (classLoader instanceof URLClassLoader) {
            return ((URLClassLoader) classLoader).findResource(JarFile.MANIFEST_NAME);
        }

        return classLoader.getResource(JarFile.MANIFEST_NAME);
    }

    private void readManifest() {
        String version = bootstrap.getClass().getPackage().getImplementationVersion();
        String gitCommit = null, gitBranch = null, buildTime = null;

        try {
            URL url = getManifest();
            if (url == null) {
                logger().error("Could not find manifest");
                return;
            }
            try (InputStream inputStream = url.openStream()) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attributes = manifest.getMainAttributes();

                if (version == null) {
                    version = readAttribute(attributes, "Implementation-Version");
                    if (version == null) {
                        logger().error("Failed to get version from manifest");
                    }
                }

                gitCommit = readAttribute(attributes, "Git-Commit");
                gitBranch = readAttribute(attributes, "Git-Branch");
                buildTime = readAttribute(attributes, "Build-Time");
            }
        } catch (IOException e) {
            logger().error("Failed to read manifest", e);
        }

        versionInfo = new VersionInfo(version, gitCommit, gitBranch, buildTime);
    }

    private String readAttribute(Attributes attributes, String key) {
        return attributes.getValue(key);
    }

    // DiscordSRVApi

    @Override
    public final @NotNull Status status() {
        return status.get();
    }

    @Override
    public final @NotNull EventBusImpl eventBus() {
        return eventBus;
    }

    @Override
    public final @NotNull ProfileManager profileManager() {
        return profileManager;
    }

    @Override
    public final @NotNull PlaceholderServiceImpl placeholderService() {
        return placeholderService;
    }

    @Override
    public final @NotNull DiscordMarkdownFormatImpl discordMarkdownFormat() {
        return discordMarkdownFormat;
    }

    @Override
    public final @NotNull ComponentFactory componentFactory() {
        return componentFactory;
    }

    @Override
    public final @NotNull DiscordAPIImpl discordAPI() {
        return discordAPI;
    }

    @Override
    public final @Nullable JDA jda() {
        if (discordConnectionManager == null) {
            return null;
        }
        return discordConnectionManager.instance();
    }

    @Override
    public final @NotNull DiscordConnectionDetailsImpl discordConnectionDetails() {
        return discordConnectionDetails;
    }

    // DiscordSRV

    @Override
    public final B bootstrap() {
        return bootstrap;
    }

    @Override
    public final Logger platformLogger() {
        return platformLogger;
    }

    @Override
    public final DiscordSRVDependencyManager dependencyManager() {
        return dependencyManager;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public final DiscordSRVLogger logger() {
        return logger;
    }

    @Override
    public final Storage storage() {
        return storage;
    }

    @Override
    public TemporaryLocalData temporaryLocalData() {
        return temporaryLocalData;
    }

    @Override
    public final LinkProvider linkProvider() {
        return linkProvider;
    }

    @Override
    public final @NotNull VersionInfo versionInfo() {
        return versionInfo;
    }

    @Override
    public final ChannelConfigHelper channelConfig() {
        return channelConfig;
    }

    @Override
    public DestinationLookupHelper destinations() {
        return destinationLookupHelper;
    }

    @Override
    public final JDAConnectionManager discordConnectionManager() {
        return discordConnectionManager;
    }

    // Config
    @Override
    public abstract ConnectionConfigManager<CC> connectionConfigManager();

    @Override
    public CC connectionConfig() {
        ConnectionConfigManager<CC> configManager = connectionConfigManager();
        return configManager != null ? configManager.config() : null;
    }

    @Override
    public abstract MainConfigManager<C> configManager();

    @Override
    public C config() {
        MainConfigManager<C> configManager = configManager();
        return configManager != null ? configManager.config() : null;
    }

    @Override
    public abstract MessagesConfigManager<MC> messagesConfigManager();

    @Override
    public MC messagesConfig(@Nullable Locale locale) {
        MessagesConfigManager<MC> configManager = messagesConfigManager();
        if (configManager == null) {
            return null;
        }

        MessagesConfigSingleManager<MC> manager = locale != null ? configManager.getManager(locale) : null;
        if (manager == null) {
            manager = configManager.getManager(defaultLocale());
        }
        if (manager == null) {
            manager = configManager.getManager(Locale.US);
        }
        return manager.config();
    }

    // Module

    @Override
    public <T extends Module> T getModule(Class<T> moduleType) {
        return moduleManager.getModule(moduleType);
    }

    @Override
    public void registerModule(AbstractModule<?> module) {
        moduleManager.register(module);
    }

    @SuppressWarnings("unchecked")
    protected final <T extends DiscordSRV> void registerModule(CheckedFunction<T, AbstractModule<?>> function) {
        moduleManager.registerModule((T) this, function);
    }

    /**
     * @param className a class which has a constructor with {@link DiscordSRV} (or implementation specific) as the only parameter.
     */
    protected final void registerIntegration(
            @Language(value = "JAVA", prefix = "class X{static{Class.forName(\"", suffix = "\");}}") String className
    ) {
        Object module;
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructors()[0];
            module = constructor.newInstance(this);
        } catch (Throwable e) {
            String suffix = "";
            if (e instanceof LinkageError || e instanceof ClassNotFoundException) {
                suffix = " (Integration likely not installed or using wrong version)";
            }
            moduleManager.logger().debug("Failed to load integration: " + className + suffix, e);
            return;
        }
        moduleManager.registerModule(this, d -> (AbstractModule<?>) module);
    }

    @Override
    public void unregisterModule(AbstractModule<?> module) {
        moduleManager.unregister(module);
    }

    @Override
    public ModuleManager moduleManager() {
        return moduleManager;
    }

    @Override
    public Locale defaultLocale() {
        MainConfig config = config();
        if (config != null) {
            String defaultLanguage = config.messages.defaultLanguage;
            if (StringUtils.isNotBlank(defaultLanguage)) {
                return Locale.forLanguageTag(defaultLanguage);
            }
        }

        return Locale.getDefault();
    }

    // Status

    @Override
    public void setStatus(Status status) {
        synchronized (this.status) {
            this.status.set(status);
            this.status.notifyAll();
        }
        if (status == Status.CONNECTED) {
            eventBus().publish(new DiscordSRVConnectedEvent());
            if (beenReady.compareAndSet(false, true)) {
                eventBus.publish(new DiscordSRVReadyEvent());
            }
        }
    }

    @Override
    public void waitForStatus(Status statusToWaitFor, long time, TimeUnit unit) throws InterruptedException {
        long deadline = time > 0 ? System.currentTimeMillis() + unit.toMillis(time) : Long.MAX_VALUE;
        while (status().ordinal() < statusToWaitFor.ordinal()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime > deadline) {
                break;
            }

            synchronized (this.status) {
                if (time > 0) {
                    this.status.wait(deadline - currentTime);
                } else {
                    this.status.wait();
                }
            }
        }
    }

    @Override
    public OkHttpClient httpClient() {
        return httpClient;
    }

    @Override
    public ObjectMapper json() {
        return objectMapper;
    }

    // Lifecycle

    public boolean isServerStarted() {
        return serverStarted;
    }

    public ZonedDateTime getInitializeTime() {
        return initializeTime;
    }

    /**
     * Run blocking when plugin/mod is triggered for enabling.
     */
    @Override
    public final void runEnable() {
        try {
            this.enable();
        } catch (Throwable t) {
            logger().error("Failed to enable", t);
            setStatus(Status.FAILED_TO_START);
        }
    }

    /**
     * Must be manually triggered for {@link DiscordSRV.ServerType#SERVER}, automatically triggered in {@link #enable()} for {@link DiscordSRV.ServerType#PROXY}.
     * @return a future running on the {@link #scheduler()}
     */
    public final CompletableFuture<Void> runServerStarted() {
        return scheduler().execute(() -> {
            if (status().isShutdown()) {
                // Already shutdown/shutting down, don't bother
                return;
            }
            try {
                this.serverStarted();
            } catch (Throwable t) {
                if (status().isShutdown() && t instanceof NoClassDefFoundError) {
                    // Already shutdown, ignore errors for classes that already got unloaded
                    return;
                }
                logger().error("Failed to start", t);
                setStatus(Status.FAILED_TO_START);

                disable();
            }
        });
    }

    /**
     * Triggers a reload of DiscordSRV.
     * @param flags the targets to reload
     * @return the results of the reload
     */
    @Override
    public final List<ReloadResult> runReload(Set<ReloadFlag> flags) {
        try {
            synchronized (reloadLock) {
                return reload(flags, false);
            }
        } catch (Throwable e) {
            logger.error("Failed to reload", e);
            return Collections.singletonList(ReloadResult.ERROR);
        }
    }

    @Override
    public final CompletableFuture<Void> runDisable() {
        return scheduler().execute(this::disable);
    }

    @MustBeInvokedByOverriders
    protected void enable() throws Throwable {
        if (eventBus == null) {
            // Error that should only occur with new platforms
            throw new IllegalStateException("AbstractDiscordSRV#load was not called from the end of "
                    + getClass().getName() + " constructor");
        }

        // Register PlayerProvider listeners
        playerProvider().subscribe();

        this.translationLoader = new TranslationLoader(this);

        // Placeholder result stringifiers & global contexts
        placeholderService().addResultMapper(new ComponentResultStringifier(this));
        placeholderService().addGlobalContext(new TextHandlingContext(this));
        placeholderService().addGlobalContext(new DateFormattingContext(this));
        placeholderService().addGlobalContext(new GamePermissionContext(this));
        placeholderService().addGlobalContext(new ReceivedDiscordMessageContext(this));
        placeholderService().addGlobalContext(new DiscordBotContext(this));
        placeholderService().addGlobalContext(new AvatarProviderContext(this));
        placeholderService().addGlobalContext(UUIDUtil.class);

        // Modules
        registerModule(BanSyncModule::new);
        registerModule(ConsoleModule::new);
        registerModule(ChannelLockingModule::new);
        registerModule(TimedUpdaterModule::new);
        registerModule(DiscordCommandModule::new);
        registerModule(GameCommandModule::new);
        registerModule(GlobalChannelLookupModule::new);
        registerModule(DiscordAPIEventModule::new);
        registerModule(GroupSyncModule::new);
        registerModule(DiscordChatMessageModule::new);
        registerModule(DiscordMessageMirroringModule::new);
        registerModule(JoinMessageModule::new);
        registerModule(LeaveMessageModule::new);
        registerModule(DiscordInviteModule::new);
        registerModule(MentionCachingModule::new);
        registerModule(LinkingModule::new);
        registerModule(PresenceUpdaterModule::new);
        registerModule(MentionGameRenderingModule::new);
        registerModule(CustomCommandModule::new);
        registerModule(NicknameSyncModule::new);

        if (serverType() == ServerType.PROXY) {
            registerModule(ServerSwitchMessageModule::new);
        }
        if (serverType() == ServerType.SERVER) {
            registerModule(AwardMessageModule::new);
            registerModule(DeathMessageModule::new);
        }

        // Integrations
        registerIntegration("com.discordsrv.common.integration.LuckPermsIntegration");

        // Check if the system has working DNS
        try {
            String discordDomain = "discord.com";
            String gatewayDomain = "gateway.discord.gg";
            String discordAddresses = Arrays.stream(InetAddress.getAllByName(discordDomain))
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.joining(", "));
            String gatewayAddresses = Arrays.stream(InetAddress.getAllByName(gatewayDomain))
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.joining(", "));

            logger().debug("DNS OK (" + discordDomain + "=[" + discordAddresses
                                   + "], " + gatewayDomain + "=[" + gatewayAddresses + "])");
        } catch (UnknownHostException e) {
            logger().debug("DNS check failed", e);
        }

        // Initial load
        reload(ReloadFlag.LOAD, true);

        if (serverType() == ServerType.PROXY) {
            runServerStarted().get();
        }
    }

    @MustBeInvokedByOverriders
    protected void serverStarted() {
        serverStarted = true;
        moduleManager().enableModules();

        registerModule(StartMessageModule::new);
        registerModule(StopMessageModule::new);
        Optional.ofNullable(getModule(PresenceUpdaterModule.class)).ifPresent(PresenceUpdaterModule::serverStarted);
    }

    @MustBeInvokedByOverriders
    protected List<ReloadResult> reload(Set<ReloadFlag> flags, boolean initial) throws Throwable {
        if (!initial) {
            logger().info("Reloading DiscordSRV...");
        }

        boolean configUpgrade = flags.contains(ReloadFlag.CONFIG_UPGRADE);

        if (flags.contains(ReloadFlag.CONFIG) || configUpgrade) {
            Path backupPath = null;
            if (configUpgrade) {
                backupPath = generateBackupPath();
            }

            AtomicBoolean anyMissingOptions = new AtomicBoolean(false);
            connectionConfigManager().reload(configUpgrade, anyMissingOptions, backupPath);
            configManager().reload(configUpgrade, anyMissingOptions, backupPath);
            messagesConfigManager().reload(configUpgrade, anyMissingOptions, backupPath);

            if (anyMissingOptions.get()) {
                if (config().automaticConfigurationUpgrade) {
                    logger().info("Some configuration options are missing, attempting to upgrade configuration...");

                    if (backupPath == null) {
                        backupPath = generateBackupPath();
                    }
                    AtomicBoolean stillMissingOptions = new AtomicBoolean(false);

                    connectionConfigManager().reload(true, stillMissingOptions, backupPath);
                    configManager().reload(true, stillMissingOptions, backupPath);
                    messagesConfigManager().reload(true, stillMissingOptions, backupPath);

                    if (stillMissingOptions.get()) {
                        logger().warning("Attempted to upgrade configuration automatically, but some options are still missing.");
                    } else {
                        logger().info("Configuration successfully upgraded");
                    }
                } else if (configUpgrade) {
                    logger().warning("Attempted to upgrade configuration by reload command, but some options are still missing.");
                } else {
                    logger().info("Use \"/discordsrv reload config_upgrade\" to write the latest configuration");
                    logger().info("Or set \"automatic-configuration-upgrade\" to true in the config to automatically upgrade the configuration on startup or reload");
                }
            }

            channelConfig().reload();
            createHttpClient();
        }

        List<ReloadResult> results = new ArrayList<>();
        // Reload any modules that can be enabled before DiscordSRV is ready
        if (initial) {
            results.addAll(moduleManager().reload());
        }

        if (connectionConfig().bot.token.equals(BotConfig.DEFAULT_TOKEN)) {
            if (initial) {
                logger().info("");
                logger().info("Welcome to DiscordSRV!");
                logger().info("");
                logger().info("To get started with using DiscordSRV please configure a bot token, instructions will be printed below");
                logger().info("You can review and/or disable external services DiscordSRV uses in the " + ConnectionConfig.FILE_NAME + " before adding a bot token");
                logger().info("");
            }
            discordConnectionManager.invalidToken(true);
            setStatus(Status.NOT_CONFIGURED);
            results.add(ReloadResult.DEFAULT_BOT_TOKEN);
            return results;
        }

        // Update check
        UpdateConfig updateConfig = connectionConfig().update;
        if (updateConfig.security.enabled) {
            if (updateChecker.isSecurityFailed()) {
                // Security has already failed
                return Collections.singletonList(ReloadResult.SECURITY_FAILED);
            }

            if (initial && !updateChecker.check(true)) {
                // Security failed cancel startup & shutdown
                runDisable();
                return Collections.singletonList(ReloadResult.SECURITY_FAILED);
            }
        } else if (initial) {
            // Not using security, run update check off thread
            scheduler().run(() -> updateChecker.check(true));
        }
        if (initial) {
            scheduler().runAtFixedRate(() -> updateChecker.check(false), Duration.ofHours(6));
        }

        if (flags.contains(ReloadFlag.STORAGE)) {
            if (storage != null) {
                storage.close();
            }

            try {
                try {
                    StorageType storageType = getStorageType();
                    logger().info("Using " + storageType.prettyName() + " as storage, loading drivers...");
                    if (storageType == StorageType.MEMORY) {
                        logger().warning("Using memory as storage backend.");
                        logger().warning("Data will not persist across server restarts.");
                    }
                    if (storageType.hikari()) {
                        dependencyManager().hikari().downloadRelocateAndLoad().get();
                    }
                    storage = storageType.storageFunction().apply(this);
                    storage.initialize();
                    logger().info("Storage connection successfully established");
                } catch (ExecutionException e) {
                    throw new StorageException(e.getCause());
                } catch (StorageException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new StorageException(t);
                }
            } catch (StorageException e) {
                e.log(this);
                logger().error("Failed to connect to storage");
                if (initial) {
                    setStatus(Status.FAILED_TO_START);
                }
                return Collections.singletonList(ReloadResult.STORAGE_CONNECTION_FAILED);
            }
        }

        if (flags.contains(ReloadFlag.LINKED_ACCOUNT_PROVIDER)) {
            LinkedAccountConfig linkedAccountConfig = config().linkedAccounts;
            boolean linkProviderMissing = linkProvider == null;
            if (linkedAccountConfig != null && linkedAccountConfig.enabled) {
                LinkedAccountConfig.Provider provider = linkedAccountConfig.provider;
                boolean permitMinecraftAuth = connectionConfig().minecraftAuth.allow;
                if (provider == LinkedAccountConfig.Provider.AUTO) {
                    provider = permitMinecraftAuth && onlineMode().isOnline() ? LinkedAccountConfig.Provider.MINECRAFTAUTH : LinkedAccountConfig.Provider.STORAGE;
                }
                switch (provider) {
                    case MINECRAFTAUTH:
                        if (!permitMinecraftAuth) {
                            linkProvider = null;
                            logger().error("minecraftauth.me is disabled in the " + ConnectionConfig.FILE_NAME + ", "
                                                   + "but linked-accounts.provider is set to \"minecraftauth\". Linked accounts will be disabled");
                            break;
                        }
                        logger().info("Loading MinecraftAuth library");
                        dependencyManager.mcAuthLib().downloadRelocateAndLoad().get();
                        linkProvider = new MinecraftAuthenticationLinker(this);
                        logger().info("Using minecraftauth.me for linked accounts");
                        break;
                    case STORAGE:
                        linkProvider = new StorageLinker(this);
                        logger().info("Using storage for linked accounts");
                        break;
                    default: {
                        linkProvider = null;
                        logger().error("Unknown linked account provider: \"" + provider + "\", linked accounts will be disabled");
                        break;
                    }
                }
            } else {
                linkProvider = null;
                logger().info("Linked accounts are disabled");
            }

            if (linkProviderMissing && linkProvider != null) {
                playerProvider().loadAllProfilesAsync();
            }
        }

        if (flags.contains(ReloadFlag.DISCORD_CONNECTION)) {
            // Shutdown will not fail even if not connected
            discordConnectionManager.shutdown(DiscordConnectionManager.DEFAULT_SHUTDOWN_TIMEOUT);

            discordConnectionManager.connect();
            if (!initial) {
                waitForStatus(Status.CONNECTED, 20, TimeUnit.SECONDS);
                if (status() != Status.CONNECTED) {
                    return Collections.singletonList(ReloadResult.DISCORD_CONNECTION_FAILED);
                }
            }
        }

        // Modules are reloaded upon DiscordSRV being ready, thus not needed at initial
        if (!initial && flags.contains(ReloadFlag.CONFIG)) {
            results.addAll(moduleManager().reload());
        }

        if (translationLoader != null && flags.contains(ReloadFlag.TRANSLATIONS)) {
            translationLoader.reload();
        }

        if (flags.contains(ReloadFlag.DISCORD_COMMANDS) && isReady()) {
            discordAPI().commandRegistry().reloadCommands();
        }

        if (!initial) {
            eventBus().publish(new DiscordSRVReloadedEvent(flags));
            logger().info("Reload complete.");
        }

        return results;
    }

    private StorageType getStorageType() {
        String backend = connectionConfig().storage.backend;
        switch (backend.toLowerCase(Locale.ROOT)) {
            case "h2": return StorageType.H2;
            case "mysql": return StorageType.MYSQL;
            case "mariadb": return StorageType.MARIADB;
        }
        if (backend.equals(MemoryStorage.IDENTIFIER)) {
            return StorageType.MEMORY;
        }
        throw new StorageException("Unknown storage backend \"" + backend + "\"");
    }

    @SuppressWarnings("resource") //
    @MustBeInvokedByOverriders
    protected void disable() {
        Status status = this.status.get();
        if (status == Status.INITIALIZED || status.isShutdown()) {
            // Hasn't started or already shutting down/shutdown
            return;
        }
        this.status.set(Status.SHUTTING_DOWN);

        // Unregister PlayerProvider listeners
        playerProvider().unsubscribe();

        eventBus().publish(new DiscordSRVShuttingDownEvent());
        eventBus().shutdown();

        // Shutdown OkHttp
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdownNow();
            httpClient.connectionPool().evictAll();
            try {
                Cache cache = httpClient.cache();
                if (cache != null) {
                    cache.close();
                }
            } catch (IOException ignored) {}
        }

        try {
            if (storage != null) {
                storage.close();
            }
        } catch (Throwable t) {
            logger().error("Failed to close storage connection", t);
        }
        temporaryLocalData.save();
        this.status.set(Status.SHUTDOWN);
    }

    private Path generateBackupPath() throws IOException {
        String dateAndTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
        Path backupPath = dataDirectory().resolve("config-migrated").resolve(dateAndTime);
        if (Files.notExists(backupPath)) {
            Files.createDirectories(backupPath);
        }

        return backupPath;
    }
}
