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

package com.discordsrv.common.abstraction.sync;

import com.discordsrv.api.eventbus.Subscribe;
import com.discordsrv.api.reload.ReloadResult;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.abstraction.player.IPlayer;
import com.discordsrv.common.abstraction.sync.cause.GenericSyncCauses;
import com.discordsrv.common.abstraction.sync.cause.ISyncCause;
import com.discordsrv.common.abstraction.sync.enums.SyncDirection;
import com.discordsrv.common.abstraction.sync.enums.SyncSide;
import com.discordsrv.common.abstraction.sync.result.GenericSyncResults;
import com.discordsrv.common.abstraction.sync.result.ISyncResult;
import com.discordsrv.common.config.main.generic.AbstractSyncConfig;
import com.discordsrv.common.core.logging.NamedLogger;
import com.discordsrv.common.core.module.type.AbstractModule;
import com.discordsrv.common.events.player.PlayerConnectedEvent;
import com.discordsrv.common.helper.Someone;
import com.discordsrv.common.util.CompletableFutureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Abstraction for synchronization between Minecraft and Discord.
 *
 * @param <DT> the DiscordSRV type
 * @param <C> the configuration type for a single synchronizable
 * @param <G> the identifier for the game object to be synced
 * @param <D> the identifier for the Discord object to be synced
 * @param <S> state of synchronization on Minecraft/Discord
 */
public abstract class AbstractSyncModule<
        DT extends DiscordSRV,
        C extends AbstractSyncConfig<C, G, D>,
        G,
        D,
        S
