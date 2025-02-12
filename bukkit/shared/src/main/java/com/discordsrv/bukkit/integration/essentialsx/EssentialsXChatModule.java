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

import com.discordsrv.api.channel.GameChannel;
import com.discordsrv.api.component.MinecraftComponent;
import com.discordsrv.api.eventbus.EventPriorities;
import com.discordsrv.api.eventbus.Subscribe;
import com.discordsrv.api.events.channel.GameChannelLookupEvent;
import com.discordsrv.api.events.message.receive.game.GameChatMessageReceiveEvent;
import com.discordsrv.api.player.DiscordSRVPlayer;
import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.bukkit.player.BukkitPlayer;
import com.discordsrv.common.util.ComponentUtil;
import net.essentialsx.api.v2.events.chat.GlobalChatEvent;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class EssentialsXChatModule extends AbstractEssentialsXModule {

    private final GlobalChannel channel = new GlobalChannel();

    public EssentialsXChatModule(BukkitDiscordSRV discordSRV) {
        super(discordSRV);
    }

    @Override
    public boolean isEnabled() {
        try {
            Class.forName("net.essentialsx.api.v2.events.chat.GlobalChatEvent");
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        return super.isEnabled();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onGlobalChat(GlobalChatEvent event) {
        Player player = event.getPlayer();
        MinecraftComponent component = ComponentUtil.toAPI(
                BukkitComponentSerializer.legacy().deserialize(event.getMessage())
        );

        BukkitPlayer srvPlayer = discordSRV.playerProvider().player(player);
        boolean cancelled = event.isCancelled();
        discordSRV.scheduler().run(() -> discordSRV.eventBus().publish(
                new GameChatMessageReceiveEvent(event, srvPlayer, component, channel, cancelled)
        ));
    }

    @Subscribe(priority = EventPriorities.LAST)
    public void onGameChannelLookup(GameChannelLookupEvent event) {
        if (!discordSRV.server().getPluginManager().isPluginEnabled("EssentialsChat")) {
            return;
        }

        if (event.isDefault()) {
            event.process(channel);
        }
    }

    private class GlobalChannel implements GameChannel {

        @Override
        public @NotNull String getOwnerName() {
            return getIntegrationId();
        }

        @Override
        public @NotNull String getChannelName() {
            return GameChannel.DEFAULT_NAME;
        }

        @Override
        public boolean isChat() {
            return true;
        }

        @Override
        public @NotNull Collection<? extends DiscordSRVPlayer> getRecipients() {
            return discordSRV.playerProvider().allPlayers();
        }
    }
}
