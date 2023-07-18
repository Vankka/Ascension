package com.discordsrv.common.config.main;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MessagesMainConfig {

    @Comment("If there should be multiple messages files, one for every language")
    public boolean multiple = false;

    @Comment("The 3 letter ISO 639-2 code for the default language, if left blank the system default will be used")
    public String defaultLanguage = "eng";
}
