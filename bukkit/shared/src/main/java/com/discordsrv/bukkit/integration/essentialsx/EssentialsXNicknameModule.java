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

import com.discordsrv.api.module.type.NicknameModule;
import com.discordsrv.bukkit.BukkitDiscordSRV;
import com.discordsrv.common.feature.nicknamesync.NicknameSyncModule;
import com.earth2me.essentials.UserData;
import net.ess3.api.events.NickChangeEvent;
import org.bukkit.event.EventHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EssentialsXNicknameModule extends AbstractEssentialsXModule implements NicknameModule {

    public EssentialsXNicknameModule(BukkitDiscordSRV discordSRV) {
        super(discordSRV);
    }

    @Override
    public boolean isEnabled() {
        try {
            Class.forName("net.ess3.api.events.NickChangeEvent");
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        return super.isEnabled();
    }

    @EventHandler(ignoreCancelled = true)
    public void onNickChange(NickChangeEvent event) {
        NicknameSyncModule module = discordSRV.getModule(NicknameSyncModule.class);
        if (module != null) {
            module.newGameNickname(event.getAffected().getUUID(), event.getValue());
        }
    }

    @Override
    public CompletableFuture<String> getNickname(UUID playerUUID) {
        return getUser(playerUUID).thenApply(UserData::getNickname);
    }

    @Override
    public CompletableFuture<Void> setNickname(UUID playerUUID, String nickname) {
        return getUser(playerUUID).thenApply(user -> {
            user.setNickname(nickname);
            return null;
        });
    }
}
