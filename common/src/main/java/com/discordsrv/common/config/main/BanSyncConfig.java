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

package com.discordsrv.common.config.main;

import com.discordsrv.common.abstraction.sync.enums.BanSyncDiscordAction;
import com.discordsrv.common.abstraction.sync.enums.BanSyncDiscordTrigger;
import com.discordsrv.common.config.configurate.annotation.Constants;
import com.discordsrv.common.config.configurate.annotation.Order;
import com.discordsrv.common.config.main.generic.AbstractSyncConfig;
import com.discordsrv.common.feature.bansync.BanSyncModule;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class BanSyncConfig extends AbstractSyncConfig<BanSyncConfig, BanSyncModule.Game, Long> {

    @Comment("The id for the Discord server where the bans should be synced from/to")
    @Order(-10)
    public long serverId = 0L;

    @Comment("Role id that will be used for role related actions, if they are configured below")
    public Long bannedRoleId = 0L;

    @Comment("Options for syncing bans from Minecraft to Discord")
    public MinecraftToDiscordConfig minecraftToDiscord = new MinecraftToDiscordConfig();

    @Comment("Options for syncing bans from Discord to Minecraft")
    public DiscordToMinecraftConfig discordToMinecraft = new DiscordToMinecraftConfig();

    @Comment("Resync upon linking")
    public boolean resyncUponLinking = true;

    @Override
    public boolean isSet() {
        return serverId != 0;
    }

    @Override
    public BanSyncModule.Game gameId() {
        return BanSyncModule.Game.INSTANCE;
    }

    @Override
    public Long discordId() {
        return serverId;
    }

    @Override
    public boolean isSameAs(BanSyncConfig config) {
        return false;
    }

    @Override
    public String describe() {
        return Long.toUnsignedString(serverId);
    }

    @ConfigSerializable
    public static class DiscordToMinecraftConfig {

        // TODO make ROLE ignore bans entirely
        @Comment("What action(s) on Discord should trigger a ban/unban in Minecraft. Valid options:\n"
                + "ban:     A ban/unban on the Discord Server\n"
                + "role:    Addition/removal of the banned role (specified above) to the user on Discord\n"
                + "either:  Either of the above\n"
                + "BEWARE: Settings of 'role' or 'either' can be exploited to remove bans from players if %1 is set to 'discord'")
        @Constants.Comment("tie-breaker")
        public BanSyncDiscordTrigger trigger = BanSyncDiscordTrigger.BAN;

        @Comment("The reason used when creating new bans in Minecraft")
        public String banReasonFormat = "%punishment_reason%";

        @Comment("The punisher shown when creating new bans in Minecraft")
        public String punisherFormat = "%user_color%@%user_name%";

        @Comment("The kick reason when a ban is applied to a online player")
        public String kickReason = "&cYou have been banned for &f%punishment_reason% &cby &f%punishment_punisher%";
    }

    @ConfigSerializable
    public static class MinecraftToDiscordConfig {

        @Comment("What action(s) to perform on the linked Discord account when a player is banned in Minecraft. Can be configured in more detail below\n"
                + "Valid options: %1, %2")
        @Constants.Comment({"ban", "role"})
        public BanSyncDiscordAction action = BanSyncDiscordAction.BAN;

        @Comment("The reason used when creating new bans in Discord or adding the banned role to users")
        public String banReasonFormat = "Banned by %punishment_punisher% in Minecraft for %punishment_reason%, ends: %punishment_until:'YYYY-MM-dd HH:mm:ss zzz'|text:'Never'%";

        @Comment("The reason used when removing bans in Discord or removing the banned role from users")
        public String unbanReasonFormat = "Unbanned in Minecraft";

        @Comment("The number of hours of Discord messages to delete when syncing bans from Minecraft to Discord (if doing so is enabled)")
        public int messageHoursToDelete = 0;
    }
}
