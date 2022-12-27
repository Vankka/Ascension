/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2022 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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

package com.discordsrv.common.command.game.command.subcommand;

import com.discordsrv.api.color.Color;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.command.game.abstraction.GameCommandArguments;
import com.discordsrv.common.command.game.abstraction.GameCommand;
import com.discordsrv.common.command.game.abstraction.GameCommandExecutor;
import com.discordsrv.common.command.game.sender.ICommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class VersionCommand implements GameCommandExecutor {

    private static GameCommand INSTANCE;

    public static GameCommand get(DiscordSRV discordSRV) {
        if (INSTANCE == null) {
            INSTANCE = GameCommand.literal("version")
                    .requiredPermission("discordsrv.admin.version")
                    .executor(new VersionCommand(discordSRV));
        }

        return INSTANCE;
    }

    private final DiscordSRV discordSRV;

    public VersionCommand(DiscordSRV discordSRV) {
        this.discordSRV = discordSRV;
    }

    @Override
    public void execute(ICommandSender sender, GameCommandArguments arguments) {
        sender.sendMessage(
                Component.text().content("Running DiscordSRV ").color(TextColor.color(Color.BLURPLE.rgb()))
                        .append(Component.text("v" + discordSRV.versionInfo(), NamedTextColor.GRAY))
        );
    }
}
