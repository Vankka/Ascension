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

package com.discordsrv.common.feature.nicknamesync;

import com.discordsrv.api.discord.entity.DiscordUser;
import com.discordsrv.api.discord.entity.guild.DiscordGuild;
import com.discordsrv.api.discord.entity.guild.DiscordGuildMember;
import com.discordsrv.api.eventbus.Subscribe;
import com.discordsrv.api.module.type.NicknameModule;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.abstraction.sync.AbstractSyncModule;
import com.discordsrv.common.abstraction.sync.SyncFail;
import com.discordsrv.common.abstraction.sync.result.GenericSyncResults;
import com.discordsrv.common.abstraction.sync.result.ISyncResult;
import com.discordsrv.common.config.main.NicknameSyncConfig;
import com.discordsrv.common.feature.nicknamesync.enums.NicknameSyncCause;
import com.discordsrv.common.feature.nicknamesync.enums.NicknameSyncResult;
import com.discordsrv.common.helper.Someone;
import com.discordsrv.common.util.CompletableFutureUtil;
import com.discordsrv.common.util.Game;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code long} is used for the guild id being synced to in Discord.
 * The state is the current
 */
public class NicknameSyncModule extends AbstractSyncModule<DiscordSRV, NicknameSyncConfig, Game, Long, String> {

    private final Map<NicknameSyncConfig, List<Pair<Pattern, String>>> replacements = new HashMap<>();

    public NicknameSyncModule(DiscordSRV discordSRV) {
        super(discordSRV, "NICKNAME_SYNC");
    }

    @Override
    protected String syncName() {
        return "Nickname Sync";
    }

    @Override
    protected String logFileName() {
        return "nicknamesync";
    }

    @Override
    protected String gameTerm() {
        return "nickname";
    }

    @Override
    protected String discordTerm() {
        return "nickname";
    }

    @Override
    protected List<NicknameSyncConfig> configs() {
        return Collections.singletonList(discordSRV.config().nicknameSync);
    }

    @Override
    protected @Nullable ISyncResult doesStateMatch(String one, String two) {
        return StringUtils.equals(one, two) ? NicknameSyncResult.MATCH : null;
    }

    @Subscribe
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        DiscordUser user = discordSRV.discordAPI().getUser(event.getUser());
        discordChanged(
                NicknameSyncCause.DISCORD_NICKNAME_CHANGED,
                Someone.of(user),
                event.getGuild().getIdLong(),
                event.getNewNickname()
        );
    }

    public void newGameNickname(UUID playerUUID, String newNickname) {
        gameChanged(
                NicknameSyncCause.GAME_NICKNAME_CHANGED,
                Someone.of(playerUUID),
                Game.INSTANCE,
                newNickname
        );
    }

    protected NicknameModule getModule() {
        return discordSRV.getModule(NicknameModule.class);
    }

    protected String cleanNickname(NicknameSyncConfig config, String nickname) {
        for (Map.Entry<Pattern, String> filter : config.nicknameRegexFilters.entrySet()) {
            nickname = filter.getKey().matcher(nickname).replaceAll(filter.getValue());
        }
        return nickname;
    }

    @Override
    protected CompletableFuture<String> getDiscord(NicknameSyncConfig config, long userId) {
        DiscordGuild guild = discordSRV.discordAPI().getGuildById(config.serverId);
        if (guild == null) {
            return CompletableFutureUtil.failed(new SyncFail(GenericSyncResults.GUILD_NOT_FOUND));
        }

        return guild.retrieveMemberById(userId)
                .thenApply(DiscordGuildMember::getNickname)
                .thenApply(nickname -> cleanNickname(config, nickname));
    }

    @Override
    protected CompletableFuture<String> getGame(NicknameSyncConfig config, UUID playerUUID) {
        NicknameModule module = getModule();
        if (module == null) {
            return CompletableFutureUtil.failed(new SyncFail(GenericSyncResults.MODULE_NOT_FOUND));
        }

        return module.getNickname(playerUUID)
                .thenApply(nickname -> cleanNickname(config, nickname));
    }

    @Override
    protected CompletableFuture<ISyncResult> applyDiscord(
            NicknameSyncConfig config,
            long userId,
            @Nullable String newNickname
    ) {
        DiscordGuild guild = discordSRV.discordAPI().getGuildById(config.serverId);
        if (guild == null) {
            return CompletableFuture.completedFuture(GenericSyncResults.GUILD_NOT_FOUND);
        }

        return guild.retrieveMemberById(userId)
                .thenApply(member -> {
                    Member jdaMember = member.asJDA();
                    if (!jdaMember.getGuild().getSelfMember().canInteract(jdaMember)) {
                        throw new SyncFail(GenericSyncResults.MEMBER_CANNOT_INTERACT);
                    }
                    return jdaMember;
                })
                .thenCompose(member -> member.modifyNickname(newNickname).submit())
                .thenApply(v -> NicknameSyncResult.SET_DISCORD);
    }

    @Override
    protected CompletableFuture<ISyncResult> applyGame(
            NicknameSyncConfig config,
            UUID playerUUID,
            @Nullable String newNickname
    ) {
        NicknameModule module = getModule();
        if (module == null) {
            return CompletableFutureUtil.failed(new SyncFail(GenericSyncResults.MODULE_NOT_FOUND));
        }

        return module.setNickname(playerUUID, newNickname).thenApply(v -> NicknameSyncResult.SET_GAME);
    }
}
