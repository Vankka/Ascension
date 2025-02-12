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

package com.discordsrv.bukkit.integration.essentialsx;

import com.discordsrv.api.component.MinecraftComponent;
import com.discordsrv.api.module.type.PunishmentModule;
import com.discordsrv.api.punishment.Punishment;
import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.common.util.ComponentUtil;
import net.ess3.api.events.MuteStatusChangeEvent;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EssentialsXMuteModule extends AbstractEssentialsXModule implements PunishmentModule.Mutes {

    public EssentialsXMuteModule(BukkitDiscordSRV discordSRV) {
        super(discordSRV);
    }

    @Override
    public boolean isEnabled() {
        try {
            Class.forName("net.ess3.api.events.MuteStatusChangeEvent");
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        return super.isEnabled();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMuteStatusChange(MuteStatusChangeEvent event) {

    }

    @Override
    public CompletableFuture<Punishment> getMute(@NotNull UUID playerUUID) {
        return getUser(playerUUID).thenApply(user -> new Punishment(
                Instant.ofEpochMilli(user.getMuteTimeout()),
                ComponentUtil.toAPI(BukkitComponentSerializer.legacy().deserialize(user.getMuteReason())),
                null
        ));
    }

    @Override
    public CompletableFuture<Void> addMute(@NotNull UUID playerUUID, @Nullable Instant until, @Nullable MinecraftComponent reason, @NotNull MinecraftComponent punisher) {
        String reasonLegacy = reason != null ? BukkitComponentSerializer.legacy().serialize(ComponentUtil.fromAPI(reason)) : null;
        return getUser(playerUUID).thenApply(user -> {
            user.setMuted(true);
            user.setMuteTimeout(until != null ? until.toEpochMilli() : 0);
            user.setMuteReason(reasonLegacy);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> removeMute(@NotNull UUID playerUUID) {
        return getUser(playerUUID).thenApply(user -> {
            user.setMuted(false);
            user.setMuteTimeout(0);
            user.setMuteReason(null);
            return null;
        });
    }
}
