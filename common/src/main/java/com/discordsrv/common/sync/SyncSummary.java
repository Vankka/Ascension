/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2024 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.common.sync;

import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.config.main.generic.AbstractSyncConfig;
import com.discordsrv.common.future.util.CompletableFutureUtil;
import com.discordsrv.common.someone.Someone;
import com.discordsrv.common.sync.cause.ISyncCause;
import com.discordsrv.common.sync.result.ISyncResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class SyncSummary<C extends AbstractSyncConfig<C, ?, ?>> {

    private final AbstractSyncModule<? extends DiscordSRV, C, ?, ?, ?> syncModule;
    private final ISyncCause cause;
    private final Someone who;
    private ISyncResult allFailReason;
    private final Map<C, CompletableFuture<ISyncResult>> results = new ConcurrentHashMap<>();

    public SyncSummary(AbstractSyncModule<? extends DiscordSRV, C, ?, ?, ?> syncModule, ISyncCause cause, Someone who) {
        this.syncModule = syncModule;
        this.cause = cause;
        this.who = who;
    }

    public ISyncCause cause() {
        return cause;
    }

    public Someone who() {
        return who;
    }

    public SyncSummary<C> fail(ISyncResult genericFail) {
        this.allFailReason = genericFail;
        return this;
    }

    public ISyncResult allFailReason() {
        return allFailReason;
    }

    public SyncSummary<C> appendResult(C config, ISyncResult result) {
        return appendResult(config, CompletableFuture.completedFuture(result));
    }

    public SyncSummary<C> appendResult(C config, CompletableFuture<ISyncResult> result) {
        this.results.put(config, result);
        return this;
    }

    public CompletableFuture<Map<C, ISyncResult>> resultFuture() {
        return CompletableFutureUtil.combine(results.values())
                .exceptionally(t -> null)
                .thenApply((__) -> {
                    Map<C, ISyncResult> results = new HashMap<>();
                    for (Map.Entry<C, CompletableFuture<ISyncResult>> entry : this.results.entrySet()) {
                        results.put(entry.getKey(), entry.getValue().exceptionally(t -> {
                            while (t instanceof CompletionException) {
                                t = t.getCause();
                            }
                            Throwable throwableToLog = t;
                            ISyncResult result = null;
                            if (t instanceof SyncFail) {
                                throwableToLog = t.getCause();
                                result = ((SyncFail) t).getResult();
                            }

                            if (throwableToLog != null) {
                                syncModule.logger().error(
                                        "Error in " + syncModule.syncName() + " "
                                                + entry.getKey().describe() + " for " + who()
                                                + " (sync cause: " + cause() + ")", throwableToLog);
                            }
                            return result;
                        }).join());
                    }
                    return results;
                });
    }
}