> extends AbstractModule<DT> {

    protected final Map<C, Future<?>> syncs = new LinkedHashMap<>();
    protected final Map<G, List<C>> configsForGame = new ConcurrentHashMap<>();
    protected final Map<D, List<C>> configsForDiscord = new ConcurrentHashMap<>();

    public AbstractSyncModule(DT discordSRV, String loggerName) {
        super(discordSRV, new NamedLogger(discordSRV, loggerName));
    }

    protected abstract String syncName();
    protected abstract String logFileName();

    protected abstract String gameTerm();
    protected abstract String discordTerm();

    /**
     * Returns a list of all in use synchronizables.
     * @return a list of configurations for synchronizables
     */
    protected abstract List<C> configs();

    @Override
    public boolean isEnabled() {
        boolean any = false;
        for (C config : configs()) {
            if (config.isSet()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return false;
        }

        return super.isEnabled();
    }

    @Override
    public void reload(Consumer<ReloadResult> resultConsumer) {
        synchronized (syncs) {
            syncs.values().forEach(future -> {
                if (future != null) {
                    future.cancel(false);
                }
            });
            syncs.clear();
            configsForGame.clear();
            configsForDiscord.clear();

            String syncName = syncName();
            for (C config : configs()) {
                if (!config.isSet() || !config.validate(syncName, discordSRV)) {
                    continue;
                }

                boolean failed = false;
                for (C existingConfig : syncs.keySet()) {
                    if (existingConfig.isSameAs(config)) {
                        failed = true;
                        break;
                    }
                }
                if (failed) {
                    discordSRV.logger().error("Duplicate " + syncName + " (" + config.describe() + ")");
                    continue;
                }

                Future<?> future = null;
                AbstractSyncConfig.TimerConfig timer = config.timer;
                if (timer != null && timer.enabled) {
                    int cycleTime = timer.cycleTime;
                    future = discordSRV.scheduler().runAtFixedRate(
                            () -> resyncTimer(config),
                            Duration.ofMinutes(cycleTime),
                            Duration.ofMinutes(cycleTime)
                    );
                }

                syncs.put(config, future);

                G game = config.gameId();
                if (game != null) {
                    configsForGame.computeIfAbsent(game, key -> new ArrayList<>()).add(config);
                }

                D discord = config.discordId();
                if (discord != null) {
                    configsForDiscord.computeIfAbsent(discord, key -> new ArrayList<>()).add(config);
                }
            }
        }
    }

    private void resyncTimer(C config) {
        for (IPlayer player : discordSRV.playerProvider().allPlayers()) {
            resync(GenericSyncCauses.TIMER, config, Someone.of(player.uniqueId()));
        }
    }

    @Subscribe
    public void onPlayerConnected(PlayerConnectedEvent event) {
        resyncAll(GenericSyncCauses.GAME_JOIN, Someone.of(event.player()));
    }

    /**
     * Checks if the given new and current state are the same, basically meaning that no update is necessary.
     * @return the result stating the states are the same, otherwise {@code null} to state they are not
     */
    @Nullable
    protected abstract ISyncResult doesStateMatch(S one, S two);

    /**
     * Gets the current state of the provided config for the specified user on Discord.
     *
     * @param config the configuration for the synchronizable
     * @param userId the Discord user id
     * @return a future for the state on Discord
     */
    protected abstract CompletableFuture<S> getDiscord(C config, long userId);

    /**
     * Gets the current state of the provided config for the specified player on Minecraft.
     *
     * @param config the configuration for the synchronizable
     * @param playerUUID the Minecraft player {@link UUID}
     * @return a future for the state on Minecraft
     */
    protected abstract CompletableFuture<S> getGame(C config, UUID playerUUID);

    /**
     * Applies the provided state for the provided config for the provided Discord user.
     *
     * @param config the configuration for the synchronizable
     * @param userId the Discord user id
     * @param newState the newState to apply
     * @return a future with the result of the synchronization
     */
    protected abstract CompletableFuture<ISyncResult> applyDiscord(C config, long userId, @Nullable S newState);

    protected CompletableFuture<ISyncResult> applyDiscordIfDoesNotMatch(C config, long userId, @Nullable S newState) {
        return getDiscord(config, userId).thenCompose(currentState -> {
            ISyncResult result = doesStateMatch(newState, currentState);
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            } else {
                return applyDiscord(config, userId, newState);
            }
        });
    }

    /**
     * Applies the provided state for the provided config for the provided Minecraft player.
     *
     * @param config the configuration for the synchronizable
     * @param playerUUID the Minecraft player {@link UUID}
     * @param newState the newState to apply
     * @return a future with the result of the synchronization
     */
    protected abstract CompletableFuture<ISyncResult> applyGame(C config, UUID playerUUID, @Nullable S newState);

    protected CompletableFuture<ISyncResult> applyGameIfDoesNotMatch(C config, UUID playerUUID, @Nullable S newState) {
        return getGame(config, playerUUID).thenCompose(currentState -> {
            ISyncResult result = doesStateMatch(currentState, newState);
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            } else {
                return applyGame(config, playerUUID, newState);
            }
        });
    }

    protected CompletableFuture<SyncSummary<C>> discordChanged(ISyncCause cause, Someone someone, D discordId, @Nullable S newState) {
        List<C> gameConfigs = configsForDiscord.get(discordId);
        if (gameConfigs == null) {
            return CompletableFuture.completedFuture(null);
        }

        return someone.withLinkedAccounts(discordSRV).thenApply(resolved -> {
            if (resolved == null) {
                return new SyncSummary<>(this, cause, someone).fail(GenericSyncResults.NOT_LINKED);
            }

            SyncSummary<C> summary = new SyncSummary<>(this, cause, resolved);
            for (C config : gameConfigs) {
                SyncDirection direction = config.direction;
                if (direction == SyncDirection.MINECRAFT_TO_DISCORD) {
                    // Not going Discord -> Minecraft
                    summary.appendResult(config, GenericSyncResults.WRONG_DIRECTION);
                    continue;
                }

                summary.appendResult(config, applyGameIfDoesNotMatch(config, resolved.playerUUID(), newState));

                // If the sync is bidirectional, also sync anything else linked to the same Minecraft id
                if (direction == SyncDirection.DISCORD_TO_MINECRAFT) {
                    continue;
                }

                List<C> discordConfigs = configsForGame.get(config.gameId());
                if (discordConfigs == null) {
                    continue;
                }

                for (C gameConfig : discordConfigs) {
                    if (Objects.equals(gameConfig.discordId(), discordId)) {
                        continue;
                    }

                    summary.appendResult(gameConfig, applyDiscordIfDoesNotMatch(gameConfig, resolved.userId(), newState));
                }
            }
            return summary;
        }).whenComplete((summary, t) -> {
            if (summary != null) {
                logSummary(summary);
            }
        });
    }

    protected CompletableFuture<SyncSummary<C>> gameChanged(ISyncCause cause, Someone someone, @NotNull G gameId, @Nullable S newState) {
        List<C> discordConfigs = configsForGame.get(gameId);
        if (discordConfigs == null) {
            return CompletableFuture.completedFuture(null);
        }

        return someone.withLinkedAccounts(discordSRV).thenApply(resolved -> {
            if (resolved == null) {
                return new SyncSummary<>(this, cause, someone).fail(GenericSyncResults.NOT_LINKED);
            }

            SyncSummary<C> summary = new SyncSummary<>(this, cause, resolved);
            for (C config : discordConfigs) {
                SyncDirection direction = config.direction;
                if (direction == SyncDirection.DISCORD_TO_MINECRAFT) {
                    // Not going Minecraft -> Discord
                    summary.appendResult(config, GenericSyncResults.WRONG_DIRECTION);
                    continue;
                }

                summary.appendResult(config, applyDiscordIfDoesNotMatch(config, resolved.userId(), newState));

                // If the sync is bidirectional, also sync anything else linked to the same Discord id
                if (direction == SyncDirection.MINECRAFT_TO_DISCORD) {
                    continue;
                }

                List<C> gameConfigs = configsForDiscord.get(config.discordId());
                if (gameConfigs == null) {
                    continue;
                }

                for (C gameConfig : gameConfigs) {
                    if (Objects.equals(gameConfig.gameId(), gameId)) {
                        continue;
                    }

                    summary.appendResult(gameConfig, applyGameIfDoesNotMatch(gameConfig, resolved.playerUUID(), newState));
                }
            }
            return summary;
        }).whenComplete((summary, t) -> {
            if (summary != null) {
                logSummary(summary);
            }
        });
    }

    public CompletableFuture<SyncSummary<C>> resyncAll(ISyncCause cause, Someone someone) {
        return someone.withLinkedAccounts(discordSRV).thenApply(resolved -> {
            if (resolved == null) {
                return new SyncSummary<>(this, cause, someone).fail(GenericSyncResults.NOT_LINKED);
            }

            SyncSummary<C> summary = new SyncSummary<>(this, cause, resolved);
            Set<C> configs = syncs.keySet();

            for (C config : configs) {
                summary.appendResult(config, resync(config, resolved));
            }
            return summary;
        }).whenComplete((summary, t) -> {
            if (summary != null) {
                logSummary(summary);
            }
        });
    }

    protected CompletableFuture<SyncSummary<C>> resync(ISyncCause cause, C config, Someone someone) {
        return someone.withLinkedAccounts(discordSRV).thenApply(resolved -> {
            if (resolved == null) {
                return new SyncSummary<>(this, cause, someone).fail(GenericSyncResults.NOT_LINKED);
            }

            return new SyncSummary<>(this, cause, resolved)
                    .appendResult(config, resync(config, resolved));
        }).whenComplete((summary, t) -> {
            if (summary != null) {
                logSummary(summary);
            }
        });
    }

    private CompletableFuture<ISyncResult> resync(C config, Someone.Resolved resolved) {
        UUID playerUUID = resolved.playerUUID();
        long userId = resolved.userId();

        CompletableFuture<S> gameGet = getGame(config, playerUUID);
        CompletableFuture<S> discordGet = getDiscord(config, userId);

        return CompletableFutureUtil.combine(gameGet, discordGet).thenCompose((__) -> {
            S gameState = gameGet.join();
            S discordState = discordGet.join();

            ISyncResult alreadyInSyncResult = doesStateMatch(gameState, discordState);
            if (alreadyInSyncResult != null) {
                return CompletableFuture.completedFuture(alreadyInSyncResult);
            }

            SyncSide side = config.tieBreaker;
            SyncDirection direction = config.direction;
            if (discordState != null) {
                if (side == SyncSide.DISCORD) {
                    // Has Discord, add game
                    if (direction == SyncDirection.MINECRAFT_TO_DISCORD) {
                        return CompletableFuture.completedFuture(GenericSyncResults.WRONG_DIRECTION);
                    }

                    return applyGame(config, playerUUID, discordState);
                } else {
                    // Missing game, remove Discord
                    if (direction == SyncDirection.DISCORD_TO_MINECRAFT) {
                        return CompletableFuture.completedFuture(GenericSyncResults.WRONG_DIRECTION);
                    }

                    return applyDiscord(config, userId, null);
                }
            } else {
                if (side == SyncSide.DISCORD) {
                    // Missing Discord, remove game
                    if (direction == SyncDirection.MINECRAFT_TO_DISCORD) {
                        return CompletableFuture.completedFuture(GenericSyncResults.WRONG_DIRECTION);
                    }

                    return applyGame(config, playerUUID, null);
                } else {
                    // Has game, add Discord
                    if (direction == SyncDirection.DISCORD_TO_MINECRAFT) {
                        return CompletableFuture.completedFuture(GenericSyncResults.WRONG_DIRECTION);
                    }

                    return applyDiscord(config, userId, gameState);
                }
            }
        }).exceptionally(t -> {
            if (t instanceof SyncFail) {
                return ((SyncFail) t).getResult();
            } else {
                throw (RuntimeException) t;
            }
        });
    }

    private String formatResults(SyncSummary<C> summary, List<String> results) {
        int count = results.size();
        return summary.who() + " (sync cause: " + summary.cause() + ")"
                + (count == 1 ? ": " : "\n")
                + String.join("\n", results);
    }

    private void logSummary(SyncSummary<C> summary) {
        summary.resultFuture().whenComplete((results, t) -> {
            Throwable throwableToLog = null;
            if (t != null) {
                while (t instanceof CompletionException) {
                    t = t.getCause();
                }
                if (t instanceof SyncFail) {
                    SyncFail fail = (SyncFail) t;
                    summary.fail(fail.getResult());
                    throwableToLog = fail.getCause();
                } else {
                    logger().error("Failed to " + syncName() + " " + summary.who() + " (sync cause: " + summary.cause() + ")", t);
                    return;
                }
            }

            ISyncResult allFailReason = summary.allFailReason();
            if (allFailReason != null) {
                String reason = allFailReason.format(gameTerm(), discordTerm());
                String message = "Failed to " + syncName() + " " + summary.who() + " (sync cause: " + summary.cause() + "): " + reason;
                if (allFailReason.isError()) {
                    logger().error(message, throwableToLog);
                } else {
                    logger().debug(message, throwableToLog);
                }
                return;
            }

            Map<ISyncResult, List<String>> groupedResults = new LinkedHashMap<>();
            for (Map.Entry<C, ISyncResult> entry : results.entrySet()) {
                C config = entry.getKey();
                ISyncResult result = entry.getValue();

                groupedResults.computeIfAbsent(result, key -> new ArrayList<>()).add(config.describe());
            }

            List<String> successResults = new ArrayList<>();
            List<String> failResults = new ArrayList<>();
            List<String> auditResults = new ArrayList<>();
            for (Map.Entry<ISyncResult, List<String>> entry : groupedResults.entrySet()) {
                ISyncResult result = entry.getKey();
                String line = result.format(gameTerm(), discordTerm())
                        + ": [" + String.join(", ", entry.getValue()) + "]";
                if (result.isError()) {
                    failResults.add(line);
                } else {
                    successResults.add(line);
                }
                if (result.isUpdate()) {
                    auditResults.add(line);
                }
            }

            boolean anySuccess = !successResults.isEmpty();
            boolean anyFail = !failResults.isEmpty();
            String partially = anySuccess && anyFail ? " partially" : "";
            if (anySuccess) {
                logger().debug(syncName() + partially + " succeeded for " + formatResults(summary, successResults));
            }
            if (anyFail) {
                logger().error(syncName() + partially + " failed for " + formatResults(summary, failResults));
            }
            if (!auditResults.isEmpty()) {
                discordSRV.logger().writeLogForCurrentDay(logFileName(), formatResults(summary, auditResults));
            }
        });
    }

}
