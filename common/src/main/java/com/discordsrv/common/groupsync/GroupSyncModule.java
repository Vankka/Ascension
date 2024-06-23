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

package com.discordsrv.common.groupsync;

import com.discordsrv.api.discord.entity.guild.DiscordRole;
import com.discordsrv.api.event.bus.Subscribe;
import com.discordsrv.api.event.events.discord.member.role.DiscordMemberRoleAddEvent;
import com.discordsrv.api.event.events.discord.member.role.DiscordMemberRoleRemoveEvent;
import com.discordsrv.api.module.type.PermissionModule;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.config.main.GroupSyncConfig;
import com.discordsrv.common.debug.DebugGenerateEvent;
import com.discordsrv.common.debug.file.TextDebugFile;
import com.discordsrv.common.future.util.CompletableFutureUtil;
import com.discordsrv.common.groupsync.enums.GroupSyncCause;
import com.discordsrv.common.groupsync.enums.GroupSyncResult;
import com.discordsrv.common.someone.Someone;
import com.discordsrv.common.sync.AbstractSyncModule;
import com.discordsrv.common.sync.result.ISyncResult;
import com.discordsrv.common.sync.SyncFail;
import com.discordsrv.common.sync.result.GenericSyncResults;
import com.github.benmanes.caffeine.cache.Cache;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GroupSyncModule extends AbstractSyncModule<DiscordSRV, GroupSyncConfig.PairConfig, String, Long, Boolean> {

    private final Cache<Long, Map<Long, Boolean>> expectedDiscordChanges;
    private final Cache<UUID, Map<String, Boolean>> expectedMinecraftChanges;

    public GroupSyncModule(DiscordSRV discordSRV) {
        super(discordSRV, "GROUP_SYNC");

        this.expectedDiscordChanges = discordSRV.caffeineBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
        this.expectedMinecraftChanges = discordSRV.caffeineBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String syncName() {
        return "Group sync";
    }

    @Override
    public String logFileName() {
        return "groupsync";
    }

    @Override
    public String gameTerm() {
        return "group";
    }

    @Override
    public String discordTerm() {
        return "role";
    }

    @Override
    public List<GroupSyncConfig.PairConfig> configs() {
        return discordSRV.config().groupSync.pairs;
    }

    @Override
    protected @Nullable ISyncResult doesStateMatch(Boolean one, Boolean two) {
        if (one == two) {
            return GenericSyncResults.both(one);
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        boolean any = false;
        for (GroupSyncConfig.PairConfig pair : discordSRV.config().groupSync.pairs) {
            if (pair.isSet()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return false;
        }

        return super.isEnabled();
    }

    @Subscribe
    public void onDebugGenerate(DebugGenerateEvent event) {
        StringBuilder builder = new StringBuilder("Active pairs:");

        for (Map.Entry<GroupSyncConfig.PairConfig, Future<?>> entry : syncs.entrySet()) {
            GroupSyncConfig.PairConfig pair = entry.getKey();
            builder.append("\n- ").append(pair)
                    .append(" (tie-breaker: ").append(pair.tieBreaker)
                    .append(", direction: ").append(pair.direction)
                    .append(", server context: ").append(pair.serverContext).append(")");
            if (entry.getValue() != null) {
                builder.append(" [Timed]");
            }
        }

        PermissionModule.Groups groups = getPermissionProvider();
        if (groups != null) {
            builder.append("\n\nAvailable groups (").append(groups.getClass().getName()).append("):");

            for (String group : groups.getGroups()) {
                builder.append("\n- ").append(group);
            }
        } else {
            builder.append("\n\nNo permission provider available");
        }
        event.addFile(new TextDebugFile("group-sync.txt", builder));
    }

    // Listeners & methods to indicate something changed

    @Subscribe
    public void onDiscordMemberRoleAdd(DiscordMemberRoleAddEvent event) {
        event.getRoles().forEach(role -> roleChanged(event.getMember().getUser().getId(), role.getId(), true));
    }

    @Subscribe
    public void onDiscordMemberRoleRemove(DiscordMemberRoleRemoveEvent event) {
        event.getRoles().forEach(role -> roleChanged(event.getMember().getUser().getId(), role.getId(), false));
    }

    public void groupAdded(UUID player, String groupName, @Nullable Set<String> serverContext, GroupSyncCause cause) {
        groupChanged(player, groupName, serverContext, cause, true);
    }

    public void groupRemoved(UUID player, String groupName, @Nullable Set<String> serverContext, GroupSyncCause cause) {
        groupChanged(player, groupName, serverContext, cause, false);
    }

    private void roleChanged(long userId, long roleId, boolean newState) {
        if (checkExpectation(expectedDiscordChanges, userId, roleId, newState)) {
            return;
        }

        PermissionModule.Groups permissionProvider = getPermissionProvider();
        if (permissionProvider == null) {
            logger().debug("No permission provider");
            return;
        }

        discordChanged(GroupSyncCause.DISCORD_ROLE_CHANGE, Someone.of(userId), roleId, newState);
    }

    private void groupChanged(
            UUID playerUUID,
            String groupName,
            Set<String> serverContext,
            GroupSyncCause cause,
            boolean state
    ) {
        if (cause.isDiscordSRVCanCause() && checkExpectation(expectedMinecraftChanges, playerUUID, groupName, state)) {
            return;
        }

        PermissionModule.Groups permissionProvider = getPermissionProvider();
        if (permissionProvider == null) {
            logger().debug("No permission provider");
            return;
        }

        gameChanged(cause, Someone.of(playerUUID), context(groupName, serverContext), state);
    }

    private PermissionModule.Groups getPermissionProvider() {
        PermissionModule.GroupsContext groupsContext = discordSRV.getModule(PermissionModule.GroupsContext.class);
        return groupsContext != null ? groupsContext : discordSRV.getModule(PermissionModule.Groups.class);
    }

    public boolean noPermissionProvider() {
        return getPermissionProvider() != null;
    }

    private <T, R> boolean checkExpectation(Cache<T, Map<R, Boolean>> expectations, T key, R mapKey, boolean newState) {
        // Check if we were expecting the change (when we add/remove something due to synchronization),
        // if we did expect the change, we won't trigger a synchronization since we just synchronized what was needed
        Map<R, Boolean> expected = expectations.getIfPresent(key);
        if (expected != null && Objects.equals(expected.get(mapKey), newState)) {
            expected.remove(mapKey);
            return true;
        }
        return false;
    }

    // Resync

    @Override
    public CompletableFuture<Boolean> getDiscord(GroupSyncConfig.PairConfig config, long userId) {
        DiscordRole role = discordSRV.discordAPI().getRoleById(config.roleId);
        if (role == null) {
            return CompletableFutureUtil.failed(new SyncFail(GroupSyncResult.ROLE_DOESNT_EXIST));
        }

        if (!role.getGuild().getSelfMember().canInteract(role)) {
            return CompletableFutureUtil.failed(new SyncFail(GroupSyncResult.ROLE_CANNOT_INTERACT));
        }

        return role.getGuild().retrieveMemberById(userId).thenApply(member -> {
            if (member == null) {
                throw new SyncFail(GroupSyncResult.NOT_A_GUILD_MEMBER);
            }

            return member.hasRole(role);
        });
    }

    @Override
    public CompletableFuture<Boolean> getGame(GroupSyncConfig.PairConfig config, UUID playerUUID) {
        PermissionModule.Groups permissionProvider = getPermissionProvider();
        CompletableFuture<Boolean> future;
        if (permissionProvider instanceof PermissionModule.GroupsContext) {
            future = ((PermissionModule.GroupsContext) permissionProvider)
                    .hasGroup(playerUUID, config.groupName, false, config.serverContext != null ? Collections.singleton(config.serverContext) : null);
        } else {
            future = permissionProvider.hasGroup(playerUUID, config.groupName, false);
        }

        return future.exceptionally(t -> {
            throw new SyncFail(GroupSyncResult.PERMISSION_BACKEND_FAILED, t);
        });
    }

    @Override
    public CompletableFuture<ISyncResult> applyDiscord(GroupSyncConfig.PairConfig config, long userId, Boolean newState) {
        DiscordRole role = discordSRV.discordAPI().getRoleById(config.roleId);
        if (role == null) {
            return CompletableFutureUtil.failed(new SyncFail(GroupSyncResult.ROLE_DOESNT_EXIST));
        }

        Map<Long, Boolean> expected = expectedDiscordChanges.get(userId, key -> new ConcurrentHashMap<>());
        if (expected != null) {
            expected.put(config.roleId, newState);
        }

        return role.getGuild().retrieveMemberById(userId)
                .thenCompose(member -> newState
                                       ? member.addRole(role).thenApply(v -> (ISyncResult) GenericSyncResults.ADD_DISCORD)
                                       : member.removeRole(role).thenApply(v -> GenericSyncResults.REMOVE_DISCORD)
                ).whenComplete((r, t) -> {
                    if (t != null) {
                        //noinspection DataFlowIssue
                        expected.remove(config.roleId);
                    }
                });
    }

    @Override
    public CompletableFuture<ISyncResult> applyGame(GroupSyncConfig.PairConfig config, UUID playerUUID, Boolean newState) {
        Map<String, Boolean> expected = expectedMinecraftChanges.get(playerUUID, key -> new ConcurrentHashMap<>());
        if (expected != null) {
            expected.put(config.groupName, newState);
        }

        CompletableFuture<ISyncResult> future =
                newState
                    ? addGroup(playerUUID, config).thenApply(v -> GenericSyncResults.ADD_GAME)
                    : removeGroup(playerUUID, config).thenApply(v -> GenericSyncResults.REMOVE_GAME);
        return future.exceptionally(t -> {
            //noinspection DataFlowIssue
            expected.remove(config.groupName);
            throw new SyncFail(GroupSyncResult.PERMISSION_BACKEND_FAILED, t);
        });
    }

    private Set<String> context(GroupSyncConfig.PairConfig config) {
        return config.serverContext != null ? Collections.singleton(config.serverContext) : null;
    }

    private String context(String groupName, Set<String> serverContext) {
        if (serverContext == null || serverContext.isEmpty()) {
            return GroupSyncConfig.PairConfig.makeGameId(groupName, Collections.singleton("global"));
        }
        if (serverContext.size() == 1 && serverContext.iterator().next().isEmpty()) {
            return null;
        }
        return GroupSyncConfig.PairConfig.makeGameId(groupName, serverContext);
    }

    private CompletableFuture<Void> addGroup(UUID player, GroupSyncConfig.PairConfig config) {
        PermissionModule.Groups permissionProvider = getPermissionProvider();
        String groupName = config.groupName;
        if (permissionProvider instanceof PermissionModule.GroupsContext) {
            return ((PermissionModule.GroupsContext) permissionProvider)
                    .addGroup(player, groupName, context(config));
        } else {
            return permissionProvider.addGroup(player, groupName);
        }
    }

    private CompletableFuture<Void> removeGroup(UUID player, GroupSyncConfig.PairConfig config) {
        PermissionModule.Groups permissionProvider = getPermissionProvider();
        String groupName = config.groupName;
        if (permissionProvider instanceof PermissionModule.GroupsContext) {
            return ((PermissionModule.GroupsContext) permissionProvider)
                    .removeGroup(player, groupName, context(config));
        } else {
            return permissionProvider.removeGroup(player, groupName);
        }
    }
}
