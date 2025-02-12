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

import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.common.core.logging.NamedLogger;
import com.discordsrv.common.core.module.type.PluginIntegration;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AbstractEssentialsXModule extends PluginIntegration<BukkitDiscordSRV> implements Listener {

    public AbstractEssentialsXModule(BukkitDiscordSRV discordSRV) {
        super(discordSRV, new NamedLogger(discordSRV, "ESSENTIALSX_INTEGRATION"));
    }

    @Override
    public @NotNull String getIntegrationId() {
        return "Essentials";
    }

    protected Essentials get() {
        return (Essentials) discordSRV.server().getPluginManager().getPlugin("Essentials");
    }

    protected CompletableFuture<User> getUser(UUID playerUUID) {
        return discordSRV.scheduler().supply(() -> get().getUsers().loadUncachedUser(playerUUID));
    }

    @Override
    public void enable() {
        discordSRV.server().getPluginManager().registerEvents(this, discordSRV.plugin());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

}
